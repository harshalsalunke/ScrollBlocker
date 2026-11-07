# ScrollBlocker 📱🛑

**ScrollBlocker** is an Android application designed to help users break the habit of mindless doomscrolling, regain control of their time, and promote digital wellbeing. By leveraging Android's **Accessibility Service** and system overlays, ScrollBlocker intercepts endless feeds, introduces mindful pauses/delays, and helps you stay focused on what truly matters.

---

## ✨ Key Features

- **🛡️ Endless Feed Interception**: Detects and interrupts repetitive scrolling behaviors on addictive apps.
- **⏱️ Mindful Delay Overlays**: Introduces intentional pauses before allowing continued usage, breaking autopilot scrolling.
- **🚫 App & Feed Blocking**: Restricts access or prompts mindful reflection when launching or scrolling in designated distracting apps.
- **⚙️ Custom Preferences**: Easily configure which apps to monitor, set time limits, and adjust delay intervals.
- **🚀 Background Service**: Runs reliably in the background via a foreground/accessibility service with auto-restart on boot (`BootReceiver`).

---

## 🏗️ Architecture & Project Structure

```text
com.harshal.scrollblocker/
│
├── MainActivity.java            # Main dashboard & configuration UI
├── ScrollBlockerService.java    # Core Accessibility Service monitoring scroll events & windows
├── BootReceiver.java            # Restarts monitoring service upon device reboot
├── AppPrefs.java                # Manages user preferences & blocked app states
│
├── managers/
│   ├── OverlayManager.java      # Handles rendering of block/delay custom overlays
│   └── TimeManager.java         # Tracks usage duration and time limits
│
└── res/
    ├── layout/                  # UI layouts (dashboard, cards, block & delay overlays)
    └── xml/                     # Accessibility service configuration & backup rules
```

---

## 🛠️ Tech Stack & Libraries

- **Language**: Java
- **UI**: Android Views, Material Design Components, Custom Overlays
- **Core APIs**: 
  - `AccessibilityService` (for monitoring window state and scroll events)
  - `WindowManager` (for drawing non-intrusive interactive overlays)
  - `SharedPreferences` (for local preference persistence)

---

## 📋 Required Permissions

ScrollBlocker requires the following system permissions to function properly:
1. **Accessibility Service**: Required to detect scrolling events and package transitions inside target apps.
2. **Display Overlays (`SYSTEM_ALERT_WINDOW`)**: Required to show the mindful delay and block screens over active applications.
3. **Receive Boot Completed**: Ensures protection persists after device restarts.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (Koala / Jellyfish or newer)
- Android SDK (API level 24 or higher; target API 34+)
- Physical Android device or Emulator

### Installation & Building
1. Clone the repository:
   ```bash
   git clone https://github.com/harshalsalunke/ScrollBlocker.git
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew build
   ```
4. Install the debug APK onto your connected Android device.

---

## 🤝 Contributing

Contributions, feature requests, and bug reports are welcome! Feel free to open an issue or submit a pull request.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git origin push feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
