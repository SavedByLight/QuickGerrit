# Multiplatform compile fix for QuickGerrit composeApp

Copy the `composeApp/` tree from this package **over** your existing `composeApp/` module
(merge/replace files). This makes `commonMain` compile on both Android and Desktop JVM.

## What changed

- `AppConfig` expect/actual (replaces `BuildConfig`)
- `PlatformViewModel` (replaces AndroidX ViewModel / viewModelScope)
- Multiplatform `AccountStore` (JSON file prefs)
- Multiplatform `AppLog` + platform log sinks
- Multiplatform `AppUpdater` (opens browser on desktop)
- State-based `NavGraph` (no Navigation Compose)
- Theme without Android dynamic color
- Android Manifest + MainActivity / QuickGerritApp
- Desktop `Main.kt` entry

## After applying

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:packageDeb :composeApp:packageRpm :composeApp:packageAppImage
# on Windows:
./gradlew :composeApp:packageMsi
```

Desktop packages appear under `composeApp/build/compose/binaries/`.
