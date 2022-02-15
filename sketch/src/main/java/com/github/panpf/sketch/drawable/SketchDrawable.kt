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
package com.github.panpf.sketch.drawable

import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.decode.Transformed
import com.github.panpf.sketch.request.DataFrom
import com.github.panpf.sketch.util.BitmapInfo

interface SketchDrawable {

    val requestKey: String?

    val requestUri: String

    val imageInfo: ImageInfo

    val imageExifOrientation: Int

    val imageDataFrom: DataFrom?

    val transformedList: List<Transformed>?

    val bitmapInfo: BitmapInfo
}