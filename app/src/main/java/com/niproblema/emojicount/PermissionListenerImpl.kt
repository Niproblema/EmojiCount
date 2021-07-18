package com.niproblema.emojicount

import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

class PermissionListenerImpl(
    onAccepted: (PermissionGrantedResponse?) -> Unit,
    onDenied: (PermissionDeniedResponse?) -> Unit,
    onShown: (PermissionRequest?, PermissionToken?) -> Unit
) : PermissionListener {

    private val onAccept = onAccepted
    private val onDeny = onDenied
    private val onShow = onShown

    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
        onAccept(p0)
    }

    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
        onDeny(p0)
    }

    override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {
        onShow(p0, p1);
    }
}