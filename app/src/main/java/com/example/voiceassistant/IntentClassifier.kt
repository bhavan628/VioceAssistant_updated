package com.example.voiceassistant

/**
 * Turns raw STT text into one of a fixed set of command categories using simple
 * keyword/regex matching — fast, offline, and predictable to debug.
 *
 * Upgrade path (not built here, just noted for later): once this rule-based version
 * is working end-to-end, you could swap classify() for either
 *  (a) a small on-device NLU model (e.g. TensorFlow Lite text classifier), or
 *  (b) a single cloud LLM call that returns the category + extracted slot in one shot.
 * Both are drop-in replacements as long as they still return an IntentResult — nothing
 * else in the app needs to change.
 */
enum class CommandCategory {
    TIME, MUSIC, NEWS, OPEN_APP, ALARM, MESSAGE, CALL, CALCULATION, LOCK_SCREEN, SEARCH, UNKNOWN
}

/** category + whatever's left of the command text after the trigger keyword, for the
 *  handler to parse further (song name, contact name, app name, etc). */
data class IntentResult(val category: CommandCategory, val remainder: String)

object IntentClassifier {

    // Order matters: more specific/greedy matches should come before generic ones.
    // Each entry: trigger keywords -> category. First match wins.
    private val rules: List<Pair<List<String>, CommandCategory>> = listOf(
        listOf("what time", "what's the time", "current time", "what date", "what's the date", "today's date") to CommandCategory.TIME,
        listOf("play") to CommandCategory.MUSIC,
        listOf("news", "headlines") to CommandCategory.NEWS,
        listOf("search for", "search", "google") to CommandCategory.SEARCH,
        listOf("open") to CommandCategory.OPEN_APP,
        listOf("set an alarm", "set alarm", "wake me", "remind me") to CommandCategory.ALARM,
        // Placed before CALL/MESSAGE — "lock phone" contains "phone", which would
        // otherwise get caught by CALL's "phone" trigger first.
        listOf("lock the screen", "lock screen", "lock my phone", "lock phone") to CommandCategory.LOCK_SCREEN,
        listOf("message", "text", "whatsapp") to CommandCategory.MESSAGE,
        listOf("call", "phone", "dial") to CommandCategory.CALL,
        listOf("calculate", "what is", "what's", "plus", "minus", "times", "divided by") to CommandCategory.CALCULATION
    )

    // Categories whose handler needs the FULL text, not just what's after the keyword —
    // e.g. calculation needs "12 plus 7" whole; stripping after "plus" would lose the 12.
    private val needsFullText = setOf(CommandCategory.TIME, CommandCategory.CALCULATION)

    fun classify(commandText: String): IntentResult {
        val lower = commandText.lowercase().trim()

        for ((keywords, category) in rules) {
            for (keyword in keywords) {
                if (lower.contains(keyword)) {
                    if (category in needsFullText) {
                        return IntentResult(category, lower)
                    }
                    // Strip the trigger keyword out so handlers get just the payload,
                    // e.g. "play shape of you" -> remainder "shape of you"
                    val remainder = lower.substringAfter(keyword).trim()
                    return IntentResult(category, remainder.ifBlank { lower })
                }
            }
        }
        return IntentResult(CommandCategory.UNKNOWN, lower)
    }
}
