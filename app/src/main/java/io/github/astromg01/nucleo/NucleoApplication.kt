package io.github.astromg01.nucleo

import android.app.Application
import androidx.room.Room
import io.github.astromg01.nucleo.core.ai.AiResponse
import io.github.astromg01.nucleo.core.ai.LocalModelRuntime
import io.github.astromg01.nucleo.core.ai.LocalModelUnavailableException
import io.github.astromg01.nucleo.core.ai.ModelRouter
import io.github.astromg01.nucleo.core.security.SecretStore
import io.github.astromg01.nucleo.data.local.AppDatabase
import io.github.astromg01.nucleo.data.settings.ProviderPreferences
import io.github.astromg01.nucleo.domain.task.TaskCoordinator

class NucleoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "nucleo-local.db",
    ).build()

    val providerPreferences = ProviderPreferences(application)
    val secretStore = SecretStore(application)

    /** Becomes a llama.cpp / MediaPipe / other Android runtime adapter in a later milestone. */
    val localModelRuntime: LocalModelRuntime = object : LocalModelRuntime {
        override fun isReady(): Boolean = false
        override suspend fun generate(request: io.github.astromg01.nucleo.core.ai.AiRequest): AiResponse =
            throw LocalModelUnavailableException()
    }

    val modelRouter = ModelRouter(localModelRuntime, providerPreferences, secretStore)
    val taskCoordinator = TaskCoordinator(application, database)
}
