# Branding

Final app-icon assets for Test Track (the two phones swapping icon).

## Android Studio Image Asset layers

Use these in Android Studio (New, Image Asset) to rebuild the launcher icons:

- `foreground.svg` and `foreground.png`: the foreground layer. The PNG (1024 px) keeps the soft shadows, the SVG has none.
- `background.svg` and `background.png`: the tile-blue gradient background layer.
- `monochrome.svg` and `monochrome.png`: the one-colour layer for themed icons.

## Launch animation sources

- `splash-light.svg` and `splash-dark.svg`: the artwork for the launch animation, one per theme. Every moving part is a named group.
- `scripts/build_swap_splash.py` turns them into the splash drawables under `app/src/main/res` (`drawable`, `drawable-night`, `drawable-v31`, `drawable-night-v31` and `interpolator`). Edit the SVGs here, then run `python scripts/build_swap_splash.py`.

## Play icon

The Play Store icon is `app/src/main/ic_launcher-playstore.png`, written by Android Studio together with the launcher set. `mkit.toml` `[store] icon` points there, so there is no copy in this folder.

## Where the rest lives

Review pages, generators, the shape numbers and the older working SVGs are in `docs/design/icon-swap/`.
