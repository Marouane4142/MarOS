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

## Kotlin and AndroidX runtime

MarOS 5 uses JetBrains Kotlin/coroutines and AndroidX Activity, Lifecycle, Compose UI/Foundation/Animation, TV Material, Material 3 motion and ProfileInstaller. These dependencies are Apache License 2.0; the [full license](launcher/assets/licenses/AndroidX-Apache-2.0.txt) is bundled in the APK and the licenses dialog. Copyright belongs to the respective JetBrains and Android Open Source Project contributors. Versions are pinned in the Gradle scripts. Sources: [Kotlin](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [AndroidX](https://android.googlesource.com/platform/frameworks/support/).

## Optional series metadata

Series searches and upcoming episode information can be retrieved from [TVmaze](https://www.tvmaze.com/) through its [public API](https://www.tvmaze.com/api). TVmaze API data is offered under [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/). Attribution is displayed in the watchlist screens on TV and phone. Keep that attribution and applicable share-alike obligations when redistributing derived metadata. TVmaze is not a viewing-history source; no TVmaze dataset is bundled in the APK.
