package com.futo.platformplayer.compose.engine

import android.webkit.WebView
import android.webkit.ValueCallback
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.V8PluginConfig
import com.futo.platformplayer.engine.packages.PackageBrowser
import com.caoccao.javet.values.reference.V8ValueFunction
import org.junit.Assert.*
import org.junit.Test

class BrowserLifecycleRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val field = PackageBrowser::class.java.getDeclaredField("_browser").apply { isAccessible = true }
    private class ProbeView(context: android.content.Context) : WebView(context) {
        var destroyed = false
        var loadedUrl: String? = null
        var reply: ValueCallback<String>? = null
        override fun loadUrl(url: String) { loadedUrl = url }
        override fun getUrl(): String? {
            assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
            return "about:blank"
        }
        override fun destroy() { destroyed = true; super.destroy() }
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) { reply = resultCallback }
    }
    private fun create(): Pair<V8Plugin, PackageBrowser> {
        val plugin = V8Plugin(instrumentation.targetContext, V8PluginConfig(), "globalThis.calls = 0;")
        return plugin to PackageBrowser(plugin)
    }
    @Test fun queuedDestructionDestroysOldViewNotNewlyInitializedView() {
        val (_, browser) = create()
        lateinit var old: ProbeView
        lateinit var next: ProbeView
        instrumentation.runOnMainSync {
            old = ProbeView(instrumentation.targetContext)
            next = ProbeView(instrumentation.targetContext)
            field.set(browser, old)
            browser.load("https://stale.invalid/")
            browser.deinitialize()
            field.set(browser, next)
        }
        instrumentation.waitForIdleSync()
        assertTrue(old.destroyed)
        assertFalse(next.destroyed)
        assertNull(next.loadedUrl)
        browser.deinitialize()
        instrumentation.waitForIdleSync()
        assertTrue(next.destroyed)
    }
    @Test fun duplicateBrowserReplyCannotReuseAClosedNativeFunction() = withBrowser { plugin, browser, view ->
        plugin.busy {
            plugin.executeTyped<V8ValueFunction>("(function(value) { globalThis.calls++; })").use {
                browser.runWithReturn("ignored", it)
            }
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            requireNotNull(view.reply).onReceiveValue("\"first\"")
            requireNotNull(view.reply).onReceiveValue("\"second\"")
        }
        await { plugin.busy { plugin._runtime!!.getExecutor("calls").executeInteger() } == 1 }
        Thread.sleep(100)
        assertEquals(1, plugin.busy { plugin._runtime!!.getExecutor("calls").executeInteger() })
    }
    @Test fun currentUrlIsReadOnTheWebViewThread() = withBrowser { _, browser, _ ->
        assertEquals("about:blank", browser.getCurrentUrl())
    }
    @Test fun deinitializationClosesCallbacksEvenWhenWebViewNeverReplies() = withBrowser { plugin, browser, _ ->
        plugin.busy {
            plugin.executeTyped<V8ValueFunction>("(function(value) {})").use { browser.runWithReturn("ignored", it) }
        }
        instrumentation.waitForIdleSync()
        val callbacksField = PackageBrowser::class.java.getDeclaredField("_callbacks").apply { isAccessible = true }
        val callbacks = callbacksField.get(browser) as Map<*, *>
        val pending = callbacks.values.single()!!
        val function = pending.javaClass.getDeclaredField("function").apply { isAccessible = true }.get(pending) as V8ValueFunction
        browser.deinitialize()
        assertTrue(function.isClosed)
        assertTrue(callbacks.isEmpty())
    }
    @Test fun lateReplyFromPreviousRuntimeCannotCallIntoRestartedRuntime() = withBrowser { plugin, browser, view ->
        plugin.busy {
            plugin.executeTyped<V8ValueFunction>("(function(value) { globalThis.calls++; })").use {
                browser.runWithReturn("ignored", it)
            }
        }
        instrumentation.waitForIdleSync()
        val reply = requireNotNull(view.reply)
        plugin.stop()
        plugin.start()
        instrumentation.runOnMainSync { reply.onReceiveValue("\"late\"") }
        Thread.sleep(150)
        assertEquals(0, plugin.busy { plugin._runtime!!.getExecutor("calls").executeInteger() })
    }
    private fun withBrowser(block: (V8Plugin, PackageBrowser, ProbeView) -> Unit) {
        val (plugin, browser) = create()
        lateinit var view: ProbeView
        instrumentation.runOnMainSync { view = ProbeView(instrumentation.targetContext); field.set(browser, view) }
        try { plugin.start(); block(plugin, browser, view) }
        finally { browser.deinitialize(); plugin.stop(); instrumentation.waitForIdleSync() }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(predicate())
    }
}
