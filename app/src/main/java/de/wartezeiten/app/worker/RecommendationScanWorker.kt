package de.wartezeiten.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class RecommendationScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WartezeitenRepository,
    private val preferences: PreferencesDataSource,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val language = preferences.language.first()
        return when (val result = repository.refreshParkRecommendationSnapshots(language)) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Error -> if (result.type == NetworkError.RateLimited) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
