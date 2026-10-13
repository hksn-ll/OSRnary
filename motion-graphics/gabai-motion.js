/* ==========================================================================
   GabAI — Motion Graphics Showcase
   Pure Canvas 2D · zero dependencies · 1920×1080 · ~79 s timeline
   Scenes mirror the 5 core engines documented in SYSTEM_WORKFLOW_AUDIT.md.
   Everything (logo, bubble, QR, UI) is drawn as vectors so the canvas is never
   tainted and the WebM export works even when opened via file://.
   ========================================================================== */
(() => {
  'use strict';

  /* ---------- Stage & Rendering Context ---------- */
  const W = 1920, H = 1080;
  let canvas = document.getElementById('stage');
  let ctx = canvas ? canvas.getContext('2d') : null;
  if (canvas) {
    canvas.width = W; canvas.height = H;
  }

  /* ---------- Brand tokens (app/src/main/res/values/colors.xml) ---------- */
  const C = {
    primary: '#6C5CE7', primaryLight: '#8A84FF', primaryVariant: '#A29BFE',
    secondary: '#A0C4FF', green: '#00B894', gold: '#FDCB6E', amber: '#F1C40F',
    lavender: '#D6BCFA', blue: '#0984E3', red: '#E74C3C', orange: '#E17055',
    pink: '#E84393', ink: '#2D3436', muted: '#636E72',
  };
  const FONT = '"Plus Jakarta Sans","Segoe UI",system-ui,sans-serif,"Segoe UI Emoji","Apple Color Emoji","Noto Color Emoji"';
  const MONO = '"JetBrains Mono","Cascadia Code",Consolas,monospace';
  const EMOJI = '"Segoe UI Emoji","Apple Color Emoji","Noto Color Emoji",sans-serif';

  /* ---------- Math & easing ---------- */
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const prog = (t, s, d) => clamp((t - s) / d);
  const E = {
    outCubic: t => 1 - Math.pow(1 - t, 3),
    inOutCubic: t => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
    outBack: t => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
    outExpo: t => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)),
    // Damped spring, mirrors the app's tactile press/pop feel
    spring: t => (t <= 0 ? 0 : t >= 1 ? 1 : 1 - Math.exp(-7 * t) * Math.cos(10 * t)),
  };
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function rgba(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  const typed = (s, p) => s.slice(0, Math.round(s.length * clamp(p)));

  /* ---------- Drawing primitives ---------- */
  // Squircle (continuous-curvature-ish corners) — the Bento card shape
  function squircle(x, y, w, h, r) {
    r = Math.max(0, Math.min(r, w / 2, h / 2));
    const c = Math.min(r * 1.25, w / 2, h / 2), k = c * 0.42;
    ctx.beginPath();
    ctx.moveTo(x + c, y);
    ctx.lineTo(x + w - c, y);
    ctx.bezierCurveTo(x + w - k, y, x + w, y + k, x + w, y + c);
    ctx.lineTo(x + w, y + h - c);
    ctx.bezierCurveTo(x + w, y + h - k, x + w - k, y + h, x + w - c, y + h);
    ctx.lineTo(x + c, y + h);
    ctx.bezierCurveTo(x + k, y + h, x, y + h - k, x, y + h - c);
    ctx.lineTo(x, y + c);
    ctx.bezierCurveTo(x, y + k, x + k, y, x + c, y);
    ctx.closePath();
  }
  const pill = (x, y, w, h) => squircle(x, y, w, h, h / 2);

  function grad(x0, y0, x1, y1, c0, c1) {
    const g = ctx.createLinearGradient(x0, y0, x1, y1);
    g.addColorStop(0, c0); g.addColorStop(1, c1);
    return g;
  }

  function solid(x, y, w, h, r, fill = '#fff', { shadow = true, stroke = null } = {}) {
    ctx.save();
    if (shadow) { ctx.shadowColor = 'rgba(6,3,35,0.45)'; ctx.shadowBlur = 50; ctx.shadowOffsetY = 20; }
    squircle(x, y, w, h, r);
    ctx.fillStyle = fill; ctx.fill();
    if (stroke) { ctx.shadowColor = 'transparent'; ctx.strokeStyle = stroke; ctx.lineWidth = 2; ctx.stroke(); }
    ctx.restore();
  }

  // Frosted acrylic glass card (canvas stand-in for FastBlurView)
  function glass(x, y, w, h, r = 36, { tint = null, stroke = 'rgba(255,255,255,0.22)', shadow = true } = {}) {
    ctx.save();
    if (shadow) { ctx.shadowColor = 'rgba(6,3,35,0.42)'; ctx.shadowBlur = 60; ctx.shadowOffsetY = 24; }
    squircle(x, y, w, h, r);
    ctx.fillStyle = 'rgba(30,22,90,0.42)'; ctx.fill();
    ctx.shadowColor = 'transparent';
    ctx.fillStyle = grad(x, y, x + w * 0.4, y + h, 'rgba(255,255,255,0.16)', 'rgba(255,255,255,0.04)');
    ctx.fill();
    if (tint) {
      const tg = ctx.createRadialGradient(x, y, 0, x, y, Math.max(w, h));
      tg.addColorStop(0, rgba(tint, 0.3)); tg.addColorStop(1, rgba(tint, 0));
      ctx.fillStyle = tg; ctx.fill();
    }
    ctx.lineWidth = 1.5; ctx.strokeStyle = stroke; ctx.stroke();
    ctx.restore();
  }

  function text(str, x, y, { size = 40, weight = 700, color = '#fff', align = 'left', base = 'alphabetic', alpha = 1, font = FONT } = {}) {
    if (alpha <= 0) return;
    ctx.save();
    ctx.globalAlpha *= alpha;
    ctx.font = `${weight} ${size}px ${font}`;
    ctx.fillStyle = color; ctx.textAlign = align; ctx.textBaseline = base;
    ctx.fillText(str, x, y);
    ctx.restore();
  }
  const emoji = (s, x, y, size, alpha = 1) => text(s, x, y, { size, weight: 400, align: 'center', base: 'middle', font: EMOJI, alpha });

  function measure(str, size, weight = 700, font = FONT) {
    ctx.save(); ctx.font = `${weight} ${size}px ${font}`;
    const w = ctx.measureText(str).width; ctx.restore();
    return w;
  }

  // Scale/fade about a pivot; fn draws in absolute coordinates
  function at(x, y, s, a, fn, rot = 0) {
    if (a <= 0.001 || s <= 0.001) return;
    ctx.save();
    ctx.globalAlpha *= clamp(a);
    ctx.translate(x, y); if (rot) ctx.rotate(rot); ctx.scale(s, s); ctx.translate(-x, -y);
    fn();
    ctx.restore();
  }

  function sparkle(x, y, r, color = '#fff', alpha = 1) {
    if (r <= 0 || alpha <= 0) return;
    const k = r * 0.14;
    ctx.save();
    ctx.globalAlpha *= alpha;
    ctx.translate(x, y);
    ctx.beginPath();
    ctx.moveTo(0, -r);
    ctx.quadraticCurveTo(k, -k, r, 0);
    ctx.quadraticCurveTo(k, k, 0, r);
    ctx.quadraticCurveTo(-k, k, -r, 0);
    ctx.quadraticCurveTo(-k, -k, 0, -r);
    ctx.closePath();
    ctx.shadowColor = color; ctx.shadowBlur = r * 1.4;
    ctx.fillStyle = color; ctx.fill();
    ctx.restore();
  }

  function glowDot(x, y, r, color, alpha = 1) {
    if (alpha <= 0) return;
    ctx.save();
    ctx.globalAlpha *= alpha;
    const g = ctx.createRadialGradient(x, y, 0, x, y, r * 3);
    g.addColorStop(0, rgba(color, 1)); g.addColorStop(0.35, rgba(color, 0.5)); g.addColorStop(1, rgba(color, 0));
    ctx.fillStyle = g; ctx.fillRect(x - r * 3, y - r * 3, r * 6, r * 6);
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(x, y, r * 0.55, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }

  function quadPt(a, c, b, t) {
    const u = 1 - t;
    return { x: u * u * a.x + 2 * u * t * c.x + t * t * b.x, y: u * u * a.y + 2 * u * t * c.y + t * t * b.y };
  }
  function strokeQuad(a, c, b, p, color, width = 3, dash = null) {
    if (p <= 0) return;
    ctx.save();
    ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = 'round';
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    const n = 48;
    for (let i = 0; i <= n; i++) {
      const q = quadPt(a, c, b, (p * i) / n);
      if (i) ctx.lineTo(q.x, q.y); else ctx.moveTo(q.x, q.y);
    }
    ctx.stroke();
    ctx.restore();
  }

  function checkBadge(x, y, r, color, p = 1) {
    const s = E.outBack(clamp(p));
    if (s <= 0) return;
    ctx.save();
    ctx.translate(x, y); ctx.scale(s, s);
    ctx.fillStyle = color; ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = r * 0.22; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    ctx.beginPath(); ctx.moveTo(-r * 0.42, 0); ctx.lineTo(-r * 0.1, r * 0.32); ctx.lineTo(r * 0.45, -r * 0.3); ctx.stroke();
    ctx.restore();
  }

  function avatar(x, y, r, init, color) {
    ctx.save();
    ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = grad(x - r, y - r, x + r, y + r, color, rgba(color, 0.75)); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.85)'; ctx.lineWidth = Math.max(1.5, r * 0.08); ctx.stroke();
    ctx.restore();
    text(init, x, y + 1, { size: r * 0.78, weight: 800, align: 'center', base: 'middle' });
  }

  function labelPill(str, cx, cy, color, a = 1, { size = 17, fill = '#1a1347', textColor = null } = {}) {
    if (a <= 0) return;
    const w = measure(str, size, 700) + 32, h = size + 18;
    ctx.save();
    ctx.globalAlpha *= a;
    pill(cx - w / 2, cy - h / 2, w, h);
    ctx.fillStyle = fill; ctx.fill();
    ctx.strokeStyle = rgba(color, 0.7); ctx.lineWidth = 1.5; ctx.stroke();
    ctx.restore();
    text(str, cx, cy + 1, { size, weight: 700, color: textColor || color, align: 'center', base: 'middle', alpha: a });
  }

  function tagPill(str, x, y, color, size = 16) {
    const w = measure(str, size, 800) + 28, h = size + 18;
    pill(x, y, w, h); ctx.fillStyle = rgba(color, 0.14); ctx.fill();
    text(str, x + w / 2, y + h / 2 + 1, { size, weight: 800, color, align: 'center', base: 'middle' });
    return w;
  }

  function caret(x, y, h, color, t) {
    if (Math.floor(t * 2.4) % 2) return;
    ctx.fillStyle = color; ctx.fillRect(x + 3, y - h, 3, h);
  }

  function chipRow(items, cx, y, t, start, { size = 24, gap = 18, padX = 26, h = 56, stagger = 0.1 } = {}) {
    const ws = items.map(s => measure(s, size, 600) + padX * 2);
    const total = ws.reduce((a, b) => a + b, 0) + gap * (items.length - 1);
    let x = cx - total / 2;
    items.forEach((s, i) => {
      const st = start + i * stagger;
      const p = E.spring(prog(t, st, 0.9)), a = prog(t, st, 0.25);
      const mx = x + ws[i] / 2, my = y + h / 2, w = ws[i], x0 = x;
      at(mx, my, lerp(0.6, 1, p), a, () => {
        pill(x0, y, w, h);
        ctx.fillStyle = 'rgba(255,255,255,0.1)'; ctx.fill();
        ctx.strokeStyle = 'rgba(255,255,255,0.25)'; ctx.lineWidth = 1.5; ctx.stroke();
        text(s, mx, my + 1, { size, weight: 600, align: 'center', base: 'middle' });
      });
      x += ws[i] + gap;
    });
  }

  function revealText(str, x, y, t, start, { size = 62, weight = 800, color = '#fff', stagger = 0.06, dur = 0.6, align = 'left' } = {}) {
    ctx.save();
    ctx.font = `${weight} ${size}px ${FONT}`;
    ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
    const base = ctx.globalAlpha;
    const words = str.split(' ');
    const space = ctx.measureText(' ').width;
    const widths = words.map(w => ctx.measureText(w).width);
    const total = widths.reduce((a, b) => a + b, 0) + space * (words.length - 1);
    let cx = align === 'center' ? x - total / 2 : x;
    words.forEach((w, i) => {
      const p = E.outCubic(prog(t, start + i * stagger, dur));
      if (p > 0) {
        ctx.globalAlpha = base * p;
        ctx.fillStyle = color;
        ctx.fillText(w, cx, y + (1 - p) * size * 0.55);
      }
      cx += widths[i] + space;
    });
    ctx.restore();
  }

  function header(t, kicker, title, sub, color = C.gold) {
    const p = E.outCubic(prog(t, 0.1, 0.6));
    const kw = measure(kicker, 19, 800) + 40;
    at(140, 114, 1, p, () => {
      ctx.translate((1 - p) * -40, 0);
      pill(140, 92, kw, 42);
      ctx.fillStyle = rgba(color, 0.15); ctx.fill();
      ctx.strokeStyle = rgba(color, 0.5); ctx.lineWidth = 1.5; ctx.stroke();
      text(kicker, 160, 120, { size: 19, weight: 800, color });
    });
    revealText(title, 140, 205, t, 0.25);
    if (sub) text(sub, 140, 255, { size: 27, weight: 500, color: 'rgba(255,255,255,0.7)', alpha: E.outCubic(prog(t, 0.6, 0.6)) });
  }

  function phone(x, y, w, h, drawScreen, bg = '#F5F5F5') {
    ctx.save();
    ctx.shadowColor = 'rgba(5,2,30,0.6)'; ctx.shadowBlur = 60; ctx.shadowOffsetY = 30;
    squircle(x, y, w, h, w * 0.16);
    ctx.fillStyle = '#14112b'; ctx.fill();
    ctx.shadowColor = 'transparent';
    ctx.strokeStyle = 'rgba(255,255,255,0.18)'; ctx.lineWidth = 2; ctx.stroke();
    const b = 14, sx = x + b, sy = y + b, sw = w - 2 * b, sh = h - 2 * b;
    ctx.save();
    squircle(sx, sy, sw, sh, w * 0.13); ctx.clip();
    ctx.fillStyle = bg; ctx.fillRect(sx, sy, sw, sh);
    drawScreen(sx, sy, sw, sh);
    ctx.fillStyle = '#14112b'; ctx.beginPath(); ctx.arc(sx + sw / 2, sy + 20, 7, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    ctx.restore();
  }

  /* ---------- Brand marks (vector re-creations of logo.png / bubble.png) ---------- */
  function drawLogo(cx, cy, s, open = 1, T = 0, { sparkles = 1 } = {}) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.shadowColor = 'rgba(60,40,200,0.55)'; ctx.shadowBlur = s * 0.25; ctx.shadowOffsetY = s * 0.08;
    squircle(-s / 2, -s / 2, s, s, s * 0.24);
    ctx.fillStyle = grad(-s / 2, -s / 2, s / 2, s / 2, '#4B3FD8', '#7B6CF6'); ctx.fill();
    ctx.shadowColor = 'transparent';

    ctx.save();
    squircle(-s / 2, -s / 2, s, s, s * 0.24); ctx.clip();
    const blob = (bx, by, br, col, a) => {
      const g = ctx.createRadialGradient(bx, by, 0, bx, by, br);
      g.addColorStop(0, rgba(col, a)); g.addColorStop(1, rgba(col, 0));
      ctx.fillStyle = g; ctx.fillRect(bx - br, by - br, br * 2, br * 2);
    };
    blob(-s * 0.48, s * 0.48, s * 0.55, '#00B894', 0.45);
    blob(s * 0.5, s * 0.5, s * 0.5, '#FF9ECF', 0.35);
    ctx.fillStyle = grad(0, -s / 2, 0, 0, 'rgba(255,255,255,0.2)', 'rgba(255,255,255,0)');
    ctx.fillRect(-s / 2, -s / 2, s, s / 2);
    ctx.restore();

    squircle(-s / 2, -s / 2, s, s, s * 0.24);
    ctx.strokeStyle = 'rgba(255,255,255,0.28)'; ctx.lineWidth = Math.max(1, s * 0.008); ctx.stroke();

    const o = clamp(open);
    ctx.fillStyle = 'rgba(20,10,80,0.35)';
    ctx.beginPath(); ctx.ellipse(0, s * 0.22, Math.max(0.1, s * 0.3 * o), s * 0.035, 0, 0, Math.PI * 2); ctx.fill();

    const pw = s * 0.29 * Math.max(0.04, o), ph = s * 0.24, by = s * 0.04;
    [-1, 1].forEach(side => {
      ctx.beginPath();
      ctx.moveTo(0, by - ph / 2 + s * 0.025);
      ctx.quadraticCurveTo(side * pw * 0.5, by - ph / 2 - s * 0.03, side * pw, by - ph / 2);
      ctx.lineTo(side * pw, by + ph / 2);
      ctx.quadraticCurveTo(side * pw * 0.5, by + ph / 2 - s * 0.04, 0, by + ph / 2 + s * 0.02);
      ctx.closePath();
      ctx.fillStyle = side < 0 ? grad(0, by - ph / 2, 0, by + ph / 2, '#FFFFFF', '#E6E1FF') : grad(0, by - ph / 2, 0, by + ph / 2, '#F4F2FF', '#D9D3FF');
      ctx.fill();
      if (o > 0.6) {
        ctx.strokeStyle = rgba('#8A84FF', 0.6 * ((o - 0.6) / 0.4));
        ctx.lineWidth = Math.max(1, s * 0.012); ctx.lineCap = 'round';
        for (let i = 0; i < 4; i++) {
          const ly = by - ph / 2 + s * 0.055 + i * s * 0.042;
          ctx.beginPath();
          ctx.moveTo(side * pw * 0.2, ly);
          ctx.lineTo(side * pw * (i === 3 ? 0.55 : 0.8), ly);
          ctx.stroke();
        }
      }
    });

    if (sparkles > 0) {
      const tw = 0.85 + 0.15 * Math.sin(T * 4);
      sparkle(0, -s * 0.2, s * 0.075 * sparkles * tw, '#E8FBFF');
      sparkle(s * 0.2, -s * 0.19, s * 0.035 * sparkles * (0.8 + 0.2 * Math.sin(T * 5 + 1)), '#FFB8E0');
      ctx.fillStyle = '#7EF0D8';
      ctx.beginPath(); ctx.arc(-s * 0.2, -s * 0.15, s * 0.013 * sparkles, 0, Math.PI * 2); ctx.fill();
    }
    ctx.restore();
  }

  function drawBubble(cx, cy, r) {
    ctx.save();
    ctx.translate(cx, cy);
    // Subtle ambient glow
    const glow = ctx.createRadialGradient(0, 0, r * 0.2, 0, 0, r * 1.5);
    glow.addColorStop(0, 'rgba(108,92,231,0.5)');
    glow.addColorStop(0.6, 'rgba(138,132,255,0.18)');
    glow.addColorStop(1, 'rgba(108,92,231,0)');
    ctx.fillStyle = glow;
    ctx.fillRect(-r * 1.5, -r * 1.5, r * 3, r * 3);

    // Squircle button with shadow
    ctx.shadowColor = 'rgba(60,40,200,0.6)'; ctx.shadowBlur = r * 0.45; ctx.shadowOffsetY = r * 0.15;
    squircle(-r, -r, r * 2, r * 2, r * 0.48);
    ctx.fillStyle = grad(-r, -r, r, r, '#4B3FD8', '#7B6CF6');
    ctx.fill();
    ctx.shadowColor = 'transparent';

    // Inner subtle mint & pink bloom accents
    ctx.save();
    squircle(-r, -r, r * 2, r * 2, r * 0.48); ctx.clip();
    const gMint = ctx.createRadialGradient(-r * 0.5, r * 0.5, 0, -r * 0.5, r * 0.5, r * 0.85);
    gMint.addColorStop(0, 'rgba(0,184,148,0.45)'); gMint.addColorStop(1, 'rgba(0,184,148,0)');
    ctx.fillStyle = gMint; ctx.fillRect(-r, 0, r, r);
    const gPink = ctx.createRadialGradient(r * 0.5, -r * 0.5, 0, r * 0.5, -r * 0.5, r * 0.85);
    gPink.addColorStop(0, 'rgba(255,158,207,0.35)'); gPink.addColorStop(1, 'rgba(255,158,207,0)');
    ctx.fillStyle = gPink; ctx.fillRect(0, -r, r, r);
    ctx.restore();

    // Border
    squircle(-r, -r, r * 2, r * 2, r * 0.48);
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'; ctx.lineWidth = 1.5; ctx.stroke();

    // Open book pages
    const bw = r * 0.56, bh = r * 0.44;
    [-1, 1].forEach(side => {
      ctx.beginPath();
      ctx.moveTo(0, -bh / 2 + r * 0.05);
      ctx.quadraticCurveTo(side * bw * 0.5, -bh / 2 - r * 0.06, side * bw, -bh / 2);
      ctx.lineTo(side * bw, bh / 2);
      ctx.quadraticCurveTo(side * bw * 0.5, bh / 2 - r * 0.08, 0, bh / 2 + r * 0.04);
      ctx.closePath();
      ctx.fillStyle = side < 0 ? '#FFFFFF' : '#EAE6FF';
      ctx.fill();
    });
    ctx.fillStyle = '#6C5CE7';
    ctx.fillRect(-1, -bh / 2, 2, bh);
    ctx.restore();
  }

  /* ---------- Persistent background ---------- */
  const BLOBS = [
    { c: '#6C5CE7', x: 0.18, y: 0.25, r: 760, a: 0.5, sx: 0.11, sy: 0.07 },
    { c: '#A0C4FF', x: 0.86, y: 0.18, r: 620, a: 0.22, sx: 0.08, sy: 0.12 },
    { c: '#00B894', x: 0.08, y: 0.92, r: 520, a: 0.2, sx: 0.09, sy: 0.05 },
    { c: '#D6BCFA', x: 0.82, y: 0.88, r: 700, a: 0.24, sx: 0.06, sy: 0.1 },
    { c: '#8A84FF', x: 0.5, y: 0.55, r: 900, a: 0.14, sx: 0.05, sy: 0.04 },
  ];
  const rndBg = mulberry32(7);
  const DUST = Array.from({ length: 90 }, () => ({
    x: rndBg() * W, y: rndBg() * H, r: 0.8 + rndBg() * 2.2, sp: 8 + rndBg() * 22, ph: rndBg() * 6.28, star: rndBg() < 0.12,
  }));

  function drawBackground(T) {
    ctx.fillStyle = grad(0, 0, W, H, '#0d0930', '#2a1c78');
    ctx.fillRect(0, 0, W, H);
    for (const b of BLOBS) {
      const x = (b.x + Math.sin(T * b.sx * 2 + b.r) * 0.06) * W;
      const y = (b.y + Math.cos(T * b.sy * 2 + b.r) * 0.06) * H;
      const rg = ctx.createRadialGradient(x, y, 0, x, y, b.r);
      rg.addColorStop(0, rgba(b.c, b.a)); rg.addColorStop(1, rgba(b.c, 0));
      ctx.fillStyle = rg; ctx.fillRect(x - b.r, y - b.r, b.r * 2, b.r * 2);
    }
    // drifting grid
    ctx.save();
    ctx.globalAlpha = 0.045; ctx.strokeStyle = '#fff'; ctx.lineWidth = 1;
    const off = (T * 12) % 80;
    ctx.beginPath();
    for (let x = -80 + off; x < W; x += 80) { ctx.moveTo(x, 0); ctx.lineTo(x, H); }
    for (let y = -80 + off; y < H; y += 80) { ctx.moveTo(0, y); ctx.lineTo(W, y); }
    ctx.stroke();
    ctx.restore();
    // dust + twinkles
    for (const p of DUST) {
      const y = ((((p.y - T * p.sp) % (H + 40)) + H + 40) % (H + 40)) - 20;
      const x = p.x + Math.sin(T * 0.3 + p.ph) * 20;
      const tw = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(T * 2 + p.ph * 3));
      if (p.star) sparkle(x, y, p.r * 4, '#ffffff', tw * 0.7);
      else {
        ctx.save(); ctx.globalAlpha = tw * 0.5; ctx.fillStyle = '#cfc8ff';
        ctx.beginPath(); ctx.arc(x, y, p.r, 0, Math.PI * 2); ctx.fill(); ctx.restore();
      }
    }
    const v = ctx.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H);
    v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(5,2,20,0.55)');
    ctx.fillStyle = v; ctx.fillRect(0, 0, W, H);
  }

  /* ======================================================================
     SCENE 01 — Intro
     ====================================================================== */
  function sIntro(t) {
    const cx = 960, cy = 400;
    const sp = E.spring(prog(t, 0.15, 1.3));
    const open = E.inOutCubic(prog(t, 0.7, 0.9));
    const spk = E.outBack(prog(t, 1.4, 0.6));

    for (let i = 0; i < 3; i++) {
      const rp = prog(t, 1.0 + i * 0.35, 1.8);
      if (rp > 0 && rp < 1) {
        ctx.save();
        ctx.globalAlpha *= (1 - rp) * 0.5;
        ctx.strokeStyle = C.primaryVariant; ctx.lineWidth = 3;
        const s = 300 + rp * 340;
        squircle(cx - s / 2, cy - s / 2, s, s, s * 0.24); ctx.stroke();
        ctx.restore();
      }
    }
    const gl = ctx.createRadialGradient(cx, cy, 0, cx, cy, 440);
    gl.addColorStop(0, rgba(C.primaryLight, 0.45 * clamp(sp))); gl.addColorStop(1, rgba(C.primaryLight, 0));
    ctx.fillStyle = gl; ctx.fillRect(cx - 440, cy - 440, 880, 880);

    const fl = Math.sin(t * 1.6) * 8 * prog(t, 2, 1);
    if (sp > 0.001) {
      ctx.save();
      ctx.translate(cx, cy + fl); ctx.rotate((1 - sp) * -0.25); ctx.scale(sp, sp);
      drawLogo(0, 0, 300, open, t, { sparkles: spk });
      ctx.restore();
    }

    // Wordmark
    const word = 'GabAI';
    ctx.save();
    ctx.font = `800 150px ${FONT}`;
    const base = ctx.globalAlpha;
    const widths = [...word].map(ch => ctx.measureText(ch).width);
    let x = cx - widths.reduce((a, b) => a + b, 0) / 2;
    const g1 = grad(0, 690, 0, 800, '#ffffff', C.lavender);
    const g2 = grad(0, 690, 0, 800, C.secondary, C.primaryVariant);
    [...word].forEach((ch, i) => {
      const st = 1.6 + i * 0.08;
      const p = E.outBack(prog(t, st, 0.7));
      ctx.globalAlpha = base * prog(t, st, 0.3);
      ctx.fillStyle = i >= 3 ? g2 : g1;
      ctx.fillText(ch, x, 790 + (1 - p) * 80);
      x += widths[i];
    });
    ctx.restore();

    text('Your AI-powered mobile reading assistant', cx, 858, {
      size: 34, weight: 500, color: 'rgba(255,255,255,0.82)', align: 'center', alpha: E.outCubic(prog(t, 2.4, 0.7)),
    });
    chipRow(['📚 Students', '🧑‍🏫 Teachers', '✨ Gemini AI', '🎯 Active Recall'], cx, 905, t, 3.0);
  }

  /* ======================================================================
     SCENE 02 — Dual-role platform & boundary
     ====================================================================== */
  const ROLES = [
    { icon: '🧑‍🏫', title: 'Adviser', tag: 'Homeroom Teacher', color: C.gold, lines: ['Owns advisory section', 'Generates student accounts', 'Auto-sees full homeroom'] },
    { icon: '📘', title: 'Subject Teacher', tag: 'Teaches across sections', color: C.secondary, lines: ['Links to existing sections', 'Shares a QR join code', 'Opt-in, scan-based roster'] },
    { icon: '🎒', title: 'Student', tag: 'Learner', color: C.green, lines: ['Claims a temp account', 'Scans QR to join subjects', 'Reads assigned stories'] },
  ];
  function sRoles(t) {
    header(t, 'THE PLATFORM', 'One app. Two roles. Clear boundaries.', 'Designed for real modern classroom collaboration');
    const cw = 500, ch = 390, gap = 60, x0 = 150, y0 = 315;

    // relationship arcs (drawn first, under the cards)
    const A0 = { x: 400, y: 715 }, A1 = { x: 960, y: 715 }, B = { x: 1520, y: 715 };
    const c0 = { x: 960, y: 960 }, c1 = { x: 1240, y: 800 };
    const p0 = E.inOutCubic(prog(t, 2.2, 0.9)), p1 = E.inOutCubic(prog(t, 2.5, 0.8));
    strokeQuad(A0, c0, B, p0, rgba(C.gold, 0.75), 4, [2, 12]);
    strokeQuad(A1, c1, B, p1, rgba(C.secondary, 0.75), 4, [2, 12]);
    for (let k = 0; k < 3; k++) {
      const q = (t * 0.45 + k / 3) % 1;
      if (p0 >= 1) { const pt = quadPt(A0, c0, B, q); glowDot(pt.x, pt.y, 5, C.gold); }
      if (p1 >= 1) { const pt = quadPt(A1, c1, B, q); glowDot(pt.x, pt.y, 5, C.secondary); }
    }
    labelPill('creates accounts', 960, 838, C.gold, prog(t, 2.9, 0.4));
    labelPill('QR join', 1240, 757, C.secondary, prog(t, 3.1, 0.4));

    ROLES.forEach((r, i) => {
      const st = 0.6 + i * 0.18;
      const p = E.spring(prog(t, st, 1.1)), a = prog(t, st, 0.35);
      const x = x0 + i * (cw + gap), y = y0 + (1 - p) * 120;
      at(x + cw / 2, y + ch / 2, lerp(0.9, 1, p), a, () => {
        glass(x, y, cw, ch, 40, { tint: r.color });
        solid(x + 36, y + 36, 88, 88, 26, rgba(r.color, 0.22), { shadow: false });
        emoji(r.icon, x + 80, y + 82, 46);
        text(r.title, x + 148, y + 76, { size: 34, weight: 800 });
        text(r.tag, x + 148, y + 110, { size: 21, weight: 600, color: r.color });
        ctx.fillStyle = 'rgba(255,255,255,0.16)'; ctx.fillRect(x + 36, y + 152, cw - 72, 1.5);
        r.lines.forEach((ln, j) => {
          const lp = E.outCubic(prog(t, st + 0.7 + j * 0.12, 0.5));
          const ly = y + 210 + j * 62;
          checkBadge(x + 54, ly - 8, 15, r.color, lp);
          text(ln, x + 84 + (1 - lp) * 20, ly, { size: 24, weight: 500, color: 'rgba(255,255,255,0.92)', alpha: lp });
        });
      });
    });

    const sp = E.outCubic(prog(t, 3.4, 0.7));
    at(960, 948, 1, sp, () => {
      ctx.translate(0, (1 - sp) * 30);
      glass(370, 915, 1180, 66, 33, { tint: C.primary });
      text('🛡️  Admins run schools on the Web Admin Portal  ·  📱 Mobile app = Students & Teachers only', 960, 949, {
        size: 23, weight: 600, align: 'center', base: 'middle',
      });
    });
  }

  /* ======================================================================
     SCENE 03 — Onboarding & account lifecycle (Engines 1–2)
     ====================================================================== */
  const NAMES = ['Juan Cruz', 'Maria Santos', 'Jose Reyes', 'Ana Lim', 'Paolo Garcia'];
  const USERS = ['juancruz_4812', 'mariasantos_2207', 'josereyes_5531', 'analim_0936', 'paologarcia_7144'];
  const PWS = ['7KQ2MX', 'P4ZT9A', 'M2XW8R', 'H6CN3L', 'B9VE5Q'];
  const INIT = ['JC', 'MS', 'JR', 'AL', 'PG'];
  const AV = [C.primary, C.blue, C.green, C.orange, C.pink];
  const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

  function ticket(x, y, i, scale) {
    const w = 520, h = 88;
    ctx.save();
    ctx.translate(x, y); ctx.scale(scale, scale);
    solid(-w / 2, -h / 2, w, h, 24, '#ffffff');
    solid(-w / 2 + 14, -h / 2 + 18, 7, h - 36, 4, AV[i], { shadow: false });
    avatar(-w / 2 + 64, 0, 24, INIT[i], AV[i]);
    text(USERS[i], -w / 2 + 104, -6, { size: 23, weight: 700, color: C.ink, font: MONO });
    text('temp pw · ' + PWS[i], -w / 2 + 104, 24, { size: 17, weight: 500, color: C.muted, font: MONO });
    const pw = measure('pending', 15, 800) + 26;
    pill(w / 2 - pw - 20, -15, pw, 30); ctx.fillStyle = rgba(C.amber, 0.18); ctx.fill();
    text('pending', w / 2 - pw / 2 - 20, 1, { size: 15, weight: 800, color: '#B7791F', align: 'center', base: 'middle' });
    ctx.restore();
  }

  function sOnboard(t) {
    header(t, 'ONBOARDING & ROSTER', 'From a list of names to a live classroom', 'Advisers batch-create accounts — students never type a homeroom code');

    // Column A — paste names
    const aP = E.spring(prog(t, 0.4, 1)), aA = prog(t, 0.4, 0.3);
    at(370, 600, lerp(0.92, 1, aP), aA, () => {
      glass(140, 320, 460, 560, 40, { tint: C.gold });
      emoji('📋', 190, 372, 30);
      text('Paste student names', 222, 382, { size: 26, weight: 700 });
      NAMES.forEach((n, i) => {
        const y = 420 + i * 68, tp = prog(t, 0.8 + i * 0.22, 0.35);
        solid(170, y, 400, 54, 18, 'rgba(255,255,255,0.07)', { shadow: false });
        const s = typed(n, tp);
        text(s, 196, y + 35, { size: 22, weight: 600, color: 'rgba(255,255,255,0.92)' });
        if (tp > 0 && tp < 1) caret(196 + measure(s, 22, 600), y + 38, 24, C.gold, t);
      });
      const press = prog(t, 2.0, 0.35);
      const bs = press > 0 && press < 1 ? 1 - 0.08 * Math.sin(press * Math.PI) : 1;
      at(370, 800, bs, 1, () => {
        pill(170, 770, 400, 60);
        ctx.fillStyle = grad(170, 770, 570, 830, C.primary, C.primaryLight); ctx.fill();
        text('⚡ Generate accounts', 370, 801, { size: 22, weight: 700, align: 'center', base: 'middle' });
      });
      const rp = prog(t, 2.05, 0.6);
      if (rp > 0 && rp < 1) {
        ctx.save(); ctx.globalAlpha *= 1 - rp; ctx.strokeStyle = C.primaryLight; ctx.lineWidth = 3;
        pill(170 - rp * 30, 770 - rp * 20, 400 + rp * 60, 60 + rp * 40); ctx.stroke(); ctx.restore();
      }
    });


    // Column C — class card
    const arrivals = USERS.map((_, i) => 4.4 + i * 0.3 + 0.8);
    const count = arrivals.filter(a => t >= a).length;
    const cP = E.spring(prog(t, 0.6, 1)), cA = prog(t, 0.6, 0.3);
    at(1540, 600, lerp(0.92, 1, cP), cA, () => {
      glass(1300, 320, 480, 560, 40, { tint: C.green });
      emoji('🏫', 1350, 372, 30);
      text('Grade 10 - Emerald', 1382, 382, { size: 28, weight: 800 });
      text('SCH_001 · Advisory section', 1340, 418, { size: 18, weight: 600, color: C.green });
      text('JOIN CODE', 1340, 466, { size: 15, weight: 800, color: 'rgba(255,255,255,0.55)' });
      [...'K9X2P1'].forEach((chr, i) => {
        const bx = 1335 + i * 70, settle = 1.2 + i * 0.15;
        let shown = '';
        if (t >= settle) shown = chr;
        else if (t > 0.8) shown = CODE_CHARS[(Math.floor(t * 22) * 31 + i * 17) % CODE_CHARS.length];
        const done = t >= settle, pop = done ? E.spring(prog(t, settle, 0.5)) : 1;
        solid(bx, 482, 60, 70, 18, done ? rgba(C.gold, 0.2) : 'rgba(255,255,255,0.08)', { shadow: false, stroke: done ? rgba(C.gold, 0.6) : 'rgba(255,255,255,0.15)' });
        at(bx + 30, 517, 1, 1, () => {
          ctx.translate(bx + 30, 517); ctx.scale(1, lerp(0.3, 1, pop)); ctx.translate(-bx - 30, -517);
          text(shown, bx + 30, 519, { size: 34, weight: 700, color: done ? C.gold : '#fff', align: 'center', base: 'middle', font: MONO });
        });
      });
      text('Homeroom roster', 1340, 604, { size: 20, weight: 700 });
      text(`${count} students`, 1744, 604, { size: 18, weight: 700, color: C.green, align: 'right' });
      USERS.forEach((_, i) => {
        const ax = 1380 + i * 84, ay = 660;
        if (t >= arrivals[i]) {
          const pp = E.spring(prog(t, arrivals[i], 0.6));
          at(ax, ay, pp, 1, () => avatar(ax, ay, 32, INIT[i], AV[i]));
        } else {
          ctx.save(); ctx.setLineDash([4, 6]); ctx.strokeStyle = 'rgba(255,255,255,0.3)'; ctx.lineWidth = 2;
          ctx.beginPath(); ctx.arc(ax, ay, 32, 0, Math.PI * 2); ctx.stroke(); ctx.restore();
        }
      });
      ['Student claims temp login', 'Firebase Auth account created', 'Mandatory password change'].forEach((s, j) => {
        const y = 742 + j * 50, st = 6.3 + j * 0.4, lit = t >= st;
        if (lit) checkBadge(1352, y - 7, 14, C.green, prog(t, st, 0.4));
        else { ctx.strokeStyle = 'rgba(255,255,255,0.3)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(1352, y - 7, 13, 0, Math.PI * 2); ctx.stroke(); }
        text(s, 1378, y, { size: 19, weight: 600, color: lit ? '#fff' : 'rgba(255,255,255,0.5)' });
      });
    });

    // Column B — tickets, then fly into the roster
    USERS.forEach((_, i) => {
      const gp = prog(t, 2.2 + i * 0.25, 0.6), fp = prog(t, 4.4 + i * 0.3, 0.8);
      if (gp <= 0 || fp >= 1) return;
      const slot = { x: 960, y: 384 + i * 104 }, av = { x: 1380 + i * 84, y: 660 };
      let x, y, s, a;
      if (fp <= 0) {
        x = lerp(370, slot.x, E.outCubic(gp)); y = lerp(800, slot.y, E.outCubic(gp));
        s = lerp(0.3, 1, E.outBack(gp)); a = clamp(gp * 3);
      } else {
        const e = E.inOutCubic(fp);
        x = lerp(slot.x, av.x, e); y = lerp(slot.y, av.y, e) - Math.sin(e * Math.PI) * 80;
        s = lerp(1, 0.14, e); a = 1 - prog(fp, 0.75, 0.25);
      }
      at(x, y, 1, a, () => ticket(x, y, i, s));
    });
  }

  /* ======================================================================
     SCENE 04 — QR enrollment
     ====================================================================== */
  const QR = (() => {
    const n = 25, r = mulberry32(42), m = [];
    for (let y = 0; y < n; y++) { m.push([]); for (let x = 0; x < n; x++) m[y].push(r() < 0.48); }
    const finder = (ox, oy) => {
      for (let y = -1; y < 8; y++) for (let x = -1; x < 8; x++) {
        const X = ox + x, Y = oy + y;
        if (X < 0 || Y < 0 || X >= n || Y >= n) continue;
        const ring = Math.max(Math.abs(x - 3), Math.abs(y - 3));
        m[Y][X] = x >= 0 && x < 7 && y >= 0 && y < 7 && (ring === 3 || ring <= 1);
      }
    };
    finder(0, 0); finder(n - 7, 0); finder(0, n - 7);
    return m;
  })();
  function drawQR(x, y, size, t, start) {
    const n = 25, c = size / n;
    ctx.fillStyle = C.ink;
    for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) {
      if (!QR[j][i]) continue;
      const p = E.outBack(prog(t, start + ((i + j) / (2 * n)) * 1.0, 0.35));
      if (p <= 0) continue;
      const s = c * p * 0.94;
      ctx.fillRect(x + i * c + (c - s) / 2, y + j * c + (c - s) / 2, s, s);
    }
  }
  function codeLine(segs, x, y, size = 19) {
    let cx = x;
    segs.forEach(([s, color, hl]) => {
      const w = measure(s, size, 500, MONO);
      if (hl && s) {
        ctx.save(); ctx.fillStyle = rgba(C.green, 0.22);
        squircle(cx - 4, y - size - 2, w + 8, size + 12, 6); ctx.fill(); ctx.restore();
      }
      text(s, cx, y, { size, weight: 500, color, font: MONO });
      cx += w;
    });
  }

  function sQR(t) {
    header(t, 'QR ENROLLMENT', 'Subject teachers join with one scan', 'The existing section simply gains a teacher — no duplicate classes', C.secondary);
    const A = { x: 190, y: 300, w: 360, h: 690 }, B = { x: 1370, y: 300, w: 360, h: 690 };
    const pa = E.spring(prog(t, 0.3, 1.1)), pb = E.spring(prog(t, 0.5, 1.1));

    at(A.x + A.w / 2, A.y + A.h / 2, 1, prog(t, 0.3, 0.3), () => {
      ctx.translate((1 - pa) * -160, 0);
      phone(A.x, A.y, A.w, A.h, (sx, sy, sw) => {
        text('English · Teacher 2', sx + 28, sy + 66, { size: 16, weight: 600, color: C.muted });
        text('Share to enroll', sx + 28, sy + 100, { size: 26, weight: 800, color: C.ink });
        const cs = sw - 48;
        solid(sx + 24, sy + 128, cs, cs, 28, '#fff');
        drawQR(sx + 49, sy + 153, cs - 50, t, 0.9);
        text('TEACHER JOIN CODE', sx + sw / 2, sy + 128 + cs + 44, { size: 14, weight: 800, color: C.muted, align: 'center' });
        text('ENG7Q2', sx + sw / 2, sy + 128 + cs + 86, { size: 36, weight: 700, color: C.primary, align: 'center', font: MONO });
        text('Grade 10 - Emerald English', sx + sw / 2, sy + 128 + cs + 120, { size: 16, weight: 600, color: C.muted, align: 'center' });
      });
    });

    const success = t >= 3.0;
    at(B.x + B.w / 2, B.y + B.h / 2, 1, prog(t, 0.5, 0.3), () => {
      ctx.translate((1 - pb) * 160, 0);
      phone(B.x, B.y, B.w, B.h, (sx, sy, sw, sh) => {
        ctx.fillStyle = grad(sx, sy, sx, sy + sh, '#151229', '#2a2350'); ctx.fillRect(sx, sy, sw, sh);
        text('Scan teacher QR', sx + sw / 2, sy + 70, { size: 20, weight: 700, align: 'center' });
        const cx = sx + sw / 2, cy = sy + sh * 0.42;
        at(cx, cy, 1, prog(t, 1.0, 0.5), () => {
          ctx.translate(cx, cy); ctx.rotate(-0.08); ctx.translate(-cx, -cy);
          solid(cx - 105, cy - 105, 210, 210, 18, '#fff', { shadow: false });
          drawQR(cx - 90, cy - 90, 180, 99, 0);
        });
        const bc = success ? C.green : '#ffffff', bs = 125 + (success ? 0 : Math.sin(t * 6) * 4), L = 36;
        ctx.save(); ctx.strokeStyle = bc; ctx.lineWidth = 6; ctx.lineCap = 'round';
        [[-1, -1], [1, -1], [1, 1], [-1, 1]].forEach(([dx, dy]) => {
          const ox = cx + dx * bs, oy = cy + dy * bs;
          ctx.beginPath(); ctx.moveTo(ox, oy - dy * L); ctx.lineTo(ox, oy); ctx.lineTo(ox - dx * L, oy); ctx.stroke();
        });
        ctx.restore();
        if (t > 1.4 && t < 3.0) {
          const ly = cy - 110 + (0.5 - 0.5 * Math.cos(((t - 1.4) * Math.PI * 2) / 0.8)) * 220;
          ctx.save(); ctx.shadowColor = C.secondary; ctx.shadowBlur = 24;
          ctx.fillStyle = C.secondary; ctx.fillRect(cx - 115, ly - 2, 230, 4); ctx.restore();
        }
        if (success) {
          const fl = 1 - prog(t, 3.0, 0.4);
          if (fl > 0) { ctx.fillStyle = `rgba(255,255,255,${fl * 0.6})`; ctx.fillRect(sx, sy, sw, sh); }
          checkBadge(cx, cy, 46, C.green, prog(t, 3.0, 0.5));
          const sh2 = E.outCubic(prog(t, 3.3, 0.6));
          const top = sy + sh - 230 * sh2;
          solid(sx + 14, top, sw - 28, 212, 28, '#fff');
          text('🎉 Joined!', sx + 40, top + 56, { size: 28, weight: 800, color: C.ink });
          text('Grade 10 - Emerald English', sx + 40, top + 94, { size: 18, weight: 600, color: C.muted });
          text('Teacher 2 added to your subjects', sx + 40, top + 130, { size: 16, weight: 700, color: C.green });
        }
      }, '#151229');
    });

    // beam
    const qa = { x: 370, y: 583 }, qb = { x: 1550, y: 593 }, qc = { x: 960, y: 330 };
    strokeQuad(qa, qc, qb, E.inOutCubic(prog(t, 2.4, 0.6)) * (1 - prog(t, 4.0, 0.5)), rgba(C.secondary, 0.35), 2, [3, 10]);
    for (let k = 0; k < 18; k++) {
      const p = prog(t, 2.5 + k * 0.04, 0.9);
      if (p <= 0 || p >= 1) continue;
      const pt = quadPt(qa, qc, qb, E.inOutCubic(p));
      glowDot(pt.x, pt.y + Math.sin(k * 1.7) * 16 * Math.sin(p * Math.PI), 5, k % 2 ? C.secondary : C.lavender, Math.sin(p * Math.PI));
    }

    // Firestore doc
    const dp = E.spring(prog(t, 3.3, 1)), da = prog(t, 3.3, 0.3);
    const D = { x: 660, y: 540, w: 600, h: 320 };
    at(960, 700, lerp(0.9, 1, dp), da, () => {
      solid(D.x, D.y, D.w, D.h, 32, '#16123a', { stroke: 'rgba(255,255,255,0.15)' });
      emoji('🔥', D.x + 40, D.y + 44, 22);
      text('Firestore · classes/grade10-emerald', D.x + 64, D.y + 51, { size: 17, weight: 700, color: C.gold, font: MONO });
      ctx.fillStyle = 'rgba(255,255,255,0.1)'; ctx.fillRect(D.x + 28, D.y + 76, D.w - 56, 1.5);
      const K = C.lavender, S = C.secondary, P = 'rgba(255,255,255,0.65)', G = '#7EF0C8';
      const p1 = prog(t, 3.9, 0.5), p2 = prog(t, 4.5, 0.6);
      const x = D.x + 32;
      codeLine([['{', P]], x, D.y + 118);
      codeLine([['  "className"', K], [': ', P], ['"Grade 10 - Emerald"', S], [',', P]], x, D.y + 160);
      codeLine([['  "teacherIds"', K], [': [', P], ['"adviser_01"', S], [p1 > 0 ? ', ' : '', P], [typed('"english_t2"', p1), G, true], [']', P]], x, D.y + 202);
      codeLine([['  "joinedStudents"', K], [': [', P], [typed('"juancruz_4812"', p2), G, true], [']', P]], x, D.y + 244);
      codeLine([['}', P]], x, D.y + 286);
    });

    const tp = E.spring(prog(t, 5.4, 0.8));
    at(960, 925, lerp(0.7, 1, tp), prog(t, 5.4, 0.3), () => {
      labelPill("✅  Emerald now appears under Teacher 2's Subject Sections", 960, 925, C.green, 1, { size: 21, fill: '#123a3a', textColor: '#ffffff' });
    });
  }

  /* ======================================================================
     SCENE 05 — Dual reading pipeline (Engine 3)
     ====================================================================== */
  const PA = [['🗺️', 'Quest Board', 'Embark on Trials'], ['🦅', 'Initiation', 'Once per student'], ['📄', 'Reading Passage', 'The Philippine Eagle']];
  const PB = [['📚', 'Teacher Library', 'Curate stories'], ['⬆️', 'Upload PDF', 'Reading Treasury'], ['🎯', 'Batch Assign', 'Whole section, 1 tap'], ['📖', 'Student Library', 'Mount Mayon · Bamboo'], ['✅', 'Material Quiz', 'AI comprehension']];

  function pnode(x, y, w, h, n, color, p, a) {
    at(x + w / 2, y + h / 2, lerp(0.85, 1, p), a, () => {
      glass(x, y, w, h, 30, { tint: color });
      solid(x + 18, y + (h - 60) / 2, 60, 60, 18, rgba(color, 0.22), { shadow: false });
      emoji(n[0], x + 48, y + h / 2 + 1, 30);
      text(n[1], x + 92, y + h / 2 - 4, { size: 22, weight: 800 });
      text(n[2], x + 92, y + h / 2 + 24, { size: 16, weight: 500, color: 'rgba(255,255,255,0.65)' });
    });
  }
  function track(x0, x1, y, p, color, t) {
    if (p <= 0) return;
    const xe = lerp(x0, x1, p);
    ctx.save(); ctx.strokeStyle = rgba(color, 0.55); ctx.lineWidth = 4; ctx.setLineDash([2, 12]); ctx.lineCap = 'round';
    ctx.beginPath(); ctx.moveTo(x0, y); ctx.lineTo(xe, y); ctx.stroke(); ctx.restore();
    for (let k = 0; k < 7; k++) {
      const x = lerp(x0, x1, (t * 0.16 + k / 7) % 1);
      if (x < xe) glowDot(x, y, 5, color);
    }
  }
  function gauge(cx, cy, r, v) {
    ctx.save();
    ctx.lineWidth = 24; ctx.lineCap = 'round';
    [[C.red, 0, 1 / 3], [C.gold, 1 / 3, 2 / 3], [C.green, 2 / 3, 1]].forEach(([c, a, b]) => {
      ctx.strokeStyle = c; ctx.beginPath();
      ctx.arc(cx, cy, r, Math.PI + a * Math.PI + 0.06, Math.PI + b * Math.PI - 0.06); ctx.stroke();
    });
    ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,255,255,0.35)'; ctx.lineCap = 'butt';
    for (let i = 0; i <= 12; i++) {
      const an = Math.PI + (i / 12) * Math.PI;
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(an) * (r - 26), cy + Math.sin(an) * (r - 26));
      ctx.lineTo(cx + Math.cos(an) * (r - 36), cy + Math.sin(an) * (r - 36)); ctx.stroke();
    }
    const an = Math.PI + v * Math.PI;
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 6; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(an) * (r - 44), cy + Math.sin(an) * (r - 44)); ctx.stroke();
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(cx, cy, 12, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }

  function sPipeline(t) {
    header(t, 'DUAL READING PIPELINE', 'Initial Benchmark & Ongoing Practice', 'Diagnostic assessment and classroom library reading');

    // Lane A
    text('A · DIAGNOSTIC BENCHMARK', 140, 350, { size: 19, weight: 800, color: C.gold, alpha: prog(t, 0.5, 0.4) });
    track(300, 1420, 432, E.inOutCubic(prog(t, 0.9, 1.3)), C.gold, t);
    PA.forEach((n, i) => {
      const st = 0.6 + i * 0.35;
      pnode(140 + i * 400, 372, 320, 120, n, C.gold, E.spring(prog(t, st, 1)), prog(t, st, 0.3));
    });

    const gp = E.spring(prog(t, 2.2, 1)), ga = prog(t, 2.2, 0.3);
    at(1560, 470, lerp(0.8, 1, gp), ga, () => {
      text('READING PROFICIENCY TIER', 1560, 326, { size: 16, weight: 800, color: C.gold, align: 'center' });
      gauge(1560, 480, 125, 0.83 * E.spring(prog(t, 2.8, 1.6)));
      const rp = prog(t, 4.0, 0.4);
      checkBadge(1478, 524, 13, C.green, rp);
      text('Independent', 1500, 532, { size: 24, weight: 800, color: '#7EF0C8', alpha: rp });
      text('Frustration', 1430, 576, { size: 15, weight: 700, color: C.red, align: 'center' });
      text('Instructional', 1560, 576, { size: 15, weight: 700, color: C.gold, align: 'center' });
      text('Independent', 1690, 576, { size: 15, weight: 700, color: C.green, align: 'center' });
    });

    // Lane B
    text('B · LIBRARY & PRACTICE', 140, 628, { size: 19, weight: 800, color: C.secondary, alpha: prog(t, 2.9, 0.4) });
    track(285, 1635, 710, E.inOutCubic(prog(t, 3.2, 1.4)), C.secondary, t);
    PB.forEach((n, i) => {
      const st = 3.0 + i * 0.3;
      pnode(140 + i * 337.5, 650, 290, 120, n, C.secondary, E.spring(prog(t, st, 1)), prog(t, st, 0.3));
    });

    revealText('Diagnostic assessment feeds ongoing practice.', 960, 860, t, 5.6, { size: 34, weight: 700, color: 'rgba(255,255,255,0.88)', align: 'center' });
  }

  /* ======================================================================
     SCENE 06 — AI engine (Gemini + OCR)
     ====================================================================== */
  const PASSAGE = [
    'The Philippine Eagle is one of the',
    'largest and most powerful eagles in',
    'the world. It is endemic to the',
    'Philippines, found only in the forests',
    'of Luzon, Samar, Leyte and Mindanao.',
    'As an apex predator, it sits at the',
    'top of the food chain and keeps the',
    'forest ecosystem in balance.',
  ];
  const QCARDS = [
    { tag: 'Vocabulary', c: C.primary, q: 'What does “endemic” mean?', o: ['Found only in one place', 'Dangerous', 'Extinct'] },
    { tag: 'Comprehension', c: C.blue, q: 'Why is it an apex predator?', o: ['Top of the food chain', 'Eats plants', 'Migrates'] },
    { tag: 'Cloze', c: C.green, q: 'It lives in the forests of ____.', o: ['Mindanao', 'Tokyo', 'the Sahara'] },
  ];

  function orb(cx, cy, r, T, energy) {
    const halo = ctx.createRadialGradient(cx, cy, 0, cx, cy, r * 2.6);
    halo.addColorStop(0, rgba(C.primaryLight, 0.35 + 0.25 * energy)); halo.addColorStop(1, rgba(C.primaryLight, 0));
    ctx.fillStyle = halo; ctx.fillRect(cx - r * 2.6, cy - r * 2.6, r * 5.2, r * 5.2);
    [C.secondary, C.lavender, '#7EF0D8'].forEach((col, k) => {
      ctx.save();
      ctx.translate(cx, cy); ctx.rotate(T * (0.5 + k * 0.3) * (k % 2 ? -1 : 1) + k * 1.1);
      const rx = r * 1.5, ry = r * (0.4 + k * 0.12);
      ctx.beginPath(); ctx.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2);
      ctx.strokeStyle = rgba(col, 0.55); ctx.lineWidth = 2.5; ctx.stroke();
      const a = T * (1.2 + k * 0.4);
      ctx.fillStyle = col; ctx.beginPath(); ctx.arc(Math.cos(a) * rx, Math.sin(a) * ry, 6, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
    });
    const g = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.4, r * 0.1, cx, cy, r);
    g.addColorStop(0, '#ffffff'); g.addColorStop(0.25, '#C9D8FF'); g.addColorStop(0.62, '#8A84FF'); g.addColorStop(1, '#4B3FD8');
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, r * (1 + 0.04 * energy * Math.sin(T * 18)), 0, Math.PI * 2); ctx.fill();
    sparkle(cx, cy, r * 0.42 * (1 + 0.08 * Math.sin(T * 3)), '#ffffff', 0.95);
  }

  function optionRow(opts, x, y, maxW, correct) {
    let size = 16, ws;
    do { ws = opts.map(o => measure(o, size, 700) + 26); size -= 1; } while (ws.reduce((a, b) => a + b, 0) + 10 * (opts.length - 1) > maxW && size > 11);
    size += 1;
    let cx = x;
    opts.forEach((o, i) => {
      pill(cx, y, ws[i], 34);
      ctx.fillStyle = i === correct ? rgba(C.green, 0.16) : '#F1F0F7'; ctx.fill();
      if (i === correct) { ctx.strokeStyle = C.green; ctx.lineWidth = 1.5; ctx.stroke(); }
      text(o, cx + ws[i] / 2, y + 18, { size, weight: 700, color: i === correct ? '#00876C' : C.muted, align: 'center', base: 'middle' });
      cx += ws[i] + 10;
    });
  }

  function sAI(t) {
    header(t, 'GEMINI 3.5 FLASH LITE', 'Reads the page. Writes the quiz.', 'OCR + Gemini turn any passage into vocabulary & comprehension checks', C.secondary);
    const D = { x: 140, y: 310, w: 560, h: 640 };
    const dp = E.spring(prog(t, 0.3, 1)), da = prog(t, 0.3, 0.3);
    const beamY = lerp(D.y + 140, D.y + 600, E.inOutCubic(prog(t, 0.9, 2.0)));

    at(D.x + D.w / 2, D.y + D.h / 2, lerp(0.92, 1, dp), da, () => {
      solid(D.x, D.y, D.w, D.h, 40, '#ffffff');
      text('READING PASSAGE · GRADE 7–8', D.x + 40, D.y + 60, { size: 15, weight: 800, color: C.primary });
      text('The Philippine Eagle', D.x + 40, D.y + 102, { size: 32, weight: 800, color: C.ink });
      labelPill('OCR', D.x + D.w - 64, D.y + 54, C.blue, prog(t, 0.8, 0.3), { size: 15, fill: '#EAF3FF' });
      PASSAGE.forEach((ln, i) => {
        const ly = D.y + 170 + i * 52;
        if (beamY > ly - 10) {
          const hl = i === 2 ? ['the world. It is ', 'endemic', 1.9] : i === 5 ? ['As an ', 'apex predator', 2.5] : null;
          if (hl) {
            const hp = E.outCubic(prog(t, hl[2], 0.4));
            const hx = D.x + 40 + measure(hl[0], 23, 500) - 4, hw = (measure(hl[1], 23, 500) + 8) * hp;
            ctx.fillStyle = rgba(C.primary, 0.16); squircle(hx, ly - 25, hw, 34, 8); ctx.fill();
            ctx.fillStyle = C.primary; ctx.fillRect(hx, ly + 6, hw, 3);
          }
          text(ln, D.x + 40, ly, { size: 23, weight: 500, color: '#3d4447' });
        } else {
          solid(D.x + 40, ly - 16, measure(ln, 23, 500), 14, 7, '#E9E7F3', { shadow: false });
        }
      });
      if (t > 0.9 && t < 3.1) {
        const ba = Math.min(prog(t, 0.9, 0.2), 1 - prog(t, 2.9, 0.2));
        ctx.save();
        ctx.globalAlpha *= ba;
        squircle(D.x, D.y, D.w, D.h, 40); ctx.clip();
        const g = ctx.createLinearGradient(0, beamY - 90, 0, beamY);
        g.addColorStop(0, rgba(C.secondary, 0)); g.addColorStop(1, rgba(C.secondary, 0.4));
        ctx.fillStyle = g; ctx.fillRect(D.x, beamY - 90, D.w, 90);
        ctx.shadowColor = C.blue; ctx.shadowBlur = 20; ctx.fillStyle = C.blue; ctx.fillRect(D.x, beamY - 2, D.w, 4);
        ctx.restore();
      }
    });

    // Orb
    const O = { x: 975, y: 610 };
    const energy = clamp(Math.max(prog(t, 2.8, 0.3) * (1 - prog(t, 3.8, 0.4)), t > 3.6 && t < 5.2 ? 0.6 : 0));
    const op = E.spring(prog(t, 1.0, 1.2));
    at(O.x, O.y, op, prog(t, 1.0, 0.3), () => {
      orb(O.x, O.y, 100, t, energy);
      text('Gemini 3.5 Flash Lite', O.x, 800, { size: 26, weight: 800, align: 'center' });
      text('Real-time contextual questions', O.x, 832, { size: 18, weight: 500, color: 'rgba(255,255,255,0.6)', align: 'center' });
    });

    // tokens streaming into the orb
    ['endemic', 'apex predator', 'forests', 'Mindanao', 'ecosystem', 'balance'].forEach((tok, k) => {
      const p = prog(t, 2.5 + k * 0.12, 0.9);
      if (p <= 0 || p >= 1) return;
      const e = E.inOutCubic(p);
      const sx = D.x + D.w - 30, sy = D.y + 200 + k * 64;
      const x = lerp(sx, O.x, e), y = lerp(sy, O.y, e) - Math.sin(e * Math.PI) * 40;
      at(x, y, lerp(1, 0.35, e), 1 - prog(p, 0.7, 0.3), () => {
        const w = measure(tok, 18, 700) + 26;
        pill(x - w / 2, y - 17, w, 34); ctx.fillStyle = C.primary; ctx.fill();
        text(tok, x, y + 1, { size: 18, weight: 700, align: 'center', base: 'middle' });
      });
    });

    // generated question cards
    QCARDS.forEach((q, i) => {
      const p = prog(t, 3.6 + i * 0.45, 0.8);
      if (p <= 0) return;
      const cx = 1510, cy = 320 + i * 172 + 76;
      const e = E.outCubic(p);
      const x = lerp(O.x, cx, e), y = lerp(O.y, cy, e);
      at(x, y, lerp(0.2, 1, E.outBack(p)), clamp(p * 2.5), () => {
        ctx.translate(x - cx, y - cy);
        const X = 1240, Y = cy - 76;
        solid(X, Y, 540, 152, 30, '#ffffff');
        tagPill(q.tag, X + 24, Y + 20, q.c, 15);
        text(`Q${i + 1}`, X + 516, Y + 44, { size: 16, weight: 800, color: '#B2AFC8', align: 'right' });
        text(q.q, X + 24, Y + 88, { size: 24, weight: 800, color: C.ink });
        optionRow(q.o, X + 24, Y + 104, 492, 0);
      });
    });
    labelPill('🧠  Spaced repetition: review “endemic” in 3 days', 1510, 878, C.lavender, prog(t, 5.3, 0.5), { size: 18 });
  }

  /* ======================================================================
     SCENE 07 — Floating reading lens (accessibility overlay)
     ====================================================================== */
  const ART_LINES = [
    'The Philippine Eagle is endemic to',
    'the islands and is now critically',
    'endangered. Conservationists work',
    'with local communities to protect',
    'its forest home so that future',
    'generations can watch this',
    'majestic bird soar.',
  ];
  function drawArticle(sx, sy, sw, hl) {
    text('9:41', sx + 30, sy + 34, { size: 15, weight: 700, color: C.ink });
    solid(sx + 20, sy + 52, sw - 40, 40, 20, '#ECEAF5', { shadow: false });
    text('🔒 news.ph/philippine-eagle', sx + 40, sy + 78, { size: 14, weight: 600, color: C.muted });
    squircle(sx + 20, sy + 108, sw - 40, 170, 22);
    ctx.fillStyle = grad(sx + 20, sy + 108, sx + sw - 20, sy + 278, '#00B894', '#0B6E5A'); ctx.fill();
    emoji('🦅', sx + sw / 2, sy + 194, 86);
    text('Saving the', sx + 24, sy + 326, { size: 28, weight: 800, color: C.ink });
    text('Philippine Eagle', sx + 24, sy + 360, { size: 28, weight: 800, color: C.ink });
    text('By R. Santos · 4 min read', sx + 24, sy + 392, { size: 14, weight: 600, color: C.muted });
    if (hl > 0) {
      const wb = wordBox(sx, sy);
      ctx.fillStyle = rgba(C.primary, 0.2 * hl); squircle(wb.x - 3, wb.y - 4, wb.w + 6, 26, 6); ctx.fill();
    }
    ART_LINES.forEach((l, i) => text(l, sx + 24, sy + 430 + i * 30, { size: 18, weight: 500, color: '#3d4447' }));
  }
  function wordBox(sx, sy) {
    const pre = measure('The Philippine Eagle is ', 18, 500), w = measure('endemic', 18, 500);
    return { x: sx + 24 + pre, y: sy + 430 - 14, w, cx: sx + 24 + pre + w / 2, cy: sy + 430 - 6 };
  }
  function speaker(x, y, s, color, t) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(-s * 0.5, -s * 0.18); ctx.lineTo(-s * 0.2, -s * 0.18); ctx.lineTo(s * 0.1, -s * 0.45);
    ctx.lineTo(s * 0.1, s * 0.45); ctx.lineTo(-s * 0.2, s * 0.18); ctx.lineTo(-s * 0.5, s * 0.18);
    ctx.closePath(); ctx.fill();
    ctx.strokeStyle = color; ctx.lineWidth = s * 0.09; ctx.lineCap = 'round';
    const base = ctx.globalAlpha;
    for (let i = 0; i < 2; i++) {
      ctx.globalAlpha = base * (0.3 + 0.7 * (0.5 + 0.5 * Math.sin(t * 6 - i * 1.2)));
      ctx.beginPath(); ctx.arc(s * 0.15, 0, s * (0.32 + i * 0.22), -0.8, 0.8); ctx.stroke();
    }
    ctx.restore();
  }

  function sLens(t) {
    header(t, 'ACCESSIBILITY OVERLAY', 'A reading assistant that floats over any app', 'Instant screen reading assistance, anywhere on screen', C.lavender);

    [['🫧', 'Floats over any app', 'Android overlay service'], ['⚡', 'One-tap screen scan', 'Instant OCR reading capture'], ['🗣️', 'Hear it pronounced', 'Phonetics & TTS audio'], ['✨', 'Context overview', 'AI explanation & meaning']].forEach((f, i) => {
      const p = E.outCubic(prog(t, 0.8 + i * 0.15, 0.6)), y = 360 + i * 112;
      at(390, y + 43, 1, p, () => {
        ctx.translate((1 - p) * -60, 0);
        glass(140, y, 500, 86, 30, { tint: C.lavender });
        emoji(f[0], 190, y + 44, 30);
        text(f[1], 232, y + 38, { size: 23, weight: 800 });
        text(f[2], 232, y + 64, { size: 16, weight: 500, color: 'rgba(255,255,255,0.65)' });
      });
    });

    const P = { x: 760, y: 290, w: 400, h: 700 };
    const sx = P.x + 14, sy = P.y + 14, sw = P.w - 28;
    const wb = wordBox(sx, sy);
    const start = { x: sx + sw - 44, y: sy + 300 }, ctrl = { x: sx + sw - 30, y: sy + 470 }, end = { x: wb.cx, y: wb.cy };
    const mv = E.inOutCubic(prog(t, 1.6, 1.0));
    let bp = mv > 0 ? quadPt(start, ctrl, end, mv) : { x: start.x, y: start.y + Math.sin(t * 3) * 6 };
    const tapP = prog(t, 2.6, 0.45);
    const edge = E.outCubic(tapP);
    bp = { x: bp.x + edge * 44, y: bp.y - edge * 44 };

    const pp = E.spring(prog(t, 0.3, 1.1));
    at(P.x + P.w / 2, P.y + P.h / 2, lerp(0.9, 1, pp), prog(t, 0.3, 0.3), () => {
      phone(P.x, P.y, P.w, P.h, () => {
        drawArticle(sx, sy, sw, prog(t, 2.8, 0.3));

        // When tapped: scan highlight target on the captured word "endemic"
        if (tapP > 0) {
          const sp = E.outBack(tapP);
          ctx.save();
          // Subtle glow squircle highlight
          ctx.fillStyle = rgba(C.primary, 0.22);
          squircle(wb.x - 6, wb.y - 4, wb.w + 12, 28, 8);
          ctx.fill();
          ctx.strokeStyle = C.primary;
          ctx.lineWidth = 2;
          ctx.stroke();

          // High-tech target reticle corners
          const rW = wb.w + 16, rH = 32, rx = wb.x - 8, ry = wb.y - 6;
          ctx.strokeStyle = C.secondary;
          ctx.lineWidth = 2.5;
          ctx.lineCap = 'round';
          // Top-left corner
          ctx.beginPath(); ctx.moveTo(rx, ry + 8); ctx.lineTo(rx, ry); ctx.lineTo(rx + 8, ry); ctx.stroke();
          // Top-right corner
          ctx.beginPath(); ctx.moveTo(rx + rW - 8, ry); ctx.lineTo(rx + rW, ry); ctx.lineTo(rx + rW, ry + 8); ctx.stroke();
          // Bottom-left corner
          ctx.beginPath(); ctx.moveTo(rx, ry + rH - 8); ctx.lineTo(rx, ry + rH); ctx.lineTo(rx + 8, ry + rH); ctx.stroke();
          // Bottom-right corner
          ctx.beginPath(); ctx.moveTo(rx + rW - 8, ry + rH); ctx.lineTo(rx + rW, ry + rH); ctx.lineTo(rx + rW, ry + rH - 8); ctx.stroke();

          // Ripple pulse wave
          const ripR = 38 * Math.min(1.8, sp);
          ctx.strokeStyle = rgba(C.primaryLight, Math.max(0, 1 - tapP));
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(wb.cx, wb.cy, ripR, 0, Math.PI * 2);
          ctx.stroke();
          ctx.restore();
        }

        const ba = E.spring(prog(t, 0.8, 0.8));
        at(bp.x, bp.y, ba, prog(t, 0.8, 0.2), () => drawBubble(bp.x, bp.y, 34));
      });
    });

    // tooltip card
    const tp = E.spring(prog(t, 3.0, 1)), ta = prog(t, 3.0, 0.3);
    const T0 = { x: 1230, y: 380, w: 550, h: 330 };
    if (ta > 0) {
      strokeQuad({ x: wb.x + wb.w + 10, y: wb.cy }, { x: 1200, y: wb.cy }, { x: T0.x, y: T0.y + T0.h / 2 }, E.outCubic(prog(t, 2.9, 0.4)), rgba(C.lavender, 0.8), 3, [3, 9]);
    }
    at(T0.x, T0.y + T0.h / 2, lerp(0.7, 1, tp), ta, () => {
      solid(T0.x, T0.y, T0.w, T0.h, 36, '#ffffff');
      text('endemic', T0.x + 36, T0.y + 78, { size: 48, weight: 800, color: C.ink });
      tagPill('adj.', T0.x + 50 + measure('endemic', 48, 800), T0.y + 46, C.primary, 15);
      text('/ɛnˈdɛm.ɪk/', T0.x + 36, T0.y + 120, { size: 26, weight: 600, color: C.primary, font: '"Segoe UI",system-ui,sans-serif' });
      ctx.fillStyle = rgba(C.primary, 0.12); ctx.beginPath(); ctx.arc(T0.x + T0.w - 70, T0.y + 76, 34, 0, Math.PI * 2); ctx.fill();
      speaker(T0.x + T0.w - 74, T0.y + 76, 32, C.primary, t);
      ctx.fillStyle = '#ECEAF5'; ctx.fillRect(T0.x + 36, T0.y + 152, T0.w - 72, 2);
      text('✨ CONTEXT OVERVIEW', T0.x + 36, T0.y + 192, { size: 15, weight: 800, color: C.primary });
      text('Native and restricted to a specific region.', T0.x + 36, T0.y + 236, { size: 24, weight: 700, color: C.ink });
      text('Found only in Philippine rainforest habitats.', T0.x + 36, T0.y + 278, { size: 19, weight: 500, color: C.muted });
    });
  }

  /* ======================================================================
     SCENE 08 — Gamification & combo streak (Engine 4)
     ====================================================================== */
  const QUIZ = [
    { m: 'Cloze', c: C.primary, q: ['The Philippine Eagle is ____', 'to the Philippines.'], o: ['endemic', 'foreign', 'extinct', 'frozen'], a: 0 },
    { m: 'Rapid Definition', c: C.blue, q: ['“Apex predator” means…'], o: ['Top of the food chain', 'A small prey animal', 'A plant eater', 'A night bird'], a: 0 },
    { m: 'Context Meaning', c: C.green, q: ['“The bamboo is resilient,', 'bending but never breaking.”'], o: ['Fragile', 'Able to recover', 'Colorful', 'Very tall'], a: 1 },
    { m: 'Grammar Landmine', c: C.orange, q: ['Pick the correct sentence.'], o: ['She read every days.', 'She reads every day.', 'She reading every day.', 'She readed every day.'], a: 1 },
    { m: 'Impostor Word', c: C.pink, q: ['Which word does not belong?'], o: ['Eagle', 'Hawk', 'Bamboo', 'Hen'], a: 2 },
  ];
  const CONF = Array.from({ length: 130 }, (_, i) => {
    const r = mulberry32(100 + i);
    const a = -Math.PI / 2 + (r() - 0.5) * 2.6, sp = 500 + r() * 900;
    return { vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, rot: r() * 6, vr: (r() - 0.5) * 12,
      c: [C.gold, C.primaryLight, C.green, C.secondary, C.pink, '#ffffff'][i % 6], w: 8 + r() * 10, h: 4 + r() * 6 };
  });
  function confetti(cx, cy, tt) {
    if (tt < 0 || tt > 3) return;
    const k = (1 - Math.exp(-2 * tt)) / 2;
    for (const p of CONF) {
      const x = cx + p.vx * k, y = cy + p.vy * k + 300 * tt * tt;
      ctx.save();
      ctx.globalAlpha *= 1 - tt / 3;
      ctx.translate(x, y); ctx.rotate(p.rot + p.vr * tt);
      ctx.fillStyle = p.c; ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
      ctx.restore();
    }
  }

  function sGame(t) {
    header(t, 'GAMIFICATION & RECALL', 'Every right answer feeds the combo', 'Five recall mechanics · real-time comboStreak · earned badges', C.orange);
    const S0 = 0.7, SL = 1.05, HIT = 0.55;
    const hits = QUIZ.map((_, i) => S0 + i * SL + HIT);
    const combo = hits.filter(h => t >= h).length;
    const qi = clamp(Math.floor((t - S0) / SL), 0, 4);
    const monarch = t >= hits[4];

    // Quiz card
    const X = 140, Y = 310, QW = 720, QH = 640;
    const cp = E.spring(prog(t, 0.3, 1)), ca = prog(t, 0.3, 0.3);
    const lastHit = combo > 0 ? hits[combo - 1] : -9, dt = t - lastHit;
    const shake = dt < 0.25 ? Math.sin(dt * 80) * 6 * (1 - dt / 0.25) : 0;
    at(X + QW / 2, Y + QH / 2, lerp(0.9, 1, cp), ca, () => {
      ctx.translate(shake, 0);
      solid(X, Y, QW, QH, 44, '#ffffff');
      for (let i = 0; i < 5; i++) {
        pill(X + 40 + i * 58, Y + 40, 48, 10);
        ctx.fillStyle = t >= hits[i] ? C.green : i === qi ? C.primaryVariant : '#E6E4F0'; ctx.fill();
      }
      text(`Q${qi + 1} / 5`, X + QW - 40, Y + 52, { size: 18, weight: 700, color: C.muted, align: 'right' });
      const q = QUIZ[qi], lt = t - (S0 + qi * SL);
      const ip = qi === 0 ? E.outCubic(prog(t, 0.35, 0.4)) : E.outCubic(clamp(lt / 0.3));
      ctx.save();
      ctx.beginPath(); ctx.rect(X, Y + 70, QW, QH - 70); ctx.clip();
      ctx.translate((1 - ip) * 80, 0); ctx.globalAlpha *= ip;
      tagPill(q.m, X + 40, Y + 90, q.c, 17);
      q.q.forEach((ln, j) => text(ln, X + 40, Y + 192 + j * 42, { size: 32, weight: 800, color: C.ink }));
      q.o.forEach((o, k) => {
        const oy = Y + 290 + k * 84, picked = k === q.a && lt >= HIT;
        const pp = picked ? E.spring(prog(lt, HIT, 0.5)) : 0;
        at(X + QW / 2, oy + 34, picked ? lerp(0.95, 1, pp) : 1, 1, () => {
          solid(X + 40, oy, QW - 80, 68, 22, picked ? '#E6FBF5' : '#F4F3FA', { shadow: false, stroke: picked ? C.green : '#E6E4F0' });
          ctx.fillStyle = picked ? C.green : '#ffffff'; ctx.beginPath(); ctx.arc(X + 82, oy + 34, 18, 0, Math.PI * 2); ctx.fill();
          ctx.strokeStyle = picked ? C.green : '#DAD7E8'; ctx.lineWidth = 2; ctx.stroke();
          text('ABCD'[k], X + 82, oy + 35, { size: 16, weight: 800, color: picked ? '#fff' : C.muted, align: 'center', base: 'middle' });
          text(o, X + 116, oy + 42, { size: 24, weight: 600, color: C.ink });
          if (picked) checkBadge(X + QW - 82, oy + 34, 16, C.green, pp);
        });
      });
      ctx.restore();
      if (lt >= HIT && lt < HIT + 0.8) {
        const fp = prog(lt, HIT, 0.8);
        text('+1 🔥', X + QW - 150, Y + 280 + q.a * 84 - fp * 60, { size: 28, weight: 800, color: C.orange, alpha: 1 - fp });
      }
    });

    // Combo ring
    const RX = 1150, RY = 600, RR = 165;
    if (monarch) {
      const mp = prog(t, hits[4], 0.6);
      ctx.save();
      ctx.globalAlpha *= 0.16 * mp;
      ctx.translate(RX, RY); ctx.rotate(t * 0.3); ctx.fillStyle = C.gold;
      for (let i = 0; i < 14; i++) {
        ctx.rotate((Math.PI * 2) / 14);
        ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(-40, -480); ctx.lineTo(40, -480); ctx.closePath(); ctx.fill();
      }
      ctx.restore();
    }
    const rp = E.spring(prog(t, 0.5, 1));
    at(RX, RY, lerp(0.8, 1, rp), prog(t, 0.5, 0.3), () => {
      const inner = ctx.createRadialGradient(RX, RY, 0, RX, RY, RR);
      inner.addColorStop(0, rgba(monarch ? C.gold : C.primary, 0.28)); inner.addColorStop(1, rgba(C.primary, 0.05));
      ctx.fillStyle = inner; ctx.beginPath(); ctx.arc(RX, RY, RR, 0, Math.PI * 2); ctx.fill();
      ctx.lineWidth = 22; ctx.lineCap = 'round';
      ctx.strokeStyle = 'rgba(255,255,255,0.12)'; ctx.beginPath(); ctx.arc(RX, RY, RR, 0, Math.PI * 2); ctx.stroke();
      const cv = hits.reduce((s, h) => s + E.outCubic(prog(t, h, 0.4)), 0) / 5;
      if (cv > 0) {
        ctx.save();
        ctx.shadowColor = C.gold; ctx.shadowBlur = 20;
        ctx.strokeStyle = grad(RX - RR, RY - RR, RX + RR, RY + RR, C.gold, C.orange);
        ctx.beginPath(); ctx.arc(RX, RY, RR, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * cv); ctx.stroke();
        ctx.restore();
      }
      const pop = combo > 0 ? 1 + 0.35 * (1 - E.spring(prog(t, hits[combo - 1], 0.7))) : 1;
      emoji(monarch ? '👑' : '🔥', RX, RY - 84, 54);
      at(RX, RY + 10, pop, 1, () => text(`${combo}x`, RX, RY + 46, { size: 108, weight: 800, align: 'center', color: monarch ? C.gold : '#ffffff' }));
      text('COMBO', RX, RY + 98, { size: 20, weight: 800, color: 'rgba(255,255,255,0.6)', align: 'center' });
    });
    if (monarch) {
      const mp = E.spring(prog(t, hits[4] + 0.1, 0.8));
      at(RX, 822, lerp(0.5, 1, mp), prog(t, hits[4] + 0.1, 0.2), () => text('FLAWLESS RUN UNLOCKED', RX, 834, { size: 30, weight: 800, color: C.gold, align: 'center' }));
      labelPill('🔥 Peak streak recorded to student profile', RX, 892, C.lavender, prog(t, hits[4] + 0.6, 0.4), { size: 16 });
    }

    // Badge tiers
    const active = combo === 0 ? 0 : combo < 5 ? 1 : 2;
    const changeAt = active === 0 ? 0 : active === 1 ? hits[0] : hits[4];
    [['🌱', 'Getting Started', '0x combo', C.green], ['🔥', 'Peak Streak', '1–4x combo', C.orange], ['👑', 'Combo Monarch', '≥ 5x · flawless', C.gold]].forEach((b, i) => {
      const y = 380 + i * 140, x = 1440, w = 340, h = 118, on = i === active;
      const ap = prog(t, 0.8 + i * 0.12, 0.5);
      const s = on ? lerp(1, 1.05, i === 0 ? 1 : E.spring(prog(t, changeAt, 0.6))) : 1;
      at(x + w / 2, y + h / 2, s, ap * (on ? 1 : 0.45), () => {
        glass(x, y, w, h, 32, { tint: on ? b[3] : null, stroke: on ? rgba(b[3], 0.8) : 'rgba(255,255,255,0.18)' });
        emoji(b[0], x + 52, y + h / 2, 40);
        text(b[1], x + 96, y + 54, { size: 25, weight: 800 });
        text(b[2], x + 96, y + 84, { size: 17, weight: 600, color: on ? b[3] : 'rgba(255,255,255,0.6)' });
      });
    });

    confetti(RX, RY, t - hits[4]);
  }

  /* ======================================================================
     SCENE 09 — Teacher console (Engine 5)
     ====================================================================== */
  const STATS = [
    { label: 'Students', val: 32, suffix: '', icon: '👥', c: C.secondary },
    { label: 'Completion', val: 87, suffix: '%', icon: '📈', c: C.green },
    { label: 'Avg peak combo', val: 6, suffix: 'x', icon: '🔥', c: C.gold },
  ];
  const ROSTER = [
    ['Juan Cruz', 'Independent'], ['Maria Santos', 'Instructional'], ['Jose Reyes', 'Independent'], ['Ana Lim', 'Frustration'],
    ['Paolo Garcia', 'Instructional'], ['Bea Mendoza', 'Independent'], ['Carlo Bautista', 'Instructional'],
  ];
  const TIER_C = { Independent: C.green, Instructional: C.gold, Frustration: C.red };

  function sConsole(t) {
    header(t, 'TEACHER CONSOLE', 'Assign a whole section in one tap', 'Universal search · Select All · instant classroom filtering', C.green);

    // search
    const sp = E.spring(prog(t, 0.3, 1));
    at(590, 352, lerp(0.94, 1, sp), prog(t, 0.3, 0.3), () => {
      glass(140, 310, 900, 84, 42);
      ctx.save(); ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.arc(186, 348, 11, 0, Math.PI * 2); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(194, 356); ctx.lineTo(203, 365); ctx.stroke(); ctx.restore();
      const q = typed('Emerald', prog(t, 0.8, 0.7));
      text(q || 'Search materials, folders, students…', 226, 362, { size: 26, weight: 600, color: q ? '#fff' : 'rgba(255,255,255,0.4)' });
      if (t < 2.2) caret(226 + (q ? measure(q, 26, 600) : -4), 366, 30, C.secondary, t);
      text('32 matches · Instant', 1010, 360, { size: 18, weight: 700, color: C.green, align: 'right', alpha: prog(t, 1.6, 0.3) });
    });

    // stat tiles
    STATS.forEach((s, i) => {
      const x = 140 + i * 310, y = 420, w = 280, h = 170;
      const st = 0.7 + i * 0.12;
      at(x + w / 2, y + h / 2, lerp(0.85, 1, E.spring(prog(t, st, 1))), prog(t, st, 0.3), () => {
        glass(x, y, w, h, 34, { tint: s.c });
        emoji(s.icon, x + 46, y + 48, 28);
        text(s.label, x + 78, y + 56, { size: 18, weight: 600, color: 'rgba(255,255,255,0.72)' });
        const v = Math.round(s.val * E.outCubic(prog(t, 1.0 + i * 0.12, 1.4)));
        text(v + s.suffix, x + 30, y + 140, { size: 64, weight: 800 });
      });
    });

    // chart
    at(590, 785, lerp(0.92, 1, E.spring(prog(t, 1.2, 1))), prog(t, 1.2, 0.3), () => {
      glass(140, 620, 900, 330, 36);
      text('Independent readers · Grade 10 - Emerald', 176, 668, { size: 20, weight: 700 });
      text('+41 pts in 6 weeks', 1004, 668, { size: 18, weight: 700, color: C.green, align: 'right' });
      const vals = [38, 46, 52, 61, 70, 79], bw = 80, gap = 60, x0 = 200, base = 905, maxH = 220;
      const tops = [];
      vals.forEach((v, i) => {
        const p = E.outBack(prog(t, 1.6 + i * 0.1, 0.7));
        const h = Math.max(0, maxH * (v / 100) * p), bx = x0 + i * (bw + gap);
        tops.push({ x: bx + bw / 2, y: base - maxH * (v / 100) });
        if (h > 1) {
          squircle(bx, base - h, bw, h, 14);
          ctx.fillStyle = grad(0, base - h, 0, base, C.primaryLight, rgba(C.secondary, 0.55)); ctx.fill();
        }
        text(`W${i + 1}`, bx + bw / 2, base + 30, { size: 16, weight: 700, color: 'rgba(255,255,255,0.55)', align: 'center' });
        text(`${v}%`, bx + bw / 2, base - h - 12, { size: 16, weight: 800, align: 'center', alpha: prog(t, 1.9 + i * 0.1, 0.3) });
      });
      const lp = prog(t, 2.4, 1.0);
      if (lp > 0) {
        ctx.save(); ctx.strokeStyle = C.gold; ctx.lineWidth = 3; ctx.lineJoin = 'round';
        ctx.beginPath();
        const segs = (tops.length - 1) * lp;
        tops.forEach((pt, i) => {
          if (i === 0) ctx.moveTo(pt.x, pt.y - 34);
          else if (i <= segs) ctx.lineTo(pt.x, pt.y - 34);
          else if (i - 1 < segs) { const f = segs - (i - 1); ctx.lineTo(lerp(tops[i - 1].x, pt.x, f), lerp(tops[i - 1].y, pt.y, f) - 34); }
        });
        ctx.stroke(); ctx.restore();
      }
    });

    // roster
    const R = { x: 1090, y: 310, w: 690, h: 640 };
    at(R.x + R.w / 2, R.y + R.h / 2, lerp(0.92, 1, E.spring(prog(t, 0.6, 1))), prog(t, 0.6, 0.3), () => {
      glass(R.x, R.y, R.w, R.h, 40, { tint: C.primary });
      text('Roster · Emerald', R.x + 36, R.y + 54, { size: 26, weight: 800 });
      const selP = prog(t, 3.0, 0.3), sel = t >= 3.0;
      at(1565, 356, selP > 0 && selP < 1 ? 1 - 0.1 * Math.sin(selP * Math.PI) : 1, 1, () => {
        pill(1500, 334, 130, 44); ctx.fillStyle = sel ? C.primary : 'rgba(255,255,255,0.1)'; ctx.fill();
        ctx.strokeStyle = sel ? C.primary : 'rgba(255,255,255,0.25)'; ctx.lineWidth = 1.5; ctx.stroke();
        text('Select All', 1565, 357, { size: 16, weight: 800, align: 'center', base: 'middle' });
      });
      pill(1640, 334, 110, 44); ctx.strokeStyle = 'rgba(255,255,255,0.25)'; ctx.lineWidth = 1.5; ctx.stroke();
      text('Clear All', 1695, 357, { size: 16, weight: 700, color: 'rgba(255,255,255,0.7)', align: 'center', base: 'middle' });

      ROSTER.forEach(([name, tier], k) => {
        const y = R.y + 100 + k * 62, rp = E.outCubic(prog(t, 1.7 + k * 0.06, 0.5));
        at(R.x + R.w / 2, y + 26, 1, rp, () => {
          ctx.translate(0, (1 - rp) * 20);
          solid(R.x + 20, y, R.w - 40, 52, 18, 'rgba(255,255,255,0.06)', { shadow: false });
          avatar(R.x + 50, y + 26, 18, name.split(' ').map(s => s[0]).join(''), AV[k % 5]);
          text(name, R.x + 82, y + 33, { size: 20, weight: 600 });
          const tw = measure(tier, 15, 800) + 26;
          pill(1480, y + 11, tw, 30); ctx.fillStyle = rgba(TIER_C[tier], 0.2); ctx.fill();
          text(tier, 1480 + tw / 2, y + 27, { size: 15, weight: 800, color: TIER_C[tier], align: 'center', base: 'middle' });
          const ck = prog(t, 3.1 + k * 0.05, 0.5);
          squircle(1716, y + 11, 30, 30, 9);
          ctx.strokeStyle = 'rgba(255,255,255,0.4)'; ctx.lineWidth = 2; ctx.stroke();
          if (ck > 0) {
            const s = E.outBack(ck);
            at(1731, y + 26, s, 1, () => {
              squircle(1716, y + 11, 30, 30, 9); ctx.fillStyle = C.primary; ctx.fill();
              ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
              ctx.beginPath(); ctx.moveTo(1723, y + 26); ctx.lineTo(1729, y + 32); ctx.lineTo(1740, y + 20); ctx.stroke();
            });
          }
        });
      });

      const bp = prog(t, 4.4, 0.3), done = t >= 4.7;
      at(R.x + R.w / 2, R.y + 588, bp > 0 && bp < 1 ? 1 - 0.06 * Math.sin(bp * Math.PI) : 1, prog(t, 3.4, 0.4), () => {
        pill(R.x + 36, R.y + 556, R.w - 72, 64);
        ctx.fillStyle = done ? C.green : grad(R.x, 0, R.x + R.w, 0, C.primary, C.primaryLight); ctx.fill();
        text(done ? '✓  Assigned “Mount Mayon” to 32 students' : '📖  Assign “Mount Mayon” · 32 students', R.x + R.w / 2, R.y + 589, {
          size: 21, weight: 800, align: 'center', base: 'middle',
        });
      });
      if (done) {
        const rp = prog(t, 4.7, 0.7);
        ctx.save(); ctx.globalAlpha *= 1 - rp; ctx.strokeStyle = C.green; ctx.lineWidth = 3;
        pill(R.x + 36 - rp * 20, R.y + 556 - rp * 14, R.w - 72 + rp * 40, 64 + rp * 28); ctx.stroke(); ctx.restore();
      }
    });
  }

  /* ======================================================================
     SCENE 10 — Outro
     ====================================================================== */
  function sOutro(t) {
    const cx = 960, cy = 290;
    const sp = E.spring(prog(t, 0.2, 1.2));
    const gl = ctx.createRadialGradient(cx, cy, 0, cx, cy, 460);
    gl.addColorStop(0, rgba(C.primaryLight, 0.5 * clamp(sp))); gl.addColorStop(1, rgba(C.primaryLight, 0));
    ctx.fillStyle = gl; ctx.fillRect(cx - 460, cy - 460, 920, 920);
    for (let i = 0; i < 14; i++) {
      const p = prog(t, 0.9, 1.4), a = (i / 14) * Math.PI * 2 + 0.2;
      if (p > 0 && p < 1) sparkle(cx + Math.cos(a) * (150 + p * 220), cy + Math.sin(a) * (150 + p * 220), 10 * (1 - p) + 4, i % 2 ? C.secondary : '#ffffff', 1 - p);
    }
    if (sp > 0.001) {
      ctx.save();
      ctx.translate(cx, cy + Math.sin(t * 1.6) * 6); ctx.scale(sp, sp);
      drawLogo(0, 0, 220, E.inOutCubic(prog(t, 0.5, 0.8)), t, { sparkles: E.outBack(prog(t, 1.0, 0.5)) });
      ctx.restore();
    }
    revealText('GabAI', cx, 520, t, 0.9, { size: 110, color: '#ffffff', align: 'center', dur: 0.8 });
    revealText('Read. Understand. Grow.', cx, 595, t, 1.5, { size: 40, weight: 800, color: grad(700, 0, 1220, 0, C.secondary, C.lavender), align: 'center', stagger: 0.15 });
    text('AI-powered mobile reading assistant for students & teachers', cx, 650, { size: 24, weight: 500, color: 'rgba(255,255,255,0.75)', align: 'center', alpha: E.outCubic(prog(t, 1.9, 0.6)) });

    // Friendly, human-understandable benefit pills (no technical jargon)
    chipRow(['📖 Diagnostic Assessment', '⚡ Instant Word Help', '🎮 Gamified Recall', '📊 Real-Time Progress'], cx, 706, t, 2.2, { size: 20, h: 48, padX: 22 });

    // Download Card featuring vgabai.netlify.app
    const dp = E.spring(prog(t, 2.5, 0.9)), da = prog(t, 2.5, 0.3);
    const cardW = 720, cardH = 96, cardX = cx - cardW / 2, cardY = 794;
    at(cx, cardY + cardH / 2, lerp(0.85, 1, dp), da, () => {
      ctx.save();
      ctx.shadowColor = rgba(C.primaryLight, 0.45);
      ctx.shadowBlur = 32;
      pill(cardX, cardY, cardW, cardH);
      ctx.fillStyle = 'rgba(255,255,255,0.11)';
      ctx.fill();
      ctx.strokeStyle = grad(cardX, 0, cardX + cardW, 0, C.primaryLight, C.secondary);
      ctx.lineWidth = 2.5;
      ctx.stroke();
      ctx.restore();

      // Download icon badge
      pill(cardX + 22, cardY + 20, 56, 56);
      ctx.fillStyle = grad(cardX + 22, 0, cardX + 78, 0, C.primary, C.primaryLight);
      ctx.fill();
      text('⬇', cardX + 50, cardY + 49, { size: 26, weight: 800, color: '#fff', align: 'center', base: 'middle' });

      // Action label & link
      text('Download the app free at', cardX + 98, cardY + 38, { size: 16, weight: 600, color: 'rgba(255,255,255,0.75)' });
      text('vgabai.netlify.app', cardX + 98, cardY + 70, { size: 30, weight: 800, color: '#7EF0D8' });

      // "Get App" pill on right
      pill(cardX + cardW - 180, cardY + 25, 158, 46);
      ctx.fillStyle = 'rgba(126,240,216,0.18)';
      ctx.fill();
      ctx.strokeStyle = '#7EF0D8';
      ctx.lineWidth = 1.5;
      ctx.stroke();
      text('GET APP →', cardX + cardW - 101, cardY + 48, { size: 16, weight: 800, color: '#7EF0D8', align: 'center', base: 'middle' });
    });
  }

  /* ---------- Timeline ---------- */
  const SCENES = [
    { name: 'Intro', dur: 6.5, draw: sIntro },
    { name: 'Platform', dur: 7.5, draw: sRoles },
    { name: 'Onboarding', dur: 8.5, draw: sOnboard },
    { name: 'QR Enrollment', dur: 8, draw: sQR },
    { name: 'Reading Pipeline', dur: 8.5, draw: sPipeline },
    { name: 'Gemini AI', dur: 8, draw: sAI },
    { name: 'Reading Overlay', dur: 8, draw: sLens },
    { name: 'Gamification', dur: 8.5, draw: sGame },
    { name: 'Teacher Console', dur: 8, draw: sConsole },
    { name: 'Outro', dur: 7.5, draw: sOutro },
  ];
  const STARTS = [];
  let TOTAL = 0;
  for (const s of SCENES) { STARTS.push(TOTAL); TOTAL += s.dur; }

  function sceneAt(T) {
    let i = 0;
    while (i < SCENES.length - 1 && T >= STARTS[i + 1]) i++;
    return i;
  }

  function hud(T, idx, t) {
    const ba = idx === 0 ? 0 : idx === SCENES.length - 1 ? 1 - prog(t, 0, 0.5) : idx === 1 ? prog(t, 0, 0.6) : 1;
    at(1760, 116, 1, ba, () => {
      drawLogo(1760, 116, 58, 1, T);
      text('GabAI', 1716, 128, { size: 32, weight: 800, align: 'right' });
    });
  }

  function render(T) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = 1;
    drawBackground(T);
    const idx = sceneAt(T), s = SCENES[idx], t = T - STARTS[idx];
    const isLast = idx === SCENES.length - 1;
    const aIn = idx === 0 ? 1 : E.outCubic(prog(t, 0, 0.55));
    const aOut = isLast ? 1 : 1 - E.inOutCubic(prog(t, s.dur - 0.45, 0.45));
    ctx.save();
    ctx.globalAlpha = Math.min(aIn, aOut);
    const sc = lerp(1.04, 1, aIn) * lerp(0.98, 1, aOut);
    ctx.translate(W / 2, H / 2); ctx.scale(sc, sc); ctx.translate(-W / 2, -H / 2);
    s.draw(t, s.dur);
    ctx.restore();
    hud(T, idx, t);
  }

  /* ---------- Player ---------- */
  const $ = id => document.getElementById(id);
  const wrap = $('stageWrap'), btnPlay = $('btnPlay'), btnRec = $('btnRecord');
  const fillEl = $('fill'), knob = $('knob'), timeEl = $('time'), timeline = $('timeline'), chaptersEl = $('chapters');
  const params = new URLSearchParams(location.search);
  if (params.has('clean')) document.body.classList.add('clean');

  let T = clamp(parseFloat(params.get('t')) || 0, 0, TOTAL);
  const hashScene = location.hash.replace('#', '');
  if (params.has('scene')) {
    T = STARTS[clamp((parseInt(params.get('scene'), 10) || 1) - 1, 0, SCENES.length - 1)];
  } else if (hashScene && /^\d+$/.test(hashScene)) {
    T = STARTS[clamp(parseInt(hashScene, 10) - 1, 0, SCENES.length - 1)];
  }
  let playing = !params.has('paused');
  let last = performance.now();
  let recorder = null;

  const fmt = s => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

  function fit() {
    const parent = wrap || (canvas ? canvas.parentElement : null);
    if (!parent) return;
    const r = parent.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return;
    const s = Math.min(r.width / W, r.height / H);
    canvas.style.width = `${Math.floor(W * s)}px`;
    canvas.style.height = `${Math.floor(H * s)}px`;
  }
  window.addEventListener('resize', fit);
  document.addEventListener('fullscreenchange', () => setTimeout(fit, 50));
  fit();

  const chapterBtns = [];
  if (chaptersEl) {
    STARTS.forEach((st, i) => {
      const markers = $('markers');
      if (i && markers) { const m = document.createElement('i'); m.style.left = `${(st / TOTAL) * 100}%`; markers.appendChild(m); }
      const b = document.createElement('button');
      b.innerHTML = `<b>${String(i + 1).padStart(2, '0')}</b>${SCENES[i].name}`;
      b.addEventListener('click', () => seek(st));
      chaptersEl.appendChild(b);
      chapterBtns.push(b);
    });
  }

  let toastTimer = 0;
  function toast(msg, ms = 2600) {
    const el = $('toast');
    if (!el) { console.log('[GabAI]', msg); return; }
    el.textContent = msg; el.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), ms);
  }

  function seek(v) { if (!recorder) T = clamp(v, 0, TOTAL - 0.001); }
  function setPlaying(p) { playing = p; }

  let lastIdx = -1;
  function updateUI() {
    const f = T / TOTAL;
    if (fillEl) fillEl.style.width = `${f * 100}%`;
    if (knob) knob.style.left = `${f * 100}%`;
    if (timeEl) timeEl.textContent = `${fmt(T)} / ${fmt(TOTAL)}`;
    if (btnPlay) btnPlay.textContent = playing ? '❚❚' : '▶';
    const idx = sceneAt(T);
    if (idx !== lastIdx) {
      chapterBtns.forEach((b, i) => b.classList.toggle('active', i === idx));
      lastIdx = idx;
    }
    if (recorder && btnRec) btnRec.textContent = `■ Recording… ${Math.round(f * 100)}%`;
  }

  function frame(now) {
    const dt = Math.min(0.1, (now - last) / 1000);
    last = now;
    if (playing) {
      T += dt;
      if (T >= TOTAL) {
        if (recorder) { T = TOTAL; render(T); stopRecording(); playing = false; }
        else T = 0;
      }
    }
    render(Math.min(T, TOTAL));
    updateUI();
    if (window.GabAIMotion && typeof window.GabAIMotion.onFrame === 'function') {
      window.GabAIMotion.onFrame(T, sceneAt(T), playing);
    }
    requestAnimationFrame(frame);
  }

  /* ---------- WebM export ---------- */
  function startRecording() {
    if (!canvas.captureStream || !window.MediaRecorder) { toast('Video export is not supported in this browser.'); return; }
    const mime = ['video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'].find(m => MediaRecorder.isTypeSupported(m)) || '';
    const chunks = [];
    const rec = new MediaRecorder(canvas.captureStream(60), mime ? { mimeType: mime, videoBitsPerSecond: 16e6 } : undefined);
    rec.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data); };
    rec.onstop = () => {
      document.body.classList.remove('is-recording');
      if (btnRec) btnRec.textContent = '● Export WebM';
      if (rec._cancelled) { toast('Export cancelled.'); return; }
      const url = URL.createObjectURL(new Blob(chunks, { type: 'video/webm' }));
      const a = document.createElement('a');
      a.href = url; a.download = 'gabai-motion.webm'; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
      toast('✅ gabai-motion.webm downloaded');
    };
    recorder = rec;
    T = 0; playing = true; last = performance.now();
    document.body.classList.add('is-recording');
    rec.start(250);
    toast('Recording in real time — keep this tab visible.', 3500);
  }
  function stopRecording(cancel = false) {
    if (!recorder) return;
    recorder._cancelled = cancel;
    recorder.stop();
    recorder = null;
  }

  /* ---------- Input ---------- */
  if (btnPlay) btnPlay.addEventListener('click', () => { if (!recorder) setPlaying(!playing); });
  if ($('btnRestart')) $('btnRestart').addEventListener('click', () => { seek(0); setPlaying(true); });
  if ($('btnFull') && wrap) $('btnFull').addEventListener('click', () => (document.fullscreenElement ? document.exitFullscreen() : wrap.requestFullscreen()));
  if (btnRec) btnRec.addEventListener('click', () => (recorder ? stopRecording(true) : startRecording()));

  if (timeline) {
    let dragging = false;
    const seekFromEvent = e => { const r = timeline.getBoundingClientRect(); seek(((e.clientX - r.left) / r.width) * TOTAL); };
    timeline.addEventListener('pointerdown', e => { dragging = true; timeline.setPointerCapture(e.pointerId); seekFromEvent(e); });
    timeline.addEventListener('pointermove', e => { if (dragging) seekFromEvent(e); });
    timeline.addEventListener('pointerup', () => { dragging = false; });
  }

  window.addEventListener('keydown', e => {
    if (recorder && e.key !== 'Escape') return;
    switch (e.key) {
      case ' ':
        if (btnPlay || timeline) { e.preventDefault(); setPlaying(!playing); }
        break;
      case 'ArrowRight': seek(T + 5); break;
      case 'ArrowLeft': seek(T - 5); break;
      case 'r': case 'R': if (timeline) { seek(0); setPlaying(true); } break;
      case 'f': case 'F': if ($('btnFull')) $('btnFull').click(); break;
      case 'Escape': if (recorder) stopRecording(true); break;
      default:
        if (/^[0-9]$/.test(e.key) && timeline) { const i = e.key === '0' ? 9 : +e.key - 1; if (STARTS[i] !== undefined) seek(STARTS[i]); }
    }
  });

  function renderSceneToCanvas(targetCanvas, sceneIndex, t, bgTime) {
    if (!targetCanvas) return;
    targetCanvas.width = W;
    targetCanvas.height = H;
    const targetCtx = targetCanvas.getContext('2d');
    const s = SCENES[sceneIndex];
    if (!s) return;
    const prevCtx = ctx;
    ctx = targetCtx;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = 1;
    drawBackground(bgTime !== undefined ? bgTime : t);
    const aIn = E.outCubic(prog(t, 0, 0.55));
    const aOut = 1 - E.inOutCubic(prog(t, s.dur - 0.45, 0.45));
    ctx.save();
    ctx.globalAlpha = Math.min(aIn, aOut);
    const sc = lerp(1.04, 1, aIn) * lerp(0.98, 1, aOut);
    ctx.translate(W / 2, H / 2); ctx.scale(sc, sc); ctx.translate(-W / 2, -H / 2);
    s.draw(t, s.dur);
    ctx.restore();
    ctx = prevCtx;
  }

  function loopSceneOnCanvas(targetCanvas, sceneIndex, options = {}) {
    if (!targetCanvas) return null;
    targetCanvas.width = W;
    targetCanvas.height = H;
    const targetCtx = targetCanvas.getContext('2d');
    const s = SCENES[sceneIndex];
    if (!s) return null;

    const dur = s.dur;
    const pauseAtEnd = options.pauseAtEnd !== undefined ? options.pauseAtEnd : 0.6;

    // Immediately render base frame at t = 0.01 so canvas is never blank
    const initCtx = ctx;
    ctx = targetCtx;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = 1;
    drawBackground(performance.now() / 1000);
    s.draw(0.01, dur);
    ctx = initCtx;

    let animId = null;
    let isVisible = true;
    let startTime = performance.now();

    function tick(now) {
      if (!isVisible) return;
      const cycle = dur + pauseAtEnd;
      const elapsed = ((now - startTime) / 1000) % cycle;
      const t = Math.min(dur, elapsed);

      const prevCtx = ctx;
      ctx = targetCtx;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalAlpha = 1;
      // Seamlessly loop the cosmic background: stars, dust, and grid use continuous time and never snap backwards
      drawBackground(now / 1000);

      // Full entrance and exit animations for a seamless perfect loop
      const aIn = E.outCubic(prog(t, 0, 0.55));
      const aOut = elapsed >= dur ? 0 : 1 - E.inOutCubic(prog(t, dur - 0.45, 0.45));
      const alpha = Math.max(0, Math.min(aIn, aOut));
      if (alpha > 0.001) {
        ctx.save();
        ctx.globalAlpha = alpha;
        const sc = lerp(1.04, 1, aIn) * lerp(0.98, 1, aOut);
        ctx.translate(W / 2, H / 2);
        ctx.scale(sc, sc);
        ctx.translate(-W / 2, -H / 2);
        s.draw(t, dur);
        ctx.restore();
      }
      ctx = prevCtx;

      animId = requestAnimationFrame(tick);
    }

    if (window.IntersectionObserver) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            if (!isVisible) {
              isVisible = true;
              startTime = performance.now();
              if (animId) cancelAnimationFrame(animId);
              animId = requestAnimationFrame(tick);
            }
          } else {
            isVisible = false;
            if (animId) {
              cancelAnimationFrame(animId);
              animId = null;
            }
          }
        });
      }, { threshold: 0.05 });
      observer.observe(targetCanvas);
    }

    animId = requestAnimationFrame(tick);

    return {
      stop: () => {
        isVisible = false;
        if (animId) {
          cancelAnimationFrame(animId);
          animId = null;
        }
      },
      restart: () => {
        startTime = performance.now();
        isVisible = true;
        if (animId) cancelAnimationFrame(animId);
        animId = requestAnimationFrame(tick);
      }
    };
  }

  function loopLogoOnCanvas(targetCanvas, options = {}) {
    if (!targetCanvas) return null;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = targetCanvas.getBoundingClientRect();
    const size = Math.max(rect.width || 220, 220);
    const canvasSize = Math.round(size * dpr);
    targetCanvas.width = canvasSize;
    targetCanvas.height = canvasSize;
    const targetCtx = targetCanvas.getContext('2d');

    const cx = canvasSize / 2;
    const cy = canvasSize / 2;
    const s = canvasSize * 0.44;

    let animId = null;
    let isVisible = true;

    function tick(now) {
      if (!isVisible) return;
      const t = now / 1000;

      const prevCtx = ctx;
      ctx = targetCtx;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvasSize, canvasSize);

      // 1. Concentric expanding squircle pulse rings
      for (let i = 0; i < 3; i++) {
        const phase = ((t * 0.45 + i * 0.333) % 1.0);
        const rSize = s + phase * (canvasSize * 0.46);
        const alpha = Math.sin(phase * Math.PI) * 0.35;
        if (alpha > 0.01) {
          ctx.save();
          ctx.globalAlpha = alpha;
          ctx.strokeStyle = '#8A84FF';
          ctx.lineWidth = 2.5 * dpr;
          squircle(cx - rSize / 2, cy - rSize / 2, rSize, rSize, rSize * 0.24);
          ctx.stroke();
          ctx.restore();
        }
      }

      // 2. Soft radial ambient glow
      const aura = ctx.createRadialGradient(cx, cy, s * 0.15, cx, cy, s * 1.35);
      aura.addColorStop(0, 'rgba(108, 92, 231, 0.48)');
      aura.addColorStop(0.55, 'rgba(126, 240, 216, 0.18)');
      aura.addColorStop(1, 'rgba(108, 92, 231, 0)');
      ctx.fillStyle = aura;
      ctx.fillRect(cx - s * 1.35, cy - s * 1.35, s * 2.7, s * 2.7);

      // 3. Gentle float bobbing and breathing book opening
      const floatY = Math.sin(t * 1.8) * (6 * dpr);
      const open = 0.88 + 0.12 * Math.sin(t * 1.4);

      ctx.save();
      ctx.translate(cx, cy + floatY);
      drawLogo(0, 0, s, open, t, { sparkles: 1 });
      ctx.restore();

      ctx = prevCtx;
      animId = requestAnimationFrame(tick);
    }

    if (window.IntersectionObserver) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            if (!isVisible) {
              isVisible = true;
              if (animId) cancelAnimationFrame(animId);
              animId = requestAnimationFrame(tick);
            }
          } else {
            isVisible = false;
            if (animId) {
              cancelAnimationFrame(animId);
              animId = null;
            }
          }
        });
      }, { threshold: 0.05 });
      observer.observe(targetCanvas);
    }

    animId = requestAnimationFrame(tick);

    return {
      stop: () => {
        isVisible = false;
        if (animId) {
          cancelAnimationFrame(animId);
          animId = null;
        }
      }
    };
  }

  /* Expose Global API for Landing Page & Players */
  window.GabAIMotion = {
    seek,
    setPlaying,
    render,
    renderSceneToCanvas,
    loopSceneOnCanvas,
    loopLogoOnCanvas,
    fit,
    TOTAL,
    SCENES,
    STARTS,
    getT: () => T,
    isPlaying: () => playing,
    sceneAt,
    onFrame: null,
  };

  /* ---------- Boot (only if standalone #stage is present) ---------- */
  if (canvas) {
    const fontsReady = document.fonts
      ? Promise.all(['800 40px "Plus Jakarta Sans"', '600 40px "Plus Jakarta Sans"', '500 40px "Plus Jakarta Sans"', '700 20px "JetBrains Mono"'].map(f => document.fonts.load(f)))
      : Promise.resolve();
    Promise.race([fontsReady, new Promise(r => setTimeout(r, 1500))]).finally(() => {
      last = performance.now();
      requestAnimationFrame(frame);
    });
  }
})();
