package com.example.voiceassistant.handlers

object TypeHandler {

    fun handle(remainder: String): String {
        val text = remainder.trim()
        if (text.isBlank()) return "What should I type?"

        val service = TypingAccessibilityService.instance
            ?: return "I need the accessibility permission turned on to type for you"

        val success = service.typeIntoFocusedField(text)
        return if (success) "" else "I couldn't find a text field to type into"
    }
}
