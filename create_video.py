#!/usr/bin/env python3
"""
SpendWise Automated Marketing Video Creator
===========================================
Pipeline:
  1. Generate AI voiceover (Google TTS — free, no API key)
  2. Generate ambient background music (numpy, no downloads needed)
  3. Record the animated marketing-demo.html scrolling in headless Chrome
  4. Mix voiceover + music, overlay onto video → SpendWise_Marketing_Video.mp4

Run via:  create_video.ps1  (handles all setup automatically)
Or:       python create_video.py --ffmpeg path/to/ffmpeg.exe
"""

import os
import sys
import time
import wave
import shutil
import argparse
import subprocess
import threading
from pathlib import Path

import numpy as np
from gtts import gTTS
from playwright.sync_api import sync_playwright

# ── PATHS ──────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).parent.resolve()
WORK_DIR   = SCRIPT_DIR / "_video_work"
OUTPUT     = SCRIPT_DIR / "SpendWise_Marketing_Video.mp4"
HTML_FILE  = SCRIPT_DIR / "marketing-demo.html"

# ── VOICEOVER SCRIPT (Video 1 — 60-90 second marketing ad) ────────────────
VOICEOVER_LINES = [
    "You check your balance. It's lower than you expected — and you have no idea why.",
    "Every day, your bank sends you the answer in those SMS alerts nobody reads.",
    "SpendWise reads those messages for you — automatically.",
    "The moment money moves, SpendWise captures it. Amount, merchant, bank account — done.",
    "Track multiple bank accounts in one place. HDFC, SBI, Axis — all consolidated, always live.",
    "Set monthly budgets. SpendWise shows you in real time exactly where you stand.",
    "Professional monthly reports — built automatically. One tap to download or share.",
    "Your data never leaves your phone. No bank passwords, no subscriptions, no ads.",
    "It is completely free. Always.",
    "Download SpendWise. Set up in thirty seconds. And finally — know where your money goes.",
]

# ── SCROLL TIMELINE ────────────────────────────────────────────────────────
# Each entry: (scroll_y_pixels, pause_seconds_at_this_position)
# These are approximate — the script caps them to actual page height
SCROLL_PLAN = [
    (0,    8.0),   # Hero — animated phone + counting balance
    (550,  4.0),   # Ticker + Stats row
    (1150, 7.0),   # Feature cards (top half)
    (1900, 5.0),   # Feature cards (bottom half)
    (2700, 7.0),   # SMS parsing demo (animated messages)
    (3500, 5.5),   # Multi-bank balance card
    (4200, 8.0),   # How it works (5 steps)
    (5000, 4.0),   # Transaction list demo
    (5700, 4.0),   # Security grid
    (6300, 5.0),   # CTA section
]

# ─────────────────────────────────────────────────────────────────────────
def banner(msg, step=None, total=5):
    prefix = f"[{step}/{total}] " if step else ""
    print(f"\n{'─'*55}")
    print(f"  {prefix}{msg}")
    print(f"{'─'*55}")

def run(cmd, cwd=None, label=""):
    """Run a subprocess, raise on failure with clean error output."""
    result = subprocess.run(
        [str(c) for c in cmd],
        capture_output=True, text=True, cwd=str(cwd) if cwd else None
    )
    if result.returncode != 0:
        tail = result.stderr[-800:] if result.stderr else "(no stderr)"
        raise RuntimeError(f"Command failed ({label}):\n{tail}")
    return result

def get_audio_duration(ffmpeg, wav_path):
    """Return duration of a WAV file in seconds using ffprobe/ffmpeg."""
    r = subprocess.run(
        [str(ffmpeg), "-i", str(wav_path)],
        capture_output=True, text=True
    )
    for line in r.stderr.split("\n"):
        if "Duration:" in line:
            try:
                t = line.split("Duration:")[1].split(",")[0].strip()
                h, m, s = t.split(":")
                return float(h) * 3600 + float(m) * 60 + float(s)
            except Exception:
                pass
    return 90.0  # safe fallback

# ── STEP 1: VOICEOVER ─────────────────────────────────────────────────────
def generate_voiceover(ffmpeg):
    banner("Generating AI voiceover (Google TTS — free)", step=1)
    vo_wavs = []

    for i, line in enumerate(VOICEOVER_LINES):
        mp3 = WORK_DIR / f"vo_{i:02d}.mp3"
        wav = WORK_DIR / f"vo_{i:02d}.wav"

        if not mp3.exists():
            print(f"  Line {i+1:>2}/{len(VOICEOVER_LINES)}: {line[:60]}…")
            try:
                tts = gTTS(text=line, lang="en", tld="co.in", slow=False)
                tts.save(str(mp3))
            except Exception as e:
                raise RuntimeError(
                    f"Google TTS failed (check internet connection): {e}"
                )
            time.sleep(0.4)  # be polite to Google's API
        else:
            print(f"  Line {i+1:>2}/{len(VOICEOVER_LINES)}: (cached)")

        # Convert MP3 → WAV (44100 Hz mono) for reliable concatenation
        if not wav.exists():
            run([ffmpeg, "-i", str(mp3), "-ar", "44100", "-ac", "1", "-y", str(wav)],
                label=f"mp3→wav line {i}")
        vo_wavs.append(wav)

    # Generate 0.65 s silence for gaps between lines
    silence = WORK_DIR / "silence.wav"
    if not silence.exists():
        run([ffmpeg, "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
             "-t", "0.65", "-ar", "44100", "-ac", "1", "-y", str(silence)],
            label="silence")

    # Concat list (relative paths — run ffmpeg from WORK_DIR)
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
    print(f"\n  ✓ Voiceover ready — {duration:.1f} seconds total")
    return vo_full, duration

# ── STEP 2: BACKGROUND MUSIC ──────────────────────────────────────────────
def generate_music(total_duration_sec):
    banner("Generating ambient background music", step=2)
    output = WORK_DIR / "background_music.wav"

    if output.exists():
        print("  (cached)")
        return output

    sr = 44100
    dur = total_duration_sec + 6  # a few extra seconds buffer
    n  = int(dur * sr)
    t  = np.linspace(0, dur, n, endpoint=False)
    audio = np.zeros(n, dtype=np.float64)

    # C – G – Am – F chord progression (classic corporate/uplifting feel)
    chords = [
        [261.63, 329.63, 392.00],   # C  major
        [392.00, 493.88, 587.33],   # G  major
        [220.00, 261.63, 329.63],   # A  minor
        [174.61, 220.00, 261.63],   # F  major
    ]
    bass = [130.81, 195.99, 110.00, 87.31]  # C3, G3, A2, F2

    bar = 3.0          # seconds per chord change
    cycle = bar * 4    # full 4-chord cycle

    for ci, freqs in enumerate(chords):
        for cs in np.arange(ci * bar, dur, cycle):
            s = int(cs * sr)
            e = int(min(cs + bar, dur) * sr)
            if e <= s:
                continue
            lt = np.linspace(0, bar, e - s, endpoint=False)
            for freq in freqs:
                for harm, amp in zip([1, 2, 3], [0.50, 0.25, 0.08]):
                    decay = np.exp(-harm * 0.8 * lt)
                    audio[s:e] += 0.042 * amp * np.sin(2 * np.pi * freq * harm * lt) * decay

    # Bass layer
    for ci, freq in enumerate(bass):
        for cs in np.arange(ci * bar, dur, cycle):
            s = int(cs * sr)
            e = int(min(cs + bar, dur) * sr)
            if e <= s:
                continue
            lt = np.linspace(0, bar, e - s, endpoint=False)
            audio[s:e] += 0.055 * np.sin(2 * np.pi * freq * lt) * np.exp(-0.4 * lt)

    # Master fade-in (2 s) and fade-out (3 s)
    fi = int(2.0 * sr)
    fo = int(3.0 * sr)
    audio[:fi] *= np.linspace(0, 1, fi)
    audio[-fo:] *= np.linspace(1, 0, fo)

    # Normalise to 55% headroom so voiceover stays prominent
    peak = np.max(np.abs(audio))
    if peak > 0:
        audio = audio / peak * 0.55

    # Write stereo WAV (duplicate mono → stereo)
    pcm = (audio * 32767).astype(np.int16)
    stereo = np.column_stack([pcm, pcm])
    with wave.open(str(output), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(stereo.tobytes())

    print(f"  ✓ Music generated — {dur:.0f} seconds")
    return output

# ── STEP 3: RECORD BROWSER VIDEO ─────────────────────────────────────────
def record_browser(video_duration_sec):
    banner(f"Recording animated demo in headless Chrome (~{video_duration_sec:.0f}s)", step=3)

    vid_dir = WORK_DIR / "playwright_video"
    vid_dir.mkdir(exist_ok=True)

    # If a recording already exists (re-run), reuse it
    existing = list(vid_dir.glob("*.webm"))
    if existing:
        print("  (using cached recording)")
        return existing[0]

    url = HTML_FILE.as_uri()
    print(f"  Page: {url}")

    with sync_playwright() as pw:
        browser = pw.chromium.launch(
            headless=True,
            args=["--no-sandbox", "--disable-gpu"]
        )
        ctx = browser.new_context(
            viewport={"width": 1280, "height": 720},
            record_video_dir=str(vid_dir),
            record_video_size={"width": 1280, "height": 720},
            device_scale_factor=1,
        )
        page = ctx.new_page()

        print("  Loading page…")
        page.goto(url)
        page.wait_for_load_state("domcontentloaded")
        time.sleep(3.5)  # let CSS animations initialise

        # Get actual page height and scale scroll positions accordingly
        page_h = page.evaluate("() => document.body.scrollHeight")
        vp_h   = 720
        max_scroll = max(0, page_h - vp_h)
        print(f"  Page height: {page_h}px  |  Max scroll: {max_scroll}px")

        def scroll_to(target_y, smooth_steps=30, step_delay=0.05):
            """Scroll to target_y with smooth animation."""
            current = page.evaluate("() => window.scrollY")
            for step in range(1, smooth_steps + 1):
                y = int(current + (target_y - current) * step / smooth_steps)
                page.evaluate(f"window.scrollTo({{top:{y},behavior:'instant'}})")
                time.sleep(step_delay)

        print("  Scrolling through sections…")
        for raw_y, pause in SCROLL_PLAN:
            target_y = min(raw_y, max_scroll)
            scroll_to(target_y)
            label_pct = int(target_y / max(max_scroll, 1) * 100)
            print(f"    → scroll {target_y}px ({label_pct}%) — pause {pause}s")
            time.sleep(pause)

        # Slowly scroll back to top for a clean outro
        scroll_to(0, smooth_steps=60, step_delay=0.04)
        time.sleep(2.5)

        print("  Closing browser (saving recording)…")
        page.close()
        ctx.close()
        browser.close()

    webms = list(vid_dir.glob("*.webm"))
    if not webms:
        raise FileNotFoundError(
            "Playwright did not save a video. "
            "Check that Chromium is installed: python -m playwright install chromium"
        )
    webm = webms[0]
    size_mb = webm.stat().st_size / (1024 * 1024)
    print(f"  ✓ Raw video: {webm.name}  ({size_mb:.1f} MB)")
    return webm

# ── STEP 4: MIX AUDIO ─────────────────────────────────────────────────────
def mix_audio(ffmpeg, vo_wav, music_wav, video_duration_sec):
    banner("Mixing voiceover + background music", step=4)
    mixed = WORK_DIR / "mixed_audio.wav"

    # VO starts 1.5 s into the video (after the logo reveal)
    # Music sits at 17% volume underneath
    run([
        ffmpeg,
        "-i", str(vo_wav),
        "-i", str(music_wav),
        "-filter_complex",
        "[0:a]adelay=1500|1500,volume=1.05[vo];"
        "[1:a]volume=0.17[music];"
        "[vo][music]amix=inputs=2:duration=first:dropout_transition=2[out]",
        "-map", "[out]",
        "-ar", "44100", "-ac", "2",
        "-t", str(video_duration_sec + 4),
        "-y", str(mixed),
    ], label="audio mix")

    print("  ✓ Audio mixed")
    return mixed

# ── STEP 5: COMBINE VIDEO + AUDIO → MP4 ──────────────────────────────────
def produce_mp4(ffmpeg, webm_path, mixed_audio_path, video_duration_sec):
    banner("Rendering final MP4", step=5)

    run([
        ffmpeg,
        # Raw browser recording
        "-i", str(webm_path),
        # Mixed audio
        "-i", str(mixed_audio_path),
        # Video codec — H.264, good quality, broad compatibility
        "-c:v", "libx264",
        "-preset", "medium",
        "-crf", "22",
        "-pix_fmt", "yuv420p",
        # Audio codec — AAC 192k
        "-c:a", "aac", "-b:a", "192k",
        # End when shorter stream ends; add 2 s fade-out on audio
        "-af", "afade=t=out:st={:.1f}:d=2".format(video_duration_sec - 2),
        "-shortest",
        "-movflags", "+faststart",   # optimise for web streaming
        "-y", str(OUTPUT),
    ], label="final render")

    size_mb = OUTPUT.stat().st_size / (1024 * 1024)
    print(f"\n  ✓ MP4 ready: {OUTPUT}")
    print(f"    Size: {size_mb:.1f} MB")

# ── MAIN ──────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="SpendWise Video Creator")
    parser.add_argument("--ffmpeg", default="ffmpeg",
                        help="Path to ffmpeg executable")
    parser.add_argument("--clean", action="store_true",
                        help="Delete cached work files and start fresh")
    args = parser.parse_args()

    ffmpeg = Path(args.ffmpeg)

    print("\n🎬  SpendWise Automated Video Creator")
    print("    Output →", OUTPUT)

    # Verify ffmpeg works
    try:
        subprocess.run([str(ffmpeg), "-version"], capture_output=True, check=True)
    except Exception:
        print(f"\nERROR: ffmpeg not found at '{ffmpeg}'")
        print("       Run create_video.ps1 — it installs ffmpeg automatically.")
        sys.exit(1)

    # Verify HTML asset exists
    if not HTML_FILE.exists():
        print(f"\nERROR: {HTML_FILE} not found.")
        print("       Make sure marketing-demo.html is in the Spendwise folder.")
        sys.exit(1)

    # Clean flag
    if args.clean and WORK_DIR.exists():
        print("  Cleaning previous work files…")
        shutil.rmtree(WORK_DIR)

    WORK_DIR.mkdir(exist_ok=True)

    try:
        t0 = time.time()

        vo_wav, vo_dur        = generate_voiceover(ffmpeg)
        music_wav             = generate_music(vo_dur)
        webm                  = record_browser(vo_dur)
        mixed                 = mix_audio(ffmpeg, vo_wav, music_wav, vo_dur)
        produce_mp4(ffmpeg, webm, mixed, vo_dur)

        elapsed = int(time.time() - t0)
        m, s = divmod(elapsed, 60)

        print(f"\n{'='*55}")
        print(f"  ALL DONE in {m}m {s}s!")
        print(f"  {OUTPUT}")
        print(f"{'='*55}\n")

        # Clean up temp files (keep work dir only on error)
        print("  Cleaning temporary files…")
        shutil.rmtree(WORK_DIR, ignore_errors=True)

    except KeyboardInterrupt:
        print("\n\nCancelled by user.")
        sys.exit(0)
    except Exception as exc:
        print(f"\n{'='*55}")
        print(f"  ERROR: {exc}")
        print(f"{'='*55}")
        print("\n  Tip: Run with --clean to discard cached files and retry:")
        print(f"  python create_video.py --ffmpeg {ffmpeg} --clean\n")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
