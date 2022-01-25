package com.github.panpf.sketch.svg.test.decode

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.datasource.AssetDataSource
import com.github.panpf.sketch.datasource.DataSource
import com.github.panpf.sketch.decode.SvgBitmapDecoder
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.fetch.newAssetUri
import com.github.panpf.sketch.request.DataFrom
import com.github.panpf.sketch.request.DataFrom.LOCAL
import com.github.panpf.sketch.request.LoadRequest
import com.github.panpf.sketch.request.internal.ImageRequest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileDescriptor
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class SvgBitmapDecoderTest {

    @Test
    fun testFactory() {
        val context = InstrumentationRegistry.getContext()
        val sketch = Sketch.new(context)

        // normal
        val request = LoadRequest(newAssetUri("sample.svg"))
        val fetchResult = FetchResult(AssetDataSource(sketch, request, "sample.svg"), null)
        Assert.assertNotNull(SvgBitmapDecoder.Factory(false).create(sketch, request, fetchResult))

        // not svg
        val request1 = LoadRequest(newAssetUri("sample.png"))
        val fetchResult1 = FetchResult(AssetDataSource(sketch, request1, "sample.png"), null)
        Assert.assertNull(SvgBitmapDecoder.Factory(false).create(sketch, request1, fetchResult1))

        // external mimeType it's right
        val fetchResult2 = FetchResult(ErrorDataSource(sketch, request, LOCAL), "image/svg+xml")
        Assert.assertNotNull(SvgBitmapDecoder.Factory(false).create(sketch, request, fetchResult2))
    }

    @Test
    fun testDecodeBitmap() {
        // todo Write test cases
    }

    private class ErrorDataSource(
        override val sketch: Sketch,
        override val request: ImageRequest,
        override val from: DataFrom
    ) : DataSource {
        override fun length(): Long = throw UnsupportedOperationException("Unsupported length()")

        override fun newFileDescriptor(): FileDescriptor =
            throw UnsupportedOperationException("Unsupported newFileDescriptor()")

        override fun newInputStream(): InputStream =
            throw UnsupportedOperationException("Unsupported newInputStream()")
    }
}