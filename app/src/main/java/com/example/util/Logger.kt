package com.example.util

import android.util.Log

/**
 * Privacy-aware logger that records technical diagnostics without ever logging
 * private message bodies, cryptographic keys, or raw audio buffers.
 */
object Logger {
    private const val TAG_PREFIX = "NearTalk"

    fun d(tag: String, message: String) {
        runCatching { Log.d("$TAG_PREFIX:$tag", message) }
    }

    fun i(tag: String, message: String) {
        runCatching { Log.i("$TAG_PREFIX:$tag", message) }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) {
                Log.w("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX:$tag", message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) {
                Log.e("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX:$tag", message)
            }
        }
    }
}
