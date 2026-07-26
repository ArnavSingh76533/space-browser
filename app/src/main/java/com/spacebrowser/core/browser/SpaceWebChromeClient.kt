package com.spacebrowser.core.browser

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class SpaceWebChromeClient(
    private val tab: Tab,
    private val deps: TabManagerDeps,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        tab.isLoading = newProgress < 100
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = title.orEmpty()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        return try {
            deps.events.requestFileChooser(filePathCallback, fileChooserParams.createIntent())
            true
        } catch (_: Exception) {
            filePathCallback.onReceiveValue(null)
            false
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val wantsHardware = request.resources.any {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        if (!wantsHardware || !deps.settings().askCameraMic) {
            // Privacy default: sites cannot even ask for camera/mic (this also
            // covers WebRTC device capture) unless the user opts in.
            request.deny()
            return
        }
        deps.events.sitePermissionRequest.value = request
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (deps.events.sitePermissionRequest.value === request) {
            deps.events.sitePermissionRequest.value = null
        }
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        if (!deps.settings().askLocation) {
            callback.invoke(origin, false, false)
            return
        }
        deps.events.geoRequest.value = GeoRequest(origin, callback)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        deps.events.geoRequest.value = null
    }
}
