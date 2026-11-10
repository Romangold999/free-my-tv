package com.romgold.geelyyoutube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.URL

/**
 * NewPipe Extractor transport. Requests are deliberately sent through the in-app
 * ByeDPI SOCKS5 listener; there is no direct-network fallback.
 */
class ByeDpiExtractorDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
        val connection = URL(request.url()).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.requestMethod = request.httpMethod()
        request.headers().forEach { (name, values) ->
            values.forEach { value -> connection.addRequestProperty(name, value) }
        }
        val body = request.dataToSend()
        if (body != null && request.httpMethod() !in listOf("GET", "HEAD")) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val responseBody = if (stream == null || request.httpMethod() == "HEAD") "" else
                stream.use { input ->
                    val output = ByteArrayOutputStream()
                    input.copyTo(output)
                    output.toString("UTF-8")
                }
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value ?: emptyList() }
            return Response(code, connection.responseMessage, headers, responseBody, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }
}
