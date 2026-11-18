"""Embed the existing preview icons and inspect the proposed Crew page in Edge."""

import base64
import json
from pathlib import Path

from playwright.sync_api import sync_playwright


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FRAGMENT = HERE / "crew-unified-app-list.html"
PREVIEW = HERE / "crew-unified-app-list-preview.html"


def embed_icons():
    text = FRAGMENT.read_text(encoding="utf-8")
    icons = {
        "BLUEFY": "com_eazyverse_bluefy",
        "silent": "com_eazyverse_soundscheduler",
        "astra": "com_shahadat_astramatch",
        "baka": "com_markuljdev_bakarun",
        "bluefy": "com_eazyverse_bluefy",
        "cherish": "app_cherish_people",
        "edit": "com_arisoftware_editme",
        "gaan": "com_arisoftware_gaan",
    }
    # Share each data URI between the owner tile and its list tile.
    for token, package in icons.items():
        marker = f"__ICON_{token.upper()}__"
        if marker in text:
            data = base64.b64encode((ROOT / "store/art" / f"circle_actual_{package}.png").read_bytes()).decode()
            text = text.replace(marker, f"data:image/png;base64,{data}")
    assert "__ICON_" not in text
    assert len(text.encode()) < 1_000_000
    FRAGMENT.write_text(text, encoding="utf-8")


def inspect():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        page = browser.new_page(viewport={"width": 736, "height": 1000}, device_scale_factor=1)
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(PREVIEW.as_uri(), wait_until="networkidle")
        frame = page.frames[1]
        frame.wait_for_selector("[data-app] .cu-soft")
        frame.wait_for_function("Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)")
        assert frame.locator("[data-app]").count() == 14
        assert frame.locator('[data-action="report"]').count() == 14
        assert frame.locator('[data-app="steps"] .cu-feedback-badge').get_attribute("aria-label") == "Feedback sent"
        assert frame.locator('.cu-sent').count() == 0
        assert frame.locator('[data-app="steps"] .cu-feedback').count() == 0
        assert frame.locator("[data-run]").inner_text().strip() == "Run all 6"
        for width in (736, 390, 320):
            page.set_viewport_size({"width": width, "height": 1000})
            page.wait_for_timeout(100)
            frame.wait_for_function("""() => [...document.querySelectorAll('.cu-app-heading')].every(row => {
                const mark=row.querySelector('.cu-icon-wrap').getBoundingClientRect();
                const text=row.querySelector('.cu-app-name').getBoundingClientRect();
                return Math.abs(mark.height-text.height)<1 && Math.abs(mark.top-text.top)<1;
            })""")
            overflow = frame.evaluate("""() => [...document.querySelectorAll('.cu-phone *')]
                .filter(e => !e.closest('dialog') && getComputedStyle(e).display !== 'none')
                .filter(e => e.getBoundingClientRect().right > document.querySelector('.cu-phone').getBoundingClientRect().right + 1)
                .map(e => e.className)""")
            assert not overflow, (width, overflow)
        page.set_viewport_size({"width": 736, "height": 1000})
        page.emulate_media(color_scheme="dark")
        phone = frame.locator(".cu-phone")
        height = int(phone.bounding_box()["height"]) + 100
        page.set_viewport_size({"width": 736, "height": height})
        phone.screenshot(path=str(HERE / "crew-unified-app-list-dark.png"), animations="disabled")
        frame.locator('[data-app="steps"]').screenshot(path=str(HERE / "crew-feedback-sent-dark.png"))
        page.emulate_media(color_scheme="light")
        phone.screenshot(path=str(HERE / "crew-unified-app-list-light.png"), animations="disabled")
        frame.locator(".cu-phone").screenshot(path=str(HERE / "crew-unified-dark.png"))
        page.emulate_media(color_scheme="light")
        frame.locator(".cu-phone").screenshot(path=str(HERE / "crew-unified-light.png"))
        frame.locator('[data-id="notes"][data-action="open"]').click()
        assert frame.locator('[data-app="notes"]').inner_text().find("Opened tonight") >= 0
        frame.locator('[data-id="baka"][data-action="install"]').click()
        assert frame.locator('[data-id="baka"][data-action="open"]').count() == 1
        frame.locator('[data-id="edit"][data-action="feedback"]').click()
        assert frame.locator("dialog").evaluate("e=>e.open")
        frame.locator("[data-dialog-confirm]").click()
        assert frame.locator('[data-app="edit"] .cu-feedback-badge').get_attribute("aria-label") == "Feedback sent"
        assert frame.locator('[data-app="edit"] .cu-feedback').count() == 0
        frame.locator('[data-id="astra"][data-action="report"]').click()
        assert frame.locator("#cu-dialog-title").inner_text() == "Report a problem"
        assert frame.locator("[data-dialog-confirm]").is_disabled()
        frame.locator("[data-reason]").nth(1).click()
        assert frame.locator("[data-dialog-confirm]").is_enabled()
        page.emulate_media(color_scheme="dark")
        frame.locator("dialog").screenshot(path=str(HERE / "crew-report-problem-dark.png"))
        page.keyboard.press("Escape")
        assert frame.locator('[data-id="astra"][data-action="report"]').is_enabled()
        frame.locator('[data-id="astra"][data-action="report"]').click()
        frame.locator("#cu-report-reason").fill("Oops")
        assert frame.locator("[data-dialog-confirm]").is_disabled()
        frame.locator("#cu-report-reason").fill("The app crashes after I sign in.")
        frame.locator("[data-dialog-confirm]").click()
        assert "Reported for tonight" in frame.locator('[data-app="astra"]').inner_text()
        assert frame.locator('[data-id="astra"][data-action="report"]').is_disabled()
        assert frame.locator('[data-id="astra"][data-action="open"]').count() == 0
        frame.locator("[data-run]").click()
        assert frame.locator("[data-run]").is_disabled()
        frame.locator("[data-guide]").click()
        assert frame.locator("#cu-dialog-title").inner_text() == "Nightly guide"
        page.keyboard.press("Escape")
        assert not frame.locator("dialog").evaluate("e=>e.open")
        assert not errors, errors
        print(json.dumps({"apps": 14, "viewports": [736, 390, 320], "interactions": "passed", "script_errors": errors}))
        browser.close()


if __name__ == "__main__":
    import sys

    if "--embed" in sys.argv:
        embed_icons()
    else:
        inspect()
