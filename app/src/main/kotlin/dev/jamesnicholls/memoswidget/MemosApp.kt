package dev.jamesnicholls.memoswidget

import android.app.Application
import android.content.Context
import dev.jamesnicholls.memoswidget.data.SettingsRepository
import dev.jamesnicholls.memoswidget.data.WidgetStateRepository
import dev.jamesnicholls.memoswidget.net.MemosApi
import dev.jamesnicholls.memoswidget.widget.SendMemoWorker
import dev.jamesnicholls.memoswidget.widget.WidgetRefresher
import dev.jamesnicholls.memoswidget.widget.WidgetRefreshWorker

class AppContainer(application: Application) {

    val appContext: Context = application
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val widgetStateRepository: WidgetStateRepository = WidgetStateRepository(application)
    val memosApi: MemosApi = MemosApi()

    lateinit var widgetRefresher: WidgetRefresher

    init {
        widgetRefresher = WidgetRefresher(application, this)
    }
}

class MemosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SendMemoWorker.createChannel(this)
        WidgetRefreshWorker.schedulePeriodic(this)
    }
}
