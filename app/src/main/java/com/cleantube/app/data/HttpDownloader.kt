package com.cleantube.app.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** NewPipeExtractor icin HTTP katmani. Cerez/oturum bilgisi BURADAN GECMEZ. */
class HttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody(null)
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val rb = okhttp3.Request.Builder().url(request.url()).method(method, body)
        rb.header("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            rb.removeHeader(name)
            for (v in values) rb.addHeader(name, v)
        }
        client.newCall(rb.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
