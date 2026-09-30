package com.litefiles.app

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
