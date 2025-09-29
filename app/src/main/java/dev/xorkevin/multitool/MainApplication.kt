package dev.xorkevin.multitool

import android.app.Application

class MainApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

class AppContainer {
}