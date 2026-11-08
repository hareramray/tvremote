package com.tvremote.hid

/**
 * Every button the app can press, and the HID usage that produces it on Fire OS.
 *
 * Consumer usages go through Linux hid-input -> Android Generic.kl:
 *   0x041 Menu Pick -> KEY_SELECT   -> DPAD_CENTER
 *   0x223 AC Home   -> KEY_HOMEPAGE -> HOME
 *   0x224 AC Back   -> KEY_BACK     -> BACK
 *   0x040 Menu      -> KEY_MENU     -> MENU
 */
enum class RemoteButton(val label: String, val kind: Kind, val usage: Int) {
    UP("Up", Kind.KEYBOARD, HidKeys.KEY_UP),
    DOWN("Down", Kind.KEYBOARD, HidKeys.KEY_DOWN),
    LEFT("Left", Kind.KEYBOARD, HidKeys.KEY_LEFT),
    RIGHT("Right", Kind.KEYBOARD, HidKeys.KEY_RIGHT),
    OK("OK", Kind.CONSUMER, 0x041),
    ENTER("Enter", Kind.KEYBOARD, HidKeys.KEY_ENTER),
    BACKSPACE("Delete", Kind.KEYBOARD, HidKeys.KEY_BACKSPACE),
    BACK("Back", Kind.CONSUMER, 0x224),
    HOME("Home", Kind.CONSUMER, 0x223),
    MENU("Menu", Kind.CONSUMER, 0x040),
    PLAY_PAUSE("Play/Pause", Kind.CONSUMER, 0x0CD),
    REWIND("Rewind", Kind.CONSUMER, 0x0B4),
    FAST_FORWARD("Fast forward", Kind.CONSUMER, 0x0B3),
    NEXT("Next", Kind.CONSUMER, 0x0B5),
    PREVIOUS("Previous", Kind.CONSUMER, 0x0B6),
    VOLUME_UP("Volume up", Kind.CONSUMER, 0x0E9),
    VOLUME_DOWN("Volume down", Kind.CONSUMER, 0x0EA),
    MUTE("Mute", Kind.CONSUMER, 0x0E2),
    POWER("Power", Kind.CONSUMER, 0x030);

    enum class Kind { KEYBOARD, CONSUMER }

    val isVolume: Boolean get() = this == VOLUME_UP || this == VOLUME_DOWN
}
