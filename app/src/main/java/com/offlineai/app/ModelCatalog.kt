package com.offlineai.app

enum class Tmpl { QWEN_NOTHINK, CHATML, LLAMA3 }

data class ModelInfo(
    val name: String,
    val file: String,
    val url: String,
    val sizeMb: Int,
    val tmpl: Tmpl,
    val note: String
)

object Catalog {
    val models = listOf(
        ModelInfo(
            "Qwen3.5 4B (best quality)",
            "Qwen3.5-4B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            2800, Tmpl.QWEN_NOTHINK,
            "Needs 8 GB RAM. Slower (about 2-6 tokens/sec on OnePlus 8)."
        ),
        ModelInfo(
            "Qwen2.5 1.5B Instruct (fast)",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1100, Tmpl.CHATML,
            "Works on 4 GB+ phones. Faster, less smart."
        ),
        ModelInfo(
            "Llama 3.2 1B Instruct (lightest)",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            810, Tmpl.LLAMA3,
            "Very fast, good for English."
        )
    )

    fun guess(file: String): Tmpl {
        val f = file.lowercase()
        return when {
            "qwen3" in f -> Tmpl.QWEN_NOTHINK
            "llama" in f -> Tmpl.LLAMA3
            else -> Tmpl.CHATML
        }
    }
}

object Templates {
    fun build(t: Tmpl, system: String, history: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        if (t == Tmpl.LLAMA3) {
            // BOS is added by the tokenizer.
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(system).append("<|eot_id|>")
            for ((role, text) in history)
                sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n").append(text).append("<|eot_id|>")
            sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        } else {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            for ((role, text) in history)
                sb.append("<|im_start|>").append(role).append("\n").append(text).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n")
            if (t == Tmpl.QWEN_NOTHINK) sb.append("<think>\n\n</think>\n\n")
        }
        return sb.toString()
    }
}
