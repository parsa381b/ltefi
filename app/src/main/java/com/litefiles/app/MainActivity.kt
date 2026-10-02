package com.litefiles.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.litefiles.app.ui.App
import com.litefiles.app.ui.BrowserViewModel
import com.litefiles.app.ui.LiteFilesTheme

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels()

    /** Applies the in-app language chosen in Settings (SYSTEM = follow the device language). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LiteFilesTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume() // re-check permission, storage volumes, and refresh the open folder
    }
}
