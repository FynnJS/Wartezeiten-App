package de.wartezeiten.app.ui.update

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.remote.UpdateApiService
import de.wartezeiten.app.data.remote.dto.AppUpdateInfo
import de.wartezeiten.app.update.ApkDownloadResult
import de.wartezeiten.app.update.ApkDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(val progress: Float) : ApkDownloadState
    data class ReadyToInstall(val apkUri: Uri) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

data class UpdateUiState(
    val isLoading: Boolean = false,
    val updateAvailable: Boolean = false,
    val releaseInfo: AppUpdateInfo? = null,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val errorMessage: String? = null,
    val downloadState: ApkDownloadState = ApkDownloadState.Idle,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateApiService: UpdateApiService,
    private val apkDownloader: ApkDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun downloadUpdate(language: String) {
        val releaseInfo = _uiState.value.releaseInfo ?: return
        if (_uiState.value.downloadState is ApkDownloadState.Downloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(downloadState = ApkDownloadState.Downloading(0f)) }
            val result = apkDownloader.download(releaseInfo, language) { progress ->
                _uiState.update { state ->
                    if (state.downloadState is ApkDownloadState.Downloading) {
                        state.copy(downloadState = ApkDownloadState.Downloading(progress))
                    } else {
                        state
                    }
                }
            }
            _uiState.update {
                it.copy(
                    downloadState = when (result) {
                        is ApkDownloadResult.Success -> ApkDownloadState.ReadyToInstall(result.apkUri)
                        is ApkDownloadResult.Error -> ApkDownloadState.Failed(result.message)
                    },
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = updateApiService.fetchRelease()
                if (response.isSuccessful) {
                    val releaseInfo = response.body()
                    if (releaseInfo != null) {
                        val updateAvailable = releaseInfo.versionCode > BuildConfig.VERSION_CODE
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                updateAvailable = updateAvailable,
                                releaseInfo = releaseInfo,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Release-Metadaten konnten nicht gelesen werden."
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Release-Metadaten konnten nicht geladen werden (HTTP ${response.code()})."
                        )
                    }
                }
            } catch (exception: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Netzwerkproblem beim Update-Check."
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Fehler beim Update-Check: ${exception.message ?: "unbekannt"}."
                    )
                }
            }
        }
    }
}
