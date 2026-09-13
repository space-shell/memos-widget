package dev.jamesnicholls.memoswidget

import android.app.Application
import dev.jamesnicholls.memoswidget.data.SettingsRepository
import dev.jamesnicholls.memoswidget.net.MemosApi

class AppContainer(application: Application) {
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val memosApi: MemosApi = MemosApi()
}

class MemosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
