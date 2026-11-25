# Aura AI — Offline Android AI Assistant

A 100% offline, zero-internet, ad-free Android AI assistant powered by `llama.cpp` v1.0.0 with a minimalist ChatGPT / Gemini / Claude-inspired UI, black-and-white Aura AI logo, and universal Android 7.0–15 support.

## Permanent App ID & Future Updates
- **App Name:** Aura AI
- **Permanent App ID:** `com.gopiji.auraai`
- **Consistent Signing Key:** Includes `app/aura-ai.keystore` configured in `app/build.gradle.kts`, so every GitHub Actions build uses the exact same signature.
- **How to release an update later:** Keep `applicationId = "com.gopiji.auraai"` and `app/aura-ai.keystore` unchanged, simply increase `versionCode` (e.g., `1` -> `2`) and `versionName` (e.g., `"1.0.0"` -> `"1.1.0"`) in `app/build.gradle.kts`, push to GitHub, and install the new APK right over the existing app without losing any chats, notes, or imported `.gguf` models.
