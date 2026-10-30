package com.husarp.holdfast.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.husarp.holdfast.HoldfastApplication

class HistoryCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as HoldfastApplication).container.repository.cleanup(System.currentTimeMillis())
        (applicationContext as HoldfastApplication).container.repository.checkSpam(System.currentTimeMillis())
        return Result.success()
    }
}
