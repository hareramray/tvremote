package com.tvremote.voice

import com.tvremote.hid.RemoteButton
import java.util.Locale

/** One thing to do on the TV in response to a spoken phrase. */
sealed interface VoiceStep {
    val description: String

    data class Press(val button: RemoteButton, val times: Int) : VoiceStep {
        override val description: String
            get() = if (times > 1) "${button.label} ×$times" else button.label
    }

    /** Type [text] into the focused field; press Enter afterwards if [submit]. */
    data class Type(val text: String, val submit: Boolean) : VoiceStep {
        override val description: String
            get() = if (submit) "Search “$text”" else "Type “$text”"
    }

    /** Open an app on the TV by (spoken) name. Wi-Fi mode only. */
    data class OpenApp(val appName: String) : VoiceStep {
        override val description: String
            get() = "Open “$appName”"
    }

    /** Turn the TV on, without switching it off if it's already on. */
    data object PowerOn : VoiceStep {
        override val description: String
            get() = "Turn on TV"
    }
}

/**
 * Turns phrases like "volume up 5", "down three times then ok" or "search Avengers"
 * into [VoiceStep]s. Returns null when the phrase isn't a command.
 */
object VoiceCommandParser {

    private const val MAX_REPEAT = 20

    private val TYPE_PREFIX =
        Regex("^(?:please\\s+)?(?:type|write)(?:\\s+in)?\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val SEARCH_PREFIX =
        Regex("^(?:please\\s+)?(?:search(?:\\s+for)?|find|look\\s+(?:for|up))\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val OPEN_PREFIX =
        Regex("^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE)
    private val POWER_ON = setOf("power on", "turn on", "switch on", "wake up", "wake")
    private val CLAUSE_SPLIT = Regex("\\s*(?:,|\\band then\\b|\\bthen\\b|\\band\\b)\\s*")
    private val WHITESPACE = Regex("\\s+")

    /** Words that carry no meaning for a remote ("please go to the home screen" -> "home"). */
    private val FILLER = setOf(
        "please", "go", "press", "hit", "move", "scroll", "navigate", "the", "to", "tv",
        "button", "key", "screen", "now", "can", "you", "could", "by", "a", "bit",
    )

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "once" to 1, "two" to 2, "twice" to 2, "three" to 3, "thrice" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    )

    private data class Match(val button: RemoteButton, val defaultTimes: Int)

    private val PHRASES: Map<String, Match> = buildMap<String, Match> {
        fun add(button: RemoteButton, vararg phrases: String, times: Int = 1) {
            for (p in phrases) put(p, Match(button, times))
        }
        add(RemoteButton.UP, "up", "upward", "upwards")
        add(RemoteButton.DOWN, "down", "downward", "downwards")
        add(RemoteButton.LEFT, "left")
        add(RemoteButton.RIGHT, "right")
        add(RemoteButton.OK, "ok", "okay", "select", "enter", "click", "choose", "open")
        add(RemoteButton.BACK, "back", "return", "exit")
        add(RemoteButton.HOME, "home")
        add(RemoteButton.MENU, "menu", "options")
        add(RemoteButton.PLAY_PAUSE, "play", "pause", "resume", "stop", "play pause", "continue")
        add(RemoteButton.REWIND, "rewind", "backward", "backwards", "skip back", "skip backward")
        add(RemoteButton.FAST_FORWARD, "forward", "fast forward", "skip forward", "skip ahead")
        add(RemoteButton.NEXT, "next", "next episode", "next track", "next song", "next video")
        add(RemoteButton.PREVIOUS, "previous", "previous episode", "previous track", "previous song", "previous video")
        add(
            RemoteButton.VOLUME_UP,
            "volume up", "louder", "increase volume", "volume increase", "raise volume",
            "turn up volume", "turn volume up", "turn it up",
            times = 3,
        )
        add(
            RemoteButton.VOLUME_DOWN,
            "volume down", "quieter", "softer", "decrease volume", "volume decrease", "lower volume",
            "reduce volume", "turn down volume", "turn volume down", "turn it down",
            times = 3,
        )
        add(RemoteButton.MUTE, "mute", "unmute", "silence", "mute volume")
        add(RemoteButton.POWER, "power", "power off", "turn off", "switch off", "sleep", "shut down", "standby")
    }

    fun parse(phrase: String): List<VoiceStep>? {
        val original = phrase.trim().trimEnd('.', '!', '?').trim()
        if (original.isEmpty()) return null

        TYPE_PREFIX.find(original)?.let { m ->
            return textStep(m.groupValues[1], submit = false)
        }
        SEARCH_PREFIX.find(original)?.let { m ->
            return textStep(m.groupValues[1], submit = true)
        }

        parseCommands(original)?.let { return it }

        // "open YouTube", "launch hotstar app". But "open menu" is just the Menu button.
        OPEN_PREFIX.find(original)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isEmpty() || target.equals("it", ignoreCase = true)) return null
            return parseCommands(target) ?: listOf(VoiceStep.OpenApp(target))
        }
        return null
    }

    private fun parseCommands(text: String): List<VoiceStep>? {
        val clauses = text.lowercase(Locale.ROOT)
            .split(CLAUSE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (clauses.isEmpty()) return null
        return clauses.map { parseClause(it) ?: return null }
    }

    private fun textStep(text: String, submit: Boolean): List<VoiceStep>? {
        val t = text.trim()
        return if (t.isEmpty()) null else listOf(VoiceStep.Type(t, submit))
    }

    private fun parseClause(clause: String): VoiceStep? {
        var words = clause.split(WHITESPACE).filter { it.isNotEmpty() && it !in FILLER }
        if (words.isEmpty()) return null

        var count: Int? = null
        // "up up up" -> up ×3
        if (words.size > 1 && words.distinct().size == 1) {
            count = words.size
            words = words.take(1)
        }
        // "down three times", "volume up 5", "left twice"
        if (words.size >= 3 && (words.last() == "times" || words.last() == "time")) {
            words = words.dropLast(1)
        }
        if (count == null && words.size >= 2) {
            val n = number(words.last())
            if (n != null) {
                count = n
                words = words.dropLast(1)
            }
        }

        val phrase = words.joinToString(" ")
        if (phrase in POWER_ON) return VoiceStep.PowerOn
        val match = PHRASES[phrase] ?: return null
        val times = (count ?: match.defaultTimes).coerceIn(1, MAX_REPEAT)
        return VoiceStep.Press(match.button, times)
    }

    private fun number(word: String): Int? = word.toIntOrNull() ?: NUMBER_WORDS[word]
}
