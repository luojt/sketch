package com.github.panpf.sketch.request

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.RequestDepth.NETWORK
import com.github.panpf.sketch.request.internal.ImageRequest
import com.github.panpf.sketch.request.internal.ImageResult
import com.github.panpf.sketch.stateimage.ErrorStateImage
import com.github.panpf.sketch.stateimage.StateImage
import com.github.panpf.sketch.target.ImageViewTarget
import com.github.panpf.sketch.target.Target
import com.github.panpf.sketch.target.ViewTarget
import com.github.panpf.sketch.transform.Transformation
import com.github.panpf.sketch.util.getLifecycle

interface DisplayRequest : LoadRequest {

    val memoryCacheKey: String
    val memoryCachePolicy: CachePolicy
    val disabledAnimationDrawable: Boolean?
    val placeholderImage: StateImage?
    val errorImage: ErrorStateImage?
    val target: Target?
    val lifecycle: Lifecycle?

    fun newDisplayRequestBuilder(
        configBlock: (Builder.() -> Unit)? = null
    ): Builder = Builder(this).apply {
        configBlock?.invoke(this)
    }

    fun newDisplayRequest(
        configBlock: (Builder.() -> Unit)? = null
    ): DisplayRequest = Builder(this).apply {
        configBlock?.invoke(this)
    }.build()

    companion object {
        internal const val SIZE_BY_VIEW_FIXED_SIZE: Int = -214238643

        fun new(
            uriString: String?,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = Builder(uriString).apply {
            configBlock?.invoke(this)
        }.build()

        fun new(
            uri: Uri?,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = Builder(uri ?: Uri.EMPTY).apply {
            configBlock?.invoke(this)
        }.build()

        fun newBuilder(
            uriString: String?,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = Builder(uriString).apply {
            configBlock?.invoke(this)
        }

        fun newBuilder(
            uri: Uri?,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = Builder(uri ?: Uri.EMPTY).apply {
            configBlock?.invoke(this)
        }
    }

    class Builder(private val uri: Uri) {

        private var depth: RequestDepth? = null
        private var parameters: Parameters? = null
        private var httpHeaders: Map<String, String>? = null
        private var diskCacheKey: String? = null
        private var diskCachePolicy: CachePolicy? = null
        private var resultDiskCacheKey: String? = null
        private var resultDiskCachePolicy: CachePolicy? = null
        private var maxSize: MaxSize? = null
        private var bitmapConfig: BitmapConfig? = null
        private var colorSpace: ColorSpace? = null
        private var preferQualityOverSpeed: Boolean? = null
        private var resize: Resize? = null
        private var transformations: List<Transformation>? = null
        private var disabledBitmapPool: Boolean? = null
        private var disabledCorrectExifOrientation: Boolean? = null
        private var memoryCacheKey: String? = null
        private var memoryCachePolicy: CachePolicy? = null
        private var disabledAnimationDrawable: Boolean? = null
        private var placeholderImage: StateImage? = null
        private var errorImage: ErrorStateImage? = null
        private var target: Target? = null
        private var lifecycle: Lifecycle? = null
        private var listener: Listener<ImageRequest, ImageResult, ImageResult>? = null
        private var progressListener: ProgressListener<ImageRequest>? = null

        constructor(uriString: String?) : this(
            if (uriString != null && uriString.isNotEmpty() && uriString.isNotBlank()) {
                Uri.parse(uriString)
            } else {
                Uri.EMPTY
            }
        )

        internal constructor(request: DisplayRequest) : this(request.uri) {
            this.depth = request.depth
            this.parameters = request.parameters
            this.httpHeaders = request.httpHeaders
            this.diskCacheKey = request.diskCacheKey
            this.diskCachePolicy = request.diskCachePolicy
            this.resultDiskCacheKey = request.resultDiskCacheKey
            this.resultDiskCachePolicy = request.resultDiskCachePolicy
            this.maxSize = request.maxSize
            this.bitmapConfig = request.bitmapConfig
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                this.colorSpace = request.colorSpace
            }
            this.preferQualityOverSpeed = request.preferQualityOverSpeed
            this.resize = request.resize
            this.transformations = request.transformations
            this.disabledBitmapPool = request.disabledBitmapPool
            this.disabledCorrectExifOrientation = request.disabledCorrectExifOrientation
            this.memoryCacheKey = request.memoryCacheKey
            this.memoryCachePolicy = request.memoryCachePolicy
            this.disabledAnimationDrawable = request.disabledAnimationDrawable
            this.placeholderImage = request.placeholderImage
            this.errorImage = request.errorImage
            this.target = request.target
            this.lifecycle = request.lifecycle
            this.listener = request.listener
            this.progressListener = request.progressListener
        }

        fun depth(depth: RequestDepth?): Builder = apply {
            this.depth = depth
        }

        fun parameters(parameters: Parameters?): Builder = apply {
            this.parameters = parameters
        }

        fun httpHeaders(httpHeaders: Map<String, String>?): Builder = apply {
            this.httpHeaders = httpHeaders
        }

        fun diskCacheKey(diskCacheKey: String?): Builder = apply {
            this.diskCacheKey = diskCacheKey
        }

        fun diskCachePolicy(diskCachePolicy: CachePolicy?): Builder = apply {
            this.diskCachePolicy = diskCachePolicy
        }

        fun resultDiskCacheKey(resultDiskCacheKey: String?): Builder = apply {
            this.resultDiskCacheKey = resultDiskCacheKey
        }

        fun resultDiskCachePolicy(resultDiskCachePolicy: CachePolicy?): Builder = apply {
            this.resultDiskCachePolicy = resultDiskCachePolicy
        }

        fun maxSize(maxSize: MaxSize?): Builder = apply {
            this.maxSize = maxSize
        }

        fun maxSize(width: Int, height: Int): Builder = apply {
            this.maxSize = MaxSize(width, height)
        }

        fun maxSizeByViewFixedSize(): Builder = apply {
            this.maxSize = MaxSize(SIZE_BY_VIEW_FIXED_SIZE, SIZE_BY_VIEW_FIXED_SIZE)
        }

        fun bitmapConfig(bitmapConfig: BitmapConfig?): Builder = apply {
            this.bitmapConfig = bitmapConfig
        }

        fun bitmapConfig(bitmapConfig: Bitmap.Config?): Builder = apply {
            this.bitmapConfig = if (bitmapConfig != null) BitmapConfig(bitmapConfig) else null
        }

        fun lowQualityBitmapConfig(): Builder = apply {
            this.bitmapConfig = BitmapConfig.LOW_QUALITY
        }

        fun middenQualityBitmapConfig(): Builder = apply {
            this.bitmapConfig = BitmapConfig.MIDDEN_QUALITY
        }

        fun highQualityBitmapConfig(): Builder = apply {
            this.bitmapConfig = BitmapConfig.HIGH_QUALITY
        }

        @RequiresApi(26)
        fun colorSpace(colorSpace: ColorSpace?): Builder = apply {
            this.colorSpace = colorSpace
        }

        /**
         * From Android N (API 24), this is ignored.  The output will always be high quality.
         *
         * In {@link android.os.Build.VERSION_CODES#M} and below, if
         * inPreferQualityOverSpeed is set to true, the decoder will try to
         * decode the reconstructed image to a higher quality even at the
         * expense of the decoding speed. Currently the field only affects JPEG
         * decode, in the case of which a more accurate, but slightly slower,
         * IDCT method will be used instead.
         *
         * Applied to [android.graphics.BitmapFactory.Options.inPreferQualityOverSpeed]
         */
        @Deprecated("From Android N (API 24), this is ignored.  The output will always be high quality.")
        fun preferQualityOverSpeed(inPreferQualityOverSpeed: Boolean?): Builder = apply {
            if (VERSION.SDK_INT < VERSION_CODES.N) {
                this.preferQualityOverSpeed = inPreferQualityOverSpeed
            }
        }

        fun resize(resize: Resize?): Builder = apply {
            this.resize = resize
        }

        fun resize(
            @Px width: Int,
            @Px height: Int,
            mode: Resize.Mode = Resize.Mode.EXACTLY_SAME
        ): Builder = apply {
            this.resize = Resize(width, height, mode)
        }

        fun resizeByViewFixedSize(
            mode: Resize.Mode = Resize.DEFAULT_MODE,
            scaleType: ScaleType = Resize.DEFAULT_SCALE_TYPE,
            minAspectRatio: Float = Resize.DEFAULT_MIN_ASPECT_RATIO
        ): Builder = apply {
            this.resize = Resize(
                SIZE_BY_VIEW_FIXED_SIZE,
                SIZE_BY_VIEW_FIXED_SIZE,
                mode,
                scaleType,
                minAspectRatio
            )
        }

        fun transformations(transformations: List<Transformation>?): Builder = apply {
            this.transformations = transformations
        }

        fun transformations(vararg transformations: Transformation): Builder = apply {
            this.transformations = transformations.toList()
        }

        fun disabledBitmapPool(disabledBitmapPool: Boolean? = true): Builder = apply {
            this.disabledBitmapPool = disabledBitmapPool
        }

        fun disabledCorrectExifOrientation(disabledCorrectExifOrientation: Boolean? = true): Builder =
            apply {
                this.disabledCorrectExifOrientation = disabledCorrectExifOrientation
            }

        fun memoryCacheKey(memoryCacheKey: String?): Builder = apply {
            this.memoryCacheKey = memoryCacheKey
        }

        fun memoryCachePolicy(memoryCachePolicy: CachePolicy?): Builder = apply {
            this.memoryCachePolicy = memoryCachePolicy
        }

        fun disabledAnimationDrawable(disabledAnimationDrawable: Boolean?): Builder = apply {
            this.disabledAnimationDrawable = disabledAnimationDrawable
        }

        fun placeholderImage(placeholderImage: StateImage?): Builder = apply {
            this.placeholderImage = placeholderImage
        }

        fun errorImage(errorImage: ErrorStateImage?): Builder = apply {
            this.errorImage = errorImage
        }

        fun target(target: Target?): Builder = apply {
            this.target = target
        }

        fun target(imageView: ImageView): Builder = apply {
            this.target = ImageViewTarget(imageView)
        }

        fun lifecycle(lifecycle: Lifecycle?): Builder = apply {
            this.lifecycle = lifecycle
        }

        fun listener(listener: Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error>?): Builder =
            apply {
                @Suppress("UNCHECKED_CAST")
                this.listener = listener as Listener<ImageRequest, ImageResult, ImageResult>?
            }

        /**
         * Convenience function to create and set the [Listener].
         */
        inline fun listener(
            crossinline onStart: (request: DisplayRequest) -> Unit = {},
            crossinline onCancel: (request: DisplayRequest) -> Unit = {},
            crossinline onError: (request: DisplayRequest, result: DisplayResult.Error) -> Unit = { _, _ -> },
            crossinline onSuccess: (request: DisplayRequest, result: DisplayResult.Success) -> Unit = { _, _ -> }
        ) = listener(object : Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error> {
            override fun onStart(request: DisplayRequest) = onStart(request)
            override fun onCancel(request: DisplayRequest) = onCancel(request)
            override fun onError(request: DisplayRequest, result: DisplayResult.Error) =
                onError(request, result)

            override fun onSuccess(request: DisplayRequest, result: DisplayResult.Success) =
                onSuccess(request, result)
        })

        fun progressListener(progressListener: ProgressListener<DisplayRequest>?): Builder =
            apply {
                @Suppress("UNCHECKED_CAST")
                this.progressListener = progressListener as ProgressListener<ImageRequest>?
            }

        fun build(): DisplayRequest = DisplayRequestImpl(
            uri = uri,
            _depth = depth,
            parameters = parameters,
            httpHeaders = httpHeaders,
            _diskCacheKey = diskCacheKey,
            _diskCachePolicy = diskCachePolicy,
            _resultDiskCacheKey = resultDiskCacheKey,
            _resultDiskCachePolicy = resultDiskCachePolicy,
            maxSize = maxSize,
            bitmapConfig = bitmapConfig,
            colorSpace = if (VERSION.SDK_INT >= VERSION_CODES.O) colorSpace else null,
            preferQualityOverSpeed = preferQualityOverSpeed,
            resize = resize,
            transformations = transformations,
            disabledBitmapPool = disabledBitmapPool,
            disabledCorrectExifOrientation = disabledCorrectExifOrientation,
            _memoryCacheKey = memoryCacheKey,
            _memoryCachePolicy = memoryCachePolicy,
            disabledAnimationDrawable = disabledAnimationDrawable,
            placeholderImage = placeholderImage,
            errorImage = errorImage,
            target = target,
            lifecycle = lifecycle ?: resolveLifecycle(),
            listener = listener,
            progressListener = progressListener,
        )

        private fun resolveLifecycle(): Lifecycle? {
            val target = target
            val context = if (target is ViewTarget<*>) target.view.context else null
            return context.getLifecycle()
        }
    }

    private class DisplayRequestImpl(
        override val uri: Uri,
        _depth: RequestDepth?,
        override val parameters: Parameters?,
        override val httpHeaders: Map<String, String>?,
        _diskCacheKey: String?,
        _diskCachePolicy: CachePolicy?,
        _resultDiskCacheKey: String?,
        _resultDiskCachePolicy: CachePolicy?,
        override val maxSize: MaxSize?,
        override val bitmapConfig: BitmapConfig?,
        override val colorSpace: ColorSpace?,
        override val preferQualityOverSpeed: Boolean?,
        override val resize: Resize?,
        override val transformations: List<Transformation>?,
        override val disabledBitmapPool: Boolean?,
        override val disabledCorrectExifOrientation: Boolean?,
        _memoryCacheKey: String?,
        _memoryCachePolicy: CachePolicy?,
        override val disabledAnimationDrawable: Boolean?,
        override val placeholderImage: StateImage?,
        override val errorImage: ErrorStateImage?,
        override val target: Target?,
        override val lifecycle: Lifecycle?,
        override val listener: Listener<ImageRequest, ImageResult, ImageResult>?,
        override val progressListener: ProgressListener<ImageRequest>?,
    ) : DisplayRequest {

        override val uriString: String by lazy { uri.toString() }

        override val depth: RequestDepth = _depth ?: NETWORK

        override val diskCacheKey: String = _diskCacheKey ?: uriString

        override val diskCachePolicy: CachePolicy = _diskCachePolicy ?: CachePolicy.ENABLED

        override val resultDiskCacheKey: String? by lazy {
            _resultDiskCacheKey ?: qualityKey?.let { "${uriString}_$it" }
        }

        override val resultDiskCachePolicy: CachePolicy =
            _resultDiskCachePolicy ?: CachePolicy.ENABLED

        override val memoryCachePolicy: CachePolicy = _memoryCachePolicy ?: CachePolicy.ENABLED

        private val qualityKey: String? by lazy {
            LoadRequest.newQualityKey(this)
        }

        override val memoryCacheKey: String by lazy {
            _memoryCacheKey ?: buildString {
                append(uriString)
                qualityKey?.let {
                    append("_").append(it)
                }
                if (disabledAnimationDrawable == true) {
                    append("_").append("DisabledAnimationDrawable")
                }
            }
        }

        override val key: String by lazy {
            buildString {
                append("Display")
                append("_").append(uriString)
                qualityKey?.let {
                    append("_").append(it)
                }
                if (disabledAnimationDrawable == true) {
                    append("_").append("DisabledAnimationDrawable")
                }
                parameters?.let {
                    append("_").append(it.key)
                }
                append("_").append("diskCacheKey($diskCacheKey)")
                append("_").append("diskCachePolicy($diskCachePolicy)")
                append("_").append("memoryCacheKey($memoryCacheKey)")
                append("_").append("memoryCachePolicy($memoryCachePolicy)")
            }
        }
    }
}