// Shared by final.js, _mono.js and anim.js (eval'd after _t.js). The chosen icon's bug and screen helpers.
const BUG_INK = "#17253B", BUG_CREAM = "#FFFCF1", CHECK_W = 2.3, BUG_SCALE = (typeof process !== "undefined" && process.env.BUG_SCALE) || 0.86;
// Screen centre of a phone, in the phone's own (unrotated) frame.
function screenOf(side) {
  const cx = C + side * P.gap / 2, cy = C + P.phoneY, H = P.phoneH, y = cy - H / 2, sy = y + P.topGap, sh = H - P.topGap - 5.2;
  return {cx, cy, mx: cx, my: sy + sh / 2 + .4, rot: side * P.tilt};
}
// The bug, drawn at the origin. ink = body colour; cream = the split line and the ring round the head
// (pass null for one-colour use, where those details are left out).
function bugShape(ink, cream) {
  const L = (x1, y1, x2, y2) => '<path d="M' + x1 + ' ' + y1 + 'L' + x2 + ' ' + y2 + '" stroke="' + ink + '" stroke-width="1.3" stroke-linecap="round"/>';
  return L(-2.5, -.4, -4.6, -1.8) + L(2.5, -.4, 4.6, -1.8) + L(-3.0, 1.7, -5.1, 1.9) + L(3.0, 1.7, 5.1, 1.9) + L(-2.5, 3.8, -4.3, 5.3) + L(2.5, 3.8, 4.3, 5.3) +
    '<path d="M-.8 -3.7Q-1.3 -5.6 -2.7 -6M.8 -3.7Q1.3 -5.6 2.7 -6" fill="none" stroke="' + ink + '" stroke-width=".9" stroke-linecap="round"/>' +
    '<rect x="-3.4" y="-1.1" width="6.8" height="7.3" rx="3.4" fill="' + ink + '"/>' +
    (cream ? '<circle cx="0" cy="-2.8" r="2.05" fill="' + ink + '" stroke="' + cream + '" stroke-width=".6"/><path d="M0 .1V5.6" stroke="' + cream + '" stroke-width=".55" stroke-linecap="round"/>'
           : '<circle cx="0" cy="-2.8" r="1.75" fill="' + ink + '"/>');
}
// The bug placed on the left phone's screen (rotated with the phone). extraClass lets the animation target it.
function bugOnLeft(ink, cream, extraClass, upright) {
  const s = screenOf(-1);
  const inner = '<g transform="translate(' + s.mx.toFixed(2) + ' ' + (s.my - .3).toFixed(2) + ') scale(' + BUG_SCALE + ')"><g' + (extraClass ? ' class="' + extraClass + '"' : '') + '>' + bugShape(ink, cream) + '</g></g>';
  return upright ? inner : '<g transform="rotate(' + s.rot + ' ' + s.cx.toFixed(2) + ' ' + s.cy.toFixed(2) + ')">' + inner + '</g>';
}
