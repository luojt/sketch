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
package com.github.panpf.sketch.cache

import android.content.Context
import com.github.panpf.sketch.cache.DiskCache.Companion.DEFAULT_DIR_NAME
import com.github.panpf.sketch.cache.DiskCache.Editor
import com.github.panpf.sketch.cache.DiskCache.Snapshot
import com.github.panpf.sketch.cache.internal.KeyMapperCache
import com.github.panpf.sketch.util.DiskLruCache
import com.github.panpf.sketch.util.Logger
import com.github.panpf.sketch.util.MD5Utils
import com.github.panpf.sketch.util.fileNameCompatibilityMultiProcess
import com.github.panpf.sketch.util.formatFileSize
import kotlinx.coroutines.sync.Mutex
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.WeakHashMap

/**
 * A disk cache that manages the cache according to a least-used rule
 *
 * @param version       Version, used to delete the old cache, update this value when you want to actively delete the old cache
 * @param maxSize       Maximum capacity
 */
class LruDiskCache constructor(
    context: Context,
    private val logger: Logger,
    override val maxSize: Long = DiskCache.DEFAULT_MAX_SIZE,
    private val _directory: File? = null,
    val version: Int = 1,
) : DiskCache, Closeable {

    companion object {
        private const val MODULE = "LruDiskCache"

        @JvmStatic
        private val editLockLock = Any()
    }

    private var _cache: DiskLruCache? = null
    private val keyMapperCache = KeyMapperCache { MD5Utils.md5(it) }
    private val editLockMap: MutableMap<String, Mutex> = WeakHashMap()

    override val size: Long
        get() = _cache?.size() ?: 0
    override val directory: File by lazy {
        (_directory ?: File(context.externalCacheDir ?: context.cacheDir, DEFAULT_DIR_NAME)).run {
            fileNameCompatibilityMultiProcess(context, this)
        }
    }

    private fun cache(): DiskLruCache = synchronized(this) {
        _cache ?: openDiskLruCache().apply {
            this@LruDiskCache._cache = this
        }
    }

    private fun openDiskLruCache(): DiskLruCache {
        directory.apply {
            if (this.exists()) {
                val journalFile = File(this, DiskLruCache.JOURNAL_FILE)
                if (!journalFile.exists()) {
                    this.deleteRecursively()
                    this.mkdirs()
                }
            } else {
                this.mkdirs()
            }
        }
        return DiskLruCache.open(directory, version, 1, maxSize)
    }

    override fun edit(key: String): Editor? {
        val cache = cache()
        val encodedKey = keyMapperCache.mapKey(key)
        var diskEditor: DiskLruCache.Editor? = null
        try {
            diskEditor = cache.edit(encodedKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return diskEditor?.let { LruDiskCacheEditor(this, logger, key, it) }
    }

    override fun remove(key: String): Boolean {
        val cache = cache()
        val encodedKey = keyMapperCache.mapKey(key)
        return try {
            cache.remove(encodedKey)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun exist(key: String): Boolean {
        val cache = cache()
        val encodedKey = keyMapperCache.mapKey(key)
        return try {
            cache.exist(encodedKey)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun get(key: String): Snapshot? {
        val cache = cache()
        val encodedKey = keyMapperCache.mapKey(key)
        var snapshot: DiskLruCache.SimpleSnapshot? = null
        try {
            snapshot = cache.getSimpleSnapshot(encodedKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return snapshot?.let { LruDiskCacheSnapshot(this, logger, key, it) }
    }

    override fun clear() {
        val oldSize = size
        val cache = this._cache
        if (cache != null) {
            try {
                cache.delete()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            this._cache = null
        } else {
            DiskLruCache.deleteContents(directory)
        }
        logger.w(MODULE, "clear. cleared ${oldSize.formatFileSize()}")
    }

    @Synchronized
    override fun editLock(key: String): Mutex = synchronized(editLockLock) {
        val encodedKey = keyMapperCache.mapKey(key)
        editLockMap[encodedKey] ?: Mutex().apply {
            this@LruDiskCache.editLockMap[encodedKey] = this
        }
    }

    /**
     * It can still be used after closing, and will reopen a new DiskLruCache
     */
    override fun close() {
        try {
            _cache?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _cache = null
    }

    override fun toString(): String =
        "${MODULE}(maxSize=${maxSize.formatFileSize()},version=${version},directory='${directory.path}')"

    class LruDiskCacheSnapshot(
        private val lruDiskCache: LruDiskCache,
        private val logger: Logger,
        override val key: String,
        private val snapshot: DiskLruCache.SimpleSnapshot
    ) : Snapshot {

        override val file: File = snapshot.getFile(0)

        @Throws(IOException::class)
        override fun newInputStream(): InputStream = snapshot.newInputStream(0)

        override fun edit(): Editor? = snapshot.edit()?.let {
            LruDiskCacheEditor(lruDiskCache, logger, key, it)
        }

        override fun remove(): Boolean =
            try {
                snapshot.diskLruCache.remove(snapshot.key)
                logger.d(MODULE) {
                    "delete. key '$key', size ${lruDiskCache.size.formatFileSize()}"
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
    }

    class LruDiskCacheEditor(
        private val lruDiskCache: LruDiskCache,
        private val logger: Logger,
        private val key: String,
        private val diskEditor: DiskLruCache.Editor
    ) : Editor {

        @Throws(IOException::class)
        override fun newOutputStream(): OutputStream {
            return diskEditor.newOutputStream(0)
        }

        @Throws(
            IOException::class,
            DiskLruCache.EditorChangedException::class,
            DiskLruCache.ClosedException::class,
            DiskLruCache.FileNotExistException::class
        )
        override fun commit() {
            diskEditor.commit()
            logger.d(MODULE) {
                "commit. key '$key', size ${lruDiskCache.size.formatFileSize()}"
            }
        }

        override fun abort() {
            try {
                diskEditor.abort()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            logger.d(MODULE) {
                "abort. key '$key', size ${lruDiskCache.size.formatFileSize()}"
            }
        }
    }
}