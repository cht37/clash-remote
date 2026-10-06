package com.clashremote.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clashremote.core.ClashException
import com.clashremote.core.WidgetCommand
import kotlinx.coroutines.CancellationException

class WidgetWorker @JvmOverloads constructor(
    context: Context, parameters: WorkerParameters,
    private val runtime: WidgetRuntime = WidgetRuntime.from(context),
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val ticket = WidgetTicket(inputData.getInt("widgetId", -1), inputData.getString("bindingToken") ?: return Result.failure(),
            inputData.getString("profileRevision") ?: return Result.failure(), inputData.getString("requestToken") ?: return Result.failure())
        val command = when (inputData.getString("action")) {
            "refresh" -> WidgetCommand.Refresh
            "mode" -> WidgetCommand.Mode(inputData.getString("mode") ?: return Result.failure())
            "select" -> WidgetCommand.Select(inputData.getString("group") ?: return Result.failure(),
                inputData.getString("node") ?: return Result.failure())
            else -> return Result.failure()
        }
        return try {
            val snapshot = runtime.execute(ticket, if (runAttemptCount > 0) WidgetCommand.Refresh else command)
            if (command is WidgetCommand.Select && snapshot != null) {
                val group = snapshot.proxies[command.group]
                if (group?.type != "Selector") Result.failure(workDataOf("error" to "策略组不可用，请重新选择"))
                else Result.success(workDataOf("mode" to snapshot.mode, "node" to group.now))
            } else Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = if (e is ClashException) e.message ?: "操作失败，请重试" else "操作失败，请刷新确认"
            Result.failure(workDataOf("error" to message))
        }
    }
}
