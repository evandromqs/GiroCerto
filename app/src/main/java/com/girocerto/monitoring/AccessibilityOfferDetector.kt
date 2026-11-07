package com.girocerto.monitoring

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Reads only the accessibility tree. Parsing and side effects live elsewhere. */
internal class AccessibilityOfferDetector {
    fun eventRoot(event: AccessibilityEvent, packageName: String): AccessibilityNodeInfo? {
        var node = event.source ?: return null
        if (node.packageName?.toString() != packageName) {
            recycle(node)
            return null
        }
        repeat(MAX_PARENT_DEPTH) {
            val parent = node.parent ?: return node
            if (parent.packageName?.toString() != packageName) {
                recycle(parent)
                return node
            }
            recycle(node)
            node = parent
        }
        return node
    }

    /** Returns null when the tree exceeds the bounded read budget. */
    fun collectTexts(root: AccessibilityNodeInfo): List<String>? {
        val texts = mutableListOf<String>()
        return if (collect(root, texts, ReadBudget())) texts else null
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        budget: ReadBudget,
        depth: Int = 0
    ): Boolean {
        if (!budget.permits(depth)) return false
        val children = mutableListOf<String>()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (!collect(child, children, budget, depth + 1)) return false
            } finally {
                recycle(child)
            }
        }
        val childText = children.joinToString(" ")
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .map(String::trim).filter(String::isNotBlank).distinct()
        texts += own.filter { value ->
            val tokens = TOKEN.findAll(value).map { it.value }.toList()
            val available = TOKEN.findAll(childText).map { it.value }.toList().toMutableList()
            val fullyRepresented = tokens.isNotEmpty() && tokens.all { available.remove(it) }
            !fullyRepresented
        }.map { it.take(MAX_TEXT_LENGTH) }
        texts += children
        return true
    }

    private class ReadBudget {
        var nodes = 0
        private val started = android.os.SystemClock.uptimeMillis()
        fun permits(depth: Int): Boolean =
            ++nodes <= MAX_NODES && depth <= MAX_TREE_DEPTH &&
                android.os.SystemClock.uptimeMillis() - started < MAX_READ_MILLIS
    }

    private companion object {
        const val MAX_PARENT_DEPTH = 32
        const val MAX_NODES = 1024
        const val MAX_TREE_DEPTH = 50
        const val MAX_READ_MILLIS = 300
        const val MAX_TEXT_LENGTH = 500
        val TOKEN = Regex("[\\p{L}\\p{N}$.,]+")

        fun recycle(node: AccessibilityNodeInfo) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
