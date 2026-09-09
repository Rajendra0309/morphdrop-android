package com.morphdrop.app.domain.usecase

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.morphdrop.app.BuildConfig
import com.morphdrop.app.domain.model.UpdateInfo
import com.morphdrop.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class UpdateCheckUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val githubApiUrl = "https://api.github.com/repos/Rajendra0309/morphdrop-android/releases/latest"
    private val checkIntervalMillis = TimeUnit.DAYS.toMillis(1)

    suspend operator fun invoke(force: Boolean = false): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val lastCheck = settingsRepository.lastUpdateCheck.first()
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCheck < checkIntervalMillis) {
                    return@withContext Result.success(UpdateInfo("", "", "", false))
                }
            }

            val connection = URL(githubApiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = Gson().fromJson(responseBody, JsonObject::class.java)
                
                val tagName = json.get("tag_name").asString.replace("v", "")
                val body = json.get("body").asString
                val assets = json.getAsJsonArray("assets")
                var downloadUrl = ""
                
                for (i in 0 until assets.size()) {
                    val asset = assets[i].asJsonObject
                    val name = asset.get("name").asString
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.get("browser_download_url").asString
                        break
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val skippedVersion = settingsRepository.skippedUpdateVersion.first()
                val isUpdateAvailable = isVersionHigher(tagName, currentVersion) && (force || tagName != skippedVersion)

                settingsRepository.setLastUpdateCheck(System.currentTimeMillis())

                Result.success(UpdateInfo(tagName, body, downloadUrl, isUpdateAvailable))
            } else {
                Result.failure(Exception("Failed to check for updates: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e("UpdateCheckUseCase", "Error checking for updates", e)
            Result.failure(e)
        }
    }

    private fun isVersionHigher(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = if (i < latestParts.size) latestParts[i] else 0
            val c = if (i < currentParts.size) currentParts[i] else 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
