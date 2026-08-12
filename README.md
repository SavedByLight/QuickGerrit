# QuickGerrit

A modern **Kotlin + Jetpack Compose** Android client for [Gerrit Code Review](https://www.gerritcodereview.com/).

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

1. Open the `QuickGerrit` folder in **Android Studio** (Hedgehog / Iguana / newer).
2. Let Gradle sync (it will download dependencies).
3. Run on an emulator or device (minSdk 26).

### Adding an account

1. Open Gerrit in a browser → **Settings** → **HTTP Password** → generate one.
2. In the app tap the account icon → **Add Account**.
3. Enter:
   - Display name
   - Base URL (e.g. `https://gerrit.example.com` or `https://android-review.googlesource.com`)
   - Username
   - The HTTP password (not your login password)

The app authenticates with HTTP Basic on `/a/...` endpoints and strips Gerrit’s `)]}'` XSSI prefix.

## CI / GitHub Releases

Every push to `main`/`master` (and manual **workflow_dispatch**) builds **both** APKs and publishes a GitHub Release:

| Asset | Description |
|-------|-------------|
| `QuickGerrit-release.apk` | Release build (preferred for daily use) |
| `QuickGerrit-debug.apk` | Debug build with verbose logging |
| `QuickGerrit-<ver>-release.apk` | Versioned release APK |
| `QuickGerrit-<ver>-debug.apk` | Versioned debug APK |

Version scheme: `1.0.<github.run_number>` (also used as `versionCode`).

### Secrets (optional, for signed release)

| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | Base64-encoded PKCS12/JKS keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Without these, the release APK is still published (unsigned).

### In-app updates

The app checks `https://api.github.com/repos/<owner>/<repo>/releases/latest` on launch and from **Accounts → Check for updates**.  
`GITHUB_REPO` is injected by CI via `-PgithubRepo=${{ github.repository }}` into `BuildConfig`.  
Installing an update requires allowing “Install unknown apps” for QuickGerrit (Android 8+).

## Architecture

```
ui/          Compose screens + ViewModels (UDF / StateFlow)
data/
  api/       Retrofit GerritApi + OkHttp (auth + XSSI interceptor)
  model/     kotlinx.serialization DTOs
  local/     DataStore multi-account store
  repository/ GerritRepository
update/      GitHub Releases auto-updater
```

## Notes

- CORS is not an issue (native app).
- Some Gerrit instances require the account to have generated an HTTP password.
- Diff viewer is text-based (no syntax highlighting yet); easy to extend with a library later.
- Review labels are hard-coded to the common Code-Review / Verified pair; the detail screen shows whatever labels the server returns.

## License

Apache-2.0 (same spirit as Gerrit clients).
