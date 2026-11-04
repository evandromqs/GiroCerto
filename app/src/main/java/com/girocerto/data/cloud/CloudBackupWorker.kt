package com.girocerto.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.girocerto.app.GiroCertoApp

class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GiroCertoApp
        return if (app.cloudBackup.upload().isSuccess) Result.success() else Result.retry()
    }
}
