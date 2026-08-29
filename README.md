# QuickGerrit

A modern **Kotlin + Jetpack Compose / Compose Multiplatform** client for [Gerrit Code Review](https://www.gerritcodereview.com/).

## Platforms

- **Android** (original) – minSdk 26
- **Desktop** (new) – Windows, Linux, macOS
  - Native packages: **MSI** (Windows), **DEB** / **RPM** / **AppImage** (Linux), DMG (macOS)

## Features

- **Credentials manager** – store multiple Gerrit accounts (URL + username + HTTP password)
- **Multi-account** – switch between instances/accounts
- **Changes** – Open / Merged / Abandoned tabs with search
- **Projects / Repos** list
- **Change detail**
  - Subject, project, branch, owner, status, WIP
  - Labels (Code-Review, Verified, …)
  - All patch sets / commits
  - File list with +/- stats
  - Messages history
- **Code viewing** – unified & side-by-side diff
- **Reviewing** – post Code-Review / Verified scores + comment
- **Actions** – Abandon, Restore, Submit (when allowed)

## How to run

### Android

1. Open the project in **Android Studio**.
2. Run the `:app` or `:composeApp` Android target.

### Desktop

```bash
./gradlew :composeApp:run
```

### Build desktop packages

```bash
# All formats (on Linux host for Deb/Rpm/AppImage; Windows host for MSI)
./gradlew :composeApp:packageDistributionForCurrentOS

# Or specific:
./gradlew :composeApp:packageDeb
./gradlew :composeApp:packageRpm
./gradlew :composeApp:packageAppImage
./gradlew :composeApp:packageMsi   # requires Windows
```

Packages appear under `composeApp/build/compose/binaries/main/`.

## Architecture (multiplatform)

```
composeApp/
  src/
    commonMain/   Shared UI + data (Gerrit API, models, repository, screens)
    androidMain/  Android entry + platform actuals (DataStore, etc.)
    desktopMain/  Desktop entry (Window) + JVM actuals
app/              Original pure-Android module (kept for compatibility)
```

## CI / GitHub Releases

Builds APK (Android) and desktop packages (Deb, Rpm, AppImage, MSI when runners allow) and publishes releases.

Version scheme: `1.0.<github.run_number>`.

## License

Apache-2.0
