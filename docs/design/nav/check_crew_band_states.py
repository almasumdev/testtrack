"""Check the complete Action band state preview at phone widths."""

import base64
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FRAGMENT = HERE / "crew-action-band-all-states.html"
PREVIEW = HERE / "crew-action-band-all-states-preview.html"


def embed():
    text = FRAGMENT.read_text(encoding="utf-8")
    for token, package in {
        "SILENT": "com_eazyverse_soundscheduler", "CHERISH": "app_cherish_people",
        "BAKA": "com_markuljdev_bakarun", "GAAN": "com_arisoftware_gaan",
        "BLUEFY": "com_eazyverse_bluefy", "EDIT": "com_arisoftware_editme",
        "ASTRA": "com_shahadat_astramatch",
    }.items():
        marker = f"__ICON_{token}__"
        if marker in text:
            data = (ROOT / "store/art" / f"circle_actual_{package}.png").read_bytes()
            text = text.replace(marker, "data:image/png;base64," + base64.b64encode(data).decode())
    assert "__ICON_" not in text and len(text.encode()) < 1_000_000
    FRAGMENT.write_text(text, encoding="utf-8")


def inspect():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        page = browser.new_page(viewport={"width": 736, "height": 1000})
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(PREVIEW.as_uri(), wait_until="networkidle")
        frame = page.frames[1]
        frame.wait_for_function("document.querySelectorAll('.bs-tile').length===19")
        frame.wait_for_function("Array.from(document.images).every(i=>i.complete&&i.naturalWidth>0)")
        assert frame.locator('[data-app="late-feedback"] .bs-feedback').count() == 0
        assert frame.locator('[data-app="late-feedback"]').inner_text().count('Feedback overdue') == 1
        assert frame.locator('.bs-context').count() == 0
        assert frame.locator('[data-app="sending"] .bs-cta').count() == 0
        for key, explanation in (
            ('missing-picture', 'Your app time is saved.'),
            ('sending', 'count once the upload finishes'),
            ('delayed', 'Saved on this phone for over 3 hours.'),
        ):
            assert explanation not in frame.locator(f'[data-app="{key}"]').inner_text()
            frame.locator(f'[data-info="{key}"]').click()
            assert explanation in frame.locator('#bs-info-popup').inner_text()
            assert frame.locator('#bs-info-popup').is_visible()
            assert not frame.locator('.bs-dialog').is_visible()
            frame.locator(f'[data-info="{key}"]').press('Escape')
            assert not frame.locator('#bs-info-popup').is_visible()
        assert frame.locator('[data-app="update-grace"] .bs-secondary').count() == 0
        assert frame.locator('[data-app="update-grace"] .bs-band button').count() == 2
        assert frame.get_by_role('button', name='Pick', exact=True).count() == 0
        assert frame.get_by_role('button', name='Send', exact=True).count() == 0
        for width in (736, 390, 320):
            page.set_viewport_size({"width": width, "height": 1000})
            frame.wait_for_function("""() => [...document.querySelectorAll('.bs-feedback-copy')].every(copy => {
                const icon=copy.parentElement.querySelector('svg').getBoundingClientRect();
                const text=copy.querySelector('strong').getBoundingClientRect();
                const due=copy.querySelector('small').getBoundingClientRect();
                return icon.height===16 && Math.abs(icon.top+icon.height/2-text.top-text.height/2)<1 && Math.abs(due.top-text.top)<1 && copy.querySelector('small').textContent==='· By night 7';
            })""")
            overflow = frame.evaluate("""() => [...document.querySelectorAll('.bs-tile *')]
                .filter(e=>e.getBoundingClientRect().width>0)
                .filter(e=>e.getBoundingClientRect().right>e.closest('.bs-tile').getBoundingClientRect().right+1)
                .map(e=>e.className)""")
            assert not overflow, (width, overflow)
        page.set_viewport_size({"width": 736, "height": 1000})
        height = int(frame.locator('.bs-page').bounding_box()['height']) + 100
        page.set_viewport_size({"width": 736, "height": height})
        page.emulate_media(color_scheme="dark")
        frame.locator('.bs-page').screenshot(path=str(HERE / 'crew-band-all-states-dark.png'))
        frame.locator('[data-app="ready"] .bs-feedback').screenshot(path=str(HERE / 'crew-band-feedback-icon.png'))
        for key in ('update-grace', 'late-feedback', 'missing-picture', 'sending', 'retry', 'delayed'):
            frame.locator(f'[data-app="{key}"]').screenshot(path=str(HERE / f'crew-band-clear-{key}.png'))
        page.emulate_media(color_scheme="light")
        frame.locator('.bs-page').screenshot(path=str(HERE / 'crew-band-all-states-light.png'))
        frame.locator('[data-act="reported"]').click()
        assert 'Reported · opened tonight' in frame.locator('[data-app="reported"]').inner_text()
        assert frame.locator('[data-app="reported"] [data-report]').count() == 0
        assert frame.locator('[data-act="reported"]').is_enabled()
        frame.locator('[data-act="reported-install"]').click()
        assert frame.locator('[data-act="reported-install"]').get_attribute('aria-label').startswith('Test again')
        frame.locator('[data-act="update"]').click()
        assert frame.locator('[data-act="update"]').get_attribute('aria-label').startswith('Open')
        frame.locator('[data-act="late-feedback"]').click()
        assert frame.locator('#bs-dialog-title').inner_text() == 'Private feedback'
        frame.locator('[data-confirm]').click()
        assert frame.locator('#bs-dialog-title').inner_text() == 'Capture your feedback'
        frame.locator('[data-confirm]').click()
        assert frame.locator('[data-app="late-feedback"] .bs-sent').count() == 1
        assert frame.locator('[data-app="late-feedback"] .bs-feedback').count() == 0
        frame.locator('[data-feedback="ready"]').click()
        frame.locator('[data-secondary]').click()
        assert frame.locator('#bs-dialog-title').inner_text() == 'Use saved feedback screenshot'
        frame.locator('[data-confirm]').click()
        frame.locator('[data-act="missing-picture"]').click()
        assert frame.locator('#bs-dialog-title').inner_text() == 'Complete your test'
        frame.locator('[data-secondary]').click()
        assert frame.locator('#bs-dialog-title').inner_text() == 'Use saved app screenshot'
        frame.locator('[data-confirm]').click()
        assert 'Opened tonight' in frame.locator('[data-app="missing-picture"]').inner_text()
        frame.locator('[data-open-current="update-grace"]').click()
        assert 'Opened tonight' in frame.locator('[data-app="update-grace"]').inner_text()
        assert frame.locator('[data-app="sending"] button.bs-band').count() == 0
        frame.locator('[data-act="retry"]').click()
        assert 'Opened tonight' in frame.locator('[data-app="retry"]').inner_text()
        frame.locator('[data-act="left-uninstall"]').click()
        frame.locator('.bs-dialog').press('Escape')
        assert frame.locator('[data-app="left-uninstall"]').count() == 1
        assert frame.locator('[data-act="left-uninstall"]').is_enabled()
        frame.locator('[data-act="left-uninstall"]').click()
        frame.locator('[data-confirm]').click()
        assert frame.locator('.bs-tile').count() == 18
        assert frame.locator('[data-app="left-uninstall"]').count() == 0
        assert not errors, errors
        print(json.dumps({'tile_examples': 19, 'widths': [736, 390, 320], 'interactions': 'passed', 'errors': errors}))
        browser.close()


if __name__ == '__main__':
    embed() if '--embed' in sys.argv else inspect()
