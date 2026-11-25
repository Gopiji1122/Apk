package com.gopiji.auraai.engine

interface TokenCallback {
    fun onToken(token: String)
}

data class PromptTurn(
    val role: String,
    val content: String
)

object LlamaEngine {
    init {
        System.loadLibrary("auraai")
    }

    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var currentModelPath: String? = null
        private set

    private external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerateChat(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): String
    private external fun nativeStop()
    private external fun nativeUnload()

    fun loadModel(path: String, contextSize: Int, threads: Int): Boolean {
        val ok = nativeLoadModel(path, contextSize, threads)
        isLoaded = ok
        currentModelPath = if (ok) path else null
        return ok
    }

    fun generateChat(
        turns: List<PromptTurn>,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.90f,
        onToken: (String) -> Unit
    ): String {
        val roles = turns.map { it.role }.toTypedArray()
        val contents = turns.map { it.content }.toTypedArray()
        return nativeGenerateChat(
            roles = roles,
            contents = contents,
            maxTokens = maxTokens,
            temperature = temperature,
            topK = topK,
            topP = topP,
            callback = object : TokenCallback {
                override fun onToken(token: String) {
                    onToken(token)
                }
            }
        )
    }

    fun stopGeneration() {
        nativeStop()
    }

    fun unload() {
        nativeUnload()
        isLoaded = false
        currentModelPath = null
    }
}
