@file:Suppress("DEPRECATION")

/*
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.panpf.sketch.decode

import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Bitmap.Config.RGB_565
import android.graphics.Movie
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.datasource.BasedStreamDataSource
import com.github.panpf.sketch.decode.internal.ImageFormat
import com.github.panpf.sketch.decode.internal.isGif
import com.github.panpf.sketch.drawable.MovieDrawable
import com.github.panpf.sketch.drawable.SketchAnimatableDrawable
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ANIMATION_REPEAT_INFINITE
import com.github.panpf.sketch.request.animatable2CompatCallbackOf
import com.github.panpf.sketch.request.animatedTransformation
import com.github.panpf.sketch.request.animationEndCallback
import com.github.panpf.sketch.request.animationStartCallback
import com.github.panpf.sketch.request.internal.RequestContext
import com.github.panpf.sketch.request.repeatCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adds gif support by Movie
 */
@RequiresApi(Build.VERSION_CODES.KITKAT)
fun ComponentRegistry.Builder.supportMovieGif(): ComponentRegistry.Builder = apply {
    addDrawableDecoder(GifMovieDrawableDecoder.Factory())
}

/**
 * A [DrawableDecoder] that uses [Movie] to decode GIFs.
 *
 * Only the following attributes are supported:
 *
 * repeatCount
 *
 * animatedTransformation
 *
 * onAnimationStart
 *
 * onAnimationEnd
 *
 * disallowReuseBitmap
 */
@RequiresApi(Build.VERSION_CODES.KITKAT)
class GifMovieDrawableDecoder(
    private val requestContext: RequestContext,
    private val dataSource: BasedStreamDataSource,
) : DrawableDecoder {

    @WorkerThread
    override suspend fun decode(): Result<DrawableDecodeResult> = kotlin.runCatching {
        val request = requestContext.request
        val movie: Movie? = dataSource.newInputStream().buffered().use { Movie.decodeStream(it) }

        val width = movie?.width() ?: 0
        val height = movie?.height() ?: 0
        check(movie != null && width > 0 && height > 0) { "Failed to decode GIF." }

        val config = if (movie.isOpaque && request.bitmapConfig?.isLowQuality == true)
            RGB_565 else ARGB_8888
        val movieDrawable = MovieDrawable(movie, config).apply {
            setRepeatCount(request.repeatCount ?: ANIMATION_REPEAT_INFINITE)

            // Set the animated transformation to be applied on each frame.
            setAnimatedTransformation(request.animatedTransformation)
        }

        val imageInfo =
            ImageInfo(width, height, ImageFormat.GIF.mimeType, ExifInterface.ORIENTATION_UNDEFINED)

        val animatableDrawable = SketchAnimatableDrawable(
            animatableDrawable = movieDrawable,
            imageUri = request.uriString,
            requestKey = requestContext.key,
            requestCacheKey = requestContext.cacheKey,
            imageInfo = imageInfo,
            dataFrom = dataSource.dataFrom,
            transformedList = null,
            extras = null,
        ).apply {
            // Set the start and end animation callbacks if any one is supplied through the request.
            val onStart = request.animationStartCallback
            val onEnd = request.animationEndCallback
            if (onStart != null || onEnd != null) {
                withContext(Dispatchers.Main) {
                    registerAnimationCallback(animatable2CompatCallbackOf(onStart, onEnd))
                }
            }
        }

        DrawableDecodeResult(
            drawable = animatableDrawable,
            imageInfo = animatableDrawable.imageInfo,
            dataFrom = animatableDrawable.dataFrom,
            transformedList = animatableDrawable.transformedList,
            extras = animatableDrawable.extras,
        )
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    class Factory : DrawableDecoder.Factory {

        override fun create(
            sketch: Sketch,
            requestContext: RequestContext,
            fetchResult: FetchResult
        ): GifMovieDrawableDecoder? {
            val dataSource = fetchResult.dataSource
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && !requestContext.request.disallowAnimatedImage
                && dataSource is BasedStreamDataSource
            ) {
                val imageFormat = ImageFormat.parseMimeType(fetchResult.mimeType)
                val isGif =
                    if (imageFormat == null) fetchResult.headerBytes.isGif() else imageFormat == ImageFormat.GIF
                if (isGif) {
                    return GifMovieDrawableDecoder(requestContext, dataSource)
                }
            }
            return null
        }

        override fun toString(): String = "GifMovieDrawableDecoder"

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
