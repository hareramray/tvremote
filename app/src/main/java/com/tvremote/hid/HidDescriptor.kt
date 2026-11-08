package com.tvremote.hid

/**
 * HID report map advertised to the TV: a keyboard (Report ID 1) plus a
 * consumer-control device (Report ID 2).
 *
 * Fire OS is Android on Linux, so these become ordinary Android key events:
 *  - keyboard arrows  -> DPAD_UP / DOWN / LEFT / RIGHT
 *  - keyboard keys    -> letters, digits, ENTER, DEL (for typing into search boxes)
 *  - consumer usages  -> HOME, BACK, MENU, DPAD_CENTER, media keys, volume, power
 */
internal object HidDescriptor {
    const val ID_KEYBOARD = 1
    const val ID_CONSUMER = 2

    const val KEYBOARD_REPORT_SIZE = 8 // modifiers, reserved, 6 key slots
    const val CONSUMER_REPORT_SIZE = 2 // one 16-bit usage

    val REPORT_MAP: ByteArray = bytes(
        // ---- Keyboard ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_KEYBOARD, //   Report ID (1)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Variable, Absolute)   ; modifier bits
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x08,       //   Report Size (8)
        0x81, 0x01,       //   Input (Constant)                   ; reserved byte
        0x95, 0x05,       //   Report Count (5)
        0x75, 0x01,       //   Report Size (1)
        0x05, 0x08,       //   Usage Page (LEDs)
        0x19, 0x01,       //   Usage Minimum (Num Lock)
        0x29, 0x05,       //   Usage Maximum (Kana)
        0x91, 0x02,       //   Output (Data, Variable, Absolute)  ; LED bits
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x03,       //   Report Size (3)
        0x91, 0x01,       //   Output (Constant)                  ; LED padding
        0x95, 0x06,       //   Report Count (6)
        0x75, 0x08,       //   Report Size (8)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x65,       //   Logical Maximum (101)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0x65,       //   Usage Maximum (101)
        0x81, 0x00,       //   Input (Data, Array)                ; 6 key slots
        0xC0,             // End Collection

        // ---- Consumer control (remote / media keys) ----
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_CONSUMER, //   Report ID (2)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (1023)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (1023)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array, Absolute)
        0xC0,             // End Collection
    )

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
