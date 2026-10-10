# Third-party notices

## Mihomo Android node-test core

The APK currently embeds [libmihomo-android v0.3.5](https://github.com/oviron/libmihomo-android/releases/tag/v0.3.5),
which bundles [Mihomo v1.19.31](https://github.com/MetaCubeX/mihomo). Both are GPL-3.0.
The AAR is downloaded from the fixed release URL during the build and checked against
SHA-256 `41ddcce67de37f406377798c5adbc7ff925e6cd9cadbef3f187da3a03e564176`.
The wrapper source revision for this tag is `d8cbe8c42acbe5c35e6b31ced324f0f928322fc2`.
It is used only for local per-node request tests; OwnBridge does not call its TUN API.
The public source tag, [build instructions](docs/BUILDING.md), pinned upstream
source references, and bundled GPL-3.0 license text accompany any preview APK.
This project is not affiliated with MetaCubeX or the wrapper maintainer.

## sing-box server executable

The experimental SS deployment downloads an unmodified, version-pinned
[sing-box release](https://github.com/SagerNet/sing-box/releases) directly from
the upstream project onto the user's VPS and checks its published SHA-256
digest. The executable is not bundled into the OwnBridge APK or copied into
this repository. sing-box is published under
[GPL-3.0-or-later](https://github.com/SagerNet/sing-box/blob/testing/LICENSE);
OwnBridge is not affiliated with or endorsed by SagerNet. Release packaging
and the installed license notice require a separate review before public
distribution.

## Icons

`ob_mihomo.xml` is a small vector adaptation of the cube mark in
[MetaCubeXD's favicon.svg](https://github.com/MetaCubeX/metacubexd/blob/main/packages/ui/public/favicon.svg),
from the [MIT-licensed](https://github.com/MetaCubeX/metacubexd/blob/main/LICENSE)
official Mihomo dashboard. It identifies the test engine, not an affiliation or endorsement.

The icons supplied in `OB.txt` and `OB2.txt`, plus the later help, Ping, cancel-Ping, Visibility, Visibility Off, warning, save, link, and expand icons, are stored as local Android vector drawables under `app/src/main/res/drawable/` and registered in `ProjectIcons.kt`. Their SVG geometry is retained; the Google paths are translated from the original `0 -960 960 960` viewBox into Android's positive viewport. Colors are supplied by OwnBridge at rendering time. The full license texts in `third_party_licenses/` are also packaged with the APK as assets.

## Google Material Symbols

All `ob_*.xml` icons except `ob_github.xml` and `ob_mihomo.xml` came from [Google Fonts Material Symbols](https://developers.google.com/fonts/docs/material_symbols). Google distributes Material Symbols under [Apache License 2.0](third_party_licenses/Apache-2.0.txt). The original SVGs were supplied by the project owner; these vector drawables are format conversions.

## Bootstrap Icons GitHub glyph

`ob_github.xml` is the [Bootstrap Icons GitHub glyph](https://icons.getbootstrap.com/icons/github/), licensed under [MIT](third_party_licenses/Bootstrap-Icons-MIT.txt). It is registered for a future link to the project's GitHub page, not used as OwnBridge branding. GitHub's [logo usage rules](https://brand.github.com/foundations/logo) are separate from the icon library's copyright license; using this asset must not imply GitHub sponsorship or affiliation.
