package com.github.panpf.sketch.decode.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.exifinterface.media.ExifInterface
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.BitmapPool
import com.github.panpf.sketch.decode.BitmapDecodeResult
import com.github.panpf.sketch.decode.BitmapDecoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.Resize
import com.github.panpf.sketch.request.LoadRequest
import com.github.panpf.sketch.util.Size

abstract class AbsBitmapDecoder(
    protected val sketch: Sketch,
    protected val request: LoadRequest,
) : BitmapDecoder {

    abstract suspend fun executeDecode(): BitmapDecodeResult

    final override suspend fun decode(): BitmapDecodeResult =
        applyResize(applyExifOrientation(executeDecode()))

    // todo Blend the ExifOrientation and Resize
    private fun applyExifOrientation(bitmapResult: BitmapDecodeResult): BitmapDecodeResult {
        val exifOrientationHelper = if (request.ignoreExifOrientation != true) {
            ExifOrientationHelper(bitmapResult.exifOrientation)
        } else {
            ExifOrientationHelper(ExifInterface.ORIENTATION_UNDEFINED)
        }
        val bitmapPool = sketch.bitmapPool
        val bitmap = bitmapResult.bitmap
        val newBitmap = exifOrientationHelper.applyToBitmap(bitmap, bitmapPool)
        return if (newBitmap != null) {
            bitmapPool.free(bitmap)
            bitmapResult.new(newBitmap) {
                addTransformed(ExifOrientationTransformed(exifOrientationHelper.exifOrientation))
                val newSize = exifOrientationHelper.applyToSize(
                    Size(bitmapResult.imageInfo.width, bitmapResult.imageInfo.height)
                )
                imageInfo(ImageInfo(newSize.width, newSize.height, bitmapResult.imageInfo.mimeType))
            }
        } else {
            bitmapResult
        }
    }

    private fun applyResize(bitmapResult: BitmapDecodeResult): BitmapDecodeResult {
        val bitmap = bitmapResult.bitmap
        val resize = request.resize
//        val maxSize = chain.request.maxSize
        val bitmapPool = sketch.bitmapPool
        return if (resize?.shouldUse(bitmap.width, bitmap.height) == true) {
            val newBitmap = resize(bitmap, resize, bitmapPool)
            bitmapPool.free(bitmap)
            bitmapResult.new(newBitmap) {
                addTransformed(ResizeTransformed(resize))
            }
//        } else if(maxSize != null && bitmap.width * bitmap.height > maxSize.width * maxSize.height){
            // todo 限制不超过 maxSize
        } else {
            bitmapResult
        }
    }

    private fun resize(bitmap: Bitmap, resize: Resize, bitmapPool: BitmapPool): Bitmap {
        val mapping = ResizeMapping.calculator(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            resizeWidth = resize.width,
            resizeHeight = resize.height,
            resizeScale = resize.scale,
            exactlySize = resize.precision == Resize.Precision.EXACTLY
        )
        // todo 限制不超过 maxSize
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val resizeBitmap =
            bitmapPool.getOrCreate(mapping.newWidth, mapping.newHeight, config)
        val canvas = Canvas(resizeBitmap)
        canvas.drawBitmap(bitmap, mapping.srcRect, mapping.destRect, null)
        return resizeBitmap
    }
}