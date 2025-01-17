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
package com.github.panpf.sketch.sample.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.github.panpf.sketch.sample.databinding.MainActivityBinding
import com.github.panpf.sketch.sample.service.NotificationService
import com.github.panpf.sketch.sample.ui.base.BaseBindingActivity

class MainActivity : BaseBindingActivity<MainActivityBinding>() {

    override fun onCreate(binding: MainActivityBinding, savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenResumed {
            startService(Intent(this@MainActivity, NotificationService::class.java))
        }
    }
}
