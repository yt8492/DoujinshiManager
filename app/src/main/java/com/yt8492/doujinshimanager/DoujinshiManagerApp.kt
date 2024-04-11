package com.yt8492.doujinshimanager

import android.app.Application
import com.yt8492.doujinshimanager.di.infraModule
import com.yt8492.doujinshimanager.di.uiModule
import com.yt8492.doujinshimanager.shared.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DoujinshiManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DoujinshiManagerApp)
            modules(
                commonModule,
                infraModule,
                uiModule,
            )
        }
    }
}
