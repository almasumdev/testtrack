// variants.html: the current icon untouched, plus three one-cue variants (robot face, 12-tester ring, bug + check),
// and the same four bigger on the robot's deep blue. Shown at 160/64/48 px on a white and a dark shelf.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/_t.js', 'utf8');
const els = {};
const mk = id => els[id] || (els[id] = {innerHTML: '', value: '', textContent: '', appendChild() {}, querySelector() { return {addEventListener() {}, textContent: ''}; }, querySelectorAll() { return []; }, set onclick(v) {}});
global.document = {getElementById: mk, createElement: () => ({className: '', set innerHTML(v) {}, querySelector() { return {addEventListener() {}, textContent: ''}; }})};
global.localStorage = {getItem: () => null, setItem() {}};
global.navigator = {};
eval(src + `
const CORAL = ["#F99B80", "#EF806B"], MINT = ["#77D6C2", "#54C5AC"];
const CREAM = "#FFFCF1", INK = "#17253B";
const lin = (id, c) => '<linearGradient id="' + id + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + c[0] + '"/><stop offset="1" stop-color="' + c[1] + '"/></linearGradient>';
const sh = (id, sd, dy, a) => '<filter id="' + id + '" x="-40%" y="-30%" width="180%" height="170%"><feGaussianBlur in="SourceAlpha" stdDeviation="' + sd + '"/><feOffset dy="' + dy + '" result="b"/><feFlood flood-color="#0E2E44" flood-opacity="' + a + '"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>';

// Screen centre of a phone, in the phone's own (unrotated) frame.
function screenOf(side) {
  const cx = C + side * P.gap / 2, cy = C + P.phoneY, H = P.phoneH, y = cy - H / 2, sy = y + P.topGap, sh = H - P.topGap - 5.2;
  return {cx, cy, mx: cx, my: sy + sh / 2 + .4, rot: side * P.tilt};
}
const inPhone = (side, inner) => { const s = screenOf(side); return '<g transform="rotate(' + s.rot + ' ' + f(s.cx) + ' ' + f(s.cy) + ')">' + inner(s.mx, s.my) + '</g>'; };

// The robot's head, from testtrack_robot.xml colours, about 11 units wide.
const robot = (x, y) =>
  '<line x1="' + f(x) + '" y1="' + f(y - 4.6) + '" x2="' + f(x) + '" y2="' + f(y - 6.4) + '" stroke="#1D5C82" stroke-width=".8" stroke-linecap="round"/>' +
  '<circle cx="' + f(x) + '" cy="' + f(y - 6.8) + '" r="1.05" fill="#EF806B"/>' +
  '<rect x="' + f(x - 6.3) + '" y="' + f(y - 1.6) + '" width="1.6" height="3.6" rx=".8" fill="#5E9DC5"/>' +
  '<rect x="' + f(x + 4.7) + '" y="' + f(y - 1.6) + '" width="1.6" height="3.6" rx=".8" fill="#5E9DC5"/>' +
  '<rect x="' + f(x - 5.2) + '" y="' + f(y - 4.6) + '" width="10.4" height="9" rx="3.2" fill="#32729B"/>' +
  '<rect x="' + f(x - 3.9) + '" y="' + f(y - 3.1) + '" width="7.8" height="6" rx="2.4" fill="' + CREAM + '"/>' +
  '<circle cx="' + f(x - 1.6) + '" cy="' + f(y - .6) + '" r=".95" fill="' + INK + '"/><circle cx="' + f(x + 1.6) + '" cy="' + f(y - .6) + '" r=".95" fill="' + INK + '"/>' +
  '<path d="M' + f(x - 1.1) + ' ' + f(y + 1.1) + 'Q' + f(x) + ' ' + f(y + 2) + ' ' + f(x + 1.1) + ' ' + f(y + 1.1) + '" fill="none" stroke="' + INK + '" stroke-width=".6" stroke-linecap="round"/>';

// A plain bug: oval body with a centre line, head, six legs, two feelers.
const bug = (x, y) => {
  const leg = (dx, dy, ex, ey) => '<path d="M' + f(x + dx) + ' ' + f(y + dy) + 'L' + f(x + ex) + ' ' + f(y + ey) + '" stroke="' + INK + '" stroke-width="1" stroke-linecap="round"/>';
  return leg(-2.2, -1.2, -4.6, -2.6) + leg(2.2, -1.2, 4.6, -2.6) + leg(-2.5, .8, -5, .8) + leg(2.5, .8, 5, .8) + leg(-2.2, 2.8, -4.4, 4.6) + leg(2.2, 2.8, 4.4, 4.6) +
    '<path d="M' + f(x - .9) + ' ' + f(y - 3.8) + 'L' + f(x - 2.1) + ' ' + f(y - 5.6) + 'M' + f(x + .9) + ' ' + f(y - 3.8) + 'L' + f(x + 2.1) + ' ' + f(y - 5.6) + '" stroke="' + INK + '" stroke-width=".8" stroke-linecap="round"/>' +
    '<circle cx="' + f(x) + '" cy="' + f(y - 3) + '" r="1.6" fill="' + INK + '"/>' +
    '<ellipse cx="' + f(x) + '" cy="' + f(y + 1) + '" rx="2.9" ry="3.7" fill="' + INK + '"/>' +
    '<path d="M' + f(x) + ' ' + f(y - 2.4) + 'V' + f(y + 4.4) + '" stroke="' + CREAM + '" stroke-width=".5"/>';
};

// Twelve dots on a ring behind everything: Play's 12 testers.
const ring = (col) => { let s = ''; for (let i = 0; i < 12; i++) { const a = (i / 12) * 2 * Math.PI - Math.PI / 2; s += '<circle cx="' + f(54 + 31 * Math.cos(a)) + '" cy="' + f(54 + 31 * Math.sin(a)) + '" r="1.5" fill="' + col + '"/>'; } return s; };

let n = 0;
// A friendlier, bolder bug: round shell split down the middle, round head, short thick legs, curled feelers.
const bug2 = (x, y) => {
  // Drawn at the origin, then scaled up a little and moved onto the screen.
  const L = (x1, y1, x2, y2) => '<path d="M' + x1 + ' ' + y1 + 'L' + x2 + ' ' + y2 + '" stroke="' + INK + '" stroke-width="1.3" stroke-linecap="round"/>';
  const g = L(-2.5, -.4, -4.6, -1.8) + L(2.5, -.4, 4.6, -1.8) + L(-3.0, 1.7, -5.1, 1.9) + L(3.0, 1.7, 5.1, 1.9) + L(-2.5, 3.8, -4.3, 5.3) + L(2.5, 3.8, 4.3, 5.3) +
    '<path d="M-.8 -3.7Q-1.3 -5.6 -2.7 -6M.8 -3.7Q1.3 -5.6 2.7 -6" fill="none" stroke="' + INK + '" stroke-width=".9" stroke-linecap="round"/>' +
    '<rect x="-3.4" y="-1.1" width="6.8" height="7.3" rx="3.4" fill="' + INK + '"/>' +
    '<circle cx="0" cy="-2.8" r="2.05" fill="' + INK + '" stroke="' + CREAM + '" stroke-width=".6"/>' +
    '<path d="M0 .1V5.6" stroke="' + CREAM + '" stroke-width=".55" stroke-linecap="round"/>';
  return '<g transform="translate(' + f(x) + ' ' + f(y - .3) + ') scale(1.12)">' + g + '</g>';
};
function icon(cue, deep, opt) {
  opt = opt || {};
  const id = "v" + (n++);
  const bgStops = opt.bg || (deep ? ["#2A6A93", "#174866"] : ["#E7F4FC", "#D5EAF7"]);
  const arm = opt.arm || (deep ? "#80BCDE" : "#32729B");
  const hideL = cue === "bug", hideR = cue === "robot";
  const Lleft = {parts: CREAM, check: hideL ? "none" : INK}, Lright = {parts: CREAM, check: hideR ? "none" : INK};
  const shadowA = deep ? .35 : .22;
  const bgDef = opt.diag ? '<linearGradient id="' + id + 'bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="' + bgStops[0] + '"/><stop offset="1" stop-color="' + bgStops[1] + '"/></linearGradient>' : lin(id + "bg", bgStops);
  const defs = bgDef + lin(id + "Lbody", CORAL) + lin(id + "Rbody", MINT) + lin(id + "Lscreen", [CREAM, CREAM]) + lin(id + "Rscreen", [CREAM, CREAM]) +
    sh(id + "Ldrop", 1.7, 1.8, shadowA) + sh(id + "Rdrop", 1.7, 1.8, shadowA) + sh(id + "soft", 1.1, 1.3, shadowA * .64);
  const ringSvg = cue === "ring" ? ring(deep ? "#FFFCF1" : "#32729B") : '';
  let art =
    '<g filter="url(#' + id + 'soft)">' + arrow(1, arm) + arrow(-1, arm) + '</g>' + phone(-1, Lleft, id + "L") + phone(1, Lright, id + "R");
  if (cue === "robot") art += inPhone(1, robot);
  if (cue === "bug") art += inPhone(-1, opt.bug2 ? bug2 : bug);
  if (opt.checkW) art = art.split('stroke="#17253B" stroke-width="' + f(2.9 * P.check) + '"').join('stroke="#17253B" stroke-width="' + opt.checkW + '"');
  const k = deep ? 1.07 : 1;  // bigger: fill more of the tile, scaled about the centre
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>' + defs + '</defs><rect width="108" height="108" fill="url(#' + id + 'bg)"/>' + (opt.decor || '') + ringSvg +
    '<g transform="translate(54 54) scale(' + k + ') translate(-54 -54)">' + art + '</g></svg>';
}

const cues = [["now", "Current (unchanged)", "What we have today."],
  ["robot", "A. Robot on the screen", "The right phone shows the robot instead of a check. Ties the icon to the mascot; no transfer app has it."],
  ["ring", "B. 12-tester ring", "Twelve dots around the swap: the 12 testers Play asks for. Developers searching for testers know the number."],
  ["bug", "C. Bug and check", "The left phone shows a bug, the right one a check: found it, fixed it. Says testing, not transfer."]];
const tile = (s, size, r) => '<div class="t" style="width:' + size + 'px;height:' + size + 'px;border-radius:' + r + '">' + s + '</div>';
const card = (deep) => cues.map(([c, name, text]) => { const s = icon(c, deep); fs.writeFileSync(__dirname + "/_v-" + c + (deep ? "-deep" : "") + ".svg", s);
  return '<figure>' + tile(s, 160, '22%') + '<div class="shelf lt">' + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div><div class="shelf dk">' + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div>' +
    '<figcaption><b>' + name + '</b><p>' + text + '</p></figcaption></figure>'; }).join('');

fs.writeFileSync(__dirname + "/variants.html", \`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Icon variants</title><style>
:root{--ink:#142F43;--quiet:#59758A;--line:#B7D1E2;--page:#E9F0F4;--card:#F2F9FD}
*{box-sizing:border-box}body{margin:0;background:var(--page);color:var(--ink);font:14px/1.45 Barlow,"Segoe UI",system-ui,sans-serif}
main{max-width:1240px;margin:auto;padding:24px 16px 48px}h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:28px 0 10px}main>p{margin:0 0 8px;color:var(--quiet);max-width:780px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:18px}
figure{margin:0;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px;display:flex;flex-direction:column;align-items:center;gap:12px}
.t{overflow:hidden;flex:none}.t svg{width:100%;height:100%;display:block}
.shelf{display:flex;gap:16px;align-items:center;justify-content:center;padding:12px 18px;border-radius:12px;width:100%}.shelf.lt{background:#FFFFFF;border:1px solid #E3E8EC}.shelf.dk{background:#131314}
figcaption{width:100%}figcaption p{margin:2px 0 0;color:var(--quiet)}
</style></head><body><main><h1>Icon variants, current kept as is</h1>
<p>Each variant adds one cue to the current icon and changes nothing else. The second row is the same four, bigger in the tile and on the robot's deep blue. Small tiles sit on a white and a dark Play-style shelf at 64 and 48 px.</p>
<h2>As it is</h2><div class="grid">\${card(false)}</div>
<h2>Bigger, on the robot's deep blue</h2><div class="grid">\${card(true)}</div></main></body></html>\`);
console.log("ok");

const cOpts = [
  [false, {}, "Old C, pale", "As shown before."],
  [false, {bug2: true, checkW: 2.3}, "New C, pale", "Bolder, rounder bug; thinner check."],
  [true, {}, "Old C, deep blue", "As shown before."],
  [true, {bug2: true, checkW: 2.3}, "New C, deep blue", "Bolder, rounder bug; thinner check."],
];
const cCards = cOpts.map(([deep, opt, name, text], i) => { const s = icon("bug", deep, opt); fs.writeFileSync(__dirname + "/_c2-" + i + ".svg", s);
  return '<figure>' + tile(s, 200, '22%') + '<div class="shelf lt">' + tile(s, 96, '22%') + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div><div class="shelf dk">' + tile(s, 96, '22%') + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div>' +
    '<figcaption><b>' + name + '</b><p>' + text + '</p></figcaption></figure>'; }).join('');
const vh = fs.readFileSync(__dirname + "/variants.html", "utf8"); const pageCss = vh.slice(vh.indexOf("<style>"), vh.indexOf("</style>") + 8);
fs.writeFileSync(__dirname + "/c-bug.html", '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Bug and check</title>' + pageCss +
  '</head><body><main><h1>C. Bug and check</h1><p>Old and new side by side. New: the bug is rounder and bolder with short thick legs, and the check is thinner. Small tiles on a white and a dark Play-style shelf.</p><div class="grid">' + cCards + '</div></main></body></html>');
console.log("c ok");

// Background options for the new C.
const dots = (col, a) => { let d = ''; for (let yy = 21; yy < 90; yy += 6) for (let xx = 21; xx < 90; xx += 6) d += '<circle cx="' + xx + '" cy="' + yy + '" r=".55" fill="' + col + '" fill-opacity="' + a + '"/>'; return d; };
const disc = (col, a) => '<circle cx="54" cy="54" r="30" fill="' + col + '" fill-opacity="' + a + '"/>';
const B = {bug2: true, checkW: 2.3};
const bgOpts = [
  ["Pale tile (current)", false, {}],
  ["Deep blue (current)", true, {}],
  ["Deep blue, diagonal", true, {bg: ["#2E77A3", "#123C56"], diag: true}],
  ["Accent to ink", true, {bg: ["#1D5C82", "#142F43"]}],
  ["Deep blue, soft disc behind", true, {decor: disc("#FFFFFF", .08)}],
  ["Deep blue, faint dot grid", true, {decor: dots("#FFFFFF", .16)}],
  ["Sky blue, cream arrows", true, {bg: ["#3E92C4", "#2B74A3"], arm: "#FFFCF1"}],
  ["Warm cream, robot-blue arrows", false, {bg: ["#FFFCF1", "#F4E5DC"]}],
  ["Pale, soft disc behind", false, {bg: ["#EEF7FC", "#D5EAF7"], decor: disc("#FFFFFF", .7)}],
  ["Pale, faint dot grid", false, {bg: ["#EEF7FC", "#D5EAF7"], decor: dots("#32729B", .18)}],
];
const bgCards = bgOpts.map(([name, deep, o], i) => { const s = icon("bug", deep, Object.assign({}, B, o)); fs.writeFileSync(__dirname + "/_bg2-" + i + ".svg", s);
  return '<figure>' + tile(s, 180, '22%') + '<div class="shelf lt">' + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div><div class="shelf dk">' + tile(s, 64, '22%') + tile(s, 48, '22%') + '</div>' +
    '<figcaption><b>' + (i + 1) + '. ' + name + '</b></figcaption></figure>'; }).join('');
fs.writeFileSync(__dirname + "/c-backgrounds.html", '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Icon backgrounds</title>' + pageCss +
  '</head><body><main><h1>Backgrounds for the bug and check icon</h1><p>The icon is the same in every card (new bug, thinner check). Only the background changes, plus the arrow colour where the background needs it. Small tiles on a white and a dark Play-style shelf.</p><div class="grid">' + bgCards + '</div></main></body></html>');
console.log("bg ok");

// Light backgrounds x arrow colours, for the new C.
const lightBgs = [["Pale tile", ["#E7F4FC", "#D5EAF7"]], ["White to tile", ["#FFFFFF", "#D5EAF7"]], ["Ice", ["#EEF7FC", "#B8D8EB"]], ["Glass", ["#F7FCFF", "#E7F4FC"]], ["Warm cream", ["#FFFCF1", "#F4E5DC"]]];
const arms = [["Robot blue", "#32729B"], ["App accent", "#1D5C82"], ["Navy ink", "#17253B"], ["Light robot blue", "#5E9DC5"], ["Pro gold", "#E3A93C"], ["Deep coral", "#D9654F"]];
let rows = '';
lightBgs.forEach(([bn, bg], r) => {
  rows += '<tr><th>' + bn + '</th>' + arms.map(([an, col], c) => { const sv = icon("bug", false, Object.assign({}, B, {bg, arm: col}));
    if (r === 0) fs.writeFileSync(__dirname + "/_arm-" + c + ".svg", sv);
    if (c === 0) fs.writeFileSync(__dirname + "/_lbg-" + r + ".svg", sv);
    return '<td>' + tile(sv, 132, '22%') + '<div class="mini">' + tile(sv, 48, '22%') + tile(sv, 48, '22%').replace('class="t"', 'class="t onDark"') + '</div></td>'; }).join('') + '</tr>';
});
const head = '<tr><th></th>' + arms.map(([an, col]) => '<th><i style="background:' + col + '"></i>' + an + '</th>').join('') + '</tr>';
fs.writeFileSync(__dirname + "/c-light.html", '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Light backgrounds</title>' + pageCss +
  '<style>table{border-collapse:separate;border-spacing:10px}th{font-size:13px;text-align:left;color:#142F43;white-space:nowrap}th i{display:inline-block;width:12px;height:12px;border-radius:4px;margin-right:6px;vertical-align:-1px}' +
  'td{background:#fff;border:1px solid #E3E8EC;border-radius:14px;padding:10px;vertical-align:top}.mini{display:flex;gap:8px;margin-top:8px}.mini .onDark{outline:8px solid #131314;margin-left:8px}.wrap{overflow-x:auto}</style>' +
  '</head><body><main><h1>Light backgrounds and arrow colours</h1><p>Same icon in every cell (new bug, thinner check). Rows are light backgrounds, columns are arrow colours. Under each: 48 px on white and on a dark shelf.</p><div class="wrap"><table>' + head + rows + '</table></div></main></body></html>');
console.log("light ok");
`);
