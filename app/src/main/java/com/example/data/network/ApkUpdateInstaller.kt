package com.example.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class ApkDownloadState {
    data object Idle : ApkDownloadState()

    data class Downloading(
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ApkDownloadState()

    data class ReadyToInstall(
        val apkFile: File,
        val versionTag: String,
        val needsInstallPermission: Boolean = false
    ) : ApkDownloadState()

    data class Error(
        val message: String
    ) : ApkDownloadState()
}

sealed class InstallLaunchResult {
    data object Launched : InstallLaunchResult()
    data object PermissionRequired : InstallLaunchResult()
    data class Failed(val message: String) : InstallLaunchResult()
}

object ApkUpdateInstaller {

    private const val UPDATES_SUBDIR = "updates"
    private const val APK_FILENAME = "ZS5Assist-update.apk"

    fun defaultDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun isValidApkHeader(headerBytes: ByteArray): Boolean {
        return headerBytes.size >= 2 &&
            headerBytes[0] == 0x50.toByte() &&
            headerBytes[1] == 0x4B.toByte()
    }

    fun fileProviderAuthority(context: Context): String {
        return "${context.packageName}.fileprovider"
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildInstallPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun buildInstallApkIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            fileProviderAuthority(context),
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchApkInstall(context: Context, apkFile: File): InstallLaunchResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return InstallLaunchResult.Failed("Pobrany plik APK nie istnieje lub jest pusty.")
        }
        if (!canInstallPackages(context)) {
            return InstallLaunchResult.PermissionRequired
        }
        return try {
            val intent = buildInstallApkIntent(context, apkFile)
            context.startActivity(intent)
            InstallLaunchResult.Launched
        } catch (e: Exception) {
            InstallLaunchResult.Failed(
                e.localizedMessage ?: "Nie udało się uruchomić instalatora systemu Android."
            )
        }
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        client: OkHttpClient = defaultDownloadClient(),
        onProgress: (ApkDownloadState.Downloading) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
        val targetFile = File(updatesDir, APK_FILENAME)
        val tempFile = File(updatesDir, "$APK_FILENAME.tmp")

        try {
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .header("User-Agent", "ZS5-Asystent-Ucznia-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Błąd pobierania APK (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Pusta odpowiedź serwera podczas pobierania APK")
                val totalBytes = body.contentLength()

                onProgress(
                    ApkDownloadState.Downloading(
                        progress = if (totalBytes > 0L) 0f else null,
                        downloadedBytes = 0L,
                        totalBytes = totalBytes
                    )
                )

                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastReported = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastReported >= 64 * 1024 || (totalBytes > 0L && downloaded == totalBytes)) {
                                lastReported = downloaded
                                val fraction = if (totalBytes > 0L) {
                                    (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    null
                                }
                                onProgress(
                                    ApkDownloadState.Downloading(
                                        progress = fraction,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 1024L) {
                throw IOException("Pobrany plik jest niekompletny lub uszkodzony.")
            }

            val header = ByteArray(4)
            val bytesRead = FileInputStream(tempFile).use { it.read(header) }
            if (bytesRead < 2 || !isValidApkHeader(header)) {
                throw IOException("Pobrany plik nie jest prawidłowym pakietem APK.")
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    fun formatBytesMb(bytes: Long): String {
        if (bytes <= 0L) return "0.0 MB"
        val mb = bytes / (1024f * 1024f)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
