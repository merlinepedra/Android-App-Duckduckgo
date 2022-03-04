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

import androidx.annotation.WorkerThread
import com.duckduckgo.app.statistics.Probabilistic
import com.duckduckgo.app.statistics.WeightedRandomizer
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.impl.remote.models.RemoteFeatureRollout
import com.duckduckgo.feature.toggles.impl.remote.models.RemoteFeatureToggleConfig
import com.duckduckgo.feature.toggles.store.remote.RemoteFeatureToggle
import com.duckduckgo.feature.toggles.store.remote.RemoteFeatureTogglesRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface RemoteFeatureToggleConfigPersister {
    suspend fun persistConfig(config: RemoteFeatureToggleConfig)
}

@WorkerThread
@ContributesBinding(AppScope::class)
class DefaultRemoteFeatureToggleConfigPersister @Inject constructor(
    private val repository: RemoteFeatureTogglesRepository,
    private val weightedRandomizer: WeightedRandomizer
) : RemoteFeatureToggleConfigPersister {

    override suspend fun persistConfig(config: RemoteFeatureToggleConfig) {
        if (repository.configVersion() < config.version) {
            config.rollouts.forEach { configRollout ->
                repository.getFeature(configRollout.featureName)?.also { localFeatureToggle ->
                    updateFeatureToggle(configRollout, localFeatureToggle)
                } ?: updateFeature(configRollout)
            }
            repository.updateConfigVersion(config.version)
        }
    }

    private fun updateFeatureToggle(
        newFeatureRollout: RemoteFeatureRollout,
        localFeatureToggle: RemoteFeatureToggle
    ) {
        // feature has been rolled back, so we update the percentage to be 0
        if (newFeatureRollout.rolloutToPercentage == 0 && localFeatureToggle.rolloutToPercentage != 0) {
            disabledFeature(localFeatureToggle)
            return
        }

        // rollout percentage didn't change, do nothing
        // rollout percentage increased and user is already enabled, do nothing
        if (newFeatureRollout.rolloutToPercentage != localFeatureToggle.rolloutToPercentage) {
            // rollout percentage changed and user is not enabled
            if (!localFeatureToggle.isEnabled) {
                updateFeature(newFeatureRollout)
            }
        }
    }

    private fun updateFeature(newFeatureRollout: RemoteFeatureRollout) {
        val shouldEnable = shouldEnable(newFeatureRollout.rolloutToPercentage)
        repository.insert(
            RemoteFeatureToggle(
                newFeatureRollout.featureName,
                newFeatureRollout.rolloutToPercentage,
                shouldEnable
            )
        )
    }

    private fun shouldEnable(rolloutToPercentage: Int): Boolean {
        val enabledWeight = rolloutToPercentage.toWeight()
        val disabledWeight = 1 - enabledWeight
        val probabilistic = listOf(
            RemoteFeatureToggleProbabilistic(false, disabledWeight),
            RemoteFeatureToggleProbabilistic(true, enabledWeight)
        )
        return probabilistic[weightedRandomizer.random(probabilistic)].enabled
    }

    private fun Int.toWeight(): Double = this / 100.0

    private fun disabledFeature(localFeatureToggle: RemoteFeatureToggle) {
        repository.insert(
            RemoteFeatureToggle(
                featureName = localFeatureToggle.featureName,
                rolloutToPercentage = 0,
                isEnabled = localFeatureToggle.isEnabled,
            )
        )
    }
}

data class RemoteFeatureToggleProbabilistic(
    val enabled: Boolean,
    override val weight: Double
) : Probabilistic
