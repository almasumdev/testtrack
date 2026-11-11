
// Appended to final.js's eval (plain string, so regex backslashes survive).
// Android Studio import copies: no SVG filters (Studio's vector import drops them; shadows live only in the PNGs).
const noFx = x => x.replace(/ filter="url\(#[^)]*\)"/g, '');
w("foreground.svg", noFx(wrap(fg("#32729B", .22))));
w("background.svg", wrap(bg));
// Launch-animation sources: every moving part is its own named group, no filters, phones at rest.
function splashSrc(arm) {
  const defs = lin("fLbody", CORAL) + lin("fRbody", MINT) + lin("fLscreen", [CREAM, CREAM]) + lin("fRscreen", [CREAM, CREAM]);
  const right = thin(phone(1, L, "fR"));
  const chkRe = /<path d="([^"]*)" fill="none" stroke="#17253B"([^>]*)\/>/;
  const chk = right.match(chkRe);
  const rightNoChk = noFx(right.replace(chkRe, ''));
  const close = rightNoChk.lastIndexOf('</g>');
  return '<defs>' + defs + '</defs>' +
    '<g id="arrows"><g id="arrow_top">' + arrow(1, arm) + '</g><g id="arrow_bottom">' + arrow(-1, arm) + '</g></g>' +
    '<g id="phone_left">' + noFx(phone(-1, LL, "fL")) + '<g id="bug">' + bugOnLeft(INK, CREAM) + '</g></g>' +
    '<g id="phone_right">' + rightNoChk.slice(0, close) +
      '<path id="check" d="' + chk[1] + '" fill="none" stroke="#17253B"' + chk[2] + '/></g></g>';
}
w("splash-light.svg", wrap(splashSrc("#32729B")));
w("splash-dark.svg", wrap(splashSrc("#80BCDE")));
console.log("extra ok");
