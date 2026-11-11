// Monochrome (themed icon) layer: one colour, screens/speaker/home cut out with evenodd, so it maps 1:1 to a vector drawable.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/_t.js', 'utf8') + ';' + fs.readFileSync(__dirname + '/_bug.js', 'utf8');
const els = {};
const mk = id => els[id] || (els[id] = {innerHTML: '', value: '', textContent: '', appendChild() {}, querySelector() { return {addEventListener() {}, textContent: ''}; }, querySelectorAll() { return []; }, set onclick(v) {}});
global.document = {getElementById: mk, createElement: () => ({className: '', set innerHTML(v) {}, querySelector() { return {addEventListener() {}, textContent: ''}; }})};
global.localStorage = {getItem: () => null, setItem() {}};
global.navigator = {};
eval(src + `
const rr = (x, y, w, h, r) => "M" + f(x + r) + " " + f(y) + "H" + f(x + w - r) + "A" + r + " " + r + " 0 0 1 " + f(x + w) + " " + f(y + r) +
  "V" + f(y + h - r) + "A" + r + " " + r + " 0 0 1 " + f(x + w - r) + " " + f(y + h) + "H" + f(x + r) + "A" + r + " " + r + " 0 0 1 " + f(x) + " " + f(y + h - r) +
  "V" + f(y + r) + "A" + r + " " + r + " 0 0 1 " + f(x + r) + " " + f(y) + "Z";
const circ = (cx, cy, r) => "M" + f(cx - r) + " " + f(cy) + "A" + r + " " + r + " 0 1 0 " + f(cx + r) + " " + f(cy) + "A" + r + " " + r + " 0 1 0 " + f(cx - r) + " " + f(cy) + "Z";
function monoPhone(side, col, noCheck) {
  const cx = C + side * P.gap / 2, cy = C + P.phoneY, W = P.phoneW, H = P.phoneH;
  const x = cx - W / 2, y = cy - H / 2, sx = x + P.sideGap, sy = y + P.topGap, sw = W - 2 * P.sideGap, sh = H - P.topGap - 5.2;
  const mx = cx, my = sy + sh / 2 + .4, k = P.check, spY = y + P.topGap / 2 - .7;
  const chk = "M" + f(mx - 4.6 * k) + " " + f(my + 1.2 * k) + "L" + f(mx - 1.4 * k) + " " + f(my + 4.3 * k) + "L" + f(mx + 4.4 * k) + " " + f(my - 2.3 * k);
  const body = rr(x, y, W, H, P.phoneR) + rr(sx, sy, sw, sh, 1.9) + rr(cx - 2.8, spY + .15, 5.6, 1.2, .6) + circ(cx, y + H - 2.6, 1.15);
  return '<g transform="rotate(' + (side * P.tilt) + ' ' + f(cx) + ' ' + f(cy) + ')"><path d="' + body + '" fill="' + col + '" fill-rule="evenodd"/>' +
    (noCheck ? '' : '<path d="' + chk + '" fill="none" stroke="' + col + '" stroke-width="' + CHECK_W + '" stroke-linecap="round" stroke-linejoin="round"/>') + '</g>';
}
const mono = col => '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">' + arrow(1, col) + arrow(-1, col) + monoPhone(-1, col, true) + bugOnLeft(col, null) + monoPhone(1, col) + '</svg>';
fs.writeFileSync(__dirname + "/swap-monochrome.svg", mono("#000000"));
// Themed previews: Android tints the glyph and fills the plate from the wallpaper palette.
const themes = [["#D5EAF7", "#1D5C82"], ["#1E2B36", "#B8D8EB"], ["#E3F0E9", "#246B59"], ["#2A2633", "#E0D3F2"]];
const tiles = themes.map(([bg, fg]) => [56, 44].map(s => '<div class="t" style="width:' + s + 'px;height:' + s + 'px;border-radius:50%;background:' + bg + '">' + mono(fg).replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"') + '</div>').join("")).join("");
const big = '<div class="t" style="width:220px;height:220px;border-radius:50%;background:#D5EAF7">' + mono("#1D5C82").replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"') + '</div>' +
            '<div class="t" style="width:220px;height:220px;border-radius:50%;background:#1E2B36">' + mono("#B8D8EB").replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"') + '</div>';
let p = fs.readFileSync(__dirname + "/pick.html", "utf8");
p = p.replace(/<h2 id="mono">[\\s\\S]*?<!--\\/mono-->/, "");
p = p.replace("</main>", '<h2 id="mono" style="margin:12px 0 0;font-size:15px">Monochrome (themed icons)</h2><div class="row lt">' + big + '</div><div class="row lt">' + tiles + '</div><!--/mono--></main>');
fs.writeFileSync(__dirname + "/pick.html", p);
console.log("ok");
`);
