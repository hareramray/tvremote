package com.tvremote.hid

import java.text.Normalizer

/** USB HID keyboard usage IDs (page 0x07) and helpers for typing text on a US layout. */
object HidKeys {
    const val MOD_LCTRL = 0x01
    const val MOD_LSHIFT = 0x02

    const val KEY_A = 0x04
    const val KEY_1 = 0x1E
    const val KEY_0 = 0x27
    const val KEY_ENTER = 0x28
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    data class KeyStroke(val usage: Int, val modifier: Int = 0)

    private val SYMBOLS: Map<Char, KeyStroke> = buildMap<Char, KeyStroke> {
        fun plain(c: Char, usage: Int) { put(c, KeyStroke(usage)) }
        fun shifted(c: Char, usage: Int) { put(c, KeyStroke(usage, MOD_LSHIFT)) }

        plain(' ', KEY_SPACE)
        plain('\n', KEY_ENTER)
        plain('\t', KEY_TAB)
        // Shift + 1..0
        "!@#\$%^&*()".forEachIndexed { i, c -> shifted(c, KEY_1 + i) }
        plain('-', 0x2D); shifted('_', 0x2D)
        plain('=', 0x2E); shifted('+', 0x2E)
        plain('[', 0x2F); shifted('{', 0x2F)
        plain(']', 0x30); shifted('}', 0x30)
        plain('\\', 0x31); shifted('|', 0x31)
        plain(';', 0x33); shifted(':', 0x33)
        plain('\'', 0x34); shifted('"', 0x34)
        plain('`', 0x35); shifted('~', 0x35)
        plain(',', 0x36); shifted('<', 0x36)
        plain('.', 0x37); shifted('>', 0x37)
        plain('/', 0x38); shifted('?', 0x38)
    }

    /** The key (plus shift if needed) that types [c], or null if a keyboard can't type it. */
    fun forChar(c: Char): KeyStroke? = when (c) {
        in 'a'..'z' -> KeyStroke(KEY_A + (c - 'a'))
        in 'A'..'Z' -> KeyStroke(KEY_A + (c - 'A'), MOD_LSHIFT)
        in '1'..'9' -> KeyStroke(KEY_1 + (c - '1'))
        '0' -> KeyStroke(KEY_0)
        else -> SYMBOLS[c]
    }

    /**
     * Makes text typeable: smart quotes/dashes become plain ones, accents are removed
     * ("café" -> "cafe"), and anything a US keyboard can't type is dropped.
     */
    fun toTypeable(text: String): String {
        val plain = buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        '‘', '’', '′' -> '\''
                        '“', '”', '″' -> '"'
                        '–', '—' -> '-'
                        ' ' -> ' '
                        else -> c
                    }
                )
            }
        }
        val noAccents = Normalizer.normalize(plain, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noAccents.filter { forChar(it) != null }
    }
}
