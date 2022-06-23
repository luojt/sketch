package com.github.panpf.sketch.fetch.internal

import com.github.panpf.sketch.fetch.HttpUriFetcher
import com.github.panpf.sketch.http.HttpStack.Response
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.internal.ProgressListenerDelegate
import com.github.panpf.sketch.util.getMimeTypeFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


@Throws(IOException::class, CancellationException::class)
internal fun writeToByteArray(
    request: ImageRequest,
    response: Response,
    coroutineScope: CoroutineScope
): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.use { out ->
        response.content.use { input ->
            copyToWithActive(
                request = request,
                inputStream = input,
                outputStream = out,
                coroutineScope = coroutineScope,
                contentLength = response.contentLength
            )
        }
    }
    return byteArrayOutputStream.toByteArray()
}

@Throws(IOException::class, CancellationException::class)
internal fun copyToWithActive(
    request: ImageRequest,
    inputStream: InputStream,
    outputStream: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    coroutineScope: CoroutineScope,
    contentLength: Long,
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = inputStream.read(buffer)
    var lastNotifyTime = 0L
    val progressListenerDelegate = request.progressListener?.let {
        ProgressListenerDelegate(coroutineScope, it)
    }
    var lastUpdateProgressBytesCopied = 0L
    while (bytes >= 0 && coroutineScope.isActive) {
        outputStream.write(buffer, 0, bytes)
        bytesCopied += bytes
        if (progressListenerDelegate != null && contentLength > 0) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastNotifyTime) > 300) {
                lastNotifyTime = currentTime
                val currentBytesCopied = bytesCopied
                lastUpdateProgressBytesCopied = currentBytesCopied
                progressListenerDelegate.onUpdateProgress(
                    request, contentLength, currentBytesCopied
                )
            }
        }
        bytes = inputStream.read(buffer)
    }
    if (coroutineScope.isActive) {
        if (progressListenerDelegate != null
            && contentLength > 0
            && bytesCopied > 0
            && lastUpdateProgressBytesCopied != bytesCopied
        ) {
            progressListenerDelegate.onUpdateProgress(request, contentLength, bytesCopied)
        }
    } else {
        throw CancellationException()
    }
    return bytesCopied
}

/**
 * Parse the response's `content-type` header.
 *
 * "text/plain" is often used as a default/fallback MIME type.
 * Attempt to guess a better MIME type from the file extension.
 */
internal fun getMimeType(url: String, contentType: String?): String? {
    if (contentType == null || contentType.startsWith(HttpUriFetcher.MIME_TYPE_TEXT_PLAIN)) {
        getMimeTypeFromUrl(url)?.let { return it }
    }
    return contentType?.substringBefore(';')
}
