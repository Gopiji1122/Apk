package com.gopiji.auraai.data

enum class ModelCapability(val label: String) {
    TEXT("Text Chat"),
    REASONING("Math & Logic"),
    CODE("Coding & PC"),
    MULTILINGUAL("Hindi / English"),
    VOICE_TTS("Voice Readout"),
    DEVICE_CONTROL("Device Control"),
    AUDIO_INPUT("Audio Input"),
    IMAGE_INPUT("Image Input")
}

data class CatalogModel(
    val id: String,
    val name: String,
    val fileName: String,
    val category: String,
    val sizeGb: Double,
    val minRamGb: Double,
    val recommendedRamGb: Double,
    val downloadUrl: String,
    val description: String,
    val guide: String,
    val capabilities: List<ModelCapability>
)

object ModelCatalog {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "qwen35_4b",
            name = "Qwen 3.5 (4B) — Q4_K_M",
            fileName = "Qwen3.5-4B-Q4_K_M.gguf",
            category = "All-Rounder & Study",
            sizeGb = 2.6,
            minRamGb = 6.0,
            recommendedRamGb = 8.0,
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            description = "Best all-round model for study notes, PC troubleshooting, Hindi/Hinglish, and Device Control.",
            guide = "Keep Fast Mode ON for instant daily chat. Turn Fast Mode OFF in Settings for hard logic puzzles.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.REASONING,
                ModelCapability.MULTILINGUAL,
                ModelCapability.VOICE_TTS,
                ModelCapability.DEVICE_CONTROL
            )
        ),
        CatalogModel(
            id = "phi4_mini_reasoning",
            name = "Phi-4-Mini-Reasoning (3.8B)",
            fileName = "Phi-4-mini-reasoning-Q4_K_M.gguf",
            category = "Math & Logic",
            sizeGb = 2.4,
            minRamGb = 6.0,
            recommendedRamGb = 8.0,
            downloadUrl = "https://huggingface.co/unsloth/Phi-4-mini-reasoning-GGUF/resolve/main/Phi-4-mini-reasoning-Q4_K_M.gguf",
            description = "Specialized for step-by-step math, calendar puzzles, and logical deduction.",
            guide = "Set Temperature to 0.4 and Max Reply Length to 1024+ so it finishes step-by-step reasoning.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.REASONING,
                ModelCapability.VOICE_TTS
            )
        ),
        CatalogModel(
            id = "qwen25_coder_3b",
            name = "Qwen 2.5 Coder (3B)",
            fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            category = "Coding & PC Scripts",
            sizeGb = 2.0,
            minRamGb = 6.0,
            recommendedRamGb = 8.0,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            description = "Trained on 92 programming languages. Ideal for HTML/JS, Python, Kotlin, and Windows CMD/PowerShell.",
            guide = "Use Temperature 0.2–0.4 for clean, bug-free code and scripts.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.CODE,
                ModelCapability.DEVICE_CONTROL
            )
        ),
        CatalogModel(
            id = "gemma3_4b",
            name = "Gemma 3 (4B) Instruct",
            fileName = "gemma-3-4b-it-Q4_K_M.gguf",
            category = "Language & Writing",
            sizeGb = 2.5,
            minRamGb = 6.0,
            recommendedRamGb = 8.0,
            downloadUrl = "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            description = "Google's natural writing model. Great for essays, summaries, letters, and clear study explanations.",
            guide = "Works best at Temperature 0.7. Supports image attachment notes.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.VOICE_TTS,
                ModelCapability.IMAGE_INPUT
            )
        ),
        CatalogModel(
            id = "qwen25_1_5b",
            name = "Qwen 2.5 (1.5B) Ultra-Fast",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            category = "Low RAM / High Speed",
            sizeGb = 1.0,
            minRamGb = 3.5,
            recommendedRamGb = 4.0,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            description = "Very fast and light. Runs smoothly even on 4 GB RAM phones with minimal battery drain.",
            guide = "Best choice for older devices or ultra-fast offline Q&A from your saved Teach AI notes.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.MULTILINGUAL,
                ModelCapability.VOICE_TTS,
                ModelCapability.DEVICE_CONTROL
            )
        ),
        CatalogModel(
            id = "llama31_8b",
            name = "Llama 3.1 (8B) — High RAM",
            fileName = "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            category = "Flagship 12 GB+ Phones",
            sizeGb = 4.9,
            minRamGb = 10.0,
            recommendedRamGb = 12.0,
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            description = "Heavy flagship-class model with deep general knowledge. Requires 12 GB+ physical RAM.",
            guide = "Warning: Will crash on 6–8 GB RAM phones. Only download on 12 GB or 16 GB RAM devices.",
            capabilities = listOf(
                ModelCapability.TEXT,
                ModelCapability.REASONING,
                ModelCapability.CODE,
                ModelCapability.VOICE_TTS,
                ModelCapability.DEVICE_CONTROL
            )
        )
    )
}
