/*
 * Copyright (C) 2019 panpf <panpfpanpf@outlook.com>
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

package com.github.panpf.sketch.decode;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.github.panpf.sketch.SLog;
import com.github.panpf.sketch.SketchCallback;
import com.github.panpf.sketch.cache.BitmapPool;
import com.github.panpf.sketch.cache.BitmapPoolUtils;
import com.github.panpf.sketch.datasource.DataSource;
import com.github.panpf.sketch.request.ErrorCause;
import com.github.panpf.sketch.request.LoadRequest;
import com.github.panpf.sketch.request.MaxSize;

public class NormalDecodeHelper extends DecodeHelper {
    private static final String NAME = "NormalDecodeHelper";

    @Override
    public boolean match(@NonNull LoadRequest request, @NonNull DataSource dataSource, @Nullable ImageType imageType,
                         @NonNull BitmapFactory.Options boundOptions) {
        return true;
    }

    @NonNull
    @Override
    public DecodeResult decode(@NonNull LoadRequest request, @NonNull DataSource dataSource, @Nullable ImageType imageType,
                               @NonNull BitmapFactory.Options boundOptions, @NonNull BitmapFactory.Options decodeOptions, int exifOrientation) throws DecodeException {

        ImageOrientationCorrector orientationCorrector = request.getConfiguration().getOrientationCorrector();
        orientationCorrector.rotateSize(boundOptions, exifOrientation);

        // Calculate inSampleSize according to max size
        MaxSize maxSize = request.getOptions().getMaxSize();
        if (maxSize != null) {
            ImageSizeCalculator sizeCalculator = request.getConfiguration().getSizeCalculator();
            boolean smallerThumbnail = sizeCalculator.canUseSmallerThumbnails(request, imageType);
            decodeOptions.inSampleSize = sizeCalculator.calculateInSampleSize(boundOptions.outWidth, boundOptions.outHeight,
                    maxSize.getWidth(), maxSize.getHeight(), smallerThumbnail);
        }

        // Set inBitmap from bitmap pool
        if (!request.getOptions().isBitmapPoolDisabled()) {
            BitmapPool bitmapPool = request.getConfiguration().getBitmapPool();
            BitmapPoolUtils.setInBitmapFromPool(decodeOptions,
                    boundOptions.outWidth, boundOptions.outHeight, boundOptions.outMimeType, bitmapPool);
        }

        Bitmap bitmap;
        try {
            bitmap = ImageDecodeUtils.decodeBitmap(dataSource, decodeOptions);
        } catch (Throwable tr) {
            Context application = request.getConfiguration().getContext();
            SketchCallback callback = request.getConfiguration().getCallback();
            BitmapPool bitmapPool = request.getConfiguration().getBitmapPool();
            if (ImageDecodeUtils.isInBitmapDecodeError(tr, decodeOptions, false)) {
                ImageDecodeUtils.recycleInBitmapOnDecodeError(callback, bitmapPool, request.getUri(),
                        boundOptions.outWidth, boundOptions.outHeight, boundOptions.outMimeType, tr, decodeOptions, false);

                try {
                    bitmap = ImageDecodeUtils.decodeBitmap(dataSource, decodeOptions);
                } catch (Throwable throwable1) {
                    SLog.emf(NAME, "onDecodeNormalImageError. " +
                                    "outWidth=%d, outHeight=%d, outMimeType=%s. " +
                                    "appMemoryInfo: maxMemory=%s, freeMemory=%s, totalMemory=%s. %s",
                            boundOptions.outWidth, boundOptions.outHeight, boundOptions.outMimeType,
                            Formatter.formatFileSize(application, Runtime.getRuntime().maxMemory()),
                            Formatter.formatFileSize(application, Runtime.getRuntime().freeMemory()),
                            Formatter.formatFileSize(application, Runtime.getRuntime().totalMemory()),
                            request.getKey());
                    callback.onError(new DecodeImageException(throwable1, request, boundOptions.outWidth,
                            boundOptions.outHeight, boundOptions.outMimeType));
                    throw new DecodeException("InBitmap retry", tr, ErrorCause.DECODE_UNKNOWN_EXCEPTION);
                }
            } else {
                SLog.emf(NAME, "onDecodeNormalImageError. " +
                                "outWidth=%d, outHeight=%d, outMimeType=%s. " +
                                "appMemoryInfo: maxMemory=%s, freeMemory=%s, totalMemory=%s. %s",
                        boundOptions.outWidth, boundOptions.outHeight, boundOptions.outMimeType,
                        Formatter.formatFileSize(application, Runtime.getRuntime().maxMemory()),
                        Formatter.formatFileSize(application, Runtime.getRuntime().freeMemory()),
                        Formatter.formatFileSize(application, Runtime.getRuntime().totalMemory()),
                        request.getKey());
                callback.onError(new DecodeImageException(tr, request, boundOptions.outWidth,
                        boundOptions.outHeight, boundOptions.outMimeType));
                throw new DecodeException(tr, ErrorCause.DECODE_UNKNOWN_EXCEPTION);
            }
        }

        // 过滤掉无效的图片
        if (bitmap == null || bitmap.isRecycled()) {
            ImageDecodeUtils.decodeError(request, dataSource, NAME, "Bitmap invalid", null);
            throw new DecodeException("Bitmap invalid", ErrorCause.DECODE_RESULT_BITMAP_INVALID);
        }

        // 过滤宽高小于等于1的图片
        if (bitmap.getWidth() <= 1 || bitmap.getHeight() <= 1) {
            String cause = String.format(Locale.US, "Bitmap width or height less than or equal to 1px. imageSize: %dx%d. bitmapSize: %dx%d",
                    boundOptions.outWidth, boundOptions.outHeight, bitmap.getWidth(), bitmap.getHeight());
            ImageDecodeUtils.decodeError(request, dataSource, NAME, cause, null);
            bitmap.recycle();
            throw new DecodeException(cause, ErrorCause.DECODE_RESULT_BITMAP_SIZE_INVALID);
        }

        TransformCacheManager transformCacheManager = request.getConfiguration().getTransformCacheManager();
        boolean processed = transformCacheManager.canUseByInSampleSize(decodeOptions.inSampleSize);

        ImageAttrs imageAttrs = new ImageAttrs(boundOptions.outMimeType, boundOptions.outWidth, boundOptions.outHeight, exifOrientation);
        BitmapDecodeResult result = new BitmapDecodeResult(imageAttrs, bitmap, dataSource.getImageFrom()).setProcessed(processed);

        try {
            correctOrientation(orientationCorrector, result, exifOrientation, request);
        } catch (CorrectOrientationException e) {
            throw new DecodeException(e, ErrorCause.DECODE_CORRECT_ORIENTATION_FAIL);
        }

        ImageDecodeUtils.decodeSuccess(bitmap, boundOptions.outWidth, boundOptions.outHeight, decodeOptions.inSampleSize, request, NAME);
        return result;
    }
}