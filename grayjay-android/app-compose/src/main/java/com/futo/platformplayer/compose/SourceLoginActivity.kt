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
import android.webkit.WebViewClient
import android.webkit.WebStorage
import android.webkit.RenderProcessGoneDetail
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SourceLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val loginCompleted = AtomicBoolean(false)
    private var channelWarningShown = false
    private var pendingProfileAuth: SourceAuth? = null

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
                withContext(Dispatchers.IO) {
                    SourcePluginConfig.fromJson(loadSourceLoginConfig(cached, configUrl), configUrl)
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                finishWithError(it.localizedMessage ?: getString(R.string.source_login_page_failed))
                return@launch
            }
            val auth = config.authentication
            if (auth == null) {
                finishWithError(getString(R.string.source_does_not_support_login, config.name))
                return@launch
            }
            val savedAuth = withContext(Dispatchers.IO) {
                GrayjayPluginAuthStore.load(this@SourceLoginActivity, profileId, config.id)
            }
            showWebLogin(config, profileId, sourceId, savedAuth)
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
        savedAuth: SourceAuth?,
    ) {
        val authConfig = requireNotNull(config.authentication)
        val chooseAccountProfile = sourceId.equals("crunchyroll", ignoreCase = true)
        pendingProfileAuth = savedAuth
        val statusText = TextView(this).apply {
            text = getString(R.string.sign_in_to_source_name, config.name)
            maxLines = if (chooseAccountProfile) 4 else 2
            setPadding(24, 18, 12, 18)
        }
        val close = Button(this).apply {
            text = getString(R.string.close)
            setOnClickListener { finish() }
        }
        val useProfile = Button(this).apply {
            text = getString(R.string.use_selected_source_profile)
            isEnabled = false
            visibility = if (chooseAccountProfile) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener {
                val latest = pendingProfileAuth ?: return@setOnClickListener
                val manager = CookieManager.getInstance()
                val cookies = hashMapOf<String, HashMap<String, String>>()
                latest.cookieMap.orEmpty().forEach { (domain, values) ->
                    if (domain.trimStart('.').let { it == "crunchyroll.com" || it.endsWith(".crunchyroll.com") }) {
                        cookies[domain] = HashMap(values)
                    }
                }
                val current = parseCookies(manager.getCookie("https://www.crunchyroll.com/"))
                val sessionCookie = current["etp_rt"]?.takeIf(String::isNotBlank)
                    ?: return@setOnClickListener
                // Replace every captured copy: a stale parent-domain refresh cookie must not
                // override the profile selected on the website.
                cookies.values.forEach { it["etp_rt"] = sessionCookie }
                cookies.getOrPut(".crunchyroll.com") { hashMapOf() }.putAll(current)
                completeLogin(profileId, config.id, sourceId, SourceAuth(
                    cookieMap = cookies, headers = latest.headers, userAgent = latest.userAgent,
                ))
            }
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
        val client = object : LoginWebViewClient(config, browser.settings.userAgentString) {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Profile selection can be an SPA navigation without onPageFinished.
                if (chooseAccountProfile && webView === view) {
                    useProfile.isEnabled = pendingProfileAuth != null &&
                        isSourceProfileConfirmationPage(url.orEmpty())
                }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // This WebView can never be reused after renderer termination.
                val wasCurrent = webView === view
                if (wasCurrent) webView = null
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (wasCurrent) finishWithError(getString(R.string.source_login_page_failed))
                return true
            }
        }
        client.onPageLoaded.subscribe { _, url ->
            if (isFinishing || isDestroyed || webView !== browser) return@subscribe
            val pageUri = Uri.parse(url.orEmpty())
            if (chooseAccountProfile) {
                statusText.text = getString(R.string.choose_source_profile_instructions)
                useProfile.isEnabled = pendingProfileAuth != null &&
                    isSourceProfileConfirmationPage(url.orEmpty())
            }
            val isChannelSwitcher = url.orEmpty().contains("/channel_switcher", ignoreCase = true) ||
                (
                    config.name.equals("Youtube", ignoreCase = true) &&
                        pageUri.host.orEmpty().endsWith(".youtube.com", ignoreCase = true) &&
                        pageUri.path == "/account"
                    )
            if (!chooseAccountProfile) statusText.text = when {
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
                if (isFinishing || isDestroyed || webView !== browser || loginCompleted.get()) {
                    return@runOnUiThread
                }
                if (chooseAccountProfile) {
                    pendingProfileAuth = auth
                    useProfile.isEnabled = isSourceProfileConfirmationPage(browser.url.orEmpty())
                } else {
                    completeLogin(profileId, config.id, sourceId, auth)
                }
            }
        }
        browser.webViewClient = client
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(useProfile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        val openLogin = {
            if (!isFinishing && !isDestroyed && webView === browser) {
                browser.loadUrl(if (chooseAccountProfile && savedAuth != null) {
                    "https://www.crunchyroll.com/"
                } else authConfig.loginUrl)
            }
        }
        val seedCookies = sourceLoginCookieSeeds(savedAuth)
        // CookieManager is app-wide, while plugin credentials belong to a Grayjoy profile.
        // Finish clearing the previous web session before restoring only this profile's cookies.
        // This does not remove the saved authentication of any source or profile.
        val cookieManager = CookieManager.getInstance()
        WebStorage.getInstance().deleteAllData()
        cookieManager.removeAllCookies {
            if (!isFinishing && !isDestroyed && webView === browser) {
                if (seedCookies.isEmpty()) openLogin() else {
                    val remaining = java.util.concurrent.atomic.AtomicInteger(seedCookies.size)
                    seedCookies.forEach { (url, cookie) ->
                        cookieManager.setCookie(url, cookie) {
                            if (remaining.decrementAndGet() == 0) runOnUiThread { openLogin() }
                        }
                    }
                }
            }
        }
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
        if (isFinishing || isDestroyed || webView == null) return
        if (!loginCompleted.compareAndSet(false, true)) return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    GrayjayPluginAuthStore.save(this@SourceLoginActivity, profileId, pluginId, auth)
                    CookieManager.getInstance().flush()
                }
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_SOURCE_ID, sourceId),
                )
                finish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                finishWithError(error.localizedMessage ?: getString(R.string.source_login_page_failed))
            }
        }
    }

    private fun finishWithError(message: String) {
        if (isFinishing || isDestroyed) return
        disposeWebView()
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
        disposeWebView()
        super.onDestroy()
    }

    private fun disposeWebView() {
        val browser = webView
        webView = null
        browser?.apply {
            webViewClient = WebViewClient()
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            destroy()
        }
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

internal fun isSourceProfileConfirmationPage(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty().lowercase()
    uri.scheme == "https" && (host == "www.crunchyroll.com" || host == "crunchyroll.com") &&
        !path.contains("login") && !path.contains("profile")
}.getOrDefault(false)
