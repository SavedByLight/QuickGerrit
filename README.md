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

1. Open the `quickgerrit` folder in **Android Studio** (Hedgehog / Iguana / newer).
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

## Architecture

```
ui/          Compose screens + ViewModels (UDF / StateFlow)
data/
  api/       Retrofit GerritApi + OkHttp (auth + XSSI interceptor)
  model/     kotlinx.serialization DTOs
  local/     DataStore multi-account store
  repository/ GerritRepository
```

## Notes

- CORS is not an issue (native app).
- Some Gerrit instances require the account to have generated an HTTP password.
- Diff viewer is text-based (no syntax highlighting yet); easy to extend with a library later.
- Review labels are hard-coded to the common Code-Review / Verified pair; the detail screen shows whatever labels the server returns.

## License

Apache-2.0 (same spirit as Gerrit clients).
