# HIDClient‑Macro

Use your Android phone as a USB HID keyboard and macro pad. Works anywhere a real keyboard does — including BIOS/UEFI — because it speaks the USB HID protocol directly.

This project adds first‑class macro support (Ducky Script–style) on top of the core HID client functionality.

Based on and originally derived from [Arian04/android-hid-client](https://github.com/Arian04/android-hid-client); this fork focuses on macro functionality.


## Features

- **Keyboard**: Type in real‑time from any Android soft keyboard.
- **Manual input**: Type a string and send it in one go.
- **Macros**: Create, edit, and run Ducky Script–style macros with syntax highlighting, presets, delays, and repeats.
- **Media and special keys**: Send common modifiers and media/navigation keys.
- **Works without drivers on the host**: Appears as a standard USB HID device.


## Requirements

- **Rooted Android device**.
  - Supported live SELinux patchers: Magisk (`magiskpolicy`) and KernelSU (`ksud sepolicy patch`).
- **USB OTG support and cable** for connecting to the host computer/device.
- Tested on Android 14; other versions may work.

Notes:

- A capable soft keyboard like Unexpected Keyboard is recommended for easy access to modifiers and function keys, though any standard keyboard app works for basic typing.


## How it works

The app augments Android’s default USB gadget to expose HID functions and provides a UI to send HID reports. Internally it creates character devices (for example, `/dev/hidg0`, `/dev/hidg1`) and writes keyboard reports to them. Because this happens at the USB gadget level, the connected host sees a normal keyboard and requires no software.


## Installation

### From source

1. Clone this repository.
   ```sh
   git clone https://github.com/renzceee/HIDClient-Macro.git
   ```
2. Open in Android Studio, build, and install the APK on your device.

### From releases

- Download the latest APK from the repository’s Releases page and install it on your device.


## Usage

1. Connect your Android device to the target computer using a USB OTG cable.
2. Launch the app and grant root permissions when prompted.
3. Start the service if required by your device/ROM.
4. For real‑time typing, tap the keyboard icon to open your soft keyboard and begin typing.
5. To send a prepared string, use the Manual Input field and press Send.

### Macros

- The main screen lists your saved macros.
- Tap a macro to run it; use the Stop button to cancel playback.
- Each macro card has quick actions for Run, Edit, and Delete.
- Confirmation prompts can be toggled in Settings → Misc.

#### Editing and creating macros

- Editor supports Ducky Script–style commands, highlighting, and presets (including an ADD DELAY helper).
- You can copy the full script from the editor toolbar.
- Example snippets:

```text
STRING Hello
ENTER
DELAY 250
DEFAULT_DELAY 100
GUI r
CTRL ALT DEL
LEFT
REPEAT 3
```


## Troubleshooting

- **Host doesn’t detect a keyboard**
  - Use a known‑good OTG cable and port. Try another cable/port if possible.
  - Ensure root was granted and SELinux policy was patched successfully (Magisk/KernelSU).
  - Reconnect the USB cable after starting the service.

- **Keys don’t match expected output**
  - Try a different Android keyboard app (e.g., Unexpected Keyboard) for better access to modifiers and function keys.

- **BIOS/UEFI still not responding**
  - Power cycle the host with the phone already connected.
  - Try a different USB port (prefer rear I/O on desktops).

If issues persist, capture logs and open an issue with device model, Android version, root method, and a clear description of the problem.


## Roadmap

- Keyboard support
- Macro editor with Ducky Script–style syntax
- Manual input sender
- Settings and safety prompts
- Additional convenience actions and key presets


## Contributing

Contributions are welcome. Feel free to open issues and pull requests with improvements, bug fixes, or new features.

Basic workflow:

1. Fork the repo
2. Create a feature branch
3. Commit and push your changes
4. Open a pull request


## License

This project is licensed under the GNU GPLv3. See the `LICENSE` file for details.


## Credits

- Based on the excellent work behind USB HID client implementations on Android. Original inspiration and foundations from [Arian04/android-hid-client](https://github.com/Arian04/android-hid-client).

