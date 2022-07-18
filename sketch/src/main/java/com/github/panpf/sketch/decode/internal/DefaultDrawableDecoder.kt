package com.github.panpf.sketch.decode.internal

import androidx.annotation.WorkerThread
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.CountBitmap
import com.github.panpf.sketch.datasource.DataFrom.MEMORY_CACHE
import com.github.panpf.sketch.decode.DrawableDecodeResult
import com.github.panpf.sketch.decode.DrawableDecoder
import com.github.panpf.sketch.drawable.SketchCountBitmapDrawable
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.internal.RequestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultDrawableDecoder(
    private val sketch: Sketch,
    private val request: ImageRequest,
    private val requestContext: RequestContext,
    private val fetchResult: FetchResult
) : DrawableDecoder {

    @WorkerThread
    override suspend fun decode(): DrawableDecodeResult {
        val (countBitmap, dataFrom) = safeAccessMemoryCache(sketch, request) { helper ->
            val cachedCountBitmap = helper?.read()
            if (cachedCountBitmap != null) {
                cachedCountBitmap to MEMORY_CACHE
            } else {
                val decodeResult = BitmapDecodeInterceptorChain(
                    sketch = sketch,
                    request = request,
                    requestContext = requestContext,
                    fetchResult = fetchResult,
                    interceptors = sketch.components.bitmapDecodeInterceptorList,
                    index = 0,
                ).proceed()
                val newCountBitmap = CountBitmap(
                    bitmap = decodeResult.bitmap,
                    sketch = sketch,
                    imageUri = request.uriString,
                    requestKey = request.key,
                    requestCacheKey = request.cacheKey,
                    imageInfo = decodeResult.imageInfo,
                    imageExifOrientation = decodeResult.imageExifOrientation,
                    transformedList = decodeResult.transformedList,
                )
                helper?.write(newCountBitmap)
                newCountBitmap to decodeResult.dataFrom
            }
        }

        val resources = request.context.resources
        val countDrawable = SketchCountBitmapDrawable(resources, countBitmap, dataFrom).apply {
            withContext(Dispatchers.Main) {
                requestContext.pendingCountDrawable(this@apply, "DefaultDrawableDecoder")
            }
        }
        return DrawableDecodeResult(
            drawable = countDrawable,
            imageInfo = countBitmap.imageInfo,
            imageExifOrientation = countBitmap.imageExifOrientation,
            dataFrom = dataFrom,
            transformedList = null,
        )
    }

    class Factory : DrawableDecoder.Factory {

        override fun create(
            sketch: Sketch,
            request: ImageRequest,
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): DrawableDecoder = DefaultDrawableDecoder(sketch, request, requestContext, fetchResult)

        override fun toString(): String = "DefaultDrawableDecoder"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }
    }
}