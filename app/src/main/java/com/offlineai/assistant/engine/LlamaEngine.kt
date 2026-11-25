package com.offlineai.assistant.engine

interface TokenCallback {
    fun onToken(token: String)
}

object LlamaEngine {
    init {
        System.loadLibrary("offlineai")
    }

    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var currentModelPath: String? = null
        private set

    private external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback
    ): String
    private external fun nativeStop()
    private external fun nativeUnload()

    fun loadModel(path: String, contextSize: Int = 2048, threads: Int = 6): Boolean {
        val ok = nativeLoadModel(path, contextSize, threads)
        isLoaded = ok
        currentModelPath = if (ok) path else null
        return ok
    }

    fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        onToken: (String) -> Unit
    ): String {
        return nativeGenerate(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
            temperature = temperature,
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
