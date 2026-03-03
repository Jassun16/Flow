package com.jassun16.flow

import android.app.Application
import com.jassun16.flow.data.db.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlowApplication : Application()
// That's it — one annotation does everything!
{

    // Inject the DB so we can touch it eagerly
    @Inject
    lateinit var database: AppDatabase

    // Application-scoped coroutine scope — lives as long as the process
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()   // Hilt injection happens here — database is ready after this line

        // Touch the DB on IO thread immediately — this opens the SQLite
        // file and runs any pending migrations BEFORE MainActivity starts.
        // By the time AppViewModel calls getArticleCount(), the file is
        // already open and the query returns almost instantly.
        applicationScope.launch {
            database.openHelper.writableDatabase
        }
    }
}
