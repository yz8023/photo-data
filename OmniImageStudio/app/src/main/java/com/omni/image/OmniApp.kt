package com.omni.image

import android.app.Application

class OmniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: OmniApp
            private set
    }
}
