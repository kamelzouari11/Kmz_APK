package com.example.simpleiptv

import android.app.Application

class SimpleIPTVApp : Application() {

    companion object {
        lateinit var instance: SimpleIPTVApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
