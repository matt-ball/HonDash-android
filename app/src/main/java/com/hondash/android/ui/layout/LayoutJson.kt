package com.hondash.android.ui.layout

import org.json.JSONTokener

/**
 * org.json accepts non-JSON syntax, and Android's implementation throws on overflowing
 * number literals before our field validator can reset them. Validate syntax first, quoting
 * only overflowing numbers so both implementations deliver them to the same validator.
 */
internal object LayoutJson {
    fun parse(text: String): Any = JSONTokener(Input(text).normalize()).nextValue()

    private class Input(private val text: String) {
        private var index = 0
        private val output = StringBuilder(text.length)

        fun normalize(): String {
            value(0)
            whitespace()
            requireJson(index == text.length)
            return output.toString()
        }

        private fun value(depth: Int) {
            // Bound nesting before entering org.json as well, avoiding stack exhaustion.
            requireJson(depth <= 64)
            whitespace()
            when (peek()) {
                '{' -> container('}', depth) {
                    requireJson(peek() == '"')
                    string()
                    whitespace()
                    consume(':')
                    value(depth + 1)
                }
                '[' -> container(']', depth) { value(depth + 1) }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> number()
                else -> requireJson(false)
            }
        }

        private inline fun container(end: Char, depth: Int, entry: () -> Unit) {
            // depth is checked by value() before each recursive entry.
            requireJson(depth <= 64)
            output.append(text[index++])
            whitespace()
            if (peek() != end) {
                while (true) {
                    entry()
                    whitespace()
                    if (peek() != ',') break
                    consume(',')
                    whitespace()
                }
            }
            consume(end)
        }

        private fun string() {
            consume('"')
            while (index < text.length) {
                val char = text[index++]
                output.append(char)
                when {
                    char == '"' -> return
                    char == '\\' -> {
                        requireJson(index < text.length)
                        val escape = text[index++]
                        output.append(escape)
                        if (escape == 'u') {
                            repeat(4) {
                                requireJson(peek() in '0'..'9' || peek() in 'a'..'f' || peek() in 'A'..'F')
                                output.append(text[index++])
                            }
                        } else {
                            requireJson(escape in "\"\\/bfnrt")
                        }
                    }
                    char < ' ' -> requireJson(false)
                }
            }
            requireJson(false)
        }

        private fun number() {
            val start = index
            if (peek() == '-') index++
            if (peek() == '0') index++ else digits()
            if (peek() == '.') {
                index++
                digits()
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                digits()
            }
            val number = text.substring(start, index)
            if (number.toDoubleOrNull()?.isFinite() == true) output.append(number)
            else output.append('"').append(number).append('"')
        }

        private fun digits() {
            val start = index
            while (peek() in '0'..'9') index++
            requireJson(index > start)
        }

        private fun literal(literal: String) {
            requireJson(text.startsWith(literal, index))
            output.append(literal)
            index += literal.length
        }

        private fun whitespace() {
            while (index < text.length && text[index] in " \t\r\n") {
                output.append(text[index++])
            }
        }

        private fun consume(char: Char) {
            requireJson(peek() == char)
            output.append(text[index++])
        }

        private fun peek(): Char = text.getOrNull(index) ?: '\u0000'

        private fun requireJson(valid: Boolean) {
            if (!valid) throw LayoutCodecException("Invalid layout JSON at character $index")
        }
    }
}
