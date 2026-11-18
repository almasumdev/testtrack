"""Render and inspect the five local Crew tile proposals."""

import base64
import json
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FRAGMENT = HERE / "crew-app-actions-five.html"
PREVIEW = HERE / "crew-app-actions-five-preview.html"


def embed():
    text = FRAGMENT.read_text(encoding="utf-8")
    for token, package in {
        "BAKA": "com_markuljdev_bakarun",
        "BLUEFY": "com_eazyverse_bluefy",
        "CHERISH": "app_cherish_people",
    }.items():
        marker = f"__ICON_{token}__"
        if marker in text:
            image = (ROOT / "store/art" / f"circle_actual_{package}.png").read_bytes()
            text = text.replace(marker, "data:image/png;base64," + base64.b64encode(image).decode())
    assert "__ICON_" not in text and len(text.encode()) < 1_000_000
    FRAGMENT.write_text(text, encoding="utf-8")


def inspect():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        page = browser.new_page(viewport={"width": 736, "height": 850})
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(PREVIEW.as_uri(), wait_until="networkidle")
        frame = page.frames[1]
        frame.wait_for_function("document.querySelectorAll('.ca-tile').length===15")
        frame.wait_for_function("Array.from(document.images).every(i=>i.complete&&i.naturalWidth>0)")
        layouts = []
        for index in range(5):
            section = frame.locator("section[data-variant]:visible")
            name = section.get_attribute("data-variant")
            layouts.append(name)
            assert section.locator(".ca-tile").count() == 3
            for width in (736, 390, 320):
                page.set_viewport_size({"width": width, "height": 850})
                overflow = section.evaluate("""section => [...section.querySelectorAll('.ca-tile *')]
                    .filter(e=>e.getBoundingClientRect().width>0)
                    .filter(e=>e.getBoundingClientRect().right>e.closest('.ca-tile').getBoundingClientRect().right+1)
                    .map(e=>e.className)""")
                assert not overflow, (name, width, overflow)
            page.set_viewport_size({"width": 736, "height": 850})
            page.emulate_media(color_scheme="dark")
            section.locator(".ca-tiles").screenshot(path=str(HERE / f"crew-actions-{index + 1}-dark.png"))
            section.locator('[data-act="bluefy"]').click()
            assert section.locator('[data-app="bluefy"] [data-act]').get_attribute("aria-label").startswith("Open ")
            section.locator('[data-act="baka"]').click()
            assert "Tested again" in section.locator('[data-app="baka"]').inner_text()
            if index < 4:
                frame.get_by_role("button", name=re.compile("next", re.I)).click()
        assert len(set(layouts)) == 5, layouts
        section.locator('[data-report="cherish"]').click()
        assert frame.locator("[data-submit]").is_disabled()
        frame.locator("[data-reason]").fill("The app crashes when I try to log in.")
        frame.locator("[data-submit]").click()
        assert "Reported for tonight" in section.locator('[data-app="cherish"]').inner_text()
        assert not errors, errors
        print(json.dumps({"designs": layouts, "widths": [736, 390, 320], "actions": "passed", "errors": errors}))
        browser.close()


if __name__ == "__main__":
    embed() if "--embed" in sys.argv else inspect()
