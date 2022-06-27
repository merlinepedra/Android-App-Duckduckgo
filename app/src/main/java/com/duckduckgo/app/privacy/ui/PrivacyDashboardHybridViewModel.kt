/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.ui

import android.net.http.SslCertificate
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.brokensite.BrokenSiteData
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_DASHBOARD_WHITELIST_ADD
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_DASHBOARD_WHITELIST_REMOVE
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.tls.certificatePem
import okio.utf8Size
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class PrivacyDashboardHybridViewModel @Inject constructor(
    private val userWhitelistDao: UserWhitelistDao,
    private val contentBlocking: ContentBlocking,
    networkLeaderboardDao: NetworkLeaderboardDao,
    private val pixel: Pixel,
    private val dispatcher: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    sealed class Command {
        class LaunchReportBrokenSite(val data: BrokenSiteData) : Command()
    }

    data class ViewState(
        val userSettingsViewState: UserSettingsViewState,
        val siteProtectionsViewState: SiteProtectionsViewState,
        val userChangedValues: Boolean = false
    )

    data class UserSettingsViewState(
        val privacyProtectionEnabled: Boolean
    )

    data class SiteProtectionsViewState(
        val url: String,
        val status: String = "complete",
        val upgradedHttps: Boolean,
        val parentEntity: EntityViewState?,
        val site: SiteViewState,
        val trackers: Map<String, TrackerViewState>,
        val trackerBlocked: Map<String, TrackerViewState>,
        val secCertificateViewModels: List<CertificateViewState?> = emptyList(),
        val locale: String = Locale.getDefault().language
    )

    data class CertificateViewState(
        val commonName: String,
        val publicKey: PublicKeyViewState? = null,
        val emails: List<String> = emptyList(),
        val summary: String? = null
    )

    data class PublicKeyViewState(
        val blockSize: Int?,
        val canEncrypt: Boolean?,
        val bitSize: Int?,
        val canSign: Boolean?,
        val canDerive: Boolean?,
        val canUnwrap: Boolean?,
        val canWrap: Boolean?,
        val canDecrypt: Boolean?,
        val effectiveSize: Int?,
        val isPermanent: Boolean?,
        val type: String?,
        val externalRepresentation: String?,
        val canVerify: Boolean?,
        val keyId: String?
    )

    data class EntityViewState(
        val displayName: String,
        val prevalence: Double
    )

    data class SiteViewState(
        val url: String,
        val domain: String,
        val trackersUrls: Set<String>,
        val whitelisted: Boolean,
    )

    data class TrackerViewState(
        val displayName: String,
        val prevalence: Double,
        val urls: Map<String, TrackerEventViewState>,
        val count: Int,
        val type: String = ""
    )

    data class TrackerEventViewState(
        val isBlocked: Boolean,
        val reason: String,
        val categories: Set<String> = emptySet()
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    private var site: Site? = null

    private val sitesVisited: LiveData<Int> = networkLeaderboardDao.sitesVisited()
    private val sitesVisitedObserver = Observer<Int> { onSitesVisitedChanged(it) }
    private val trackerNetworkLeaderboard: LiveData<List<NetworkLeaderboardEntry>> = networkLeaderboardDao.trackerNetworkLeaderboard()
    private val trackerNetworkActivityObserver = Observer<List<NetworkLeaderboardEntry>> { onTrackerNetworkEntriesChanged(it) }

    init {
        pixel.fire(PRIVACY_DASHBOARD_OPENED)
        resetViewState()
        sitesVisited.observeForever(sitesVisitedObserver)
        trackerNetworkLeaderboard.observeForever(trackerNetworkActivityObserver)
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onReportBrokenSiteSelected() {
        viewModelScope.launch(dispatcher.io()) {
            command.send(LaunchReportBrokenSite(BrokenSiteData.fromSite(site)))
        }
    }

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        sitesVisited.removeObserver(sitesVisitedObserver)
        trackerNetworkLeaderboard.removeObserver(trackerNetworkActivityObserver)
    }

    fun onSitesVisitedChanged(count: Int?) {
    }

    fun onTrackerNetworkEntriesChanged(networkLeaderboardEntries: List<NetworkLeaderboardEntry>?) {
    }

    private fun showTrackerNetworkLeaderboard(
        siteVisitedCount: Int,
        networkCount: Int
    ): Boolean {
        return siteVisitedCount > LEADERBOARD_MIN_DOMAINS_EXCLUSIVE && networkCount >= LEADERBOARD_MIN_NETWORKS
    }

    fun onSiteChanged(site: Site?) {
        Timber.i("PDHy: $site")
        this.site = site
        if (site == null) {
            resetViewState()
        } else {
            viewModelScope.launch { updateSite(site) }
        }
    }

    private fun resetViewState() {
    }

    private suspend fun updateSite(site: Site) {
        Timber.i("PDHy: will generate viewstate for site:$site entity:${site.entity}")
        withContext(dispatchers.main()) {
            val certificateViewState = site.certificate?.let {
                it
            }

            val trackingEvents: MutableMap<String, TrackerViewState> = mutableMapOf()
            val trackersBlocked: MutableMap<String, TrackerViewState> = mutableMapOf()
            val trackerUrls: MutableSet<String> = mutableSetOf()

            Timber.i("PDHy: site had ${site.trackingEvents.size} events / ${site.trackerCount}")

            site.trackingEvents.forEach {
                if (it.entity == null) return@forEach

                trackerUrls.add(it.trackerUrl)

                val trackerViewState: TrackerViewState = trackingEvents[it.entity.displayName]?.let { trackerViewState ->
                    val urls = trackerViewState.urls + Pair(
                        it.trackerUrl,
                        TrackerEventViewState(
                            isBlocked = it.blocked,
                            reason = "first party",
                            categories = it.categories?.toSet() ?: emptySet()
                        )
                    )
                    trackerViewState.copy(
                        urls = urls,
                        count = trackerViewState.count + 1
                    )
                } ?: TrackerViewState(
                    displayName = it.entity.displayName,
                    prevalence = it.entity.prevalence,
                    urls = mutableMapOf(
                        it.trackerUrl to TrackerEventViewState(
                            isBlocked = it.blocked,
                            reason = "first party",
                            categories = it.categories?.toSet() ?: emptySet()
                        )
                    ),
                    count = 1,
                    type = "here goes type" // TODO: ????
                )

                trackingEvents[it.entity.displayName] = trackerViewState
            }

            site.trackingEvents.filter { it.blocked }.forEach {
                if (it.entity == null) return@forEach

                val trackerViewState: TrackerViewState = trackersBlocked[it.entity.displayName]?.let { trackerViewState ->
                    val urls = trackerViewState.urls + Pair(
                        it.trackerUrl,
                        TrackerEventViewState(
                            isBlocked = it.blocked,
                            reason = "first party",
                            categories = it.categories?.toSet() ?: emptySet()
                        )
                    )
                    trackerViewState.copy(
                        urls = urls,
                        count = trackerViewState.count + 1
                    )
                } ?: TrackerViewState(
                    displayName = it.entity.displayName,
                    prevalence = it.entity.prevalence,
                    urls = mutableMapOf(
                        it.trackerUrl to TrackerEventViewState(
                            isBlocked = it.blocked,
                            reason = "first party",
                            categories = it.categories?.toSet() ?: emptySet()
                        )
                    ),
                    count = 1,
                    type = "here goes type" // TODO: ????
                )

                trackersBlocked[it.entity.displayName] = trackerViewState
            }

            val entityViewState = site.entity?.let {
                EntityViewState(
                    displayName = it.displayName,
                    prevalence = site.entity?.prevalence ?: 0.toDouble()
                )
            }

            viewState.value = ViewState(
                siteProtectionsViewState = SiteProtectionsViewState(
                    url = site.url,
                    upgradedHttps = site.upgradedHttps,
                    parentEntity = entityViewState,
                    site = SiteViewState(
                        url = site.url,
                        domain = site.domain!!,
                        trackersUrls = trackerUrls,
                        whitelisted = site.userAllowList
                    ),
                    trackers = trackingEvents,
                    trackerBlocked = trackersBlocked,
                    secCertificateViewModels = listOf(site.certificate?.map())
                ),
                userSettingsViewState = UserSettingsViewState(
                    privacyProtectionEnabled = !site.userAllowList
                )
            )
        }
    }

    fun onPrivacyProtectionsClicked(enabled: Boolean) {
        Timber.i("PDHy: onPrivacyProtectionsClicked newValue $enabled")
        appCoroutineScope.launch(dispatchers.io()) {
            if (enabled) {
                userWhitelistDao.delete(viewState.value!!.siteProtectionsViewState.site.domain)
                pixel.fire(PRIVACY_DASHBOARD_WHITELIST_REMOVE)
            } else {
                userWhitelistDao.insert(viewState.value!!.siteProtectionsViewState.site.domain)
                pixel.fire(PRIVACY_DASHBOARD_WHITELIST_ADD)
            }
            withContext(dispatcher.main()) {
                viewState.value = viewState.value!!.copy(
                    userSettingsViewState = viewState.value!!.userSettingsViewState.copy(privacyProtectionEnabled = enabled),
                    userChangedValues = true
                )
            }
        }
    }

    private companion object {
        private const val LEADERBOARD_MIN_NETWORKS = 3
        private const val LEADERBOARD_MIN_DOMAINS_EXCLUSIVE = 30
    }

    data class PublicKeyInfo(
        val blockSize: Int? = null,
        val canEncrypt: Boolean? = null,
        val bitSize: Int? = null,
        val canSign: Boolean? = null,
        val canDerive: Boolean? = null,
        val canUnwrap: Boolean? = null,
        val canWrap: Boolean? = null,
        val canDecrypt: Boolean? = null,
        val effectiveSize: Int? = null,
        val isPermanent: Boolean? = null,
        val type: String? = null,
        val externalRepresentation: String? = null,
        val canVerify: Boolean? = null,
        val keyId: String? = null
    )

    private fun SslCertificate.map(): CertificateViewState {
        val publicKeyInfo = publicKeyInfo()
        return CertificateViewState(
            commonName = this.issuedTo.cName,
            publicKey = publicKeyInfo?.let {
                PublicKeyViewState(
                    blockSize = publicKeyInfo.blockSize,
                    canEncrypt = publicKeyInfo.canEncrypt,
                    bitSize = publicKeyInfo.bitSize,
                    canSign = publicKeyInfo.canSign,
                    canDerive = publicKeyInfo.canDerive,
                    canUnwrap = publicKeyInfo.canUnwrap,
                    canWrap = publicKeyInfo.canWrap,
                    canDecrypt = publicKeyInfo.canDecrypt,
                    effectiveSize = publicKeyInfo.effectiveSize,
                    isPermanent = publicKeyInfo.isPermanent,
                    type = publicKeyInfo.type,
                    externalRepresentation = publicKeyInfo.externalRepresentation,
                    canVerify = publicKeyInfo.canVerify,
                    keyId = publicKeyInfo.keyId
                )
            }
        )
    }

    private fun SslCertificate.publicKeyInfo(): PublicKeyInfo? {
        if (Build.VERSION.SDK_INT < VERSION_CODES.Q) return null

        Timber.i("cert: issuedBy ${this.issuedBy.cName}")
        Timber.i("cert: issuedTo ${this.issuedTo.cName}")

        return this.x509Certificate?.let { it ->
            Timber.i("cert: constr ${it.basicConstraints}")
            Timber.i("cert: sigAlgName ${it.sigAlgName}")
            Timber.i("cert: sigAlgName size ${it.sigAlgName.utf8Size()}")
            Timber.i("cert: sigAlgOID ${it.sigAlgOID}")
            Timber.i("cert: sigAlgOID size ${it.sigAlgOID.utf8Size()}")
            Timber.i("cert: sigAlgParams ${it.sigAlgParams}")
            Timber.i("cert: sigAlgParams size ${it.sigAlgParams?.size}")
            Timber.i("cert: version ${it.version}")
            Timber.i("cert: signature size ${it.signature.size}")
            Timber.i("cert: certificatePem ${it.certificatePem()}")
            Timber.i("cert: certificatePem size ${it.certificatePem().utf8Size()}")
            Timber.i("cert: tbsCertificate ${it.tbsCertificate}")
            Timber.i("cert: tbsCertificate size ${it.tbsCertificate?.size}")
            it.keyUsage.forEach { usage ->
                Timber.i("cert: keyUsage $usage")
            }
            it.extendedKeyUsage.forEach { usage ->
                Timber.i("cert: extendedKeyUsage $usage")
            }
            Timber.i("cert: alg ${it.publicKey.algorithm}")
            Timber.i("cert: format ${it.publicKey.format}")
            Timber.i("cert: encoded ${it.publicKey.encoded}")
            Timber.i("cert: encoded size ${it.publicKey.encoded.size}")

            val bundle = SslCertificate.saveState(this)
            val bytes = bundle.getByteArray("x509-certificate")
            Timber.i("cert: bytes ${bytes!!.size}")

            PublicKeyInfo(
                type = it.publicKey.algorithm,
                bitSize = it.signature.size * 8,
                effectiveSize = it.signature.size * 8
            )
        }
    }
}
