package com.girocerto.update

import java.net.URI

internal data class AppRelease(val versionCode: Long, val versionName: String, val sha256: String, val apkUrl: String) {
    val fileName get() = "GiroCerto-$versionName.apk"
    fun isNewerThan(installed: Long) = versionCode > installed
    companion object {
        fun validated(code: Long, name: String, checksum: String, url: String): AppRelease {
            require(code in 1..Int.MAX_VALUE.toLong())
            require(name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            require(checksum.matches(Regex("[a-fA-F0-9]{64}")))
            val uri = URI(url)
            val prefix = "/evandromqs/GiroCerto/releases/download/v$name/"
            require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null)
            require(uri.path == prefix + "GiroCerto-$name.apk" || uri.path == prefix + "GiroCerto.apk")
            return AppRelease(code, name, checksum.lowercase(), url)
        }
    }
}
