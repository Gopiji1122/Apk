# Offline AI — Android App (llama.cpp + Local Memory RAG)

A 100% offline, zero-internet, ad-free Android AI assistant built with Kotlin, Jetpack Compose, `llama.cpp`, and a local SQLite knowledge base ("Teach AI").

## Features
- **100% Offline:** No `INTERNET` permission in `AndroidManifest.xml`.
- **Universal `.gguf` Engine:** Runs Qwen 3.5 (4B), Phi-4-Mini (3.8B), Qwen 2.5 (3B), Llama 3.2 (3B), or any GGUF model.
- **Teach AI (Local Memory / RAG):** Save study notes, PC troubleshooting guides, or import `.txt` files. The app automatically searches your saved notes and injects relevant facts into every answer.
- **AMOLED Black UI:** Lightweight, distraction-free dark interface.

## How to Build the APK on GitHub Actions
1. Create a new repository on GitHub.
2. Unzip `OfflineAI-Android-App.zip` and upload all files (including the `.github` folder!) to your repository.
3. Go to the **Actions** tab in your GitHub repo and wait ~6–8 minutes for **Build Offline AI Android APK** to finish.
4. Click the completed workflow run and download the **OfflineAI-arm64-debug-apk** artifact.
5. Unzip and install `app-debug.apk` on your Android phone.

## Recommended Free Models (Download on Phone, Then Import in App)
1. **Qwen 3.5 (4B) — Best Overall for Study + PC Help + Hindi/English**
   - Search Hugging Face for `bartowski/Qwen_Qwen3.5-4B-GGUF` or `unsloth/Qwen3.5-4B-GGUF`
   - Download the **`Q4_K_M`** file (~2.6 GB)
2. **Phi-4-Mini (3.8B) — Faster & Lighter on 8 GB RAM**
   - Search Hugging Face for `unsloth/Phi-4-mini-instruct-GGUF`
   - Download the **`Q4_K_M`** file (~2.4 GB)
