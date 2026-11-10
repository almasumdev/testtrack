// bot.html: swap icon in the robot mascot's own colours (testtrack_robot.xml), each phone can have its own colour.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/_t.js', 'utf8');
const els = {};
const mk = id => els[id] || (els[id] = {innerHTML: '', value: '', textContent: '', appendChild() {}, querySelector() { return {addEventListener() {}, textContent: ''}; }, querySelectorAll() { return []; }, set onclick(v) {}});
global.document = {getElementById: mk, createElement: () => ({className: '', set innerHTML(v) {}, querySelector() { return {addEventListener() {}, textContent: ''}; }})};
global.localStorage = {getItem: () => null, setItem() {}};
global.navigator = {};

// Robot colours.
const CORAL = ["#F99B80", "#EF806B"], MINT = ["#77D6C2", "#54C5AC"], RBLUE = ["#245F84", "#174866"], HAND = ["#80BCDE", "#5E9DC5"];
const CREAM = "#FFFCF1", INK = "#17253B", ARM = "#32729B";
const WASH = ["#F7FCFF", "#E7F4FC"], TILE = ["#E7F4FC", "#D5EAF7"], BODYBG = ["#245F84", "#174866"], NIGHT = ["#1D5C82", "#123C56"];
// name, bg, left phone, right phone, screen, arrows, checks, phone parts
const COMBOS = [
  ["The robot's own swap: coral + mint", WASH, CORAL, MINT, CREAM, ARM, INK, CREAM],
  ["Robot's swap on tile blue", TILE, CORAL, MINT, CREAM, ARM, INK, CREAM],
  ["Robot's swap on the robot's blue", BODYBG, CORAL, MINT, CREAM, "#80BCDE", INK, CREAM],
  ["Coral + light blue (no green)", WASH, CORAL, HAND, CREAM, ARM, INK, CREAM],
  ["Coral + light blue on robot blue", BODYBG, CORAL, HAND, CREAM, "#FFFCF1", INK, CREAM],
  ["Robot blue phones, coral arrows", WASH, RBLUE, RBLUE, CREAM, "#EF806B", INK, "#80BCDE"],
  ["Robot blue phones, coral checks", TILE, RBLUE, RBLUE, CREAM, "#5E9DC5", "#EF806B", "#80BCDE"],
  ["Light blue phones, coral arrows", WASH, HAND, HAND, CREAM, "#EF806B", INK, CREAM],
  ["Night: light blue phones, coral arrows", NIGHT, HAND, HAND, CREAM, "#F99B80", INK, CREAM],
  ["Night: cream phones, coral arrows", BODYBG, ["#FFFCF1", "#F1EAD6"], ["#FFFCF1", "#F1EAD6"], "#1D5C82", "#F99B80", CREAM, "#174866"],
  // Codex's ideas (light 1-6, dark 7-9); Codex picked its #1.
  ["Codex 1 (Codex's pick): sky phones, brick arrows", ["#EEF7FC", "#D5EAF7"], ["#3E92C4", "#2B74A3"], ["#3E92C4", "#2B74A3"], CREAM, "#B94F3D", INK, "#123C56"],
  ["Codex 2: cream field, robot blue, brick arrows", ["#FFFCF1", "#F4E5DC"], ["#32729B", "#245F84"], ["#32729B", "#245F84"], CREAM, "#B94F3D", INK, "#123C56"],
  ["Codex 3: coral phones, accent arrows", ["#F7FCFF", "#E7F4FC"], CORAL, CORAL, CREAM, "#1D5C82", INK, INK],
  ["Codex 4: cream field, two blues, brick arrows", ["#FFFCF1", "#F6E9D2"], ["#245F84", "#245F84"], ["#32729B", "#32729B"], CREAM, "#B94F3D", INK, "#9CCBEA"],
  ["Codex 5: blue + coral phones, brick checks", ["#F2F9FD", "#D5EAF7"], ["#245F84", "#245F84"], ["#EF806B", "#EF806B"], CREAM, "#123C56", "#B94F3D", INK],
  ["Codex 6: warm field, sky phones, brick checks", ["#FFF7F1", "#F4DDD3"], ["#2B74A3", "#2B74A3"], ["#2B74A3", "#2B74A3"], CREAM, "#123C56", "#B94F3D", "#123C56"],
  ["Codex 7 (dark): light blue phones, coral arrows", ["#17253B", "#123C56"], HAND, HAND, CREAM, "#F99B80", INK, "#174866"],
  ["Codex 8 (dark): coral phones, pale blue arrows", ["#142F43", "#17253B"], CORAL, CORAL, CREAM, "#9CCBEA", INK, INK],
  ["Codex 9 (dark): blue + coral phones, gold arrows", ["#174866", "#17253B"], HAND, ["#F99B80", "#F99B80"], CREAM, "#E3A93C", INK, INK],
];

eval(src + `
const lum = c => parseInt(c.slice(1, 3), 16) * .3 + parseInt(c.slice(3, 5), 16) * .6 + parseInt(c.slice(5, 7), 16) * .1;
const tiles = COMBOS.map(([name, bg, left, right, screen, arw, chk, parts], i) => {
  const id = "b" + i, darkBg = lum(bg[1]) < 140;
  const lin = (n, c) => '<linearGradient id="' + id + n + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + c[0] + '"/><stop offset="1" stop-color="' + c[1] + '"/></linearGradient>';
  const sh = (n, sd, dy, a) => '<filter id="' + id + n + '" x="-40%" y="-30%" width="180%" height="170%"><feGaussianBlur in="SourceAlpha" stdDeviation="' + sd + '"/><feOffset dy="' + dy + '" result="b"/><feFlood flood-color="#0E2E44" flood-opacity="' + a + '"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>';
  const shadowA = darkBg ? .4 : .22;
  const defs = lin("bg", bg) + lin("Lbody", left) + lin("Rbody", right) + lin("Lscreen", [screen, screen]) + lin("Rscreen", [screen, screen]) +
    sh("Ldrop", 1.7, 1.8, shadowA) + sh("Rdrop", 1.7, 1.8, shadowA) + sh("soft", 1.1, 1.3, darkBg ? .3 : .14);
  const L = {parts, check: chk};
  const s = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>' + defs + '</defs><rect width="108" height="108" fill="url(#' + id + 'bg)"/>' +
    '<g filter="url(#' + id + 'soft)">' + arrow(1, arw) + arrow(-1, arw) + '</g>' + phone(-1, L, id + "L") + phone(1, L, id + "R") + '</svg>';
  fs.writeFileSync(__dirname + "/_b" + i + ".svg", s);
  const sw = [bg[0], left[0], right[0], screen, arw, chk].map(c => '<i style="background:' + c + '" title="' + c + '"></i>').join("");
  return '<figure><div class="tile">' + s + '</div><div class="home dk"><div class="tile sm">' + s + '</div></div><div class="home lt"><div class="tile sm">' + s +
    '</div></div><figcaption><b>' + (i + 1) + '</b> ' + name + '<span class="sw">' + sw + '</span></figcaption></figure>';
}).join("\\n");
const robot = fs.readFileSync(__dirname + "/robot.svg", "utf8");
fs.writeFileSync(__dirname + "/bot.html", \`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Robot colours</title><style>
:root{--ink:#142F43;--quiet:#59758A;--line:#B7D1E2;--page:#E9F0F4;--card:#F2F9FD}
*{box-sizing:border-box}body{margin:0;background:var(--page);color:var(--ink);font:14px/1.4 Barlow,"Segoe UI",system-ui,sans-serif}
main{max-width:1240px;margin:auto;padding:24px 16px 48px}h1{font-size:22px;margin:0 0 4px}p{margin:0 0 20px;color:var(--quiet);max-width:760px}
.top{display:flex;gap:20px;align-items:center;margin-bottom:20px}.robot{width:150px;height:150px;flex:none}.robot svg{width:100%;height:100%}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:18px}
figure{margin:0;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:14px;display:flex;flex-wrap:wrap;gap:10px;align-items:flex-end}
.tile{width:150px;height:150px;border-radius:24%;overflow:hidden}.tile svg{width:100%;height:100%;display:block}
.tile.sm{width:44px;height:44px}.home{padding:10px;border-radius:12px}.home.dk{background:#1B2430}.home.lt{background:#EDEFF2}
figcaption{width:100%;font-weight:600}figcaption b{color:var(--quiet);margin-right:4px}
.sw{display:flex;gap:4px;margin-top:6px}.sw i{width:18px;height:18px;border-radius:6px;border:1px solid var(--line)}
</style></head><body><main>
<div class="top"><div class="robot">\${robot}</div><div><h1>Swap icon in the robot's colours</h1>
<p>The robot already holds this exact idea: a coral card and a mint card, swap arrows between them, ink checks on cream screens. Every colour below comes from the robot drawing. 1 to 3 copy it straight; 4 and 5 swap the mint for the robot's light blue hands; 6 to 10 use one phone colour with coral as the pop. 11 to 19 are Codex's ideas; Codex picked 11. Swatches: background, left phone, right phone, screen, arrows, checks.</p></div></div>
<div class="grid">\${tiles}</div></main></body></html>\`);
console.log("ok");
`);
