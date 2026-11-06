package com.arcanatable

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private const val PROJECT_PREF = "arcana_studio_project_v1"
private const val MAX_PROJECT = 250_000
private const val MAX_EXPORT = 12_000_000
private const val STUDIO_URL = "https://arcana-studio.invalid/"

/** Studio position edits are saved as designs; only its color roles map to live native UI today. */
internal fun readStudioTheme(project: String): TableColors? {
    if (project.length > MAX_PROJECT) return null
    return runCatching {
        val obj = JSONObject(project)
        if (obj.optInt("version") != 1) return null
        val layouts = obj.optJSONObject("layouts") ?: return null
        if (listOf("hand", "board", "combat").any { layouts.optJSONArray(it) == null }) return null
        val theme = obj.optJSONObject("theme") ?: return null
        var colors = darkTableColors
        for ((role, _) in tableColorRoles) {
            val key = if (role == "counter") "gold" else role
            colors = colors.withColorRole(role, parseTableColorHex(theme.optString(key)) ?: return null)
        }
        colors
    }.getOrNull()
}

internal fun studioThemeJson(colors: TableColors): String = JSONObject().apply {
    for ((role, _) in tableColorRoles) {
        put(if (role == "counter") "gold" else role, tableColorHex(colors.colorRole(role)))
    }
}.toString()

private data class StudioExport(val name: String, val mime: String, val payload: String)

/** Only the packaged, offline Studio page can reach this restricted bridge. */
private class StudioBridge(
    private val projectChanged: (String) -> Unit,
    private val exportRequested: (String, String, String) -> Unit,
) {
    @JavascriptInterface fun saveProject(json: String) {
        if (json.length in 1..MAX_PROJECT) projectChanged(json)
    }

    @JavascriptInterface fun exportFile(dataUrl: String, filename: String, mime: String) {
        if (dataUrl.length in 1..MAX_EXPORT && mime in setOf("application/json", "image/png")) {
            exportRequested(dataUrl, filename, mime)
        }
    }
}

private const val STUDIO_SHIM = """
<script>
(function () {
  'use strict';
  var oldPersist = persist, saveTimer;
  persist = function () {
    oldPersist();
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      if (window.ArcanaStudioHost) ArcanaStudioHost.saveProject(JSON.stringify(state));
    }, 350);
  };
  var oldDownload = download;
  download = function (data, name, mime) {
    if (!window.ArcanaStudioHost) return oldDownload(data, name, mime);
    var reader = new FileReader();
    reader.onload = function () {
      ArcanaStudioHost.exportFile(String(reader.result), name, mime);
    };
    reader.readAsDataURL(data instanceof Blob ? data : new Blob([data], {type:mime}));
  };
})();
</script>
"""

@Composable
internal fun ArcanaStudioPage(onClassic: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    val editor = LocalTablePaletteEditor.current
    val activeColors by rememberUpdatedState(editor?.selectedColors ?: darkTableColors)
    val applyColors by rememberUpdatedState(editor?.onStudioTheme)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingExport by remember { mutableStateOf<StudioExport?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        // Only an explicit user-selected content:// URI can reach Studio's FileReader.
        val picked = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            ?.filter { it.scheme == "content" }?.take(1)?.toTypedArray()
        callback?.onReceiveValue(picked?.takeIf { it.isNotEmpty() })
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val export = pendingExport
        pendingExport = null
        if (export != null && result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val ok = runCatching {
                val data = export.payload.substringAfter("base64,", "")
                require(data.isNotBlank())
                val decoded = Base64.decode(data, Base64.DEFAULT)
                require(decoded.size <= 8_000_000)
                context.contentResolver.openOutputStream(result.data!!.data!!)?.use { it.write(decoded) }
                    ?: error("No writable destination")
            }.isSuccess
            Toast.makeText(context, if (ok) "Studio design saved" else "Could not export Studio design",
                Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(webView) {
        val view = webView
        onDispose {
            view?.apply {
                stopLoading()
                removeJavascriptInterface("ArcanaStudioHost")
                webChromeClient = null
                loadUrl("about:blank")
                destroy()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.99f).fillMaxHeight(.97f), color = Panel) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ARCANA STUDIO", color = SoftWhite, fontSize = 14.sp,
                        fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClassic, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("CLASSIC CONTROLS", color = Mint, fontSize = 10.sp)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("DONE", color = Mint, fontWeight = FontWeight.Bold)
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { hostContext ->
                        val view = WebView(hostContext)
                        view.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            // File inputs use only system-picker-selected content:// URIs; restrict schemes above.
                            allowContentAccess = true
                            blockNetworkLoads = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportMultipleWindows(false)
                        }
                        view.isHorizontalScrollBarEnabled = false
                        view.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?,
                            ): Boolean = true // No in-editor web navigation or external content.

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view == null || url != STUDIO_URL) return
                                val saved = prefs.getString(PROJECT_PREF, null)?.takeIf { readStudioTheme(it) != null }
                                val projectScript = if (saved != null)
                                    "state = JSON.parse(" + JSONObject.quote(saved) + ");" else ""
                                val themeScript = studioThemeJson(activeColors)
                                view.evaluateJavascript("""
                                    (function () {
                                      try {
                                        $projectScript
                                        state.theme = $themeScript;
                                        render();
                                      } catch (e) { console.error('Studio restore failed', e); }
                                    })();
                                """.trimIndent(), null)
                            }
                        }
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?, callback: ValueCallback<Array<Uri>>?,
                                params: WebChromeClient.FileChooserParams?,
                            ): Boolean {
                                if (callback == null || params == null) return false
                                fileCallback?.onReceiveValue(null)
                                fileCallback = callback
                                return runCatching { importPicker.launch(params.createIntent()); true }.getOrElse {
                                    fileCallback = null
                                    callback.onReceiveValue(null)
                                    true
                                }
                            }

                            override fun onJsConfirm(
                                view: WebView?, url: String?, message: String?, result: JsResult?,
                            ): Boolean {
                                if (result == null) return false
                                android.app.AlertDialog.Builder(hostContext)
                                    .setMessage(message ?: "Confirm layout change?")
                                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                                    .setNegativeButton("CANCEL") { _, _ -> result.cancel() }
                                    .setOnCancelListener { result.cancel() }
                                    .show()
                                return true
                            }
                        }
                        val mainThread = Handler(Looper.getMainLooper())
                        view.addJavascriptInterface(
                            StudioBridge(
                                projectChanged = { project ->
                                    mainThread.post {
                                        val colors = readStudioTheme(project) ?: return@post
                                        prefs.edit().putString(PROJECT_PREF, project).apply()
                                        if (colors != activeColors) applyColors?.invoke(colors)
                                    }
                                },
                                exportRequested = { url, filename, mime ->
                                    mainThread.post {
                                        if (!url.startsWith("data:$mime;base64,")) return@post
                                        val name = filename.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                                            .ifBlank { if (mime == "image/png") "Studio.png" else "Studio.json" }
                                        pendingExport = StudioExport(name, mime, url)
                                        exportPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                                            .addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
                                            .putExtra(Intent.EXTRA_TITLE, name))
                                    }
                                },
                            ), "ArcanaStudioHost",
                        )
                        val html = hostContext.assets.open("studio/arcana-layout-studio.html")
                            .bufferedReader(Charsets.UTF_8).use { it.readText() }
                            .replace("</body>", "$STUDIO_SHIM</body>")
                        view.loadDataWithBaseURL(STUDIO_URL, html, "text/html", "UTF-8", null)
                        webView = view
                        view
                    },
                )
                Text("PALETTES APPLY LIVE · FREE-POSITION LAYOUTS ARE SAVED PREVIEWS, NOT YET IN-GAME CONTROLS",
                    color = Muted, fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
