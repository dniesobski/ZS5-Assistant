package com.example.data.network

import com.example.model.ParsedPlan
import com.example.model.PlanEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class TimetableRemoteDataSource(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        const val BASE_URL = "http://zs5rybnik.pl/_plan/"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    suspend fun fetchIndex(): Result<List<PlanEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val listHtml = fetchHtml("${BASE_URL}lista.html")
            var entries = OptivumHtmlParser.parseIndex(listHtml)
            if (entries.isEmpty()) {
                val indexHtml = fetchHtml("${BASE_URL}index.html")
                val frameHref = OptivumHtmlParser.extractListFrameHref(indexHtml)
                if (frameHref != null) {
                    val resolvedUrl = resolveUrl(frameHref)
                    entries = OptivumHtmlParser.parseIndex(fetchHtml(resolvedUrl))
                } else {
                    entries = OptivumHtmlParser.parseIndex(indexHtml)
                }
            }
            if (entries.isEmpty()) {
                throw IOException("Nie znaleziono listy oddziałów, nauczycieli ani sal pod adresem zs5rybnik.pl/_plan.")
            }
            entries
        }
    }

    suspend fun fetchPlan(entry: PlanEntry): Result<ParsedPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val url = resolveUrl(entry.href)
            val html = fetchHtml(url)
            OptivumHtmlParser.parsePlanPage(html, entry)
        }
    }

    suspend fun fetchPlansBatch(
        entries: List<PlanEntry>,
        concurrency: Int = 6
    ): List<ParsedPlan> = coroutineScope {
        val semaphore = Semaphore(concurrency)
        entries.map { entry ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchPlan(entry).getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun resolveUrl(href: String): String {
        val clean = href.trim().removePrefix("./").removePrefix("/")
        return "$BASE_URL$clean"
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ZS5RybnikPlanApp/1.0 (Android)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Błąd HTTP ${response.code} podczas pobierania $url")
            }
            val bodyBytes = response.body?.bytes()
                ?: throw IOException("Pusta odpowiedź serwera dla $url")
            val contentType = response.header("Content-Type")
            return OptivumHtmlParser.decodeHtmlBytes(bodyBytes, contentType)
        }
    }
}
