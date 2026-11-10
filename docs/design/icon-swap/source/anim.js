// anim.html: three splash animations of icon #2 (robot's swap on tile blue). Only moves Android's animated vector can do:
// rotate, translate, scale, fade, and trimming the stroked checks.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/_t.js', 'utf8');
const els = {};
const mk = id => els[id] || (els[id] = {innerHTML: '', value: '', textContent: '', appendChild() {}, querySelector() { return {addEventListener() {}, textContent: ''}; }, querySelectorAll() { return []; }, set onclick(v) {}});
global.document = {getElementById: mk, createElement: () => ({className: '', set innerHTML(v) {}, querySelector() { return {addEventListener() {}, textContent: ''}; }})};
global.localStorage = {getItem: () => null, setItem() {}};
global.navigator = {};

eval(src + `
const CORAL = ["#F99B80", "#EF806B"], MINT = ["#77D6C2", "#54C5AC"], TILE = ["#E7F4FC", "#D5EAF7"];
const CREAM = "#FFFCF1", INK = "#17253B", ARM = "#32729B";
const lin = (id, c) => '<linearGradient id="' + id + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + c[0] + '"/><stop offset="1" stop-color="' + c[1] + '"/></linearGradient>';
const sh = (id, sd, dy, a) => '<filter id="' + id + '" x="-40%" y="-30%" width="180%" height="170%"><feGaussianBlur in="SourceAlpha" stdDeviation="' + sd + '"/><feOffset dy="' + dy + '" result="b"/><feFlood flood-color="#0E2E44" flood-opacity="' + a + '"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>';
const L = {parts: CREAM, check: INK};
const markCheck = s => s.replace(/<path d="([^"]*)" fill="none" stroke="#17253B"/, '<path class="chk" pathLength="1" d="$1" fill="none" stroke="#17253B"');
// A phone drawn upright at the centre; CSS moves and tilts it, so it can travel.
function centrePhone(id) { const t = P.tilt; P.tilt = 0; const s = markCheck(phone(0, L, id)); P.tilt = t; return s; }
const X = P.gap / 2, T = P.tilt;

function icon(k, bg, arm, full) {
  arm = arm || ARM;
  const id = "a" + k;
  const defs = lin(id + "bg", TILE) + lin(id + "Lbody", CORAL) + lin(id + "Rbody", MINT) + lin(id + "Lscreen", [CREAM, CREAM]) + lin(id + "Rscreen", [CREAM, CREAM]) +
    sh(id + "Ldrop", 1.7, 1.8, .22) + sh(id + "Rdrop", 1.7, 1.8, .22) + sh(id + "soft", 1.1, 1.3, .14);
  const vb = full ? "0 0 108 108" : "18 18 72 72";
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="' + vb + '"><defs>' + defs + '</defs>' +
    (bg ? '<rect width="108" height="108" fill="url(#' + id + 'bg)"/>' : '') +
    '<g class="arrows"><g class="atop" filter="url(#' + id + 'soft)">' + arrow(1, arm) + '</g><g class="abot" filter="url(#' + id + 'soft)">' + arrow(-1, arm) + '</g></g>' +
    '<g class="ph left">' + centrePhone(id + "L") + '</g><g class="ph right">' + centrePhone(id + "R") + '</g></svg>';
}

// Positions: left phone rests at (-X, 0) tilted -T, right at (+X, 0) tilted +T.
const rest = (side) => 'translate(' + (side * X).toFixed(2) + 'px,0) rotate(' + (side * T) + 'deg)';
// Arc points for the swap: coral (left) starts on the right and goes over the top; mint goes under.
function arc(fromSide, overTop) {
  const pts = [];
  for (let i = 0; i <= 8; i++) {
    const a = Math.PI * i / 8, x = fromSide * X * Math.cos(a), y = (overTop ? -1 : 1) * 16.5 * Math.sin(a);
    const rot = fromSide * T * Math.cos(a);
    pts.push((i * 100 / 8).toFixed(1) + '%{transform:translate(' + x.toFixed(2) + 'px,' + y.toFixed(2) + 'px) rotate(' + rot.toFixed(2) + 'deg)}');
  }
  return pts.join('');
}

const css = \`
.stage svg{width:100%;height:100%;display:block;overflow:visible}
.stage .ph,.stage .arrows,.stage .atop,.stage .abot{transform-box:view-box;transform-origin:54px \${(54 + P.phoneY).toFixed(2)}px}
.stage .ph.left{transform:\${rest(-1)}}.stage .ph.right{transform:\${rest(1)}}
.stage .chk{stroke-dasharray:1;stroke-dashoffset:0}
/* A: spin and tick (900 ms) */
.play.A .arrows{animation:aSpin 560ms cubic-bezier(.2,.8,.2,1) both}
.play.A .ph.left{animation:aPopL 420ms cubic-bezier(.3,1.4,.5,1) both}
.play.A .ph.right{animation:aPopR 420ms 60ms cubic-bezier(.3,1.4,.5,1) both}
.play.A .left .chk{animation:draw 260ms 480ms ease-out both}.play.A .right .chk{animation:draw 260ms 600ms ease-out both}
@keyframes aSpin{from{transform:rotate(-200deg) scale(.6);opacity:0}to{transform:none;opacity:1}}
@keyframes aPopL{from{transform:\${rest(-1)} scale(.7);opacity:0}to{transform:\${rest(-1)};opacity:1}}
@keyframes aPopR{from{transform:\${rest(1)} scale(.7);opacity:0}to{transform:\${rest(1)};opacity:1}}
@keyframes draw{from{stroke-dashoffset:1}to{stroke-dashoffset:0}}
/* B: the phones trade places along the arrows (1000 ms) */
.play.B .ph.left{animation:bL 640ms cubic-bezier(.45,0,.25,1) both}
.play.B .ph.right{animation:bR 640ms cubic-bezier(.45,0,.25,1) both}
.play.B .atop{animation:bArrow 640ms ease-out both}.play.B .abot{animation:bArrow 640ms ease-out both}
.play.B .left .chk{animation:draw 240ms 660ms ease-out both}.play.B .right .chk{animation:draw 240ms 740ms ease-out both}
@keyframes bL{\${arc(1, true)}}
@keyframes bR{\${arc(-1, false)}}
@keyframes bArrow{0%{opacity:0;transform:rotate(-60deg)}60%{opacity:1}100%{opacity:1;transform:none}}
/* C: rise and turn (950 ms) */
.play.C .ph.left{animation:cRiseL 420ms cubic-bezier(.2,.9,.3,1) both}
.play.C .ph.right{animation:cRiseR 420ms 90ms cubic-bezier(.2,.9,.3,1) both}
.play.C .arrows{animation:cTurn 600ms 260ms cubic-bezier(.3,1.25,.5,1) both}
.play.C .left .chk{animation:draw 240ms 600ms ease-out both}.play.C .right .chk{animation:draw 240ms 690ms ease-out both}
@keyframes cRiseL{from{transform:translate(\${(-X).toFixed(2)}px,10px) rotate(\${-T}deg);opacity:0}to{transform:\${rest(-1)};opacity:1}}
@keyframes cRiseR{from{transform:translate(\${X.toFixed(2)}px,10px) rotate(\${T}deg);opacity:0}to{transform:\${rest(1)};opacity:1}}
@keyframes cTurn{from{transform:rotate(-180deg)}to{transform:none}}
.play.C .arrows>g{animation:cFade 520ms 260ms ease-in both}
@keyframes cFade{from{opacity:0}to{opacity:1}}
\`;

// Only C, the owner's pick, on the light and the dark launch screen.
// Launch screen: only the icon's foreground (no plate, no circle), at Android's size (240 dp icon on a 360 dp wide screen).
const cards = '<figure data-k="C"><div class="pair">' +
  '<div class="shot"><div class="splash"><div class="notch"></div><div class="stage bare">' + icon(1, false, ARM, true) + '</div></div><div class="cap">Light theme</div></div>' +
  '<div class="shot"><div class="splash dark"><div class="notch"></div><div class="stage bare">' + icon(2, false, "#80BCDE", true) + '</div></div><div class="cap">Dark theme</div></div></div>' +
  '<figcaption><b>Rise and turn</b> <span>950 ms</span><p>The phones rise into place, the arrows fade in slowly while making a half turn with a small bounce, then the checks draw. On the dark screen the arrows use the light blue of the robot, because its darker blue disappears on black.</p><button>Play again</button></figcaption></figure>';

fs.writeFileSync(__dirname + "/anim.html", \`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Icon animation</title><style>
:root{--ink:#142F43;--quiet:#59758A;--line:#B7D1E2;--page:#E9F0F4;--card:#F2F9FD;--accent:#1D5C82}
*{box-sizing:border-box}body{margin:0;background:var(--page);color:var(--ink);font:14px/1.45 Barlow,"Segoe UI",system-ui,sans-serif}
main{max-width:1180px;margin:auto;padding:24px 16px 48px}h1{font-size:22px;margin:0 0 4px}main>p{margin:0 0 20px;color:var(--quiet);max-width:760px}
.grid{display:flex;justify-content:center}
figure{margin:0;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px}
.pair{display:flex;gap:28px;align-items:center;justify-content:center}
.tile{width:150px;height:150px;border-radius:24%;overflow:hidden;box-shadow:0 6px 18px rgba(16,47,70,.18)}
.splash{width:180px;height:360px;border-radius:28px;background:#F7FCFF;border:5px solid #142F43;position:relative;display:flex;align-items:center;justify-content:center}
.splash.dark{background:#0F1011;border-color:#2A2E33}.splash.dark .notch{background:#2A2E33}
.shot{display:flex;flex-direction:column;align-items:center;gap:8px}.cap{font-size:12px;color:var(--quiet)}
.notch{position:absolute;top:8px;width:36px;height:6px;border-radius:3px;background:#142F43}
.bare{width:113px;height:113px}
.name{font-weight:700;font-size:13px;color:var(--ink);letter-spacing:.02em}
figcaption{margin-top:14px}figcaption span{color:var(--quiet)}figcaption p{margin:4px 0 10px;color:var(--quiet)}
button{font:inherit;font-weight:700;border:0;border-radius:999px;padding:8px 16px;background:var(--accent);color:#fff;cursor:pointer}
\${css}
@media (prefers-reduced-motion:reduce){.play *{animation:none!important}}
</style></head><body><main><h1>Launch animation: Rise and turn</h1>
<p>The launch screen in the app's light theme and in a fully dark theme. Only the icon itself shows: no plate, no circle. It stops on the finished icon, stays under Android's one-second limit, and only uses moves Android's animated icon supports.</p>
<div class="grid">\${cards}</div></main>
<script>
function play(fig){const k=fig.dataset.k;fig.querySelectorAll('.stage').forEach(s=>{s.classList.remove('play','A','B','C');void s.offsetWidth;s.classList.add('play',k);});}
document.querySelectorAll('figure').forEach(f=>{f.querySelector('button').onclick=()=>play(f);play(f);});
setInterval(()=>document.querySelectorAll('figure').forEach(play),3200);
</script></body></html>\`);
console.log("ok");
`);
