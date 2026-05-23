#!/usr/bin/env python3
"""
SpendWise Automated Marketing Video Creator
===========================================
Pipeline:
  1. Generate AI voiceover (Microsoft Edge Neural TTS - deep Indian English)
  2. Generate professional background music (drums + bass + pads + stabs)
  3. Record the animated marketing-demo.html scrolling in headless Chrome
  4. Mix voiceover + music, overlay onto video -> SpendWise_Marketing_Video.mp4
"""

import os
import sys
import time
import wave
import shutil
import asyncio
import argparse
import subprocess
from pathlib import Path

import numpy as np

# Try edge-tts (neural voice, much better quality)
try:
    import edge_tts
    USE_EDGE_TTS = True
except ImportError:
    USE_EDGE_TTS = False
    try:
        from gtts import gTTS
    except ImportError:
        print("ERROR: Install voice package:  pip install edge-tts")
        sys.exit(1)

from playwright.sync_api import sync_playwright

# ── PATHS ──────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).parent.resolve()
WORK_DIR   = SCRIPT_DIR / "_video_work"
OUTPUT     = SCRIPT_DIR / "SpendWise_Marketing_Video.mp4"
HTML_FILE  = SCRIPT_DIR / "marketing-demo.html"

# ── VOICE SETTINGS ─────────────────────────────────────────────────────────
# en-IN-PrabhatNeural = deep male Indian English neural voice
VOICE       = "en-IN-PrabhatNeural"
VOICE_RATE  = "-8%"    # slightly slower for gravitas
VOICE_PITCH = "-4Hz"   # lower pitch for bass feel

# ── VOICEOVER SCRIPT ───────────────────────────────────────────────────────
VOICEOVER_LINES = [
    "You check your balance. It's lower than expected — and you have no idea why.",
    "Every day, your bank sends you the answer in those SMS alerts nobody reads.",
    "SpendWise reads those messages for you — automatically.",
    "The moment money moves, SpendWise captures it. Amount, merchant, bank account — done.",
    "Multiple bank accounts — HDFC, SBI, Axis — one consolidated dashboard, always live.",
    "Set monthly budgets. SpendWise shows you exactly where you stand — in real time.",
    "Professional monthly reports — built automatically. One tap to download or share.",
    "Your data never leaves your phone. No bank passwords. No subscriptions. No ads.",
    "Completely free. Always.",
    "Download SpendWise. Set up in thirty seconds. Finally — know where your money goes.",
]

# ── SCROLL TIMELINE ────────────────────────────────────────────────────────
SCROLL_PLAN = [
    (0,    8.0),
    (550,  4.0),
    (1150, 7.0),
    (1900, 5.0),
    (2700, 7.0),
    (3500, 5.5),
    (4200, 8.0),
    (5000, 4.0),
    (5700, 4.0),
    (6300, 5.0),
]

# ─────────────────────────────────────────────────────────────────────────
def banner(msg, step=None, total=5):
    prefix = f"[{step}/{total}] " if step else ""
    print(f"\n{'─'*58}")
    print(f"  {prefix}{msg}")
    print(f"{'─'*58}")

def run(cmd, cwd=None, label=""):
    result = subprocess.run(
        [str(c) for c in cmd],
        capture_output=True, text=True, cwd=str(cwd) if cwd else None
    )
    if result.returncode != 0:
        tail = result.stderr[-1000:] if result.stderr else "(no stderr)"
        raise RuntimeError(f"Command failed ({label}):\n{tail}")
    return result

def get_audio_duration(ffmpeg, wav_path):
    r = subprocess.run([str(ffmpeg), "-i", str(wav_path)], capture_output=True, text=True)
    for line in r.stderr.split("\n"):
        if "Duration:" in line:
            try:
                t = line.split("Duration:")[1].split(",")[0].strip()
                h, m, s = t.split(":")
                return float(h) * 3600 + float(m) * 60 + float(s)
            except Exception:
                pass
    return 90.0

# ── STEP 1: VOICEOVER ─────────────────────────────────────────────────────

async def _edge_speak(text, output_mp3):
    comm = edge_tts.Communicate(text, VOICE, rate=VOICE_RATE, pitch=VOICE_PITCH)
    await comm.save(str(output_mp3))

def generate_voiceover(ffmpeg):
    if USE_EDGE_TTS:
        banner(f"Generating voiceover  [{VOICE}]", step=1)
        print(f"  Rate: {VOICE_RATE}  Pitch: {VOICE_PITCH}  (Microsoft Neural TTS)")
    else:
        banner("Generating voiceover (Google TTS fallback)", step=1)
        print("  TIP: pip install edge-tts  for a much better deep voice")

    vo_wavs = []

    for i, line in enumerate(VOICEOVER_LINES):
        mp3    = WORK_DIR / f"vo_{i:02d}.mp3"
        wav_eq = WORK_DIR / f"vo_{i:02d}_eq.wav"

        if not mp3.exists():
            print(f"  Line {i+1:>2}/{len(VOICEOVER_LINES)}: {line[:60]}...")
            if USE_EDGE_TTS:
                asyncio.run(_edge_speak(line, mp3))
            else:
                tts = gTTS(text=line, lang="en", tld="co.in", slow=False)
                tts.save(str(mp3))
            time.sleep(0.3)
        else:
            print(f"  Line {i+1:>2}/{len(VOICEOVER_LINES)}: (cached)")

        # Convert MP3 -> WAV + voice enhancement:
        #   bass boost  : low shelf +5dB @ 120 Hz  (warm, full body)
        #   presence    : peak +3dB  @ 3000 Hz     (clarity, cuts through music)
        #   compress    : 3:1 ratio, -20 dB thresh  (even dynamics)
        #   loudnorm    : broadcast-level loudness
        if not wav_eq.exists():
            run([
                ffmpeg, "-i", str(mp3),
                "-af", (
                    "equalizer=f=120:width_type=o:width=2:g=5,"
                    "equalizer=f=3000:width_type=o:width=2:g=3,"
                    "acompressor=threshold=-20dB:ratio=3:attack=5:release=100:makeup=3,"
                    "loudnorm"
                ),
                "-ar", "44100", "-ac", "1", "-y", str(wav_eq),
            ], label=f"voice-eq line {i}")

        vo_wavs.append(wav_eq)

    # 0.75 s silence gap between lines
    silence = WORK_DIR / "silence.wav"
    if not silence.exists():
        run([ffmpeg, "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
             "-t", "0.75", "-ar", "44100", "-ac", "1", "-y", str(silence)],
            label="silence")

    concat_txt = WORK_DIR / "vo_concat.txt"
    with open(concat_txt, "w") as f:
        for wav in vo_wavs:
            f.write(f"file '{wav.name}'\n")
            f.write(f"file '{silence.name}'\n")

    vo_full = WORK_DIR / "voiceover_full.wav"
    if not vo_full.exists():
        run([ffmpeg, "-f", "concat", "-safe", "0",
             "-i", "vo_concat.txt", "-y", "voiceover_full.wav"],
            cwd=WORK_DIR, label="concat VO")

    duration = get_audio_duration(ffmpeg, vo_full)
    print(f"\n  Voice ready  |  {duration:.1f} s total")
    return vo_full, duration


# ── STEP 2: PROFESSIONAL BACKGROUND MUSIC ─────────────────────────────────

def _saw(freq, t, harmonics=10):
    """Band-limited sawtooth (additive synthesis) — avoids aliasing artifacts."""
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        out += ((-1) ** (k + 1)) * np.sin(2 * np.pi * k * freq * t) / k
    return out * (2.0 / np.pi)

def _adsr(n, sr, attack=0.01, decay=0.1, sustain=0.8, release=0.35):
    env = np.full(n, sustain, dtype=np.float64)
    a = min(int(attack * sr), n)
    d = min(int(decay  * sr), n - a)
    r = min(int(release* sr), n)
    if a > 0: env[:a] = np.linspace(0.0, 1.0, a)
    if d > 0: env[a:a+d] = np.linspace(1.0, sustain, d)
    if r > 0: env[-r:] = np.linspace(sustain, 0.0, r)
    return env

def generate_music(total_duration_sec):
    banner("Generating professional background music", step=2)
    output = WORK_DIR / "background_music.wav"
    if output.exists():
        print("  (cached)")
        return output

    sr  = 44100
    dur = total_duration_sec + 6
    n   = int(dur * sr)
    audio = np.zeros(n, dtype=np.float64)

    np.random.seed(42)

    BPM  = 120
    beat = 60.0 / BPM   # 0.50 s
    bar  = beat * 4      # 2.00 s

    # Em - C - G - D  (inspiring, works beautifully for corporate/aspirational)
    # 2 bars per chord => 4.0 s each => 16.0 s cycle
    chords = [
        # (bass_hz, pad_notes_hz)
        (82.41,  [164.81, 196.00, 246.94]),  # Em  E2 | E3 G3 B3
        (65.41,  [130.81, 164.81, 196.00]),  # C   C2 | C3 E3 G3
        (98.00,  [196.00, 246.94, 293.66]),  # G   G2 | G3 B3 D4
        (73.42,  [146.83, 185.00, 220.00]),  # D   D2 | D3 F#3 A3
    ]
    chord_dur   = bar * 2       # 4.0 s
    chord_cycle = chord_dur * 4  # 16.0 s

    # ── PRE-BAKE DRUM SAMPLES ──────────────────────────────────────────────

    def make_kick():
        ln = int(0.45 * sr)
        t  = np.linspace(0, 0.45, ln)
        # Pitch envelope: 150 Hz -> 45 Hz
        freq  = (150 - 45) * np.exp(-18 * t) + 45
        phase = np.cumsum(freq) / sr
        body  = np.sin(2 * np.pi * phase) * np.exp(-9 * t)
        click = np.random.randn(ln) * np.exp(-300 * t) * 0.22
        return np.clip((body + click) * 0.92, -1, 1)

    def make_snare():
        ln = int(0.30 * sr)
        t  = np.linspace(0, 0.30, ln)
        body = (0.40 * np.sin(2 * np.pi * 185 * t) +
                0.22 * np.sin(2 * np.pi * 270 * t)) * np.exp(-22 * t)
        snap = np.random.randn(ln) * np.exp(-28 * t) * 0.72
        return np.clip((body + snap) * 0.68, -1, 1)

    def make_hihat(open_hat=False):
        dur_h = 0.18 if open_hat else 0.055
        ln    = int(dur_h * sr)
        t     = np.linspace(0, dur_h, ln)
        decay = 10 if open_hat else 60
        return np.random.randn(ln) * np.exp(-decay * t) * 0.22

    def make_crash():
        ln = int(1.8 * sr)
        t  = np.linspace(0, 1.8, ln)
        return np.random.randn(ln) * np.exp(-3.5 * t) * 0.32

    kick_s   = make_kick()
    snare_s  = make_snare()
    hh_c     = make_hihat(False)
    hh_o     = make_hihat(True)
    crash_s  = make_crash()

    def stamp(sample, pos_sec):
        s = int(pos_sec * sr)
        e = min(s + len(sample), n)
        if e > s:
            audio[s:e] += sample[:e - s]

    # ── DRUMS (from bar 2 onward — 2-bar soft intro first) ────────────────
    drum_start = bar * 2   # 4.0 s
    pos = drum_start
    bar_idx = 0
    while pos < dur - bar:
        if bar_idx % 4 == 0:
            stamp(crash_s, pos)           # crash on every 4-bar phrase

        stamp(kick_s,  pos)              # beat 1
        stamp(hh_c,    pos + beat*0.5)   # and 1
        stamp(snare_s, pos + beat)        # beat 2
        stamp(hh_c,    pos + beat*1.5)   # and 2
        stamp(kick_s,  pos + beat*2)      # beat 3  (double kick on even bars)
        if bar_idx % 2 == 0:
            stamp(kick_s, pos + beat*2 + beat*0.5)  # kick ghost on "and 3"
        stamp(hh_c,    pos + beat*2.5)   # and 3
        stamp(snare_s, pos + beat*3)      # beat 4
        # Open hi-hat on "and 4" every other bar for groove
        hat = hh_o if bar_idx % 2 == 1 else hh_c
        stamp(hat,     pos + beat*3.5)

        pos += bar
        bar_idx += 1

    # ── BASS (enters at bar 1, i.e. from t=2.0 s) ─────────────────────────
    bass_start = bar   # 2.0 s

    for cyc in np.arange(bass_start, dur, chord_cycle):
        for ci, (bass_hz, _) in enumerate(chords):
            cs = cyc + ci * chord_dur
            if cs >= dur:
                break
            ce  = min(cs + chord_dur, dur)
            cln = int((ce - cs) * sr)
            if cln <= 0:
                continue
            tc  = np.linspace(0, ce - cs, cln, endpoint=False)

            # Sawtooth fundamental + sub-octave sine for punch
            saw = _saw(bass_hz, tc, harmonics=8)
            sub = np.sin(2 * np.pi * (bass_hz * 0.5) * tc)  # one octave down
            env = _adsr(cln, sr, attack=0.008, decay=0.18, sustain=0.72, release=0.4)
            mix = (saw * 0.55 + sub * 0.45) * env * 0.20

            s = int(cs * sr);  e = s + cln
            audio[s:e] += mix

    # ── CHORD PAD (detuned sawtooth ensemble — starts from t=0) ───────────
    VOICES        = 5
    DETUNE_CENTS  = 10

    for cyc in np.arange(0, dur, chord_cycle):
        for ci, (_, pad_freqs) in enumerate(chords):
            cs = cyc + ci * chord_dur
            if cs >= dur:
                break
            ce  = min(cs + chord_dur, dur)
            cln = int((ce - cs) * sr)
            if cln <= 0:
                continue
            tc  = np.linspace(0, ce - cs, cln, endpoint=False)

            # Slow attack on pad (it swells in, not abrupt)
            env = _adsr(cln, sr, attack=0.9, decay=0.4, sustain=0.68, release=0.7)
            pad = np.zeros(cln)

            for freq in pad_freqs:
                for vi in range(VOICES):
                    c_off = (vi - VOICES / 2) * DETUNE_CENTS / VOICES
                    f_det = freq * (2 ** (c_off / 1200))
                    pad  += _saw(f_det, tc, harmonics=6)

            pad = pad * env / (len(pad_freqs) * VOICES) * 0.11

            s = int(cs * sr);  e = s + cln
            audio[s:e] += pad

    # ── CHORD STABS (piano-like transients on beats 1 & 3, from drum_start) ──
    for cyc in np.arange(drum_start, dur, chord_cycle):
        for ci, (_, pad_freqs) in enumerate(chords):
            chord_start = cyc + ci * chord_dur

            for bar_off in [0, bar]:
                for beat_off in [0, beat * 2]:
                    spos = chord_start + bar_off + beat_off
                    if spos >= dur:
                        continue
                    sdur = min(0.55, dur - spos)
                    sln  = int(sdur * sr)
                    ts   = np.linspace(0, sdur, sln, endpoint=False)
                    # Fast attack, exponential decay (piano-like)
                    senv = np.exp(-7 * ts)
                    stab = np.zeros(sln)
                    for freq in pad_freqs:
                        stab += np.sin(2 * np.pi * freq * ts)
                        stab += 0.28 * np.sin(2 * np.pi * freq * 2 * ts)   # 2nd harmonic
                        stab += 0.08 * np.sin(2 * np.pi * freq * 3 * ts)   # 3rd harmonic
                    stab = stab * senv / len(pad_freqs) * 0.13

                    s = int(spos * sr);  e = min(s + sln, n)
                    audio[s:e] += stab[:e - s]

    # ── MASTER: fade-in 1 s, fade-out 3.5 s, normalise ───────────────────
    fi = int(1.0 * sr)
    fo = int(3.5 * sr)
    audio[:fi]  *= np.linspace(0, 1, fi)
    audio[-fo:] *= np.linspace(1, 0, fo)

    peak = np.max(np.abs(audio))
    if peak > 0:
        audio = audio / peak * 0.62

    # Stereo widening: 0.3 ms inter-channel delay
    pcm = np.clip(audio * 32767, -32767, 32767).astype(np.int16)
    delay_samp = int(0.0003 * sr)
    stereo = np.column_stack([pcm, np.roll(pcm, delay_samp)])

    with wave.open(str(output), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(stereo.tobytes())

    print(f"  Music ready  |  {dur:.0f} s  |  120 BPM  |  Em-C-G-D  |  drums+bass+pad+stabs")
    return output


# ── STEP 3: RECORD BROWSER VIDEO ─────────────────────────────────────────

def record_browser(video_duration_sec):
    banner(f"Recording animated demo in headless Chrome  (~{video_duration_sec:.0f} s)", step=3)

    vid_dir = WORK_DIR / "playwright_video"
    vid_dir.mkdir(exist_ok=True)

    existing = list(vid_dir.glob("*.webm"))
    if existing:
        print("  (using cached recording)")
        return existing[0]

    url = HTML_FILE.as_uri()
    print(f"  Page: {url}")

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True, args=["--no-sandbox", "--disable-gpu"])
        ctx = browser.new_context(
            viewport={"width": 1280, "height": 720},
            record_video_dir=str(vid_dir),
            record_video_size={"width": 1280, "height": 720},
            device_scale_factor=1,
        )
        page = ctx.new_page()

        print("  Loading page...")
        page.goto(url)
        page.wait_for_load_state("domcontentloaded")
        time.sleep(3.5)

        page_h     = page.evaluate("() => document.body.scrollHeight")
        max_scroll = max(0, page_h - 720)
        print(f"  Page height: {page_h}px  |  Max scroll: {max_scroll}px")

        def scroll_to(target_y, steps=30, delay=0.05):
            current = page.evaluate("() => window.scrollY")
            for i in range(1, steps + 1):
                y = int(current + (target_y - current) * i / steps)
                page.evaluate(f"window.scrollTo({{top:{y},behavior:'instant'}})")
                time.sleep(delay)

        print("  Scrolling through sections...")
        for raw_y, pause in SCROLL_PLAN:
            target_y = min(raw_y, max_scroll)
            scroll_to(target_y)
            pct = int(target_y / max(max_scroll, 1) * 100)
            print(f"    -> {target_y}px ({pct}%)  pause {pause}s")
            time.sleep(pause)

        scroll_to(0, steps=60, delay=0.04)
        time.sleep(2.5)

        print("  Closing browser (saving recording)...")
        page.close()
        ctx.close()
        browser.close()

    webms = list(vid_dir.glob("*.webm"))
    if not webms:
        raise FileNotFoundError(
            "Playwright did not save a video. "
            "Ensure Chromium is installed: python -m playwright install chromium"
        )
    webm = webms[0]
    print(f"  Raw video: {webm.name}  ({webm.stat().st_size/1024/1024:.1f} MB)")
    return webm


# ── STEP 4: MIX AUDIO ─────────────────────────────────────────────────────

def mix_audio(ffmpeg, vo_wav, music_wav, video_duration_sec):
    banner("Mixing voiceover + background music", step=4)
    mixed = WORK_DIR / "mixed_audio.wav"

    # VO starts 1.0 s into video (after logo reveal)
    # Music at 30% — punchy but never drowns the voice
    run([
        ffmpeg,
        "-i", str(vo_wav),
        "-i", str(music_wav),
        "-filter_complex",
        "[0:a]adelay=1000|1000,volume=1.1[vo];"
        "[1:a]volume=0.30[music];"
        "[vo][music]amix=inputs=2:duration=first:dropout_transition=2[out]",
        "-map", "[out]",
        "-ar", "44100", "-ac", "2",
        "-t", str(video_duration_sec + 4),
        "-y", str(mixed),
    ], label="audio mix")

    print("  Audio mixed")
    return mixed


# ── STEP 5: COMBINE VIDEO + AUDIO -> MP4 ─────────────────────────────────

def produce_mp4(ffmpeg, webm_path, mixed_audio_path, video_duration_sec):
    banner("Rendering final MP4", step=5)

    run([
        ffmpeg,
        "-i", str(webm_path),
        "-i", str(mixed_audio_path),
        "-c:v", "libx264", "-preset", "medium", "-crf", "22",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k",
        "-af", "afade=t=out:st={:.1f}:d=2.5".format(max(0, video_duration_sec - 2.5)),
        "-shortest",
        "-movflags", "+faststart",
        "-y", str(OUTPUT),
    ], label="final render")

    size_mb = OUTPUT.stat().st_size / (1024 * 1024)
    print(f"\n  MP4 ready: {OUTPUT}")
    print(f"  Size: {size_mb:.1f} MB")


# ── MAIN ──────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="SpendWise Video Creator")
    parser.add_argument("--ffmpeg", default="ffmpeg", help="Path to ffmpeg executable")
    parser.add_argument("--clean",  action="store_true", help="Delete cached files and restart")
    args = parser.parse_args()

    ffmpeg = Path(args.ffmpeg)

    print("\n  SpendWise Automated Video Creator")
    print("  Output ->", OUTPUT)

    try:
        subprocess.run([str(ffmpeg), "-version"], capture_output=True, check=True)
    except Exception:
        print(f"\nERROR: ffmpeg not found at '{ffmpeg}'")
        print("       Run create_video.ps1 — it installs ffmpeg automatically.")
        sys.exit(1)

    if not HTML_FILE.exists():
        print(f"\nERROR: {HTML_FILE} not found.")
        sys.exit(1)

    if args.clean and WORK_DIR.exists():
        print("  Cleaning previous work files...")
        shutil.rmtree(WORK_DIR)

    WORK_DIR.mkdir(exist_ok=True)

    try:
        t0 = time.time()

        vo_wav, vo_dur = generate_voiceover(ffmpeg)
        music_wav      = generate_music(vo_dur)
        webm           = record_browser(vo_dur)
        mixed          = mix_audio(ffmpeg, vo_wav, music_wav, vo_dur)
        produce_mp4(ffmpeg, webm, mixed, vo_dur)

        m, s = divmod(int(time.time() - t0), 60)
        print(f"\n{'='*58}")
        print(f"  ALL DONE in {m}m {s}s!")
        print(f"  {OUTPUT}")
        print(f"{'='*58}\n")

        shutil.rmtree(WORK_DIR, ignore_errors=True)

    except KeyboardInterrupt:
        print("\n\nCancelled.")
        sys.exit(0)
    except Exception as exc:
        print(f"\n{'='*58}")
        print(f"  ERROR: {exc}")
        print(f"{'='*58}")
        print(f"\n  Retry with clean cache:")
        print(f"  python create_video.py --ffmpeg {ffmpeg} --clean\n")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
