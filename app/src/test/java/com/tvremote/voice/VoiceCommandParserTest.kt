package com.tvremote.voice

import com.tvremote.hid.RemoteButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    private fun press(button: RemoteButton, times: Int = 1) = VoiceStep.Press(button, times)

    @Test fun simpleNavigation() {
        assertEquals(listOf(press(RemoteButton.HOME)), VoiceCommandParser.parse("Go home"))
        assertEquals(listOf(press(RemoteButton.BACK)), VoiceCommandParser.parse("go back"))
        assertEquals(listOf(press(RemoteButton.OK)), VoiceCommandParser.parse("OK."))
    }

    @Test fun counts() {
        assertEquals(listOf(press(RemoteButton.DOWN, 3)), VoiceCommandParser.parse("down three times"))
        assertEquals(listOf(press(RemoteButton.LEFT, 2)), VoiceCommandParser.parse("left twice"))
        assertEquals(listOf(press(RemoteButton.UP, 3)), VoiceCommandParser.parse("up up up"))
        assertEquals(listOf(press(RemoteButton.VOLUME_UP, 5)), VoiceCommandParser.parse("Volume up 5"))
    }

    @Test fun volumeDefaultsToThreeSteps() {
        assertEquals(listOf(press(RemoteButton.VOLUME_DOWN, 3)), VoiceCommandParser.parse("turn the volume down"))
        assertEquals(listOf(press(RemoteButton.VOLUME_UP, 3)), VoiceCommandParser.parse("louder"))
    }

    @Test fun chainedCommands() {
        assertEquals(
            listOf(press(RemoteButton.DOWN, 2), press(RemoteButton.OK)),
            VoiceCommandParser.parse("down 2 then ok"),
        )
    }

    @Test fun searchAndType() {
        assertEquals(
            listOf(VoiceStep.Type("Avengers Endgame", submit = true)),
            VoiceCommandParser.parse("search for Avengers Endgame"),
        )
        assertEquals(
            listOf(VoiceStep.Type("formula one", submit = true)),
            VoiceCommandParser.parse("search formula one"),
        )
        assertEquals(
            listOf(VoiceStep.Type("hello world", submit = false)),
            VoiceCommandParser.parse("type hello world"),
        )
    }

    @Test fun openApps() {
        assertEquals(listOf(VoiceStep.OpenApp("YouTube")), VoiceCommandParser.parse("Open YouTube"))
        assertEquals(listOf(VoiceStep.OpenApp("hotstar")), VoiceCommandParser.parse("launch the hotstar app"))
        // Words that are remote buttons stay buttons.
        assertEquals(listOf(press(RemoteButton.MENU)), VoiceCommandParser.parse("open menu"))
        assertEquals(listOf(press(RemoteButton.OK)), VoiceCommandParser.parse("open"))
        assertEquals(listOf(press(RemoteButton.PLAY_PAUSE)), VoiceCommandParser.parse("play pause"))
    }

    @Test fun powerOnAndOff() {
        assertEquals(listOf(VoiceStep.PowerOn), VoiceCommandParser.parse("turn on the TV"))
        assertEquals(listOf(press(RemoteButton.POWER)), VoiceCommandParser.parse("turn off the TV"))
    }

    @Test fun notACommand() {
        assertNull(VoiceCommandParser.parse("what's the weather"))
        assertNull(VoiceCommandParser.parse("open it"))
        assertNull(VoiceCommandParser.parse("   "))
    }
}
