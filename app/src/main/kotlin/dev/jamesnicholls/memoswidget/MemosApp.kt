package dev.jamesnicholls.memoswidget

import android.app.Application
import dev.jamesnicholls.memoswidget.data.SettingsRepository
import dev.jamesnicholls.memoswidget.data.WidgetStateRepository
import dev.jamesnicholls.memoswidget.net.MemosApi
import dev.jamesnicholls.memoswidget.widget.WidgetRefresher

class AppContainer(application: Application) {
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val widgetStateRepository: WidgetStateRepository = WidgetStateRepository(application)
    val memosApi: MemosApi = MemosApi()
    val widgetRefresher: WidgetRefresher = WidgetRefresher(application, this)
}

class MemosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        dev.jamesnicholls.memoswidget.widget.WidgetRefreshWorker.schedulePeriodic(this)
    }
}
