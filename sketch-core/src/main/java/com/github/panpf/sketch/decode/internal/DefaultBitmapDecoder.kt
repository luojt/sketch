package com.github.panpf.sketch.decode.internal

import android.graphics.Bitmap
import android.graphics.Rect
import com.github.panpf.sketch.ImageType
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.datasource.DataSource
import com.github.panpf.sketch.decode.BitmapDecoder
import com.github.panpf.sketch.decode.DecodeConfig
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.request.LoadRequest
import com.github.panpf.sketch.util.supportBitmapRegionDecoder

open class DefaultBitmapDecoder(
    sketch: Sketch,
    request: LoadRequest,
    dataSource: DataSource
) : AbsBitmapDecoder(sketch, request, dataSource) {

    companion object {
        const val MODULE = "DefaultBitmapDecoder"
    }

    override fun readImageInfo(): ImageInfo = dataSource.readImageInfo(request)

    override fun canDecodeRegion(imageInfo: ImageInfo, imageType: ImageType?): Boolean =
        imageType?.supportBitmapRegionDecoder() == true

    override fun decodeRegion(
        imageInfo: ImageInfo,
        srcRect: Rect,
        decodeConfig: DecodeConfig
    ): Bitmap {
        val decodeOptions = decodeConfig.toBitmapOptions()
        if (request.disabledBitmapPool != true) {
            // todo 这里的宽高，貌似有问题，需要验证一下
            bitmapPoolHelper.setInBitmapForRegionDecoder(
                width = srcRect.width(),
                height = srcRect.height(),
                options = decodeOptions,
            )
        }

        val bitmap = try {
            dataSource.decodeRegionBitmap(srcRect, decodeOptions)
        } catch (throwable: Throwable) {
            val inBitmap = decodeOptions.inBitmap
            when {
                inBitmap != null && isInBitmapError(throwable, true) -> {
                    val message =
                        "Bitmap region decode error. Because inBitmap. uri=${request.uriString}"
                    logger.e(MODULE, throwable, message)

                    decodeOptions.inBitmap = null
                    bitmapPoolHelper.freeBitmapToPool(inBitmap)
                    try {
                        dataSource.decodeRegionBitmap(srcRect, decodeOptions)
                    } catch (throwable2: Throwable) {
                        throw BitmapDecodeException(
                            request,
                            "Bitmap region decode error. uri=${request.uriString}",
                            throwable2
                        )
                    }
                }
                isSrcRectError(throwable, imageInfo.width, imageInfo.height, srcRect) -> {
                    throw BitmapDecodeException(
                        request,
                        "Bitmap region decode error. Because srcRect. imageInfo=${imageInfo}, resize=${request.resize}, srcRect=${srcRect}, uri=${request.uriString}",
                        throwable
                    )
                }
                else -> {
                    throw BitmapDecodeException(
                        request,
                        "Bitmap region decode error. uri=${request.uriString}",
                        throwable
                    )
                }
            }
        } ?: throw BitmapDecodeException(
            request, "Bitmap region decode return null. uri=${request.uriString}"
        )
        if (bitmap.width <= 1 || bitmap.height <= 1) {
            bitmap.recycle()
            throw BitmapDecodeException(
                request,
                "Invalid image size. size=${bitmap.width}x${bitmap.height}, uri=${request.uriString}"
            )
        }
        return bitmap
    }

    override fun decode(imageInfo: ImageInfo, decodeConfig: DecodeConfig): Bitmap {
        val decodeOptions = decodeConfig.toBitmapOptions()
        // Set inBitmap from bitmap pool
        if (request.disabledBitmapPool != true) {
            bitmapPoolHelper.setInBitmap(
                decodeOptions, imageInfo.width, imageInfo.height, imageInfo.mimeType
            )
        }

        val bitmap: Bitmap = try {
            dataSource.decodeBitmap(decodeOptions)
        } catch (throwable: Throwable) {
            val inBitmap = decodeOptions.inBitmap
            if (inBitmap != null && isInBitmapError(throwable, false)) {
                val message = "Bitmap decode error. Because inBitmap. uri=%s"
                    .format(request.uriString)
                logger.e(MODULE, throwable, message)

                decodeOptions.inBitmap = null
                bitmapPoolHelper.freeBitmapToPool(inBitmap)
                try {
                    dataSource.decodeBitmap(decodeOptions)
                } catch (throwable2: Throwable) {
                    throw BitmapDecodeException(
                        request,
                        "Bitmap decode error. uri=%s".format(request.uriString),
                        throwable2
                    )
                }
            } else {
                throw BitmapDecodeException(
                    request,
                    "Bitmap decode error. uri=%s".format(request.uriString),
                    throwable
                )
            }
        } ?: throw BitmapDecodeException(
            request, "Bitmap decode return null. uri=%s".format(request.uriString)
        )
        if (bitmap.width <= 1 || bitmap.height <= 1) {
            bitmap.recycle()
            throw BitmapDecodeException(
                request,
                "Invalid image size. size=%dx%d, uri=%s"
                    .format(bitmap.width, bitmap.height, request.uriString)
            )
        }
        return bitmap
    }

    override fun close() {

    }

    class Factory : BitmapDecoder.Factory {

        override fun create(
            sketch: Sketch,
            request: LoadRequest,
            dataSource: DataSource
        ): BitmapDecoder = DefaultBitmapDecoder(sketch, request, dataSource)
    }
}