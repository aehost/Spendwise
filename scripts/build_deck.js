const pptxgen = require("pptxgenjs");
const p = new pptxgen();
p.defineLayout({ name: "W", width: 13.333, height: 7.5 });
p.layout = "W";

// ── Brand palette (from the app) ─────────────────────────────────
const C = {
  bg:    "080B14",
  bg2:   "0D1425",
  card:  "141D2E",
  card2: "1A2640",
  border:"243352",
  primary:"7C6BFF",
  primaryLt:"9D8FFF",
  cyan:  "00D4FF",
  mint:  "00E5A0",
  coral: "FF4D6A",
  amber: "FFB547",
  gold:  "FFD700",
  text:  "F0F4FF",
  sub:   "8A9BC4",
  muted: "5A6A92",
  white: "FFFFFF",
};
const HF = "Trebuchet MS";
const BF = "Calibri";
const W = 13.333, H = 7.5, M = 0.7;

function bg(s, color) { s.background = { color: color || C.bg }; }

// Small label "eyebrow" above titles
function eyebrow(s, text, x, y, color) {
  s.addText(text.toUpperCase(), {
    x, y, w: 8, h: 0.3, fontFace: HF, fontSize: 12, bold: true,
    color: color || C.cyan, charSpacing: 3, align: "left",
  });
}
function title(s, text, x, y, w, size) {
  s.addText(text, {
    x, y, w: w || 11.9, h: 1.1, fontFace: HF, fontSize: size || 36, bold: true,
    color: C.text, align: "left",
  });
}
// rounded card
function card(s, x, y, w, h, fill, line) {
  s.addShape(p.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.12,
    fill: { color: fill || C.card },
    line: line ? { color: line, width: 1 } : { color: C.border, width: 1 },
  });
}
// icon chip: filled circle with a glyph
function chip(s, x, y, d, fillHex, glyph, glyphColor, glyphSize) {
  s.addShape(p.ShapeType.ellipse, { x, y, w: d, h: d, fill: { color: fillHex } });
  s.addText(glyph, {
    x, y, w: d, h: d, align: "center", valign: "middle",
    fontFace: HF, fontSize: glyphSize || 18, bold: true, color: glyphColor || C.white,
  });
}

// ═══════════════════════════════════════════════════════════════
// SLIDE 1 — Title
// ═══════════════════════════════════════════════════════════════
let s = p.addSlide(); bg(s);
// soft accent ellipses
s.addShape(p.ShapeType.ellipse, { x: 9.6, y: -1.6, w: 5.5, h: 5.5, fill: { color: C.primary, transparency: 80 }, line: { type: "none" } });
s.addShape(p.ShapeType.ellipse, { x: 11.0, y: 3.8, w: 4.2, h: 4.2, fill: { color: C.cyan, transparency: 86 }, line: { type: "none" } });
// brand mark
chip(s, M, 0.8, 0.95, C.primary, "₹", C.white, 34);
s.addText("SpendWise", { x: M + 1.15, y: 0.86, w: 6, h: 0.85, fontFace: HF, fontSize: 30, bold: true, color: C.text, valign: "middle" });

s.addText("Your money,", { x: M, y: 2.7, w: 11, h: 1.0, fontFace: HF, fontSize: 54, bold: true, color: C.text });
s.addText([
  { text: "on autopilot.", options: { color: C.primaryLt } },
], { x: M, y: 3.62, w: 11, h: 1.0, fontFace: HF, fontSize: 54, bold: true });
s.addText("Automatic, private, and motivating personal finance for India —\nno manual logging, no bank logins.", {
  x: M, y: 4.85, w: 9.5, h: 0.9, fontFace: BF, fontSize: 17, color: C.sub, lineSpacingMultiple: 1.15,
});
s.addText("INVESTOR BRIEF   ·   v5.0", { x: M, y: 6.6, w: 8, h: 0.4, fontFace: HF, fontSize: 12, bold: true, color: C.muted, charSpacing: 3 });

// ═══════════════════════════════════════════════════════════════
// SLIDE 2 — The Problem
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s, C.bg2);
eyebrow(s, "The Problem", M, 0.6, C.coral);
title(s, "Managing money today is broken", M, 0.95);
s.addText("Especially in India, where spending is fragmented across UPI, cards, net-banking, wallets and EMIs.", {
  x: M, y: 1.95, w: 11.5, h: 0.5, fontFace: BF, fontSize: 15, color: C.sub,
});
const probs = [
  ["Manual tracking fails", "Every app expects you to log expenses by hand. 80%+ of users quit within weeks — data goes stale.", C.coral],
  ["Money is fragmented", "HDFC UPI, an ICICI card, an SBI account, NEFT, wallets — no single source of truth.", C.amber],
  ["Privacy fear", "Aggregator apps demand net-banking logins or bank links. Users rightly refuse.", C.cyan],
  ["No proactive guidance", "Bank apps show balances, not behavior. You learn you overspent after the month ends.", C.primaryLt],
];
let cx = M, cy = 2.7, cw = 5.85, ch = 1.85, gx = 0.4, gy = 0.3;
probs.forEach((pr, i) => {
  const x = cx + (i % 2) * (cw + gx);
  const y = cy + Math.floor(i / 2) * (ch + gy);
  card(s, x, y, cw, ch);
  s.addShape(p.ShapeType.roundRect, { x: x, y: y, w: 0.09, h: ch, rectRadius: 0.04, fill: { color: pr[2] }, line: { type: "none" } });
  s.addText(pr[0], { x: x + 0.35, y: y + 0.22, w: cw - 0.6, h: 0.45, fontFace: HF, fontSize: 18, bold: true, color: C.text });
  s.addText(pr[1], { x: x + 0.35, y: y + 0.75, w: cw - 0.6, h: 1.0, fontFace: BF, fontSize: 13.5, color: C.sub, lineSpacingMultiple: 1.1 });
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 3 — The Solution (3 pillars)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s);
eyebrow(s, "Our Solution", M, 0.6, C.mint);
title(s, "SpendWise: effortless and private", M, 0.95);
s.addText("It reads the bank SMS and email receipts you already get, turns them into a clean ledger automatically, and guides you before you overspend.", {
  x: M, y: 1.95, w: 11.8, h: 0.6, fontFace: BF, fontSize: 15, color: C.sub, lineSpacingMultiple: 1.1,
});
const pillars = [
  ["Automatic", "Bank SMS & Gmail receipts become categorized transactions in seconds. Zero manual entry.", C.primary, "A"],
  ["Private", "No bank logins. On-device parsing. Secrets in AES-256 encrypted storage. No data resale.", C.cyan, "P"],
  ["Motivating", "Budgets, goals, a health score and gamification turn discipline into a daily habit.", C.mint, "M"],
];
let px = M, pw = 3.84, ph = 3.4, pg = 0.3, py = 2.9;
pillars.forEach((pl, i) => {
  const x = px + i * (pw + pg);
  card(s, x, py, pw, ph);
  chip(s, x + 0.4, py + 0.45, 0.85, pl[2], pl[3], C.white, 30);
  s.addText(pl[0], { x: x + 0.4, y: py + 1.55, w: pw - 0.8, h: 0.5, fontFace: HF, fontSize: 22, bold: true, color: C.text });
  s.addText(pl[2] === C.primary ? "Effortless capture" : pl[2] === C.cyan ? "Privacy by design" : "Behavior change", { x: x + 0.4, y: py + 2.0, w: pw - 0.8, h: 0.3, fontFace: BF, fontSize: 12, italic: true, color: pl[2] });
  s.addText(pl[1], { x: x + 0.4, y: py + 2.4, w: pw - 0.8, h: 0.9, fontFace: BF, fontSize: 13, color: C.sub, lineSpacingMultiple: 1.12 });
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 4 — How it works (flow)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s, C.bg2);
eyebrow(s, "How It Works", M, 0.6, C.cyan);
title(s, "From a bank alert to insight — automatically", M, 0.95);
const steps = [
  ["Capture", "Bank SMS + Gmail\nreceipts (14+ banks)", C.primary],
  ["Parse", "Amount, merchant &\ncategory extracted", C.cyan],
  ["Ledger", "One unified, de-duped\ntransaction timeline", C.mint],
  ["Guide", "Budgets, alerts, goals\n& health score", C.amber],
];
let sw = 2.6, sh = 2.5, sgap = 0.5, sx = M, sy = 2.9;
steps.forEach((st, i) => {
  const x = sx + i * (sw + sgap);
  card(s, x, sy, sw, sh);
  chip(s, x + sw/2 - 0.45, sy + 0.35, 0.9, st[2], String(i + 1), C.white, 30);
  s.addText(st[0], { x: x, y: sy + 1.35, w: sw, h: 0.4, align: "center", fontFace: HF, fontSize: 19, bold: true, color: C.text });
  s.addText(st[1], { x: x + 0.2, y: sy + 1.8, w: sw - 0.4, h: 0.7, align: "center", fontFace: BF, fontSize: 12.5, color: C.sub, lineSpacingMultiple: 1.05 });
  if (i < steps.length - 1) {
    s.addText("→", { x: x + sw + 0.02, y: sy + 0.9, w: sgap, h: 0.6, align: "center", valign: "middle", fontFace: HF, fontSize: 26, bold: true, color: C.muted });
  }
});
s.addText("Real-time on every incoming bank SMS · background Gmail sync every 30 minutes", {
  x: M, y: 5.9, w: 12, h: 0.4, align: "center", fontFace: BF, fontSize: 13, italic: true, color: C.muted,
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 5 — Problem → Solution mapping
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s);
eyebrow(s, "Problem → Solution", M, 0.6, C.primaryLt);
title(s, "Every pain point, answered", M, 0.95);
const rows = [
  ["Manual logging is tedious", "Automatic SMS + email import — zero typing"],
  ["Money scattered everywhere", "One unified ledger across all banks & rails"],
  ["Fear of sharing bank logins", "App-password + on-device + AES-256 encryption"],
  ["Overspending found too late", "Predictive alerts & 80% / 100% push warnings"],
  ["Bills & dues slip through", "Auto-detected bill reminders from email/SMS"],
];
let ry = 2.35, rh = 0.82, rgap = 0.12;
rows.forEach((r, i) => {
  const y = ry + i * (rh + rgap);
  card(s, M, y, 5.75, rh, C.card);
  card(s, M + 5.95, y, 5.45, rh, C.card2);
  s.addText(r[0], { x: M + 0.3, y: y, w: 5.3, h: rh, valign: "middle", fontFace: BF, fontSize: 14, color: C.sub });
  chip(s, M + 5.55, y + rh/2 - 0.18, 0.36, C.mint, "✓", C.bg, 14);
  s.addText(r[1], { x: M + 6.25, y: y, w: 5.0, h: rh, valign: "middle", fontFace: BF, fontSize: 14, bold: true, color: C.text });
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 6 — Key features (2x3 grid)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s, C.bg2);
eyebrow(s, "Product", M, 0.6, C.cyan);
title(s, "One app for the whole money picture", M, 0.95);
const feats = [
  ["Auto-capture", "14+ banks · UPI, cards, NEFT/IMPS/RTGS · smart categorization", C.primary],
  ["Budgets & alerts", "Per-category limits, color states, push at 80% / 100%", C.amber],
  ["Goal planner", "Targets, ETA, required monthly saving, milestone nudges", C.mint],
  ["Health score", "0–100 financial-fitness gauge with pillar breakdown", C.cyan],
  ["Bills & dues", "Auto-detected reminders & credit-card due tracking", C.coral],
  ["Gamification", "XP, levels, streaks & round-up savings that retain", C.primaryLt],
];
let fx = M, fy = 2.35, fw = 3.84, fh = 1.95, fgx = 0.3, fgy = 0.28;
feats.forEach((f, i) => {
  const x = fx + (i % 3) * (fw + fgx);
  const y = fy + Math.floor(i / 3) * (fh + fgy);
  card(s, x, y, fw, fh);
  chip(s, x + 0.32, y + 0.3, 0.5, f[2], "●", C.bg, 12);
  s.addText(f[0], { x: x + 1.0, y: y + 0.32, w: fw - 1.2, h: 0.5, valign: "middle", fontFace: HF, fontSize: 17, bold: true, color: C.text });
  s.addText(f[1], { x: x + 0.32, y: y + 0.95, w: fw - 0.6, h: 0.9, fontFace: BF, fontSize: 12.5, color: C.sub, lineSpacingMultiple: 1.1 });
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 7 — Differentiation (comparison)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s);
eyebrow(s, "Why We Win", M, 0.6, C.mint);
title(s, "Effortless AND private — the trade-off others make", M, 0.95, 12.2, 28);
const cols = ["", "SpendWise", "Manual apps", "Aggregators"];
const grid = [
  ["Zero effort to maintain", "yes", "no", "part"],
  ["No bank credentials", "yes", "yes", "no"],
  ["On-device & encrypted", "yes", "part", "no"],
  ["Proactive predictive alerts", "yes", "no", "part"],
  ["Motivation / gamification", "yes", "no", "no"],
  ["India-first (UPI, EMI, ₹ format)", "yes", "part", "part"],
];
let tx = M, ty = 2.2, c0 = 5.0, cwid = 2.3, rowH = 0.62, hdrH = 0.55;
// header
s.addText(cols[1], { x: tx + c0, y: ty, w: cwid, h: hdrH, align: "center", valign: "middle", fontFace: HF, fontSize: 14, bold: true, color: C.primaryLt });
s.addText(cols[2], { x: tx + c0 + cwid, y: ty, w: cwid, h: hdrH, align: "center", valign: "middle", fontFace: HF, fontSize: 14, bold: true, color: C.sub });
s.addText(cols[3], { x: tx + c0 + 2*cwid, y: ty, w: cwid, h: hdrH, align: "center", valign: "middle", fontFace: HF, fontSize: 14, bold: true, color: C.sub });
// highlight SpendWise column
s.addShape(p.ShapeType.roundRect, { x: tx + c0 - 0.05, y: ty + hdrH, w: cwid + 0.1, h: rowH * grid.length + 0.1, rectRadius: 0.08, fill: { color: C.primary, transparency: 86 }, line: { color: C.primary, width: 1 } });
grid.forEach((g, i) => {
  const y = ty + hdrH + i * rowH;
  s.addText(g[0], { x: tx, y: y, w: c0 - 0.2, h: rowH, valign: "middle", fontFace: BF, fontSize: 13.5, color: C.text });
  for (let cI = 1; cI <= 3; cI++) {
    const cellX = tx + c0 + (cI - 1) * cwid;
    const v = g[cI];
    const mark = v === "yes" ? "✓" : v === "part" ? "~" : "—";
    const col = v === "yes" ? C.mint : v === "part" ? C.amber : C.muted;
    s.addText(mark, { x: cellX, y: y, w: cwid, h: rowH, align: "center", valign: "middle", fontFace: HF, fontSize: 18, bold: true, color: col });
  }
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 8 — Traction / status (big stats)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s, C.bg2);
eyebrow(s, "Status & Quality", M, 0.6, C.gold);
title(s, "Built, tested, and feature-complete", M, 0.95);
const stats = [
  ["14+", "banks supported", C.primary],
  ["1,006", "documented test cases", C.cyan],
  ["115", "unit tests passing", C.mint],
  ["0", "open defects", C.gold],
];
let stx = M, sty = 2.6, stw = 2.75, sth = 2.4, stg = 0.32;
stats.forEach((st, i) => {
  const x = stx + i * (stw + stg);
  card(s, x, sty, stw, sth);
  s.addText(st[0], { x: x, y: sty + 0.45, w: stw, h: 1.0, align: "center", fontFace: HF, fontSize: 52, bold: true, color: st[2] });
  s.addText(st[1], { x: x + 0.2, y: sty + 1.6, w: stw - 0.4, h: 0.6, align: "center", fontFace: BF, fontSize: 14, color: C.sub });
});
s.addText("Native Android (Kotlin · Compose · MVVM) · 7 background workers · shared design system · a full senior-architect audit closed 25 bugs.", {
  x: M, y: 5.5, w: 12, h: 0.6, align: "center", fontFace: BF, fontSize: 13.5, italic: true, color: C.muted, lineSpacingMultiple: 1.1,
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 9 — Roadmap
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s);
eyebrow(s, "Roadmap", M, 0.6, C.cyan);
title(s, "Where we're headed", M, 0.95);
const phases = [
  ["Near term", ["Recurring monthly budgets", "Richer charts & insights", "On-device ML categorization", "Multi-currency"], C.primary],
  ["Mid term", ["Shared / family budgets", "Investment & SIP tracking", "Credit-score insights", "iOS app"], C.cyan],
  ["Long term", ["Proactive savings automation", "Money-saving offer marketplace", "Optional advisor layer"], C.mint],
];
let phx = M, phw = 3.84, phh = 3.6, phg = 0.3, phy = 2.5;
phases.forEach((ph2, i) => {
  const x = phx + i * (phw + phg);
  card(s, x, phy, phw, phh);
  s.addShape(p.ShapeType.roundRect, { x: x + 0.35, y: phy + 0.35, w: 1.8, h: 0.45, rectRadius: 0.22, fill: { color: ph2[2], transparency: 78 }, line: { color: ph2[2], width: 1 } });
  s.addText(ph2[0], { x: x + 0.35, y: phy + 0.35, w: 1.8, h: 0.45, align: "center", valign: "middle", fontFace: HF, fontSize: 13, bold: true, color: ph2[2] });
  ph2[1].forEach((it, j) => {
    s.addText("•", { x: x + 0.4, y: phy + 1.15 + j * 0.55, w: 0.3, h: 0.45, fontFace: HF, fontSize: 14, bold: true, color: ph2[2] });
    s.addText(it, { x: x + 0.7, y: phy + 1.15 + j * 0.55, w: phw - 1.0, h: 0.45, valign: "middle", fontFace: BF, fontSize: 13, color: C.sub });
  });
});

// ═══════════════════════════════════════════════════════════════
// SLIDE 10 — The opportunity / closing (dark)
// ═══════════════════════════════════════════════════════════════
s = p.addSlide(); bg(s);
s.addShape(p.ShapeType.ellipse, { x: -1.8, y: 4.0, w: 6, h: 6, fill: { color: C.primary, transparency: 82 }, line: { type: "none" } });
s.addShape(p.ShapeType.ellipse, { x: 10.5, y: -2.0, w: 5, h: 5, fill: { color: C.cyan, transparency: 86 }, line: { type: "none" } });
eyebrow(s, "The Opportunity", M, 0.9, C.gold);
s.addText("300M+ digitally-active earners.\nOne effortless, private companion.", {
  x: M, y: 1.5, w: 11.5, h: 1.8, fontFace: HF, fontSize: 40, bold: true, color: C.text, lineSpacingMultiple: 1.05,
});
s.addText("India's UPI generation is drowning in fragmented, manual money management. SpendWise is the rare app that is effortless and private — the two qualities users refuse to trade off — already working across the country's major banks.", {
  x: M, y: 3.7, w: 10.8, h: 1.4, fontFace: BF, fontSize: 16, color: C.sub, lineSpacingMultiple: 1.2,
});
card(s, M, 5.5, 11.9, 1.15, C.card);
s.addText([
  { text: "Let's talk.  ", options: { color: C.text, bold: true } },
  { text: "Request a live demo and detailed metrics.", options: { color: C.sub } },
], { x: M + 0.4, y: 5.5, w: 11, h: 1.15, valign: "middle", fontFace: BF, fontSize: 17 });

p.writeFile({ fileName: "D:/Pravveen Personal/Spendwise/SpendWise_Investor_Deck.pptx" })
  .then(f => console.log("Saved:", f))
  .catch(e => { console.error(e); process.exit(1); });
