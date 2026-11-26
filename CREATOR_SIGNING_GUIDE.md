# Creator Protection & Official Update Signing Guide

On Android, **creator protection** is enforced cryptographically by the operating system using your **App Signing Keystore (`.jks`)**.
- Once users install a Release APK signed with your private keystore, **Android will block anyone else** from pushing an update for `com.aurapdf.editor` unless it is signed with your exact same private key.
- **Never commit your `.jks` file to GitHub.** (`.gitignore` already blocks `*.jks` and `*.keystore`.)

## Step 1: Generate Your Private Creator Keystore (Run Once Locally)
Run this command on any PC/Mac/Termux with Java installed:

```bash
keytool -genkey -v -keystore aura-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias aura_creator_key
```
Save your keystore file (`aura-release-key.jks`) and passwords in a safe offline backup (Google Drive encrypted archive / password manager). If you lose it, you cannot update the same App ID later.

## Step 2: Convert Keystore to Base64
**Linux / macOS / Termux:**
```bash
base64 -w 0 aura-release-key.jks > keystore_base64.txt
```
**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("aura-release-key.jks")) | Out-File keystore_base64.txt
```

## Step 3: Add Encrypted Secrets to Your GitHub Repository
Go to your GitHub Repository -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, and add these 4 secrets:

| Secret Name | Value |
| :--- | :--- |
| `AURA_KEYSTORE_BASE64` | Full text inside `keystore_base64.txt` |
| `AURA_KEYSTORE_PASSWORD` | The keystore password you chose |
| `AURA_KEY_ALIAS` | `aura_creator_key` |
| `AURA_KEY_PASSWORD` | The key password you chose |

## Step 4: How to Release Future Updates (v1.0.1, v1.1.0, v2.0.0...)
1. Open `app/build.gradle.kts` and increment `versionCode` (e.g., `1` -> `2`) and `versionName` (e.g., `"1.0.0"` -> `"1.0.1"`).
2. Push to `main`. GitHub Actions will automatically compile and sign **both** `app-release.apk` (direct install) and `app-release.aab` (Google Play Store) using your private creator key.
