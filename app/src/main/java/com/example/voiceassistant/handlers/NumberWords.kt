package com.example.voiceassistant.handlers

/**
 * Converts spoken number words ("twelve", "seven thirty", "twenty one") into digit
 * strings before handing text to CalculationHandler/AlarmHandler's regex parsers.
 * Android's SpeechRecognizer frequently returns small numbers as words rather than
 * digits, which was silently breaking both handlers — this normalizes either form.
 */
object NumberWords {

    private val ones = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /** Replaces number-word sequences with digit strings, e.g.
     *  "twenty seven plus twelve" -> "27 plus 12", "seven thirty" -> "7 30". */
    fun normalize(text: String): String {
        val words = text.lowercase().split(Regex("\\s+")).toMutableList()
        val result = StringBuilder()
        var i = 0
        while (i < words.size) {
            val word = words[i]
            when {
                tens.containsKey(word) -> {
                    var value = tens.getValue(word)
                    var consumed = 1
                    if (i + 1 < words.size && ones.containsKey(words[i + 1]) && ones.getValue(words[i + 1]) < 10) {
                        value += ones.getValue(words[i + 1])
                        consumed = 2
                    }
                    result.append(value).append(' ')
                    i += consumed
                }
                ones.containsKey(word) -> {
                    result.append(ones.getValue(word)).append(' ')
                    i++
                }
                else -> {
                    result.append(word).append(' ')
                    i++
                }
            }
        }
        return result.toString().trim()
    }
}
