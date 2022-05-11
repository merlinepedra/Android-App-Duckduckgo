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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.privacy.ui.PrivacyDashboardHybridViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PrivacyDashboardHybridActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var moshi: Moshi

    private val binding: ActivityPrivacyHybridDashboardBinding by viewBinding()

    private val webView
        get() = binding.privacyDashboardWebview

    private val viewModel: PrivacyDashboardHybridViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupClickListeners()
        configureWebView()
        Timber.i("")
        webView.loadUrl("file:///android_asset/html/popup.html")
        repository.retrieveSiteData(intent.tabId!!).observe(
            this
        ) {
            viewModel.onSiteChanged(it)
        }
    }

    private fun configureWebView() {
        with(webView.settings) {
            builtInZoomControls = false
            javaScriptEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                Timber.i("PDHy: onPageFinished")
                setupObservers()
            }
        }

        webView.addJavascriptInterface(PrivacyDashboardJavascriptInterface(), PrivacyDashboardJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    private fun setupObservers() {
        viewModel.viewState.observe(
            this
        ) {
            Timber.i("PDHy: newEvent $it")
            val adapter = moshi.adapter(ViewState::class.java)
            val json = adapter.toJson(it)
            Timber.i("PDHy: received $json")
            webView.evaluateJavascript("javascript:onChangeTrackerBlockingData(\"${it.url}\", $json);", null)
            webView.evaluateJavascript("javascript:onChangeUpgradedHttps(true);", null)
            webView.evaluateJavascript("javascript:onChangeProtectionStatus(true);", null)
        }
    }

    private fun setupClickListeners() {
    }

    private fun updateActivityResult(shouldReload: Boolean) {
        if (shouldReload) {
            setResult(RELOAD_RESULT_CODE)
        } else {
            setResult(Activity.RESULT_OK)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {

        const val RELOAD_RESULT_CODE = 100

        fun intent(
            context: Context,
            tabId: String
        ): Intent {
            val intent = Intent(context, PrivacyDashboardHybridActivity::class.java)
            intent.tabId = tabId
            return intent
        }
    }
}
