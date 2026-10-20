package ai.opencode.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Shows status while the engine volunteers a loopback port, then hosts the upstream
 * web UI (served by the engine) in a WebView pinned to 127.0.0.1:<port>.
 */
class MainActivity : Activity() {

    private var webView: WebView? = null
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        statusView = TextView(this).apply {
            text = getString(R.string.engine_starting)
            gravity = Gravity.CENTER
        }
        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(root)

        requestNotificationPermissionIfNeeded()

        // Keep the engine alive while the activity is foregrounded; the service is sticky.
        startForegroundService(Intent(this, EngineService::class.java))

        lifecycleScope.launch {
            EngineStateBus.status.collect { status ->
                when (status) {
                    is EngineStatus.Ready -> showEngineUi(root, status.port)
                    is EngineStatus.Failed -> statusView.text =
                        "${getString(R.string.engine_failed)}: ${status.message}"
                    else -> statusView.text = getString(R.string.engine_starting)
                }
            }
        }
    }

    private fun showEngineUi(root: FrameLayout, port: Int) {
        if (webView != null) return
        statusView.text = ""

        val engineBase = "http://127.0.0.1:$port"
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // Debug inspector (chrome://inspect) in debug builds only.
            setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return false
                    // Keep engine-served pages inside; external links leave to the browser.
                    return if (url.host in LOCALHOSTS && url.port == port) {
                        false
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        true
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        webView = wv
        wv.loadUrl("$engineBase/")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF,
            )
        }
    }

    private companion object {
        const val REQ_NOTIF = 42
        val LOCALHOSTS = setOf("127.0.0.1", "localhost")
    }
}
