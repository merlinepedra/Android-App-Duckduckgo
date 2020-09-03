/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.model.TrustedSites
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.TrackerDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

interface RequestInterceptor {

    @WorkerThread
    suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): WebResourceResponse?
}

class WebViewRequestInterceptor(
    private val resourceSurrogates: ResourceSurrogates,
    private val trackerDetector: TrackerDetector,
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
    private val webViewHttpClient: OkHttpClient

) : RequestInterceptor {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    /**
     * Notify the application of a resource request and allow the application to return the data.
     *
     * If the return value is null, the WebView will continue to load the resource as usual.
     * Otherwise, the return response and data will be used.
     *
     * NOTE: This method is called on a thread other than the UI thread so clients should exercise
     * caution when accessing private data or the view system.
     */
    @WorkerThread
    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): WebResourceResponse? {
        val url = request.url

        return withContext(Dispatchers.IO) {
            val normalResponse = normalDetection(url, request, webView, documentUrl, webViewClientListener)
            if (normalResponse != null) return@withContext normalResponse

            val shouldUseExperimentalNetworkFetcher = true
            if (!shouldUseExperimentalNetworkFetcher) return@withContext null
            Timber.i("Normal response was null, meaning we should continue the load. Do the manual loading now for $url")

            if (request.method != "GET") {
                Timber.v("Can't handle request.method: ${request.method}. Delegating to WebView to load resource $url")
                return@withContext null
            }

            val httpClientRequestBuilder = Request.Builder().url(url.toString())

            addHeadersFromOriginalRequest(request, httpClientRequestBuilder)
            addCustomHttpHeader(httpClientRequestBuilder)
            addCookies(url.toString(), httpClientRequestBuilder)

            try {
                val response = webViewHttpClient.newCall(httpClientRequestBuilder.build()).execute()
                if (response.isSuccessful) {

                    val mimeTypeMain = response.body()?.contentType()?.type()
                    val mimeTypeSub = response.body()?.contentType()?.subtype()
                    val encoding = response.body()?.contentType()?.charset()?.name()
                    val data = response.body()?.byteStream()

                    val mimeType = if (mimeTypeMain != null && mimeTypeSub != null) "$mimeTypeMain/$mimeTypeSub" else null
                    Timber.i("code: ${response.code()}, reason: ${response.message()}, mimeType: $mimeType, encoding: $encoding url: $url")

                    var message = response.message()
                    if (message.isNullOrEmpty()) message = "unknown"

                    val cookieHeaders = response.headers("Set-Cookie")
                    cookieHeaders.forEach { cookie ->
                        Timber.i("Cookies: $cookie")
                        cookieManager.setCookie(url.toString(), cookie)
                    }

                    return@withContext WebResourceResponse(mimeType, encoding, response.code(), message, response.headers().flattened(), data)
                } else {
                    return@withContext blockedResponse()
                }

            } catch (e: IOException) {
                Timber.w(e, "Failed to obtain resource $url")
                return@withContext blockedResponse()
            }
        }
    }

    private fun addCookies(url: String, httpClientRequestBuilder: Request.Builder) {
        val cookie = cookieManager.getCookie(url)
        if (cookie != null) {
            httpClientRequestBuilder.addHeader("Cookie", cookie)
        }
    }

    private fun addHeadersFromOriginalRequest(request: WebResourceRequest, httpClientRequestBuilder: Request.Builder) {
        request.requestHeaders.forEach {
            httpClientRequestBuilder.addHeader(it.key, it.value)
        }
    }

    private fun addCustomHttpHeader(httpClientRequestBuilder: Request.Builder) {
        httpClientRequestBuilder.addHeader("X-DDG-Test", "it works!")
    }

    private suspend fun normalDetection(
        url: Uri,
        request: WebResourceRequest,
        webView: WebView,
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): WebResourceResponse? {
        if (shouldUpgrade(request)) {
            val newUri = httpsUpgrader.upgrade(url)

            withContext(Dispatchers.Main) {
                webView.loadUrl(newUri.toString())
            }

            webViewClientListener?.upgradedToHttps()
            privacyProtectionCountDao.incrementUpgradeCount()
            return blockedResponse()
        }

        if (documentUrl == null) return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        if (url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUrl)
        }

        if (shouldBlock(request, documentUrl, webViewClientListener)) {
            val surrogate = resourceSurrogates.get(url)
            if (surrogate.responseAvailable) {
                Timber.d("Surrogate found for $url")
                webViewClientListener?.surrogateDetected(surrogate)
                return WebResourceResponse(surrogate.mimeType, "UTF-8", surrogate.jsFunction.byteInputStream())
            }

            Timber.d("Blocking request $url")
            privacyProtectionCountDao.incrementBlockedTrackerCount()
            return blockedResponse()
        }

        return null
    }

    private fun blockedResponse() = WebResourceResponse(null, null, null)

    private fun shouldUpgrade(request: WebResourceRequest) =
        request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun shouldBlock(request: WebResourceRequest, documentUrl: String?, webViewClientListener: WebViewClientListener?): Boolean {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return false
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl) ?: return false
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent.blocked
    }

}

private fun Headers.flattened(): MutableMap<String, String> {
    val headers = mutableMapOf<String, String>()
    this.names().forEach { name ->
        this[name]?.let { value ->
            headers.put(name, value)
        }
    }
    return headers
}
