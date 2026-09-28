package com.sonothamin.meowlaundry.print

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Where to get the MeowSpool app. */
object MeowSpoolInstall {
    const val RELEASES_URL = "https://github.com/sonothamin/MeowSpool/releases/latest"

    /**
     * True if the MeowSpool app is installed on this device. Relies on the `<queries>` entry
     * in the manifest for package visibility on Android 11+.
     */
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(MEOWSPOOL_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Opens the MeowSpool download page in the browser. Returns false if nothing could open it. */
    fun openInstallPage(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
