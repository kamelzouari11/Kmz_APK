# Implementation Plan - Update Gradle and Fix Build Warnings

The project is currently using Gradle 8.14.5 and AGP 8.7.2, with several outdated dependencies and SDK configurations. This plan aims to upgrade the build system to the latest stable versions and resolve all reported warnings in the `gradle-wrapper.properties` and build scripts.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle-wrapper.properties](file:///media/kamel/DATA/KmzAPK/Football/gradle/wrapper/gradle-wrapper.properties)
- Upgrade Gradle version from `8.14.5` to `9.7.0`.
- Change distribution from `-bin.zip` to `-all.zip` for better IDE support.
- Remove redundant escape backslash in `distributionUrl`.

#### [MODIFY] [build.gradle.kts](file:///media/kamel/DATA/KmzAPK/Football/build.gradle.kts)
- Upgrade Android Gradle Plugin (AGP) from `8.7.2` to `9.3.1`.
- Upgrade Kotlin version from `1.9.25` to `2.4.10`.
- Upgrade KSP version to `2.4.10-1.0.x` (or equivalent compatible version).
- Fix deprecation warning for `rootProject.buildDir` by using `layout.buildDirectory`.

#### [MODIFY] [app/build.gradle.kts](file:///media/kamel/DATA/KmzAPK/Football/app/build.gradle.kts)
- Update `compileSdk` to `37`.
- Update `targetSdk` to `37`.
- Update Compose BOM and related dependencies to their latest stable versions.
- Update other libraries (Retrofit, OkHttp, Moshi, Coil, etc.) as suggested by the IDE inspections.
- Fix syntax warnings (missing trailing commas, redundant string templates).

## Verification Plan

### Automated Tests
- Run `./gradlew clean` to verify build script validity.
- Run `./gradlew assembleDebug` to ensure the project compiles with new versions.
- Run `./gradlew test` to ensure no regressions in unit tests (if any).

### Manual Verification
- Perform a Gradle Sync in Android Studio to ensure the IDE correctly recognizes all changes and no warnings remain in the modified files.
