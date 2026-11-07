package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Owns all automated taps; detection and offer analysis never click UI nodes. */
internal class AccessibilityActionExecutor(
    private val service: AccessibilityService,
    private val windowOwners: WindowOwnerHints,
    private val targetPackages: Set<String>
) {
    fun accept(targetPackage: String = ""): Boolean {
        activeRoot()?.let { root ->
            try {
                val pkg = root.packageName?.toString().orEmpty()
                if ((targetPackage.isBlank() || pkg == targetPackage || pkg in targetPackages) &&
                    findAndClick(root, ACCEPT_TEXT, ACCEPT_IDS)
                ) return true
            } finally { recycle(root) }
        }
        val windows = visibleWindows()
        val prioritized = if (targetPackage.isBlank()) windows else windows.sortedByDescending { window ->
            val root = window.root
            val owner = windowOwners.owner(window.id, root?.packageName?.toString(), SystemClock.elapsedRealtime())
            root?.let(::recycle)
            owner == targetPackage
        }
        prioritized.forEach { window ->
            val root = window.root ?: return@forEach
            try { if (findAndClick(root, ACCEPT_TEXT, ACCEPT_IDS)) return true }
            finally { recycle(root) }
        }
        activeRoot()?.let { root ->
            try { if (findAndClick(root, ACCEPT_TEXT, ACCEPT_IDS)) return true }
            finally { recycle(root) }
        }
        return false
    }

    fun reject(targetPackage: String = ""): Boolean {
        activeRoot()?.let { root ->
            try {
                val pkg = root.packageName?.toString().orEmpty()
                if ((targetPackage.isBlank() || pkg == targetPackage || pkg in targetPackages) &&
                    findAndClick(root, REJECT_TEXT, REJECT_IDS)
                ) return true
            } finally { recycle(root) }
        }
        visibleWindows().forEach { window ->
            val root = window.root ?: return@forEach
            try { if (findAndClick(root, REJECT_TEXT, REJECT_IDS)) return true }
            finally { recycle(root) }
        }
        return false
    }

    fun disconnect(targetPackage: String = ""): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        activeRoot()?.let(roots::add)
        try {
            visibleWindows().forEach { window ->
                val root = window.root ?: return@forEach
                val pkg = root.packageName?.toString().orEmpty()
                if (targetPackage.isBlank() || pkg == targetPackage) roots += root else recycle(root)
            }
            roots.forEach { if (findAndClick(it, DISCONNECT_TEXT, DISCONNECT_IDS, exactText = true)) return true }
        } finally { roots.forEach(::recycle) }
        return false
    }

    private fun findAndClick(
        node: AccessibilityNodeInfo,
        textPattern: Regex,
        idFragments: Set<String>,
        exactText: Boolean = false
    ): Boolean {
        val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ").trim()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val textMatches = text.isNotBlank() && if (exactText) textPattern.matches(text) else textPattern.containsMatchIn(text)
        if (textMatches || idFragments.any(viewId::contains)) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val bounds = Rect().also(node::getBoundsInScreen)
            if (bounds.width() > 0 && bounds.height() > 0 && tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { if (findAndClick(child, textPattern, idFragments, exactText)) return true }
            finally { recycle(child) }
        }
        return false
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun activeRoot() = service.rootInActiveWindow
    private fun visibleWindows() = try { service.windows.sortedByDescending { it.layer } } catch (_: Exception) { emptyList() }

    private companion object {
        val ACCEPT_TEXT = Regex("(?i)\\b(aceitar|accept)\\b")
        val ACCEPT_IDS = setOf("accept", "aceitar")
        val REJECT_TEXT = Regex("(?i)\\b(recusar|rejeitar|descartar|ignorar|fechar|decline|reject|dismiss)\\b")
        val REJECT_IDS = setOf("close", "reject", "decline", "dismiss")
        val DISCONNECT_TEXT = Regex("(?i)^(ficar offline|desconectar|parar de receber( corridas| ofertas)?|go offline|stop requests)$")
        val DISCONNECT_IDS = setOf("go_offline", "offline_button")

        fun recycle(node: AccessibilityNodeInfo) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
