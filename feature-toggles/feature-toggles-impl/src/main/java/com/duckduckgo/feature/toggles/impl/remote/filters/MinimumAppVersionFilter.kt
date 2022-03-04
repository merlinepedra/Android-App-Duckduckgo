/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.feature.toggles.impl.remote.filters

import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.feature.toggles.impl.remote.models.Filters
import javax.inject.Inject

class MinimumAppVersionFilter @Inject constructor(
    private val deviceInfo: DeviceInfo
) : RemoteFeatureToggleFilter {
    companion object {
        const val KEY = "minimumAppVersion"
    }

    override fun handle(filter: Filters): Boolean {
        val target = filter.value
        return deviceInfo.appVersion.asLong() >= target.asLong()
    }

    private fun String.asLong(): Long = this.replace(".", "").toLong()
}
