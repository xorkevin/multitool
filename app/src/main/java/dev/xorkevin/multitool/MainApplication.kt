package dev.xorkevin.multitool

import android.app.Application
import android.content.Context


class MainApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(appContext: Context) {
    val keyStore = KeyStoreService(appContext)
}
