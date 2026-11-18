package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String
)

sealed class UpdateCheckResult {
    data class UpToDate(
        val localVersion: String,
        val release: GitHubReleaseInfo
    ) : UpdateCheckResult()

    data class UpdateAvailable(
        val localVersion: String,
        val release: GitHubReleaseInfo
    ) : UpdateCheckResult()

    data class NoReleasesPublished(
        val localVersion: String,
        val repoUrl: String
    ) : UpdateCheckResult()

    data class Error(
        val localVersion: String,
        val message: String
    ) : UpdateCheckResult()
}

class GitHubReleaseChecker(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        const val REPO_OWNER = "dniesobski"
        const val REPO_NAME = "ZS5-Assistant"
        const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
        const val RELEASES_WEB_URL = "$REPO_URL/releases"
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val RELEASES_LIST_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=1"
        private const val TAGS_LIST_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/tags?per_page=1"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        fun normalizeVersion(rawTag: String): String {
            return rawTag.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
                .trim()
        }

        /**
         * Returns > 0 if remote > local, 0 if equal, < 0 if remote < local.
         */
        fun compareVersions(localRaw: String, remoteRaw: String): Int {
            val localParts = normalizeVersion(localRaw)
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val remoteParts = normalizeVersion(remoteRaw)
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

            val maxLen = maxOf(localParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val l = localParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }
            return 0
        }

        fun parseReleaseJson(jsonText: String): GitHubReleaseInfo {
            val obj = JSONObject(jsonText)
            val tagName = obj.optString("tag_name", "").ifBlank { obj.optString("name", "") }
            val normalized = normalizeVersion(tagName)
            val releaseName = obj.optString("name", "").ifBlank { tagName }
            val body = obj.optString("body", "").trim()
            val htmlUrl = obj.optString("html_url", RELEASES_WEB_URL).ifBlank { RELEASES_WEB_URL }
            val publishedAt = obj.optString("published_at", "")

            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }

            return GitHubReleaseInfo(
                tagName = tagName,
                normalizedVersion = normalized,
                releaseName = releaseName,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                publishedAt = publishedAt
            )
        }
    }

    suspend fun checkLatestRelease(localVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val latestResponse = executeGet(LATEST_RELEASE_API)
            val releaseInfo: GitHubReleaseInfo? = when (latestResponse.first) {
                200 -> parseReleaseJson(latestResponse.second)
                404 -> {
                    // Fallback to all releases (e.g. prerelease) or tags
                    val listResp = executeGet(RELEASES_LIST_API)
                    if (listResp.first == 200) {
                        val arr = JSONArray(listResp.second)
                        if (arr.length() > 0) {
                            parseReleaseJson(arr.getJSONObject(0).toString())
                        } else {
                            fetchFromTagsFallback()
                        }
                    } else {
                        fetchFromTagsFallback()
                    }
                }
                else -> {
                    throw IOException("GitHub API zwróciło kod ${latestResponse.first}")
                }
            }

            if (releaseInfo == null || releaseInfo.normalizedVersion.isBlank()) {
                return@withContext UpdateCheckResult.NoReleasesPublished(
                    localVersion = localVersion,
                    repoUrl = RELEASES_WEB_URL
                )
            }

            val cmp = compareVersions(localVersion, releaseInfo.normalizedVersion)
            if (cmp > 0) {
                UpdateCheckResult.UpdateAvailable(
                    localVersion = localVersion,
                    release = releaseInfo
                )
            } else {
                UpdateCheckResult.UpToDate(
                    localVersion = localVersion,
                    release = releaseInfo
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                localVersion = localVersion,
                message = e.localizedMessage ?: "Nie udało się połączyć z GitHub"
            )
        }
    }

    private fun fetchFromTagsFallback(): GitHubReleaseInfo? {
        val tagsResp = executeGet(TAGS_LIST_API)
        if (tagsResp.first != 200) return null
        val arr = JSONArray(tagsResp.second)
        if (arr.length() == 0) return null
        val tagObj = arr.getJSONObject(0)
        val tagName = tagObj.optString("name", "")
        if (tagName.isBlank()) return null
        return GitHubReleaseInfo(
            tagName = tagName,
            normalizedVersion = normalizeVersion(tagName),
            releaseName = tagName,
            releaseNotes = "",
            htmlUrl = "$REPO_URL/releases/tag/$tagName",
            apkDownloadUrl = null,
            publishedAt = ""
        )
    }

    private fun executeGet(url: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZS5-Asystent-Ucznia-Android")
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return resp.code to body
        }
    }
}
