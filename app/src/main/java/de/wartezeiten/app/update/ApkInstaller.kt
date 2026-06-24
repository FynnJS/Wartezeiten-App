package de.wartezeiten.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object ApkInstaller {
    fun canInstallPackages(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun installIntent(apkUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun unknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
