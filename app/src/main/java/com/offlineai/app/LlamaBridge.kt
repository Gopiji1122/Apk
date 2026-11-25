package com.offlineai.app

object LlamaBridge {
    init { System.loadLibrary("llamajni") }

    interface TokenCallback { fun onToken(bytes: ByteArray) }

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Boolean
    /** Returns "" on success, otherwise an error message. */
    external fun nativeGenerate(prompt: ByteArray, maxNew: Int, cb: TokenCallback): String
    external fun nativeStop()
    external fun nativeUnload()
}
