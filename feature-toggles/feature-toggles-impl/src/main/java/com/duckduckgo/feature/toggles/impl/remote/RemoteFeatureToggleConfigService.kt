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

package com.duckduckgo.feature.toggles.impl.remote

import com.duckduckgo.feature.toggles.impl.remote.models.RemoteFeatureToggleConfig
import retrofit2.http.GET

interface RemoteFeatureToggleConfigService {
    @GET("https://staticcdn.duckduckgo.com/remotefeatureflagging/config/v1/android-config.json")
    suspend fun config(): RemoteFeatureToggleConfig
}
