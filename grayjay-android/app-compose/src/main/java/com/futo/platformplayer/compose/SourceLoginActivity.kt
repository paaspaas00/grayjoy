package com.futo.platformplayer.compose

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.others.LoginWebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class SourceLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val loginCompleted = AtomicBoolean(false)
    private var channelWarningShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
        val configUrl = intent.getStringExtra(EXTRA_CONFIG_URL).orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty().ifBlank { "main" }
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID).orEmpty()
        if (pluginId.isBlank() || configUrl.isBlank() || sourceId.isBlank()) {
            finishWithError(getString(R.string.source_no_login_configuration))
            return
        }
        showLoading()
        lifecycleScope.launch {
            val config = runCatching {
                val cached = File(filesDir, "grayjay-js-plugins/$pluginId/config.json")
                val text = withContext(Dispatchers.IO) {
                    if (cached.isFile) cached.readText() else URL(configUrl).readText()
                }
                SourcePluginConfig.fromJson(text, configUrl)
            }.getOrElse {
                finishWithError(it.localizedMessage ?: getString(R.string.source_login_page_failed))
                return@launch
            }
            val auth = config.authentication
            if (auth == null) {
                finishWithError(getString(R.string.source_does_not_support_login, config.name))
                return@launch
            }
            showWebLogin(config, profileId, sourceId)
        }
    }

    private fun showLoading() {
        setContentView(
            TextView(this).apply {
                text = getString(R.string.loading_source_login)
                gravity = Gravity.CENTER
            },
        )
    }

    private fun showWebLogin(
        config: SourcePluginConfig,
        profileId: String,
        sourceId: String,
    ) {
        val authConfig = requireNotNull(config.authentication)
        val statusText = TextView(this).apply {
            text = getString(R.string.sign_in_to_source_name, config.name)
            maxLines = 2
            setPadding(24, 18, 12, 18)
        }
        val close = Button(this).apply {
            text = getString(R.string.close)
            setOnClickListener { finish() }
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
        }
        val browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            authConfig.userAgent?.let { settings.userAgentString = it }
        }
        webView = browser
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }
        val client = LoginWebViewClient(config, browser.settings.userAgentString)
        client.onPageLoaded.subscribe { _, url ->
            val pageUri = Uri.parse(url.orEmpty())
            val isChannelSwitcher = url.orEmpty().contains("/channel_switcher", ignoreCase = true) ||
                (
                    config.name.equals("Youtube", ignoreCase = true) &&
                        pageUri.host.orEmpty().endsWith(".youtube.com", ignoreCase = true) &&
                        pageUri.path == "/account"
                    )
            statusText.text = when {
                isChannelSwitcher ->
                    getString(R.string.select_youtube_channel_to_finish)
                Uri.parse(url.orEmpty()).host.orEmpty().contains("accounts.google", ignoreCase = true) ->
                    getString(R.string.sign_in_to_source_name, config.name)
                else -> getString(R.string.finishing_source_sign_in, config.name)
            }
            if (isChannelSwitcher && !channelWarningShown) {
                channelWarningShown = true
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.select_youtube_channel))
                    .setMessage(
                        authConfig.loginWarning
                            ?: getString(R.string.youtube_channel_choice_description),
                    )
                    .setPositiveButton(getString(R.string.continue_action), null)
                    .show()
            }
            maybeCompleteYoutubeLogin(
                config = config,
                profileId = profileId,
                sourceId = sourceId,
                browser = browser,
                pageUri = pageUri,
                isChannelSwitcher = isChannelSwitcher,
            )
        }
        client.onLogin.subscribe { auth ->
            runOnUiThread {
                completeLogin(profileId, config.id, sourceId, auth)
            }
        }
        browser.webViewClient = client
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        browser.loadUrl(authConfig.loginUrl)
    }

    private fun maybeCompleteYoutubeLogin(
        config: SourcePluginConfig,
        profileId: String,
        sourceId: String,
        browser: WebView,
        pageUri: Uri,
        isChannelSwitcher: Boolean,
    ) {
        if (!config.name.equals("Youtube", ignoreCase = true) || isChannelSwitcher) return
        val host = pageUri.host.orEmpty()
        val isYoutubeHome = host.endsWith(".youtube.com", ignoreCase = true) &&
            (pageUri.path.isNullOrBlank() || pageUri.path == "/")
        if (!isYoutubeHome) return

        val cookieManager = CookieManager.getInstance()
        val cookies = hashMapOf<String, String>()
        listOf(
            "https://www.youtube.com/",
            "https://m.youtube.com/",
            pageUri.toString(),
        ).forEach { url ->
            parseCookies(cookieManager.getCookie(url)).forEach { (name, value) ->
                cookies[name] = value
            }
        }

        val hasConfiguredCookie = config.authentication?.cookiesToFind
            ?.all(cookies::containsKey) ?: true
        val hasSigningCookie = listOf(
            "SAPISID",
            "__Secure-1PAPISID",
            "__Secure-3PAPISID",
        ).any(cookies::containsKey)
        if (!hasConfiguredCookie || !hasSigningCookie) return

        val auth = SourceAuth(
            cookieMap = hashMapOf(".youtube.com" to cookies),
            headers = mapOf(
                ".youtube.com" to mapOf(
                    "authorization" to YOUTUBE_DYNAMIC_AUTH_MARKER,
                ),
            ),
            userAgent = browser.settings.userAgentString,
        )
        completeLogin(profileId, config.id, sourceId, auth)
    }

    private fun parseCookies(cookieHeader: String?): Map<String, String> = buildMap {
        cookieHeader.orEmpty().split(';').forEach { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@forEach
            val name = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) put(name, value)
        }
    }

    private fun completeLogin(
        profileId: String,
        pluginId: String,
        sourceId: String,
        auth: SourceAuth,
    ) {
        if (!loginCompleted.compareAndSet(false, true)) return
        GrayjayPluginAuthStore.save(this, profileId, pluginId, auth)
        CookieManager.getInstance().flush()
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_SOURCE_ID, sourceId),
        )
        finish()
    }

    private fun finishWithError(message: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        setContentView(
            TextView(this).apply {
                text = message
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            },
        )
    }

    override fun onDestroy() {
        webView?.apply {
            loadUrl("about:blank")
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_ERROR = "error"
        private const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val EXTRA_CONFIG_URL = "config_url"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val YOUTUBE_DYNAMIC_AUTH_MARKER = "__GRAYJAY_DYNAMIC_SAPISIDHASH__"

        fun intent(context: Context, sourceId: String, pluginId: String, configUrl: String, profileId: String) =
            Intent(context, SourceLoginActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_ID, sourceId)
                putExtra(EXTRA_PLUGIN_ID, pluginId)
                putExtra(EXTRA_CONFIG_URL, configUrl)
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
    }
}
