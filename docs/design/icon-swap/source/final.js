// Final SVGs for the chosen icon: "Robot's swap on tile blue" with the bug on the left phone (variant C). Writes into the folder given as argv[2].
const fs = require('fs');
const path = require('path');
const out = process.argv[2];
const src = fs.readFileSync(__dirname + '/_t.js', 'utf8') + ';' + fs.readFileSync(__dirname + '/_bug.js', 'utf8');
const els = {};
const mk = id => els[id] || (els[id] = {innerHTML: '', value: '', textContent: '', appendChild() {}, querySelector() { return {addEventListener() {}, textContent: ''}; }, querySelectorAll() { return []; }, set onclick(v) {}});
global.document = {getElementById: mk, createElement: () => ({className: '', set innerHTML(v) {}, querySelector() { return {addEventListener() {}, textContent: ''}; }})};
global.localStorage = {getItem: () => null, setItem() {}};
global.navigator = {};
eval(src + `
const CORAL = ["#F99B80", "#EF806B"], MINT = ["#77D6C2", "#54C5AC"], TILE = ["#F7FCFF", "#D5EAF7"];
const CREAM = "#FFFCF1", INK = "#17253B";
const lin = (id, c) => '<linearGradient id="' + id + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + c[0] + '"/><stop offset="1" stop-color="' + c[1] + '"/></linearGradient>';
const sh = (id, sd, dy, a) => '<filter id="' + id + '" x="-40%" y="-30%" width="180%" height="170%"><feGaussianBlur in="SourceAlpha" stdDeviation="' + sd + '"/><feOffset dy="' + dy + '" result="b"/><feFlood flood-color="#0E2E44" flood-opacity="' + a + '"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>';
const L = {parts: CREAM, check: INK}, LL = {parts: CREAM, check: "none"};
const thin = s => s.split('stroke="' + INK + '" stroke-width="' + f(2.9 * P.check) + '"').join('stroke="' + INK + '" stroke-width="' + CHECK_W + '"');
function fg(arm, shadowA) {
  const defs = lin("fLbody", CORAL) + lin("fRbody", MINT) + lin("fLscreen", [CREAM, CREAM]) + lin("fRscreen", [CREAM, CREAM]) +
    sh("fLdrop", 1.7, 1.8, shadowA) + sh("fRdrop", 1.7, 1.8, shadowA) + sh("fsoft", 1.1, 1.3, shadowA * .64);
  return '<defs>' + defs + '</defs><g filter="url(#fsoft)">' + arrow(1, arm) + arrow(-1, arm) + '</g>' + phone(-1, LL, "fL") + bugOnLeft(INK, CREAM) + thin(phone(1, L, "fR"));
}
const wrap = s => '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">' + s + '</svg>';
const bg = '<defs>' + lin("bgTile", TILE) + '</defs><rect width="108" height="108" fill="url(#bgTile)"/>';
const w = (n, s) => fs.writeFileSync(require("path").join(${JSON.stringify(out)}, n), s);
w("icon-foreground.svg", wrap(fg("#32729B", .22)));
w("icon-background.svg", wrap(bg));
w("icon-combined.svg", wrap(bg + fg("#32729B", .22)));
w("splash-foreground-light.svg", wrap(fg("#32729B", .22)));
w("splash-foreground-dark.svg", wrap(fg("#80BCDE", .45)));

w("numbers.json", JSON.stringify(P, null, 2));
console.log("ok");
` + fs.readFileSync(__dirname + "/_final_extra.js", "utf8"));
