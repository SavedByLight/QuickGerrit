# Fix: version 0.0.0 and GitHub updates

## Cause
1. `GeneratedVersion.kt` stayed at 0.0.0 / empty repo because Gradle never regenerated it.
2. Empty `GITHUB_REPO` → update check is disabled.

## Fix
Replace **entire** `composeApp/build.gradle.kts` with the one in this zip.
It always runs `generateDesktopVersion` before desktop compile/package.

## Build (local)
```bash
./gradlew :composeApp:run \
  -PversionName=1.0.70 \
  -PversionCode=70 \
  -PgithubRepo=YOUR_GITHUB_USER/QuickGerrit
```

## Build (same as CI)
```bash
./gradlew :composeApp:packageDeb \
  -PversionName=1.0.70 \
  -PversionCode=70 \
  -PgithubRepo=${GITHUB_REPOSITORY}
```

After a successful build you should see in the log:
```
GeneratedVersion → VERSION_NAME=1.0.70 VERSION_CODE=70 GITHUB_REPO=you/QuickGerrit
```

And in the app: **App 1.0.70 (70)** not 0.0.0.

## Optional
Add to root `gradle.properties`:
```
versionName=1.0.70
versionCode=70
githubRepo=YourUser/QuickGerrit
```
