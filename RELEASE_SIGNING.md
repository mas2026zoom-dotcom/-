# Play Store release signing

The signed AAB workflow is `.github/workflows/release.yml`. It deliberately refuses to build a release unless all four repository Actions secrets are configured.

## Create an upload key on Windows

Run this in PowerShell on a trusted computer with JDK installed. Choose and store your own strong passwords; do not commit the keystore or passwords to Git.

```powershell
keytool -genkeypair -v -keystore al-salah-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias alsalah
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path .\al-salah-upload.jks))
$base64 = [Convert]::ToBase64String($bytes)
$base64 | Set-Clipboard
```

## Add GitHub repository secrets

Open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `ANDROID_KEYSTORE_BASE64`: clipboard contents from the PowerShell command above.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password you chose.
- `ANDROID_KEY_ALIAS`: `alsalah` (or the alias you chose).
- `ANDROID_KEY_PASSWORD`: the key password you chose.

Never paste the keystore, base64 data, or passwords into a chat or commit them to the repository. Keep at least two secure backups of the `.jks` file; losing the upload key can complicate future app updates.

## Build the signed AAB

Open **Actions → Android signed release → Run workflow**, select `feature/full-app-implementation`, and set `confirm_release` to `true`. The workflow runs release unit tests, builds `app-release.aab`, verifies its signature, and uploads it as `al-salah-play-store-release-aab`.

Before uploading to Play Console, confirm the Play App Signing enrollment, app listing, privacy policy, Data safety form, content rating, screenshots, and target API requirements in Play Console.
