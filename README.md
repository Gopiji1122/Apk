# Offline AI (Android)

Kotlin + Jetpack Compose app that runs a GGUF model on-device with llama.cpp. No server, no internet after the model download.

## Build with GitHub Actions
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow** (it also runs on every push).
3. When it finishes, open the run and download the **OfflineAI-apk** artifact. Unzip it and install `app-release.apk` (allow "install unknown apps").

If a build fails because llama.cpp changed, re-run the workflow and set `llama_ref` to a specific tag or commit.

## Using the app
- **Models** tab: download Qwen3.5 4B (default for OnePlus 8) or a smaller model, then tap Load. You can also paste a direct .gguf link or pick a file from storage.
- **Chat** tab: ask questions. Type `remember <fact>` to teach it something.
- **Knowledge** tab: save notes and import .txt / .md files. Notes go into every answer; documents are searched by keyword and the best parts are added to each question.

## Notes
- "Learning" = notes + retrieval (RAG), not retraining the model.
- Only arm64 phones are supported. Native code is tuned for ARMv8.2 (Snapdragon 865 and newer).
- Qwen3.5 runs with thinking turned off for speed.
