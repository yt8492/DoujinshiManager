package com.yt8492.doujinshimanager.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val infraModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(Database.Schema, androidContext(), "DoujinshiManager.db")
    }
}
