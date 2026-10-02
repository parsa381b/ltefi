package com.litefiles.app

import android.app.Application
import android.content.Context

/**
 * Applies the in-app language before anything reads resources ([AppSettings.wrap]) and loads the
 * persisted settings. Registered in the manifest as `android:name=".LiteFilesApp"`.
 */
class LiteFilesApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
    }
}
