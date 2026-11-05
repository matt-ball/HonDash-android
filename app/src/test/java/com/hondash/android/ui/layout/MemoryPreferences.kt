package com.hondash.android.ui.layout

import android.content.SharedPreferences
import java.lang.reflect.Proxy

/** Only the string operations used by the repository are supported; unexpected calls fail. */
internal class MemoryPreferences {
    val values = mutableMapOf<String, String>()
    val writes = mutableListOf<String>()
    private val pending = mutableMapOf<String, String>()
    private val editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "putString" -> {
                val key = args!![0] as String
                writes += key
                pending[key] = args[1] as String
                proxy
            }
            "apply" -> { values.putAll(pending); pending.clear(); null }
            else -> error("Unexpected editor operation: ${method.name}")
        }
    } as SharedPreferences.Editor

    val preferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getString" -> values[args!![0] as String] ?: args[1]
            "edit" -> editor
            else -> error("Unexpected preference operation: ${method.name}")
        }
    } as SharedPreferences
}
