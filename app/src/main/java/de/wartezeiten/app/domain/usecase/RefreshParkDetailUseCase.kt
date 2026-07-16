package de.wartezeiten.app.domain.usecase

import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import javax.inject.Inject

class RefreshParkDetailUseCase @Inject constructor(
    private val repository: WartezeitenRepository
) {
    suspend operator fun invoke(parkKey: String, language: String, forceRefresh: Boolean = false): ApiResult<Unit> {
        return repository.refreshParkDetail(parkKey, language, forceRefresh)
    }
}
