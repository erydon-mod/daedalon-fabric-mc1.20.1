# Third-party notices for the source repository

Daedalon's own code, original assets and documentation remain covered by
[Oliver's restricted licence](LICENSE). Separately licensed material retains
its original terms.

## Gradle wrapper and launcher scripts

- Project: Gradle, by the Gradle original authors and contributors.
- Repository paths: `gradle/wrapper/gradle-wrapper.jar`, `gradlew` and
  `gradlew.bat`.
- Licence: Apache License 2.0, with the upstream licence and accompanying
  component conditions retained in
  [licenses/gradle-wrapper/LICENSE](licenses/gradle-wrapper/LICENSE).
- Launcher copyright notices, retained in the scripts: Copyright 2015-2021
  the original authors (`gradlew`); Copyright 2015 the original author or
  authors (`gradlew.bat`).
- Wrapper entrypoint source copyright: Copyright 2007 the original author
  or authors.

The checked-in wrapper JAR is byte-identical to the official Gradle 7.5 and
7.5.1 wrapper JAR. Its SHA-256 is:

```text
91a239400bb638f36a1795d8fdf7939d532cdc7d794d1119b7261aac158b1e60
```

Provenance and licence sources:

- [Official Gradle wrapper checksum reference](https://gradle.org/release-checksums/)
- [Checksum-identical upstream wrapper JAR](https://github.com/gradle/gradle/blob/v7.5.1/gradle/wrapper/gradle-wrapper.jar)
- [Corresponding wrapper entrypoint source](https://github.com/gradle/gradle/blob/v7.5.1/subprojects/wrapper/src/main/java/org/gradle/wrapper/GradleWrapperMain.java)
- [Unmodified upstream licence file](https://github.com/gradle/gradle/blob/v7.5.1/LICENSE)

The local licence copy retains the complete upstream file, including its
distribution-wide component conditions. Each component's terms apply to that
component. Its SHA-256 is:

```text
e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437
```

`gradle/wrapper/gradle-wrapper.properties` selects Gradle 8.7 as the downloaded
build runtime. The wrapper JAR provenance above describes the checked-in
launcher binary separately from that configured runtime. The wrapper JAR is
unmodified, and the launcher scripts retain their upstream copyright and
licence headers. This build tooling is distributed with the source repository;
it is not bundled in Daedalon's mod JAR.

## Model assets and branding

The attribution and CC BY 4.0 terms for tina.hill's model adapted into the
Diota, Kylix and Kalyx families remain in
[the packaged model notices](src/main/resources/META-INF/THIRD_PARTY_NOTICES.md).

[BRANDING.md](BRANDING.md) records the Portorium artwork provenance and the
Cinzel rendered-lettering attribution. These notices do not change the
licences of Daedalon's own code or assets.
