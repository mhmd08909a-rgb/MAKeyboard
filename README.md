# MAKeyboard 1.1.0 (source)

STATUS (honest):
- Version 1.1.0 changes the five problem areas in code. Kotlin and Android code is NOT compiled yet:
  the build sandbox had no Android SDK and no Kotlin compiler. Expect a few compile fixes on the first real build.
- The pure-logic parts (backspace rules, layout rules, size math) are compiled and tested on the JVM:
  tools/logic-tests/LogicTest.java (57 checks, all passing). Run it with any JDK 17:
    javac -encoding UTF-8 -d out app/src/main/java/com/ma/keyboard/{DeleteLogic,LayoutModel,FitMath}.java tools/logic-tests/LogicTest.java
    java -cp out LogicTest
- NOT tested on a real phone or emulator. Anything about how it looks, feels or types there is unverified.

## What changed in 1.1.0
- Key size: keycaps are no longer forced into a circle the width of the key. Default shape is a rounded pill
  using the full cell. Row height, key height (%), key width (%), gaps, letter size, whole keyboard width (%)
  and whole keyboard height (%) are separate sliders. Rows shrink automatically if they would leave the screen.
- Layout editor («ویرایش چیدمان کیبورد», from Settings): long-press a key, drag, release (or tap, then tap the target).
  Rules: letters/symbols move between rows except the bottom row; system keys stay in their row; bottom row only
  reorders inside itself; max 13 keys per row; no empty row. Save, undo step, revert, reset page to default.
  Saved per page (lay_* prefs). Theme changes never touch the layout.
- Backspace: one tap removes exactly one character (or the selection); holding repeats and stops on release.
  Emoji, ZWJ sequences, skin tones, flags and keycaps are removed as one unit (DeleteLogic.java).
- Toolbar: opaque themed background, contrast checked (WCAG 4.5:1, icons auto-darken/lighten if needed),
  thicker configurable strokes, optional plate behind icons, height/icon size/spacing sliders.
- Calculator: fits the keyboard height; the bottom row can no longer be cut off. Added ± (sign) key.
- Settings preview: uses the real keyboard and toolbar code. Changes are a draft until «ذخیره»; «لغو تغییرات» discards them.
- Kept: clipboard, pins, emoji, notes/reminders, snippets, word codes, PIN, writing mode, symbols, themes, sounds,
  vibration, encrypted backup, private mode, package name (com.ma.keyboard).

## Known gaps
- No help/about screen. Main screen is still basic.
- Clipboard capture depends on Android allowing the IME to see copies (Android 10+ restricts this).
- Reminders only refresh when the keyboard opens.
- Copy/cut/paste return a message when the app blocks them.
- Layout editor works on the letters and symbols pages; the Persian shift page is editable too but not tested.

## BUILD
Open in Android Studio (or run `gradle :app:assembleDebug`), Android SDK 34, JDK 17.
Install the APK, then Settings > System > Languages & input > On-screen keyboard > enable MAKeyboard.
No INTERNET permission, no third-party libraries. Debug/release use a local demo keystore in app/makeyboard.keystore.
