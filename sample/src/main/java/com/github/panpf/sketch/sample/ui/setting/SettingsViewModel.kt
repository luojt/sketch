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
package com.github.panpf.sketch.sample.ui.setting

import android.app.Application
import android.graphics.ColorSpace
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.widget.ImageView.ScaleType
import android.widget.ImageView.ScaleType.MATRIX
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.sample.model.ListSeparator
import com.github.panpf.sketch.sample.model.MultiSelectMenu
import com.github.panpf.sketch.sample.model.SwitchMenuFlow
import com.github.panpf.sketch.sample.prefsService
import com.github.panpf.sketch.sample.ui.base.LifecycleAndroidViewModel
import com.github.panpf.sketch.sample.ui.setting.Page.COMPOSE_LIST
import com.github.panpf.sketch.sample.ui.setting.Page.LIST
import com.github.panpf.sketch.sample.ui.setting.Page.NONE
import com.github.panpf.sketch.sample.ui.setting.Page.ZOOM
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.util.Logger.Level
import com.github.panpf.tools4j.io.ktx.formatFileSize
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class SettingsViewModel(application1: Application, val page: Page) :
    LifecycleAndroidViewModel(application1) {

    class Factory(val application: Application, val page: Page) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, page) as T
        }
    }

    val menuListData = MutableLiveData<List<Any>>()
    private val prefsService = application1.prefsService

    init {
        val states = listOfNotNull(
            prefsService.showMimeTypeLogoInLIst.sharedFlow,
            prefsService.showProgressIndicatorInList.sharedFlow,
            prefsService.saveCellularTrafficInList.sharedFlow,
            prefsService.pauseLoadWhenScrollInList.sharedFlow,
            prefsService.resizePrecision.sharedFlow,
            prefsService.resizeScale.sharedFlow,
            prefsService.longImageResizeScale.sharedFlow,
            prefsService.otherImageResizeScale.sharedFlow,
            prefsService.inPreferQualityOverSpeed.sharedFlow,
            prefsService.bitmapQuality.sharedFlow,
            if (VERSION.SDK_INT >= VERSION_CODES.O) prefsService.colorSpace.sharedFlow else null,
            prefsService.ignoreExifOrientation.sharedFlow,
            prefsService.disabledMemoryCache.sharedFlow,
            prefsService.disabledResultCache.sharedFlow,
            prefsService.disabledDownloadCache.sharedFlow,
            prefsService.disallowReuseBitmap.sharedFlow,
            prefsService.showDataFromLogo.sharedFlow,
            prefsService.showTileBoundsInHugeImagePage.sharedFlow,
            prefsService.logLevel.sharedFlow,
        )
        viewModelScope.launch {
            merge(*states.toTypedArray()).collect {
                updateList()
            }
        }

        updateList()
    }

    private fun updateList() {
        menuListData.postValue(buildList {
            when (page) {
                LIST -> {
                    add(ListSeparator("List"))
                    addAll(makeRecyclerListMenuList())
                    addAll(makeListMenuList())
                    add(ListSeparator("Decode"))
                    addAll(makeDecodeMenuList())
                }
                COMPOSE_LIST -> {
                    add(ListSeparator("List"))
                    addAll(makeListMenuList())
                    add(ListSeparator("Decode"))
                    addAll(makeDecodeMenuList())
                }
                ZOOM -> {
                    add(ListSeparator("Zoom"))
                    addAll(makeZoomMenuList())
                    add(ListSeparator("Decode"))
                    addAll(makeDecodeMenuList())
                }
                NONE -> {
                    add(ListSeparator("List"))
                    addAll(makeRecyclerListMenuList())
                    addAll(makeListMenuList())
                    add(ListSeparator("Decode"))
                    addAll(makeDecodeMenuList())
                    add(ListSeparator("Zoom"))
                    addAll(makeZoomMenuList())
                }
            }
            add(ListSeparator("Cache"))
            addAll(makeCacheMenuList())
            add(ListSeparator("Other"))
            addAll(makeOtherMenuList())
        })
    }

    private fun makeRecyclerListMenuList(): List<Any> = buildList {
        add(
            SwitchMenuFlow(
                title = "MimeType Logo",
                data = prefsService.showMimeTypeLogoInLIst,
                desc = "Displays the image type in the lower right corner of the ImageView"
            )
        )
        add(
            SwitchMenuFlow(
                title = "Progress Indicator",
                data = prefsService.showProgressIndicatorInList,
                desc = "A black translucent mask is displayed on the ImageView surface to indicate progress"
            )
        )
        add(
            SwitchMenuFlow(
                title = "Show Data From Logo",
                data = prefsService.showDataFromLogo,
                desc = "A different color triangle is displayed in the lower right corner of the ImageView according to DataFrom"
            )
        )
    }

    private fun makeListMenuList(): List<Any> = buildList {
        add(
            SwitchMenuFlow(
                title = "Save Cellular Traffic",
                data = prefsService.saveCellularTrafficInList,
                desc = "Mobile cell traffic does not download pictures"
            )
        )
        add(
            SwitchMenuFlow(
                title = "Pause Load When Scrolling",
                data = prefsService.pauseLoadWhenScrollInList,
                desc = "No image is loaded during list scrolling to improve the smoothness"
            )
        )
        add(
            MultiSelectMenu(
                title = "Resize Precision",
                desc = null,
                values = Precision.values().map { it.name }.plus(listOf("LongImageClipMode")),
                getValue = { prefsService.resizePrecision.value },
                onSelect = { _, value -> prefsService.resizePrecision.value = value }
            )
        )
        add(
            MultiSelectMenu(
                title = "Resize Scale",
                desc = null,
                values = Scale.values().map { it.name }.plus(listOf("LongImageMode")),
                getValue = { prefsService.resizeScale.value },
                onSelect = { _, value -> prefsService.resizeScale.value = value }
            )
        )
        if (prefsService.resizeScale.value == "LongImageMode") {
            add(
                MultiSelectMenu(
                    title = "Long Image Resize Scale",
                    desc = "Only Resize Scale is LongImageMode",
                    values = Scale.values().map { it.name },
                    getValue = { prefsService.longImageResizeScale.value },
                    onSelect = { _, value -> prefsService.longImageResizeScale.value = value }
                )
            )
            add(
                MultiSelectMenu(
                    title = "Other Image Resize Scale",
                    desc = "Only Resize Scale is LongImageMode",
                    values = Scale.values().map { it.name },
                    getValue = { prefsService.otherImageResizeScale.value },
                    onSelect = { _, value ->
                        prefsService.otherImageResizeScale.value = value
                    }
                )
            )
        }
    }

    private fun makeZoomMenuList(): List<Any> = buildList {
        add(
            MultiSelectMenu(
                title = "Scale Type",
                desc = null,
                values = ScaleType.values().filter { it != MATRIX }.map { it.name },
                getValue = { prefsService.scaleType.value },
                onSelect = { _, value ->
                    prefsService.scaleType.value = value
                }
            )
        )
        add(
            SwitchMenuFlow(
                title = "Scroll Bar",
                desc = null,
                data = prefsService.scrollBarEnabled,
            )
        )
        add(
            SwitchMenuFlow(
                title = "Read Mode",
                data = prefsService.readModeEnabled,
                desc = "Long images are displayed in full screen by default"
            )
        )
        add(
            SwitchMenuFlow(
                title = "Show Tile Bounds",
                desc = "Overlay the state and area of the tile on the View",
                data = prefsService.showTileBoundsInHugeImagePage,
            )
        )
    }

    private fun makeDecodeMenuList(): List<Any> = buildList {
        add(
            MultiSelectMenu(
                title = "Bitmap Quality",
                desc = null,
                values = listOf("Default", "LOW", "HIGH"),
                getValue = { prefsService.bitmapQuality.value },
                onSelect = { _, value -> prefsService.bitmapQuality.value = value }
            )
        )
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            val items = listOf("Default").plus(ColorSpace.Named.values().map { it.name })
            add(
                MultiSelectMenu(
                    title = "Color Space",
                    desc = null,
                    values = items,
                    getValue = { prefsService.colorSpace.value },
                    onSelect = { _, value -> prefsService.colorSpace.value = value }
                )
            )
        }
        if (VERSION.SDK_INT <= VERSION_CODES.M) {
            add(
                SwitchMenuFlow(
                    title = "inPreferQualityOverSpeed",
                    desc = null,
                    data = prefsService.inPreferQualityOverSpeed
                )
            )
        }
        add(
            SwitchMenuFlow(
                title = "Exif Orientation",
                desc = null,
                data = prefsService.ignoreExifOrientation,
                reverse = true
            )
        )
    }

    private fun makeCacheMenuList(): List<Any> = buildList {
        val sketch = application1.sketch

        add(
            SwitchMenuFlow(
                title = "Memory Cache",
                desc = "%s/%s（Long Click Clean）".format(
                    sketch.memoryCache.size.formatFileSize(0, false, true),
                    sketch.memoryCache.maxSize.formatFileSize(0, false, true)
                ),
                data = prefsService.disabledMemoryCache,
                reverse = true,
                onLongClick = {
                    sketch.memoryCache.clear()
                    updateList()
                }
            )
        )

        add(
            SwitchMenuFlow(
                title = "Result Cache",
                desc = "%s/%s（Long Click Clean）".format(
                    sketch.resultCache.size.formatFileSize(0, false, true),
                    sketch.resultCache.maxSize.formatFileSize(0, false, true)
                ),
                data = prefsService.disabledResultCache,
                reverse = true,
                onLongClick = {
                    sketch.resultCache.clear()
                    updateList()
                }
            )
        )

        add(
            SwitchMenuFlow(
                title = "Download Cache",
                desc = "%s/%s（Long Click Clean）".format(
                    sketch.downloadCache.size.formatFileSize(0, false, true),
                    sketch.downloadCache.maxSize.formatFileSize(0, false, true)
                ),
                data = prefsService.disabledDownloadCache,
                reverse = true,
                onLongClick = {
                    sketch.downloadCache.clear()
                    updateList()
                }
            )
        )

        add(
            SwitchMenuFlow(
                title = "Bitmap Pool",
                desc = "%s/%s（Long Click Clean）".format(
                    sketch.bitmapPool.size.formatFileSize(0, false, true),
                    sketch.bitmapPool.maxSize.formatFileSize(0, false, true)
                ),
                data = prefsService.disallowReuseBitmap,
                reverse = true,
                onLongClick = {
                    sketch.bitmapPool.clear()
                    updateList()
                }
            )
        )
    }

    private fun makeOtherMenuList(): List<Any> = buildList {
        add(
            MultiSelectMenu(
                title = "Logger Level",
                desc = if (application1.sketch.logger.level <= Level.DEBUG) "DEBUG and below will reduce UI fluency" else "",
                values = Level.values().map { it.name },
                getValue = { application1.sketch.logger.level.toString() },
                onSelect = { _, value ->
                    application1.sketch.logger.level = Level.valueOf(value)
                    prefsService.logLevel.value = value
                }
            )
        )
    }
}