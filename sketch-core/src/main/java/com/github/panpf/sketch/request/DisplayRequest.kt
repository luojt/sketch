package com.github.panpf.sketch.request

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.decode.BitmapConfig
import com.github.panpf.sketch.decode.MaxSize
import com.github.panpf.sketch.decode.Resize
import com.github.panpf.sketch.decode.transform.Transformation
import com.github.panpf.sketch.request.RequestDepth.NETWORK
import com.github.panpf.sketch.request.internal.CombinedListener
import com.github.panpf.sketch.request.internal.CombinedProgressListener
import com.github.panpf.sketch.request.internal.ImageRequest
import com.github.panpf.sketch.request.internal.ImageResult
import com.github.panpf.sketch.stateimage.ErrorStateImage
import com.github.panpf.sketch.stateimage.StateImage
import com.github.panpf.sketch.target.DisplayOptionsProvider
import com.github.panpf.sketch.target.ImageViewTarget
import com.github.panpf.sketch.target.ListenerProvider
import com.github.panpf.sketch.target.Target
import com.github.panpf.sketch.target.ViewTarget
import com.github.panpf.sketch.util.asOrNull
import com.github.panpf.sketch.util.getLifecycle

//fun DisplayRequest(
//    uriString: String?,
//    target: Target,
//    configBlock: (Builder.() -> Unit)? = null
//): DisplayRequest = Builder(uriString, target).apply {
//    configBlock?.invoke(this)
//}.build()
//
//fun DisplayRequest(
//    uriString: String?,
//    imageView: ImageView,
//    configBlock: (Builder.() -> Unit)? = null
//): DisplayRequest = Builder(uriString, ImageViewTarget(imageView)).apply {
//    configBlock?.invoke(this)
//}.build()
//
//fun DisplayRequestBuilder(
//    uriString: String?,
//    target: Target,
//    configBlock: (Builder.() -> Unit)? = null
//): DisplayRequest.Builder = Builder(uriString, target).apply {
//    configBlock?.invoke(this)
//}
//
//fun DisplayRequestBuilder(
//    uriString: String?,
//    imageView: ImageView,
//    configBlock: (Builder.() -> Unit)? = null
//): DisplayRequest.Builder = Builder(uriString, ImageViewTarget(imageView)).apply {
//    configBlock?.invoke(this)
//}

interface DisplayRequest : LoadRequest {

    val target: Target
    val lifecycle: Lifecycle?

    val disabledAnimationDrawable: Boolean?
    val bitmapMemoryCachePolicy: CachePolicy
    val placeholderImage: StateImage?
    val errorImage: StateImage?

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
            target: Target,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = Builder(uriString, target).apply {
            configBlock?.invoke(this)
        }.build()

        fun new(
            uriString: String?,
            imageView: ImageView,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = new(uriString, ImageViewTarget(imageView), configBlock)

        fun newBuilder(
            uriString: String?,
            target: Target,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = Builder(uriString, target).apply {
            configBlock?.invoke(this)
        }

        fun newBuilder(
            uriString: String?,
            imageView: ImageView,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = newBuilder(uriString, ImageViewTarget(imageView), configBlock)

        fun new(
            uri: Uri?,
            target: Target,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = Builder(uri, target).apply {
            configBlock?.invoke(this)
        }.build()

        fun new(
            uri: Uri?,
            imageView: ImageView,
            configBlock: (Builder.() -> Unit)? = null
        ): DisplayRequest = new(uri, ImageViewTarget(imageView), configBlock)

        fun newBuilder(
            uri: Uri?,
            target: Target,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = Builder(uri, target).apply {
            configBlock?.invoke(this)
        }

        fun newBuilder(
            uri: Uri?,
            imageView: ImageView,
            configBlock: (Builder.() -> Unit)? = null
        ): Builder = newBuilder(uri, ImageViewTarget(imageView), configBlock)
    }

    class Builder {
        private val uri: Uri
        private val target: Target

        private var depth: RequestDepth? = null
        private var parametersBuilder: Parameters.Builder? = null
        private var listener: Listener<ImageRequest, ImageResult, ImageResult>? = null

        private var httpHeaders: MutableMap<String, String>? = null
        private var networkContentDiskCachePolicy: CachePolicy? = null
        private var progressListener: ProgressListener<ImageRequest>? = null

        private var maxSize: MaxSize? = null
        private var bitmapConfig: BitmapConfig? = null

        @RequiresApi(VERSION_CODES.O)
        private var colorSpace: ColorSpace? = null
        private var preferQualityOverSpeed: Boolean? = null
        private var resize: Resize? = null
        private var transformations: List<Transformation>? = null
        private var disabledBitmapPool: Boolean? = null
        private var disabledCorrectExifOrientation: Boolean? = null
        private var bitmapResultDiskCachePolicy: CachePolicy? = null

        private var bitmapMemoryCachePolicy: CachePolicy? = null
        private var disabledAnimationDrawable: Boolean? = null
        private var placeholderImage: StateImage? = null
        private var errorImage: StateImage? = null
        private var lifecycle: Lifecycle? = null

        constructor(uri: Uri?, target: Target) {
            this.uri = uri ?: Uri.EMPTY
            this.target = target
            target.asOrNull<ViewTarget<*>>()
                ?.view.asOrNull<DisplayOptionsProvider>()
                ?.displayOptions
                ?.let {
                    options(it)
                }
        }

        constructor(uriString: String?, target: Target) : this(
            if (uriString != null && uriString.isNotEmpty() && uriString.isNotBlank()) {
                Uri.parse(uriString)
            } else {
                Uri.EMPTY
            },
            target
        )

        internal constructor(request: DisplayRequest) {
            this.uri = request.uri
            this.target = request.target
            this.depth = request.depth
            this.parametersBuilder = request.parameters?.newBuilder()
            this.listener = request.listener
            this.httpHeaders = request.httpHeaders?.toMutableMap()
            this.networkContentDiskCachePolicy = request.networkContentDiskCachePolicy
            this.progressListener = request.progressListener
            this.maxSize = request.maxSize
            this.bitmapConfig = request.bitmapConfig
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                this.colorSpace = request.colorSpace
            }
            @Suppress("DEPRECATION")
            this.preferQualityOverSpeed = request.preferQualityOverSpeed
            this.resize = request.resize
            this.transformations = request.transformations
            this.disabledBitmapPool = request.disabledBitmapPool
            this.disabledCorrectExifOrientation = request.disabledCorrectExifOrientation
            this.bitmapResultDiskCachePolicy = request.bitmapResultDiskCachePolicy
            this.bitmapMemoryCachePolicy = request.bitmapMemoryCachePolicy
            this.disabledAnimationDrawable = request.disabledAnimationDrawable
            this.placeholderImage = request.placeholderImage
            this.errorImage = request.errorImage
            this.lifecycle = request.lifecycle
        }

        fun options(options: DisplayOptions): Builder = apply {
            options.depth?.let {
                this.depth = it
            }
            options.parameters?.let {
                it.forEach { entry ->
                    setParameter(entry.first, entry.second.value, entry.second.cacheKey)
                }
            }
            options.httpHeaders?.let {
                it.forEach { entry ->
                    setHttpHeader(entry.key, entry.value)
                }
            }
            options.networkContentDiskCachePolicy?.let {
                this.networkContentDiskCachePolicy = it
            }

            options.maxSize?.let {
                this.maxSize = it
            }
            options.bitmapConfig?.let {
                this.bitmapConfig = it
            }
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                options.colorSpace?.let {
                    this.colorSpace = it
                }
            }
            @Suppress("DEPRECATION")
            options.preferQualityOverSpeed?.let {
                this.preferQualityOverSpeed = it
            }
            options.resize?.let {
                this.resize = it
            }
            options.transformations?.let {
                transformations(it)
            }
            options.disabledBitmapPool?.let {
                this.disabledBitmapPool = it
            }
            options.bitmapResultDiskCachePolicy?.let {
                this.bitmapResultDiskCachePolicy = it
            }
            options.bitmapResultDiskCachePolicy?.let {
                this.bitmapResultDiskCachePolicy = it
            }

            options.disabledAnimationDrawable?.let {
                this.disabledAnimationDrawable = it
            }
            options.bitmapMemoryCachePolicy?.let {
                this.bitmapMemoryCachePolicy = it
            }
            options.placeholderImage?.let {
                this.placeholderImage = it
            }
            options.errorImage?.let {
                this.errorImage = it
            }
        }

        fun depth(depth: RequestDepth?): Builder = apply {
            this.depth = depth
        }

        fun depthFrom(from: String?): Builder = apply {
            if (from != null) {
                setParameter(ImageRequest.REQUEST_DEPTH_FROM, from, null)
            } else {
                removeParameter(ImageRequest.REQUEST_DEPTH_FROM)
            }
        }

        fun parameters(parameters: Parameters?): Builder = apply {
            this.parametersBuilder = parameters?.newBuilder()
        }

        /**
         * Set a parameter for this request.
         *
         * @see Parameters.Builder.set
         */
        @JvmOverloads
        fun setParameter(key: String, value: Any?, cacheKey: String? = value?.toString()): Builder =
            apply {
                this.parametersBuilder = (this.parametersBuilder ?: Parameters.Builder()).apply {
                    set(
                        key,
                        value,
                        cacheKey
                    )
                }
            }

        /**
         * Remove a parameter from this request.
         *
         * @see Parameters.Builder.remove
         */
        fun removeParameter(key: String): Builder = apply {
            this.parametersBuilder?.remove(key)
        }

        fun httpHeaders(httpHeaders: Map<String, String>?): Builder = apply {
            this.httpHeaders = httpHeaders?.toMutableMap()
        }

        /**
         * Add a header for any network operations performed by this request.
         */
        fun addHttpHeader(name: String, value: String): Builder = apply {
            this.httpHeaders = (this.httpHeaders ?: HashMap()).apply {
                put(name, value)
            }
        }

        /**
         * Set a header for any network operations performed by this request.
         */
        fun setHttpHeader(name: String, value: String): Builder = apply {
            this.httpHeaders = (this.httpHeaders ?: HashMap()).apply {
                set(name, value)
            }
        }

        /**
         * Remove all network headers with the key [name].
         */
        fun removeHttpHeader(name: String): Builder = apply {
            this.httpHeaders?.remove(name)
        }

        fun networkContentDiskCachePolicy(networkContentDiskCachePolicy: CachePolicy?): Builder =
            apply {
                this.networkContentDiskCachePolicy = networkContentDiskCachePolicy
            }

        fun bitmapResultDiskCachePolicy(bitmapResultDiskCachePolicy: CachePolicy?): Builder =
            apply {
                this.bitmapResultDiskCachePolicy = bitmapResultDiskCachePolicy
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

        @RequiresApi(VERSION_CODES.O)
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

        fun bitmapMemoryCachePolicy(bitmapMemoryCachePolicy: CachePolicy?): Builder = apply {
            this.bitmapMemoryCachePolicy = bitmapMemoryCachePolicy
        }

        fun disabledAnimationDrawable(disabledAnimationDrawable: Boolean? = true): Builder = apply {
            this.disabledAnimationDrawable = disabledAnimationDrawable
        }

        fun placeholderImage(placeholderImage: StateImage?): Builder = apply {
            this.placeholderImage = placeholderImage
        }

        fun placeholderImage(placeholderDrawable: Drawable?): Builder = apply {
            this.placeholderImage =
                if (placeholderDrawable != null) StateImage.drawable(placeholderDrawable) else null
        }

        fun placeholderImage(@DrawableRes placeholderDrawableResId: Int?): Builder = apply {
            this.placeholderImage = if (placeholderDrawableResId != null) {
                StateImage.drawableRes(placeholderDrawableResId)
            } else null
        }

        fun errorImage(
            errorImage: StateImage?,
            configBlock: (ErrorStateImage.Builder.() -> Unit)? = null
        ): Builder = apply {
            this.errorImage = errorImage?.let {
                if (configBlock != null) {
                    ErrorStateImage.new(it, configBlock)
                } else {
                    it
                }
            }
        }

        fun errorImage(
            errorDrawable: Drawable?,
            configBlock: (ErrorStateImage.Builder.() -> Unit)? = null
        ): Builder = apply {
            this.errorImage = errorDrawable?.let {
                if (configBlock != null) {
                    ErrorStateImage.new(StateImage.drawable(it), configBlock)
                } else {
                    StateImage.drawable(it)
                }
            }
        }

        fun errorImage(
            errorDrawableResId: Int?,
            configBlock: (ErrorStateImage.Builder.() -> Unit)? = null
        ): Builder = apply {
            this.errorImage = errorDrawableResId?.let {
                if (configBlock != null) {
                    ErrorStateImage.new(StateImage.drawableRes(it), configBlock)
                } else {
                    StateImage.drawableRes(it)
                }
            }
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
        ): Builder =
            listener(object : Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error> {
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

        fun build(): DisplayRequest {
            val target = target
            val listener = listener
            val progressListener = progressListener
            val viewListenerProvider =
                target.asOrNull<ViewTarget<*>>()?.view?.asOrNull<ListenerProvider>()
            @Suppress("UNCHECKED_CAST") val viewListener =
                viewListenerProvider?.getListener() as Listener<ImageRequest, ImageResult, ImageResult>?
            @Suppress("UNCHECKED_CAST") val viewProgressListener =
                viewListenerProvider?.getProgressListener() as ProgressListener<ImageRequest>?
            val finalListener = if (listener != null && viewListener != null) {
                CombinedListener(listOf(listener, viewListener))
            } else {
                listener ?: viewListener
            }
            val finalProgressListener =
                if (progressListener != null && viewProgressListener != null) {
                    CombinedProgressListener(listOf(progressListener, viewProgressListener))
                } else {
                    progressListener ?: viewProgressListener
                }
            return if (VERSION.SDK_INT >= VERSION_CODES.O) {
                DisplayRequestImpl(
                    uri = uri,
                    _depth = depth,
                    parameters = parametersBuilder?.build(),
                    httpHeaders = httpHeaders?.toMap(),
                    _networkContentDiskCachePolicy = networkContentDiskCachePolicy,
                    _bitmapResultDiskCachePolicy = bitmapResultDiskCachePolicy,
                    maxSize = maxSize,
                    bitmapConfig = bitmapConfig,
                    colorSpace = if (VERSION.SDK_INT >= VERSION_CODES.O) colorSpace else null,
                    preferQualityOverSpeed = preferQualityOverSpeed,
                    resize = resize,
                    transformations = transformations,
                    disabledBitmapPool = disabledBitmapPool,
                    disabledCorrectExifOrientation = disabledCorrectExifOrientation,
                    _bitmapMemoryCachePolicy = bitmapMemoryCachePolicy,
                    disabledAnimationDrawable = disabledAnimationDrawable,
                    placeholderImage = placeholderImage,
                    errorImage = errorImage,
                    target = target,
                    lifecycle = lifecycle ?: resolveLifecycle(),
                    listener = finalListener,
                    progressListener = finalProgressListener,
                )
            } else {
                DisplayRequestImpl(
                    uri = uri,
                    _depth = depth,
                    parameters = parametersBuilder?.build(),
                    httpHeaders = httpHeaders?.toMap(),
                    _networkContentDiskCachePolicy = networkContentDiskCachePolicy,
                    _bitmapResultDiskCachePolicy = bitmapResultDiskCachePolicy,
                    maxSize = maxSize,
                    bitmapConfig = bitmapConfig,
                    preferQualityOverSpeed = preferQualityOverSpeed,
                    resize = resize,
                    transformations = transformations,
                    disabledBitmapPool = disabledBitmapPool,
                    disabledCorrectExifOrientation = disabledCorrectExifOrientation,
                    _bitmapMemoryCachePolicy = bitmapMemoryCachePolicy,
                    disabledAnimationDrawable = disabledAnimationDrawable,
                    placeholderImage = placeholderImage,
                    errorImage = errorImage,
                    target = target,
                    lifecycle = lifecycle ?: resolveLifecycle(),
                    listener = finalListener,
                    progressListener = finalProgressListener,
                )
            }
        }

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
        _networkContentDiskCachePolicy: CachePolicy?,
        _bitmapResultDiskCachePolicy: CachePolicy?,
        override val maxSize: MaxSize?,
        override val bitmapConfig: BitmapConfig?,
        @Suppress("OverridingDeprecatedMember")
        override val preferQualityOverSpeed: Boolean?,
        override val resize: Resize?,
        override val transformations: List<Transformation>?,
        override val disabledBitmapPool: Boolean?,
        override val disabledCorrectExifOrientation: Boolean?,
        _bitmapMemoryCachePolicy: CachePolicy?,
        override val disabledAnimationDrawable: Boolean?,
        override val placeholderImage: StateImage?,
        override val errorImage: StateImage?,
        override val target: Target,
        override val lifecycle: Lifecycle?,
        override val listener: Listener<ImageRequest, ImageResult, ImageResult>?,
        override val progressListener: ProgressListener<ImageRequest>?,
    ) : DisplayRequest {

        @RequiresApi(VERSION_CODES.O)
        constructor(
            uri: Uri,
            _depth: RequestDepth?,
            parameters: Parameters?,
            httpHeaders: Map<String, String>?,
            _networkContentDiskCachePolicy: CachePolicy?,
            _bitmapResultDiskCachePolicy: CachePolicy?,
            maxSize: MaxSize?,
            bitmapConfig: BitmapConfig?,
            colorSpace: ColorSpace?,
            preferQualityOverSpeed: Boolean?,
            resize: Resize?,
            transformations: List<Transformation>?,
            disabledBitmapPool: Boolean?,
            disabledCorrectExifOrientation: Boolean?,
            _bitmapMemoryCachePolicy: CachePolicy?,
            disabledAnimationDrawable: Boolean?,
            placeholderImage: StateImage?,
            errorImage: StateImage?,
            target: Target,
            lifecycle: Lifecycle?,
            listener: Listener<ImageRequest, ImageResult, ImageResult>?,
            progressListener: ProgressListener<ImageRequest>?,
        ) : this(
            uri = uri,
            _depth = _depth,
            parameters = parameters,
            httpHeaders = httpHeaders,
            _networkContentDiskCachePolicy = _networkContentDiskCachePolicy,
            _bitmapResultDiskCachePolicy = _bitmapResultDiskCachePolicy,
            maxSize = maxSize,
            bitmapConfig = bitmapConfig,
            preferQualityOverSpeed = preferQualityOverSpeed,
            resize = resize,
            transformations = transformations,
            disabledBitmapPool = disabledBitmapPool,
            disabledCorrectExifOrientation = disabledCorrectExifOrientation,
            _bitmapMemoryCachePolicy = _bitmapMemoryCachePolicy,
            disabledAnimationDrawable = disabledAnimationDrawable,
            placeholderImage = placeholderImage,
            errorImage = errorImage,
            target = target,
            lifecycle = lifecycle,
            listener = listener,
            progressListener = progressListener,
        ) {
            _colorSpace = colorSpace
        }

        @RequiresApi(VERSION_CODES.O)
        private var _colorSpace: ColorSpace? = null

        @get:RequiresApi(VERSION_CODES.O)
        override val colorSpace: ColorSpace?
            get() = _colorSpace

        override val uriString: String by lazy { uri.toString() }

        override val depth: RequestDepth = _depth ?: NETWORK

        override val networkContentDiskCacheKey: String = uriString

        override val networkContentDiskCachePolicy: CachePolicy =
            _networkContentDiskCachePolicy ?: CachePolicy.ENABLED

        override val cacheKey: String by lazy {
            buildString {
                append(uriString)
                qualityKey?.let {
                    append("_").append(it)
                }
                if (disabledAnimationDrawable == true) {
                    append("_").append("DisabledAnimationDrawable")
                }
            }
        }

        override val bitmapResultDiskCachePolicy: CachePolicy =
            _bitmapResultDiskCachePolicy ?: CachePolicy.ENABLED

        override val bitmapMemoryCachePolicy: CachePolicy =
            _bitmapMemoryCachePolicy ?: CachePolicy.ENABLED

        private val qualityKey: String? by lazy { newQualityKey() }

        override val key: String by lazy {
            buildString {
                append("Display")
                append("_").append(uriString)
                parameters?.key?.takeIf { it.isNotEmpty() }?.let {
                    append("_").append(it)
                }
                httpHeaders?.takeIf { it.isNotEmpty() }?.let {
                    append("_").append("httpHeaders(").append(it.toString()).append(")")
                }
                if (networkContentDiskCachePolicy != CachePolicy.ENABLED) {
                    append("_").append("networkContentDiskCachePolicy($networkContentDiskCachePolicy)")
                }
                maxSize?.let {
                    append("_").append(it.cacheKey)
                }
                bitmapConfig?.let {
                    append("_").append(it.cacheKey)
                }
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    colorSpace?.let {
                        append("_").append("colorSpace(${it.name.replace(" ", "")}")
                    }
                }
                @Suppress("DEPRECATION")
                if (VERSION.SDK_INT < VERSION_CODES.N && preferQualityOverSpeed == true) {
                    append("_").append("preferQualityOverSpeed")
                }
                resize?.let {
                    append("_").append(it.cacheKey)
                }
                transformations?.takeIf { it.isNotEmpty() }?.let { list ->
                    append("_").append("transformations(${list.joinToString(separator = ",") { it.cacheKey }})")
                }
                if (disabledBitmapPool == true) {
                    append("_").append("disabledBitmapPool")
                }
                if (disabledCorrectExifOrientation == true) {
                    append("_").append("disabledCorrectExifOrientation")
                }
                if (bitmapResultDiskCachePolicy != CachePolicy.ENABLED) {
                    append("_").append("bitmapResultDiskCachePolicy($bitmapResultDiskCachePolicy)")
                }
                if (disabledAnimationDrawable == true) {
                    append("_").append("disabledAnimationDrawable")
                }
                if (bitmapMemoryCachePolicy != CachePolicy.ENABLED) {
                    append("_").append("bitmapMemoryCachePolicy($bitmapMemoryCachePolicy)")
                }
            }
        }
    }
}