# Third-party components

The release runtime dependency tree contains only:

| Component | Version | Licence |
| --- | --- | --- |
| Shizuku API, provider, aidl and shared | 13.1.5 | MIT, Copyright 2021 RikkaW |
| Kotlin standard library | 2.2.10 | Apache-2.0, JetBrains and contributors |
| JetBrains annotations | 13.0 | Apache-2.0 |
| AndroidX annotation | 1.3.0 | Apache-2.0, Android Open Source Project |

Complete MIT and Apache licence texts and the Kotlin notice are included in `app/src/main/assets/licenses/NOTICES.txt`, available in Help & privacy > Open-source licences. Original app code is MIT licensed; see `LICENSE`.

`LICENSES/` also includes JUnit's EPL-1.0 licence (test dependency 4.13.2) and the Gradle distribution notice for the official Gradle wrapper. These development components are not runtime libraries in the APK. Gradle and AGP use Apache-2.0. SDK and JDK distributions are installed separately and retain their own licences.

Shizuku manager is a separate prerequisite, not bundled in this source or APK. Its [official source](https://github.com/RikkaApps/Shizuku) is [Apache-2.0 licensed](https://github.com/RikkaApps/Shizuku/blob/master/LICENSE). Obtain it from its official upstream and review its own notices. ClipHistory does not use its logo or branding.

The app's original launcher artwork, native interface and synthetic store captures are covered by the project's MIT licence. No separate font binary or external stock artwork is bundled. Android provides the system typeface at runtime.

The approved ClipHistory logo is original supplied artwork. Its geometric Android resources contain no font. The repository wordmark contains outlined Inter 4.001 lettering, with provenance in `branding/ARTWORK-PROVENANCE.md` and its source SIL Open Font License in `LICENSES/Inter-OFL.txt`. No Inter font binary is distributed or loaded by the app.

Upstreams: https://github.com/RikkaApps/Shizuku-API, https://github.com/JetBrains/kotlin, https://github.com/JetBrains/java-annotations, https://android.googlesource.com/platform/frameworks/support/, https://github.com/junit-team/junit4, https://github.com/gradle/gradle.
