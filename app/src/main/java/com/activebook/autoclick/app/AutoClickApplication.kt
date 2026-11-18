package com.activebook.autoclick.app

import android.app.Application

/**
 * Owns the process-wide singletons. Both services and the activity run in the same process, so
 * they share exactly one [AppContainer] — no IPC, no duplicated state.
 */
class AutoClickApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
