# Voice Assistant (Full-Feature v2) — Build Log

Following the order: project setup → manifest → foreground service skeleton → wake
word → STT/TTS → intent classifier → command handlers (one at a time) → boot receiver
→ APK signing.

## Done so far (Steps 1-8) — BUILD COMPLETE
- **Step 8** — release signing config in `app/build.gradle.kts`, reading keystore
  details from `local.properties` (never hardcoded, never committed — `.gitignore`
  added to make sure of that). Full instructions below to generate a keystore and
  produce a signed APK to hand directly to your client.

## Fixed: app opening to a blank screen / launcher icon missing
Two real gaps in the original build, now fixed:
1. `MainActivity` never called `setContentView()` — it worked, but showed a
   completely blank screen with nothing on it, which looks exactly like "the app
   won't open." It now uses `res/layout/activity_main.xml` and shows live status text
   as it requests permissions and starts the service.
2. The manifest referenced `@mipmap/ic_launcher` but no icon resources existed. Added
   a simple adaptive icon (`res/mipmap-anydpi-v26/` + a vector foreground) — since
   `minSdk` is 26, this covers every supported device with no raster PNGs needed.

If the app still doesn't open after pulling this update, the most useful next step is
checking the actual crash log: `adb logcat | grep AndroidRuntime` while launching it
(from Termux with `pkg install android-tools`, or from a PC if you get access to one),
or share what happens on screen (crashes instantly? shows something then closes?
nothing happens at all when you tap the icon?) and I can narrow it down further.


`WakeWordEngine.kt` uses **Vosk** (open-source, Apache 2.0) instead of Picovoice
Porcupine — no account, no API key, no login of any kind, since Picovoice's signup was
blocking testing.

Setup:
1. Download a small model, e.g. `vosk-model-small-en-us-0.15` (~40MB) from
   https://alphacephei.com/vosk/models
2. Unzip it and place the contents at `app/src/main/assets/model/` (so
   `app/src/main/assets/model/conf`, `/am`, etc. sit directly under `assets/model/`).
3. Nothing else to configure — no key, no account.

**Honest tradeoff**: Vosk is a full speech-to-text engine, not a lightweight keyword
spotter like Porcupine. Running it continuously in the background uses noticeably more
CPU/battery than a purpose-built wake-word model would. It's the right call to get
something working with zero signup friction; if battery life becomes a real problem
later, Porcupine (once signup is sorted) or a device's built-in on-device hotword API
would be the more efficient long-term choice.

## Building from a phone only, with no PC (GitHub Actions)
No Android Studio needed for this path — GitHub builds the APK in the cloud, you just
download the result. `.github/workflows/build.yml` is already set up for this.

### 1. Get the project onto GitHub
The most reliable way from a phone is **Termux** (install from F-Droid, not Play Store
— the Play version is outdated):
```
pkg install git
termux-setup-storage
cd storage/downloads          # wherever you extracted the project zip
cd VoiceAssistant
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/voice-assistant.git
git push -u origin main
```
(Create the empty repo first at github.com/new, in your phone's browser — public or
private both work, Actions is free for both.) It'll ask for your GitHub username and a
Personal Access Token as the password (Settings → Developer settings → Personal access
tokens on github.com — generate one with `repo` scope, since GitHub stopped accepting
plain passwords for git push).

*(Alternative if Termux feels like too much: on github.com in your phone's browser,
create the repo, then use "Add file → Upload files" and select all the project files.
Mobile browsers usually can't upload nested folders in one go, so you may need to
upload subfolders one at a time — Termux + git is more reliable for this project's
folder depth.)*

### 2. Let it build
Once pushed, go to your repo → **Actions** tab on github.com. A "Build APK" run should
already be in progress (it triggers automatically on push). Wait for the green
checkmark (a few minutes). The workflow installs Gradle itself on GitHub's build
machine — GitHub's runners come with the Android SDK preinstalled, and no Gradle
Wrapper files need to be committed to the repo.

### 3. Download the APK
Click the finished run → scroll to **Artifacts** → download `app-debug-apk` (it's a
zip containing the `.apk`). Extract it with any file manager app.

### 4. Install it
Open the extracted `.apk` directly from your file manager. Android will ask you to
allow installs from that app ("Install unknown apps") — approve it, then install.
This is a **debug build**, auto-signed with a throwaway key so it installs
immediately — perfect for testing on your own phone. It is NOT the signed release
build you'd hand to a client (see the signing section above) — for that you'd add
your keystore as GitHub Actions **secrets** instead of a local `local.properties`
file, and add an `assembleRelease` job. Ask me when you're ready for that and I'll set
it up.

### About the Vosk model and NEWS_API_KEY on this path
Since there's no `local.properties` on GitHub's build machine, you'll need to either:
- Commit the Vosk model folder into `app/src/main/assets/model/` directly (it's just
  files, ~40MB, git handles it fine), and
- Add `NEWS_API_KEY` as a GitHub Actions **secret** (repo Settings → Secrets and
  variables → Actions → New repository secret), then reference
  `${{ secrets.NEWS_API_KEY }}` in the workflow — ask me and I'll wire that in when
  you're ready to test the news handler.



You'll do this in Android Studio (or via command-line Gradle if you prefer) once
you've opened this project folder — this sandbox can't run the Android SDK/Gradle
itself, so these are the exact steps to run on your own machine.

### 1. Generate a release keystore (one-time)
Open a terminal in the project root and run:
```
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias voiceassistant
```
It'll prompt for a keystore password, your name/org details, and a key password.
**Save these somewhere safe** — if you lose this keystore, you can never update this
app under the same signature again; you'd have to ship it as a brand new app.

### 2. Point Gradle at it via local.properties
Add these four lines to `local.properties` (create the file at the project root if it
doesn't exist yet — it's already git-ignored):
```
RELEASE_STORE_FILE=../release-key.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=voiceassistant
RELEASE_KEY_PASSWORD=your_key_password
```
While you're in `local.properties`, also add the `NEWS_API_KEY=...` line from step 6
if you haven't already — both live in the same git-ignored file.

### 3. Build the signed release APK
From the project root:
```
./gradlew assembleRelease
```
(On Windows: `gradlew.bat assembleRelease`.) The signed APK lands at:
```
app/build/outputs/apk/release/app-release.apk
```

### 4. Verify it's actually signed (optional sanity check)
```
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
(`apksigner` ships with the Android SDK build-tools.)

### 5. Install it directly on a device, or send it to your client
- Direct install over USB with the device connected and USB debugging on:
  `adb install app/build/outputs/apk/release/app-release.apk`
- To hand off to a client: just send them the `.apk` file (email, Drive link,
  whatever). They'll need **"Install unknown apps"** enabled for whichever app they
  use to open it (Files, Chrome, etc.) — this is standard for any APK installed
  outside the Play Store and worth mentioning to them ahead of time so it's not a
  surprise.

## Battery & privacy notes to be upfront with your client about
- **Battery**: Porcupine is designed for always-on listening (~10-15 mW), far cheaper
  than continuous `SpeechRecognizer`, but any always-on background process still adds
  measurable daily drain, especially with the screen off.
- **The mic indicator is not optional.** From Android 12 onward, a green dot appears
  in the status bar and Quick Settings any time the mic is actively captured —
  including by Porcupine's low-power listening. It cannot be hidden.
- **The notification is not optional either.** Android requires an ongoing,
  non-dismissible notification for any foreground service using the microphone.
- **Manufacturer battery managers** (Xiaomi/MIUI, OnePlus, Samsung, Huawei, etc.) often
  kill background services even after the standard battery-optimization exemption is
  granted. On some devices the user needs an OEM-specific "autostart"/"no
  restrictions" setting — outside Android's control, worth flagging as a known
  limitation.
- **Data handling**: news headlines go over the network to NewsAPI.org; if you later
  swap Android's on-device STT for a cloud STT API, spoken audio would leave the
  device too — either should be disclosed in a privacy notice if this ships to real
  users.
- **Play Store restriction** (only relevant if this ever moves beyond direct-APK
  delivery): `READ_CONTACTS`, `SEND_SMS`, and `CALL_PHONE` are heavily restricted by
  Play policy unless the app is a default SMS/dialer/assistant handler.

- **Step 7** — `BootReceiver.kt`. Listens for `BOOT_COMPLETED` (and
  `QUICKBOOT_POWERON`, which some OEMs fire instead/also) and restarts
  `AssistantForegroundService`. Already declared in the manifest since step 1; this
  step just added the missing class file.

  **Known limitation, worth flagging to your client**: a receiver can't request
  runtime permissions itself. If the user never opened the app once to grant
  mic/contacts/SMS/call/notification permissions via `MainActivity`, the service will
  still start after reboot but individual handlers will just reply "I need X
  permission" rather than crashing — it degrades gracefully rather than failing
  silently.

- **Step 6, handler 8/8: news** — `handlers/NewsHandler.kt` fetches top headlines from
  NewsAPI.org via OkHttp, async (this is the one handler that's genuinely async — a
  network call — so unlike the other seven it takes a callback instead of returning a
  String directly). Checks connectivity first and gives a clear spoken message if
  there's no internet, an API error, or an empty response, rather than failing silently.

  **API key handling**: `app/build.gradle.kts` now reads `NEWS_API_KEY` from
  `local.properties` (git-ignored by default) into a `BuildConfig` field — add a line
  `NEWS_API_KEY=your_key_here` to your local `local.properties` file. The key is never
  hardcoded in source.

  `AssistantForegroundService.executeCommand()` branches specially for `NEWS`: its
  callback fires on a background thread (OkHttp's own dispatch), so it hops back onto
  the service's coroutine scope (`Dispatchers.Main`) before touching `state`/TTS —
  every other handler still runs synchronously on the calling thread.

  **All 8 command categories are now wired**: time, open_app, calculation, music,
  alarm, message, call, news.

- **Step 6, handlers 6-7/8: message + call** — `handlers/ContactLookup.kt` is a shared
  helper (used by both) that fuzzy-matches a spoken contact name against
  `ContactsContract` via the same Levenshtein-distance approach as `OpenAppHandler`.
  - `MessageHandler.kt` parses "message &lt;contact&gt; that/saying &lt;text&gt;"
    (falling back to "first word = name, rest = message" if no separator word is
    spoken), then sends via `SmsManager` by default, or opens a pre-filled WhatsApp
    chat via its `wa.me` link if the command mentioned WhatsApp — WhatsApp has no
    send-without-opening API, so that path always shows its UI (another
    action-needs-it exception, same category as open_app/music/alarm).
  - `CallHandler.kt` places the call directly via `Intent.ACTION_CALL` if `CALL_PHONE`
    is granted, or falls back to `ACTION_DIAL` (opens the dialer pre-filled, user taps
    call) if not — both intentionally check the permission at call time rather than
    assuming it's still granted from the `MainActivity` request, since permissions can
    be revoked later in system settings.

- **Step 6, handler 5/8: alarm/reminder** — `handlers/AlarmHandler.kt`. Two options,
  both included:
  1. `handle()` (the one wired in by default) uses `AlarmClock.ACTION_SET_ALARM` — the
     standard "hand this to the Clock app" intent. No `SCHEDULE_EXACT_ALARM`
     special-access screen needed, and the alarm shows up in the user's Clock app like
     any other. On some OEMs this briefly shows the Clock app's UI (a few confirm
     silently) — a third instance of the "unless the action needs it" UI exception.
  2. `scheduleSilentReminder()` is a fully headless alternative using `AlarmManager`
     directly + a new `ReminderReceiver` that speaks the reminder via a throwaway TTS
     instance when it fires — zero UI ever, but requires the user to grant "Alarms &
     reminders" special access first (Settings, Android 12+) before it'll work.
  Both parse loose spoken times ("7 30", "7:30 pm", plain "7") via regex; swap which
  one `AssistantForegroundService` calls depending on which behavior your client wants.

  Added `.handlers.ReminderReceiver` to the manifest for the silent path.

- **Step 6, handler 4/8: music** — `handlers/MusicHandler.kt`. Tries Spotify first (via
  its `spotify:search:<query>` deep link — no Spotify API integration needed, Spotify
  does the search itself), falls back to YouTube Music's web search URL if Spotify
  isn't installed, then falls back further to a loose filename match against the
  device's Music folder played through `MediaPlayer`, and finally a clear spoken
  failure if none of those work. Playing via Spotify/YT Music is a second deliberate
  exception to "no UI" (same reasoning as open_app — the original spec named Spotify
  explicitly as an example of this).

  Added `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (below 13, capped
  with `maxSdkVersion`) to the manifest and `MainActivity`'s runtime-permission request
  for the local-file fallback path.

- **Step 6, handler 3/8: calculation** — `handlers/CalculationHandler.kt`. Normalizes
  spoken operator words ("plus", "times", "divided by") to symbols, extracts a single
  `number operator number` expression via regex, and evaluates it — deliberately kept
  to one binary operation (no precedence/multi-step), which is plenty for a quick voice
  calc. Handles divide-by-zero and formats whole-number results without a trailing
  ".0".

  **Bug fix while wiring this in**: the classifier was stripping everything before the
  matched keyword to build the "remainder" (so handlers only see what comes after the
  trigger word) — fine for "play a song" → remainder "a song", but wrong for
  "12 plus 7", where stripping after "plus" would have thrown away the "12". Added a
  `needsFullText` set in `IntentClassifier` so `TIME` and `CALCULATION` get the whole
  original text instead of a stripped remainder.

- **Step 6, handler 2/8: open_app** — `handlers/OpenAppHandler.kt`. Queries
  `PackageManager` for all launchable apps, fuzzy-matches the spoken name against each
  app's display label using Levenshtein edit distance (STT text rarely matches an app's
  exact name), and launches the closest match under a similarity threshold via its
  launch `Intent`. This is the one deliberate exception to "no UI" — opening another
  app necessarily shows that app's own screen, matching the Spotify example in the
  original requirement.

  Relies on `QUERY_ALL_PACKAGES` (already in the manifest from step 1) — without it,
  Android 11+ only exposes a small default-visible subset of installed apps to
  `PackageManager`, so most third-party apps wouldn't be found.

- **Step 6, handler 1/8: time** — `handlers/TimeHandler.kt`. Reads current time or, if
  the command mentioned "date", the current date, off `Date()`/`SimpleDateFormat` —
  fully offline, no extra permissions. Wired into `executeCommand()`'s `TIME` branch;
  the other 7 categories still show placeholder replies until their turn.

- **Step 5** — `IntentClassifier.kt`: pure offline keyword/regex matching, returns a
  `CommandCategory` (`TIME, MUSIC, NEWS, OPEN_APP, ALARM, MESSAGE, CALL, CALCULATION,
  UNKNOWN`) plus whatever text is left after the trigger keyword (the "remainder") for
  handlers to parse further — e.g. "play shape of you" → category `MUSIC`, remainder
  `"shape of you"`. Rule order matters (more specific phrases checked first); it's a
  flat list you can freely reorder/extend as you test real phrasing.

  The service now runs classify → `executeCommand()` → TTS confirms which category was
  matched (placeholder replies for now — real logic lands one handler at a time in step
  6). This lets you verify the classifier's accuracy against real speech before writing
  any handler logic.

  **Upgrade path noted for later** (not built): swap `IntentClassifier.classify()` for
  an on-device TFLite text classifier or a single cloud LLM call once the rule-based
  version needs smarter matching — same `IntentResult` return type either way, so
  nothing else in the app changes.

- **Step 4** — `SttEngine.kt` wraps `SpeechRecognizer` in fully headless mode (driven
  directly, not via `startActivityForResult`, so no mic UI ever appears). `TtsEngine.kt`
  wraps `TextToSpeech` and reports back via `onSpeechFinished` so the service knows when
  it's safe to resume listening. The service now runs the whole loop end-to-end: wake
  word → capture command → **echo it back via TTS** (temporary — real dispatch lands in
  step 5's classifier and step 6's handlers) → resume wake-word listening. This lets you
  test the full audio pipeline before the classifier exists.

  **STT tradeoff note**: this uses Android's built-in `SpeechRecognizer`, which is free
  and needs no API key, but accuracy and offline support vary by device/OEM. If you need
  more consistent accuracy, a cloud STT API (Google Cloud Speech-to-Text, Whisper API)
  is worth the added latency/cost/key management — `SttEngine` is written so swapping the
  implementation later doesn't require touching the service.

- **Step 3** — `WakeWordEngine.kt` originally wrapped `PorcupineManager`. **Since
  updated to use Vosk instead** (see "Wake-word engine: Vosk, not Porcupine" note
  below) — no account, API key, or login required at all.

- **Step 1** — project structure, Gradle files, full manifest (see permission list below).
- **Step 2** — `VoiceAssistantApp` (notification channel setup) and
  `AssistantForegroundService` skeleton. The state machine already has slots reserved
  for every later step (`CLASSIFYING`, `EXECUTING_COMMAND`, `SPEAKING`) so it won't need
  restructuring as we add the classifier and handlers.
  Also added `MainActivity`, which requests the full runtime-permission set
  (mic, contacts, SMS, call, notifications) and the battery exemption, then starts the
  service. Contacts/SMS/call are requested but not hard-required — the assistant still
  starts and handles time/open-app/calculation/music without them, with a toast warning
  about which features will be limited.
  - `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`
  - `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`
  - `POST_NOTIFICATIONS` (Android 13+ runtime)
  - `INTERNET`, `ACCESS_NETWORK_STATE` (news headlines)
  - `QUERY_ALL_PACKAGES` (open-app-by-name needs to see all installed apps on API 30+)
  - `READ_CONTACTS`, `SEND_SMS` (message a saved contact)
  - `CALL_PHONE` (call a saved contact)
  - `SET_ALARM`, `SCHEDULE_EXACT_ALARM` (alarm/reminder handler)

### Which permissions need a runtime prompt (Android 13+ / dangerous-level)
Requested at first run via `MainActivity`, same as before:
`RECORD_AUDIO`, `POST_NOTIFICATIONS`, `READ_CONTACTS`, `SEND_SMS`, `CALL_PHONE`.
`SCHEDULE_EXACT_ALARM` needs a separate special-access screen on Android 12+ (handled
in the alarm-handler step, not the standard runtime dialog).
Normal-level permissions (`INTERNET`, `FOREGROUND_SERVICE*`, `RECEIVE_BOOT_COMPLETED`,
`WAKE_LOCK`, `QUERY_ALL_PACKAGES`, `SET_ALARM`) are granted automatically at install —
no prompt needed.
