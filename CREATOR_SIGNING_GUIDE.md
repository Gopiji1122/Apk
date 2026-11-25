# Creator Protection & Official Update Signing Guide

On Android, **creator protection** is enforced cryptographically by the operating system using your **App Signing Keystore (`.jks`)**.
- Once users install a Release APK signed with your private keystore, **Android will block anyone else** from pushing an update for `com.aurapdf.editor` unless it is signed with your exact same private key.
- **Never commit your `.jks` file to GitHub.** (`.gitignore` already blocks `*.jks` and `*.keystore`.)

## Step 1: Generate Your Private Creator Keystore (Run Once Locally)
```bash
keytool -genkey -v -keystore aura-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias aura_creator_key
```

## Step 2: Convert Keystore to Base64
**Linux / macOS / Termux:**
```bash
base64 -w 0 aura-release-key.jks > keystore_base64.txt
```
**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("aura-release-key.jks")) | Out-File keystore_base64.txt
```

## Step 3: Add 4 Encrypted Secrets to GitHub
Go to **Settings -> Secrets and variables -> Actions -> New repository secret**:
- `AURA_KEYSTORE_BASE64`: Full text inside `keystore_base64.txt`
- `AURA_KEYSTORE_PASSWORD`: Your keystore password
- `AURA_KEY_ALIAS`: `aura_creator_key`
- `AURA_KEY_PASSWORD`: Your key password

## Step 4: Release Updates
Increment `versionCode` and `versionName` in `app/build.gradle.kts` and push to `main`.
