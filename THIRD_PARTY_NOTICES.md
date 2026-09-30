# Third-party components

No Android SDK, JDK, Gradle distribution, font files, private signing keys or third-party binary libraries are bundled in this source ZIP. Build tools retrieve dependencies separately. Keep their original notices with any distributions that include them.

- **Shizuku API / provider / transitive Shizuku shared components**, dev.rikka.shizuku 13.1.5. Upstream: https://github.com/RikkaApps/Shizuku-API — MIT licence. The dependency is used for the authorised shell UserService and protected provider connection.
- **Kotlin runtime and compiler tooling**, supplied by the Android/Gradle build tooling — Apache License 2.0. https://github.com/JetBrains/kotlin
- **AndroidX annotations**, a Shizuku transitive dependency — Apache License 2.0. https://android.googlesource.com/platform/frameworks/support/
- **JUnit 4.13.2**, test-only — Eclipse Public License 1.0. https://github.com/junit-team/junit4
- **Android SDK / Android Gradle Plugin**, development tools — subject to their respective Google/Android licences. https://developer.android.com/studio
- **Gradle**, build tool — Apache License 2.0. https://github.com/gradle/gradle
- **Eclipse Temurin / OpenJDK**, optional local JDK bootstrap — GPLv2 with Classpath Exception and associated distribution notices. https://adoptium.net/

Original ClipHistory application code is under the MIT licence in `LICENSE`. This notice is not a substitute for the complete notices in resolved dependencies. Before distributing a compiled APK, inspect its resolved dependency list and preserve all required upstream notices.
