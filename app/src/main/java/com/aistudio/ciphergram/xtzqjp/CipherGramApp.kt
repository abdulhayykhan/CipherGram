package com.aistudio.ciphergram.xtzqjp

import androidx.multidex.MultiDexApplication

/**
 * Custom Application class that enables MultiDex support.
 * Required to load all DEX files on devices running Android API 24-27.
 */
class CipherGramApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
    }
}
