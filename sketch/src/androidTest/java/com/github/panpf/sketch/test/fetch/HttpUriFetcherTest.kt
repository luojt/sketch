package com.github.panpf.sketch.test.fetch

import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.cache.CachePolicy.DISABLED
import com.github.panpf.sketch.datasource.ByteArrayDataSource
import com.github.panpf.sketch.datasource.DataFrom
import com.github.panpf.sketch.datasource.DiskCacheDataSource
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.fetch.HttpUriFetcher
import com.github.panpf.sketch.request.DisplayRequest
import com.github.panpf.sketch.request.DownloadRequest
import com.github.panpf.sketch.request.LoadRequest
import com.github.panpf.sketch.test.utils.TestHttpStack
import com.github.panpf.sketch.test.utils.getTestContextAndNewSketch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HttpUriFetcherTest {

    @Test
    fun testFactory() {
        val (context, sketch) = getTestContextAndNewSketch()
        val httpUri = "http://sample.com/sample.jpg"
        val httpsUri = "https://sample.com/sample.jpg"
        val ftpUri = "ftp://sample.com/sample.jpg"
        val contentUri = "content://sample_app/sample"
        val imageView = ImageView(context)

        val httpUriFetcherFactory = HttpUriFetcher.Factory()
        Assert.assertNotNull(
            httpUriFetcherFactory.create(
                sketch,
                DownloadRequest(context, httpUri)
            )
        )
        Assert.assertNotNull(
            httpUriFetcherFactory.create(
                sketch,
                DownloadRequest(context, httpsUri)
            )
        )
        Assert.assertNotNull(httpUriFetcherFactory.create(sketch, LoadRequest(context, httpUri)))
        Assert.assertNotNull(httpUriFetcherFactory.create(sketch, LoadRequest(context, httpsUri)))
        Assert.assertNotNull(
            httpUriFetcherFactory.create(
                sketch,
                DisplayRequest(imageView, httpUri)
            )
        )
        Assert.assertNotNull(
            httpUriFetcherFactory.create(
                sketch,
                DisplayRequest(imageView, httpsUri)
            )
        )
        Assert.assertNull(httpUriFetcherFactory.create(sketch, DownloadRequest(context, ftpUri)))
        Assert.assertNull(
            httpUriFetcherFactory.create(
                sketch,
                DownloadRequest(context, contentUri)
            )
        )

        val httpUriFetcherFactory2 = HttpUriFetcher.Factory()
        Assert.assertEquals(httpUriFetcherFactory, httpUriFetcherFactory)
        Assert.assertEquals(httpUriFetcherFactory, httpUriFetcherFactory2)
        Assert.assertNotEquals(httpUriFetcherFactory, Any())
        Assert.assertNotEquals(httpUriFetcherFactory, null)

        Assert.assertEquals(httpUriFetcherFactory.hashCode(), httpUriFetcherFactory.hashCode())
        Assert.assertEquals(httpUriFetcherFactory.hashCode(), httpUriFetcherFactory2.hashCode())
    }

    @Test
    fun testRepeatDownload() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        // Loop the test 50 times without making any mistakes
        val testUri = TestHttpStack.testImages.first()
        repeat(50) {
            runBlocking {
                val request = DownloadRequest(context, testUri.uriString)

                val diskCacheKey = request.uriString
                val diskCache = sketch.diskCache
                diskCache.remove(diskCacheKey)
                Assert.assertFalse(diskCache.exist(diskCacheKey))

                val deferredList = mutableListOf<Deferred<FetchResult?>>()
                // Make 100 requests in a short period of time, expect only the first one to be downloaded from the network and the next 99 to be read from the disk cache
                repeat(100) {
                    val deferred = async(Dispatchers.IO) {
                        HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
                    }
                    deferredList.add(deferred)
                }
                val resultList = deferredList.map { it.await() }
                Assert.assertEquals(100, resultList.size)
                val fromNetworkList = resultList.mapIndexedNotNull { index, fetchResult ->
                    if (fetchResult!!.dataFrom == DataFrom.NETWORK) {
                        index to DataFrom.NETWORK
                    } else {
                        null
                    }
                }
                val fromDiskCacheList = resultList.mapIndexedNotNull { index, fetchResult ->
                    if (fetchResult!!.dataFrom == DataFrom.DISK_CACHE) {
                        index to DataFrom.DISK_CACHE
                    } else {
                        null
                    }
                }
                val message = buildString {
                    append("The results are as follows")
                    appendLine()
                    append(fromNetworkList.joinToString { "${it.first}:${it.second}" })
                    appendLine()
                    append(fromDiskCacheList.joinToString { "${it.first}:${it.second}" })
                }
                Assert.assertTrue(
                    message,
                    fromNetworkList.size == 1 && fromDiskCacheList.size == 99
                )
            }
        }
    }

    @Test
    fun testDiskCachePolicy() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val testUri = TestHttpStack.testImages.first()

        // CachePolicy.ENABLED
        runBlocking {
            val request = DownloadRequest(context, testUri.uriString) {
                downloadCachePolicy(CachePolicy.ENABLED)
            }
            val httpUriFetcher = HttpUriFetcher.Factory().create(sketch, request)!!

            val diskCacheKey = request.uriString
            val contentTypeDiskCacheKey = request.uriString + "_contentType"
            val diskCache = sketch.diskCache
            diskCache.remove(diskCacheKey)
            diskCache.remove(contentTypeDiskCacheKey)
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is DiskCacheDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.DISK_CACHE, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is DiskCacheDataSource && this.dataSource.dataFrom == DataFrom.DISK_CACHE
                )
            }
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))
        }

        // CachePolicy.DISABLED
        runBlocking {
            val request = DownloadRequest(context, testUri.uriString) {
                downloadCachePolicy(DISABLED)
            }
            val httpUriFetcher = HttpUriFetcher.Factory().create(sketch, request)!!

            val diskCacheKey = request.uriString
            val contentTypeDiskCacheKey = request.uriString + "_contentType"
            val diskCache = sketch.diskCache
            diskCache.remove(diskCacheKey)
            diskCache.remove(contentTypeDiskCacheKey)
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))
        }

        // CachePolicy.READ_ONLY
        runBlocking {
            val request = DownloadRequest(context, testUri.uriString) {
                downloadCachePolicy(CachePolicy.READ_ONLY)
            }
            val httpUriFetcher = HttpUriFetcher.Factory().create(sketch, request)!!

            val diskCacheKey = request.uriString
            val contentTypeDiskCacheKey = request.uriString + "_contentType"
            val diskCache = sketch.diskCache
            diskCache.remove(diskCacheKey)
            diskCache.remove(contentTypeDiskCacheKey)
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            val request2 = DownloadRequest(context, testUri.uriString) {
                downloadCachePolicy(CachePolicy.ENABLED)
            }
            val httpUriFetcher2 = HttpUriFetcher.Factory().create(sketch, request2)!!
            httpUriFetcher2.fetch()
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.DISK_CACHE, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is DiskCacheDataSource && this.dataSource.dataFrom == DataFrom.DISK_CACHE
                )
            }
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))
        }

        // CachePolicy.WRITE_ONLY
        runBlocking {
            val request = DownloadRequest(context, testUri.uriString) {
                downloadCachePolicy(CachePolicy.WRITE_ONLY)
            }
            val httpUriFetcher = HttpUriFetcher.Factory().create(sketch, request)!!

            val diskCacheKey = request.uriString
            val contentTypeDiskCacheKey = request.uriString + "_contentType"
            val diskCache = sketch.diskCache
            diskCache.remove(diskCacheKey)
            diskCache.remove(contentTypeDiskCacheKey)
            Assert.assertFalse(diskCache.exist(diskCacheKey))
            Assert.assertFalse(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))

            httpUriFetcher.fetch().apply {
                Assert.assertEquals(this.toString(), DataFrom.NETWORK, this.dataFrom)
                Assert.assertTrue(
                    this.toString(),
                    this.dataSource is ByteArrayDataSource && this.dataSource.dataFrom == DataFrom.NETWORK
                )
            }
            Assert.assertTrue(diskCache.exist(diskCacheKey))
            Assert.assertTrue(diskCache.exist(contentTypeDiskCacheKey))
        }
    }

    @Test
    fun testProgress() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val testUri = TestHttpStack.testImages.first()
        val progressList = mutableListOf<Long>()
        val request = DownloadRequest(context, testUri.uriString) {
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }

        val diskCache = sketch.diskCache
        val diskCacheKey = request.uriString
        diskCache.remove(diskCacheKey)
        Assert.assertFalse(diskCache.exist(diskCacheKey))

        runBlocking {
            HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
            delay(1000)
        }
        Assert.assertTrue(progressList.size > 0)
        Assert.assertEquals(testUri.contentLength, progressList.last())

        var lastProgress: Long? = null
        progressList.forEach { progress ->
            val currentLastProgress = lastProgress
            if (currentLastProgress != null) {
                Assert.assertTrue(currentLastProgress < progress)
            }
            lastProgress = progress
        }
    }

    @Test
    fun testCancel() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it, readDelayMillis = 1000))
        }

        val testUri = TestHttpStack.testImages.first()
        val progressList = mutableListOf<Long>()
        val request = DownloadRequest(context, testUri.uriString) {
            downloadCachePolicy(DISABLED)
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }

        val diskCacheKey = request.uriString
        val diskCache = sketch.diskCache
        diskCache.remove(diskCacheKey)
        Assert.assertFalse(diskCache.exist(diskCacheKey))

        progressList.clear()
        runBlocking {
            val job = launch {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
            }
            delay(2000)
            job.cancel()
        }
        Assert.assertTrue(progressList.size > 0)
        Assert.assertNull(progressList.find { it == testUri.contentLength })
    }

    @Test
    fun testCancel2() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it, readDelayMillis = 1000, connectionDelayMillis = 1000))
        }

        val testUri = TestHttpStack.testImages.first()
        val progressList = mutableListOf<Long>()
        val request = DownloadRequest(context, testUri.uriString) {
            downloadCachePolicy(DISABLED)
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }

        val diskCacheKey = request.uriString
        val diskCache = sketch.diskCache
        diskCache.remove(diskCacheKey)
        Assert.assertFalse(diskCache.exist(diskCacheKey))

        progressList.clear()
        runBlocking {
            val job = launch {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
            }
            delay(500)
            job.cancel()
        }
        Assert.assertTrue(progressList.size == 0)
    }

    @Test
    fun testErrorUrl() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val progressList = mutableListOf<Long>()
        val request = DownloadRequest(context, "http://error.com/sample.jpeg") {
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }
        sketch.diskCache.clear()
        runBlocking {
            try {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
                Assert.fail("No exception thrown")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Assert.assertEquals(0, progressList.size)
    }

    @Test
    fun testContentUrl() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val progressList = mutableListOf<Long>()
        val request = DownloadRequest(context, TestHttpStack.errorImage.uriString) {
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }
        sketch.diskCache.clear()
        runBlocking {
            try {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
                Assert.fail("No exception thrown")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Assert.assertEquals(0, progressList.size)
    }

    @Test
    fun testChunkedError() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val progressList = mutableListOf<Long>()
        val testUri = TestHttpStack.chunkedErrorImage
        val request = DownloadRequest(context, testUri.uriString) {
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }
        sketch.diskCache.clear()
        runBlocking {
            try {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
                Assert.fail("No exception thrown")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Assert.assertTrue(progressList.size == 0)
        Assert.assertNull(progressList.find { it == testUri.contentLength })

        Assert.assertFalse(sketch.diskCache.exist(request.uriString))
    }

    @Test
    fun testLengthError() {
        val (context, sketch) = getTestContextAndNewSketch {
            httpStack(TestHttpStack(it))
        }

        val progressList = mutableListOf<Long>()
        val testUri = TestHttpStack.lengthErrorImage
        val request = DownloadRequest(context, testUri.uriString) {
            progressListener { _, _, completedLength ->
                progressList.add(completedLength)
            }
        }
        sketch.diskCache.clear()
        runBlocking {
            try {
                HttpUriFetcher.Factory().create(sketch, request)!!.fetch()
                Assert.fail("No exception thrown")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Assert.assertTrue(progressList.size > 0)
        Assert.assertNotNull(progressList.find { it == testUri.contentLength + 1 })

        Assert.assertFalse(sketch.diskCache.exist(request.uriString))
    }
}