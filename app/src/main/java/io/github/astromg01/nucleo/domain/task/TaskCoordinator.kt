package io.github.astromg01.nucleo.domain.task

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.astromg01.nucleo.data.local.AppDatabase
import io.github.astromg01.nucleo.data.local.TaskEntity
import io.github.astromg01.nucleo.worker.PlanningWorker

class TaskCoordinator(
    context: Context,
    private val database: AppDatabase,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun enqueuePlan(
        title: String,
        prompt: String,
        projectId: String? = null,
        consentToShareProjectContext: Boolean = false,
    ): TaskEntity {
        val task = TaskEntity(
            projectId = projectId,
            shareProjectContext = projectId != null && consentToShareProjectContext,
            title = title.trim(),
            prompt = prompt.trim(),
        )
        require(task.title.isNotBlank()) { "Dê um título para a tarefa." }
        require(task.prompt.isNotBlank()) { "Descreva o objetivo da tarefa." }
        database.taskDao().insert(task)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PlanningWorker>()
            .setInputData(workDataOf(PlanningWorker.KEY_TASK_ID to task.id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag(TAG_TASK)
            .build()
        try {
            workManager.enqueueUniqueWork("nucleo-plan-${task.id}", ExistingWorkPolicy.KEEP, request)
        } catch (error: RuntimeException) {
            // Avoid leaving an orphaned QUEUED row when WorkManager rejects the request immediately.
            database.taskDao().deleteById(task.id)
            throw IllegalStateException("Não foi possível agendar a tarefa em segundo plano.", error)
        }
        return task
    }

    companion object {
        const val TAG_TASK = "nucleo-task-plan"
    }
}
