package com.example.voiceassistant.handlers

/**
 * Handler 3/8: calculation.
 * Parses basic arithmetic out of spoken text and evaluates it. Handles both spoken
 * operator words ("plus", "times") and symbols, since STT sometimes returns one or
 * the other depending on phrasing and device.
 *
 * Deliberately simple: single binary operation only (e.g. "12 plus 7", "what is 15
 * times 3"). No operator precedence or multi-step expressions — good enough for a
 * voice-assistant quick calc, and easy to extend later if needed.
 */
object CalculationHandler {

    private val wordToOperator = mapOf(
        "plus" to '+', "add" to '+',
        "minus" to '-', "subtract" to '-',
        "times" to '*', "multiplied by" to '*', "multiply by" to '*',
        "divided by" to '/', "divide by" to '/'
    )

    fun handle(remainder: String): String {
        val normalized = normalize(remainder)
        val expression = extractExpression(normalized)
            ?: return "I couldn't work out a calculation from that"

        val (a, op, b) = expression
        if (op == '/' && b == 0.0) return "Can't divide by zero"

        val result = when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> a / b
            else -> return "I couldn't work out a calculation from that"
        }

        // Print as an integer when the result has no meaningful decimal part.
        val display = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }
        return "That's $display"
    }

    /** Replaces spoken operator words with their symbol, longest phrases first so
     *  "multiplied by" isn't partially matched by a shorter rule first. */
    private fun normalize(text: String): String {
        var result = text.lowercase()
        wordToOperator.keys.sortedByDescending { it.length }.forEach { word ->
            result = result.replace(word, " ${wordToOperator[word]} ")
        }
        return result
    }

    private fun extractExpression(text: String): Triple<Double, Char, Double>? {
        val regex = Regex("""(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)""")
        val match = regex.find(text) ?: return null
        val (aStr, opStr, bStr) = match.destructured
        val a = aStr.toDoubleOrNull() ?: return null
        val b = bStr.toDoubleOrNull() ?: return null
        return Triple(a, opStr[0], b)
    }
}
