package com.example.voiceassistant.handlers

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lets the assistant type text into whatever input field currently has focus, in
 * whatever app is on screen. This is the ONLY way Android allows this — there's no
 * regular Intent or permission for "type into the active app"; Accessibility Service
 * is a fundamentally different, more sensitive component:
 *  - The user must manually enable it in Settings > Accessibility (can't be
 *    auto-granted like a normal permission).
 *  - While enabled, it can read on-screen content in any app — the same mechanism
 *    screen readers use, and the same one malware abuses, which is why Android makes
 *    it deliberately hard to turn on and shows a strong warning when you do.
 */
class TypingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event handling needed — typing happens on demand via typeIntoFocusedField(),
        // which queries the currently focused node directly when called.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    /** Finds the currently focused editable field (in any app) and sets its text.
     *  Returns false if nothing editable is focused right now. */
    fun typeIntoFocusedField(text: String): Boolean {
        val focused = findFocusedEditableNode(rootInActiveWindow) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isFocused && root.isEditable) return root
        for (i in 0 until root.childCount) {
            val result = findFocusedEditableNode(root.getChild(i))
            if (result != null) return result
        }
        return null
    }

    companion object {
        var instance: TypingAccessibilityService? = null
            private set
    }
}
