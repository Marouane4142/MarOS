# Third-party notices

The MIT license for MarOS code does not replace the licenses of bundled third-party code, fonts or photographs. Preserve these notices in source and binary distributions.

| Component | Origin | License and included notice |
| --- | --- | --- |
| tv-home base | [Mihir Gupta](https://github.com/mihirgupta0900/tv-home), commit `19803542cd335e0b2643dfa73c18c38051354ffa` | MIT, copyright 2026 Mihir Gupta, retained in [LICENSE](LICENSE) |
| Lucide vector icons | [lucide-icons/lucide](https://github.com/lucide-icons/lucide) | ISC and applicable Feather MIT notices, [full notice](launcher/assets/licenses/lucide.txt) |
| Manrope font | [Manrope through Google Fonts](https://github.com/google/fonts/tree/main/ofl/manrope) | SIL Open Font License 1.1, [full notice](launcher/assets/licenses/Manrope-OFL.txt) |
| QR Code generator | [Project Nayuki](https://github.com/nayuki/QR-Code-generator) | MIT, copyright Project Nayuki; full license headers retained in each [vendored Java source](launcher/src/io/nayuki/qrcodegen/) |
| Twelve landscape photographs | Wikimedia Commons authors | Individual CC BY-SA licenses; [authors, sources and license versions](launcher/WALLPAPER-CREDITS.md) |

Pinned upstream revisions for the added graphical and QR resources are recorded in [third-party-revisions.json](docs/third-party-revisions.json). Lucide SVGs were converted to Android VectorDrawable resources. Landscape photographs are resized/cropped for TV use; preserve their original attribution and license links when redistributing adaptations. Their licenses do not relicense the independent Java application as CC BY-SA.

Application names and logos shown by MarOS are loaded from applications installed by the user. MarOS does not distribute those applications or claim affiliation with their publishers. Development design guidelines are not runtime components and are not bundled in this repository.

The original upstream license file, including its separate-photo-license clarification, is preserved verbatim in [upstream-license.txt](docs/upstream-license.txt). Full MIT and asset notices are also bundled in the APK and available in Advanced → About and licenses.
