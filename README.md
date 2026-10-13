# Crucible Intelligence SMS App

A native Android SMS Messaging Application built with modern Android development practices, Kotlin, Jetpack Compose (Material 3 Expressive design), MVVM architecture, Kotlin Coroutines/Flow, and native Telephony APIs.

---

## 📱 Application Overview

**Crucible Intelligence SMS App** delivers a modern, high-performance messaging experience for Android. Designed as a full-featured default SMS handler, it interacts directly with Android's system `Telephony` provider while maintaining local intelligence storage for messaging entities and audit records.

* **Language**: 100% Kotlin
* **UI Toolkit**: Jetpack Compose with Material 3 Expressive styling
* **Navigation**: Jetpack Navigation 3 (`androidx.navigation3`)
* **Architecture**: Model-View-ViewModel (MVVM) with Unidirectional Data Flow (UDF)
* **Asynchronous Streams**: Kotlin Coroutines & `StateFlow`
* **Local Persistence**: Native Android `Telephony.Sms` Content Provider + Room Database (`AppDatabase`)
* **System Capabilities**: Full Default SMS App integration (`RoleManager`), background SMS/MMS receivers, headless send service.

---

## 🏗️ Architecture

The app adheres strictly to clean Architecture principles with an MVVM foundation:

```
┌──────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                     │
│           (InboxScreen, ConversationScreen)               │
└────────────────────────────▲─────────────────────────────┘
                             │ StateFlow / Callbacks
┌────────────────────────────┴─────────────────────────────┐
│                       ViewModels                         │
│           (InboxViewModel, ChatViewModel)                │
└────────────────────────────▲─────────────────────────────┘
                             │ Reactive Flow
┌────────────────────────────┴─────────────────────────────┐
│                      Repositories                        │
│          (SmsRepository, SmsSenderRepository)            │
└───────────────▲──────────────────────────▲───────────────┘
                │                          │
┌───────────────┴───────────────┐  ┌───────┴───────────────┐
│     Telephony.Sms Provider    │  │     Room Database     │
│   (System Single Source)      │  │  (Local Entity Data)  │
│   + ContentObserver Flow      │  │   AppDatabase (Room)  │
└───────────────────────────────┘  └───────────────────────┘
```

### Key Architectural Concepts:
1. **System Content Provider as Single Source of Truth**:
   - Primary messaging data is read directly from and written to the native Android `Telephony.Sms` content provider (`content://sms`).
   - Changes in `Telephony.Sms` are observed in real time using a custom `ContentObserver` wrapped inside Kotlin's `callbackFlow`, automatically emitting updated state through `StateFlow`.

2. **Unidirectional Data Flow (UDF)**:
   - **State**: ViewModels expose immutable `StateFlow` representations of screens (`InboxScreen`, `ConversationScreen`).
   - **Events**: User actions (sending messages, selecting threads, requesting default role) trigger suspend methods on ViewModels, which call the repository layer.

3. **Hybrid Local Caching**:
   - `SmsRepository` manages integration between `Telephony.Sms` and local Room entity profiles (`EntityProfileDao`, `MessageRecordDao`), enabling seamless contact resolution and persistent intelligence tracking.

---

## ⚙️ Core Features

### 1. SMS Sending & Multipart Fragmentation
* **Automatic Length & Part Detection**: Utilizes `SmsManager.divideMessage(body)` to divide text bodies into standard 160-character GSM segments.
* **Multipart Delivery**: Texts exceeding single PDU capacity are dispatched via `SmsManager.sendMultipartTextMessage`, ensuring reliable delivery for long messages.
* **Delivery & Sent Callbacks**: PendingIntents track delivery and send status (`SMS_SENT` and `SMS_DELIVERED`).
* **System Provider Mirroring**: Sent messages are automatically recorded into `Telephony.Sms.Sent` so system message logs remain completely synchronized.

### 2. Incoming Message Interception
* **Broadcast Receivers**: Registered receiver `CrucibleSmsReceiver` listens for `Telephony.Sms.Intents.SMS_DELIVER_ACTION` and `SMS_RECEIVED_ACTION`.
* **Async PDU Processing**: Uses `goAsync()` with Kotlin Coroutines (`Dispatchers.IO`) to process incoming SMS PDUs without blocking the main broadcast thread.
* **Instant Database Insertion**: Extracts message body, timestamp, and sender address (`getMessagesFromIntent`), records them in system `Telephony.Sms.Inbox` and local Room storage, immediately updating the active Compose UI.

### 3. Native UI & Jetpack Navigation 3
* **Inbox Screen**:
  - Displays conversation threads ordered by timestamp.
  - Generates contact avatars based on contact names or phone numbers.
  - Unread message counters and top bar default app role setup indicator.
* **Conversation Screen**:
  - Bubble-style layout distinguishing sent vs. received messages.
  - Real-time length detection and part counter (e.g., `120/160 (1)` vs. `200/160 (2)`).
  - Quick action to view contact dossiers.
* **Navigation 3 Architecture**:
  - Navigation state managed by `NavDisplay` and `NavBackStack`.
  - Type-safe `@Serializable` routes (`InboxRoute`, `ConversationRoute`).

### 4. Runtime Permission Handling
* **`PermissionWrapper` Composable**: Encapsulates runtime permission requests for:
  - `android.permission.SEND_SMS`
  - `android.permission.RECEIVE_SMS`
  - `android.permission.READ_SMS`
  - `android.permission.READ_CONTACTS`
* Provides fallback user interface prompting users to grant necessary permissions if revoked.

### 5. Default SMS App Role Integration & Services
* **RoleManager API Support**: On Android 10+ (API 29+), uses `RoleManager.ROLE_SMS` for system default app integration (`DefaultSmsHelper`). On legacy Android versions (<10), falls back to `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`.
* **Headless SMS Service**: Implements `HeadlessSmsSendService` listening for `RESPOND_VIA_MESSAGE` to fulfill quick-response messaging requests (e.g., declining phone calls with SMS).
* **MMS Handler**: Includes `CrucibleMmsReceiver` with `WAP_PUSH_DELIVER_ACTION` for system MMS compatibility requirements.

---

## 🚀 Setup & Build Instructions

### Prerequisites
* **Android Studio**: Android Studio Jellyfish (2023.3.1) / Ladybug or newer.
* **JDK**: Version 17.
* **Android SDK**: Compile SDK Level 35, Minimum SDK Level 24 (Android 7.0 Nougat).

### Opening the Project
1. Clone or download the repository to your local machine.
2. Open Android Studio and select **Open**.
3. Navigate to the `CrucibleIntelligenceSMSApp` directory and click **OK**.
4. Allow Gradle sync to complete.

### Dependencies Overview
Key dependencies specified in `app/build.gradle.kts` and `gradle/libs.versions.toml`:
* `androidx.compose.material3:material3`
* `androidx.navigation3:navigation3-runtime` & `androidx.navigation3:navigation3-ui`
* `androidx.room:room-runtime` & `androidx.room:room-ktx`
* `org.jetbrains.kotlinx:kotlinx-serialization-json`
* `androidx.core:core-ktx`

### Building Debug APK
You can build the project from Android Studio or via the command line:

```bash
# On Linux / macOS
./gradlew app:assembleDebug

# On Windows PowerShell / Command Prompt
.\gradlew.bat app:assembleDebug
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Testing Guidelines (Android Studio Emulator)

To test the application thoroughly, use the official Android Studio Emulator.

### Step 1: Initial Launch & Role Setup
1. Run the app on an Android Emulator (API 29+ recommended).
2. Upon launching, grant the requested permissions (**SMS** and **Contacts**) via `PermissionWrapper`.
3. Accept the prompt to set **Crucible Intelligence** as your **Default SMS app** (or tap **Set Default** in the top bar).

### Step 2: Simulating Incoming SMS Texts
1. In the Emulator side toolbar, click the **Extended Controls** (`...`) icon.
2. Select the **Cellular** tab on the left menu.
3. In the **SMS message** section:
   - Enter a sender phone number in the **From** field (e.g. `+15551234567` or `555-0199`).
   - Type a test message into the **Message** box.
   - Click **Send Message**.
4. **Expected Result**:
   - The message is received by `CrucibleSmsReceiver`.
   - The thread appears instantly in the `InboxScreen`.
   - Tapping the thread opens `ConversationScreen`, displaying the incoming message bubble.

### Step 3: Testing Outgoing & Multi-part SMS
1. Tap on a conversation in the `InboxScreen` or start a new thread.
2. In the input text field, type a short message (< 160 characters) and verify the character counter displays `160`.
3. Type a long message (> 160 characters, e.g., 200 characters) and verify the counter updates to show remaining characters in the current segment and segment index (e.g. `120/160 (2)`).
4. Tap **Send**.
5. **Expected Result**:
   - `SmsManager.sendMultipartTextMessage` fragments and sends the message segments.
   - The sent message appears immediately on screen and is saved to `Telephony.Sms.Sent`.

### Step 4: Testing Role & Permission Resilience
1. Go to Emulator **Settings -> Apps -> Default apps -> SMS app**, and temporarily switch default SMS app to another app.
2. Return to Crucible Intelligence SMS App: verify the top banner "Default SMS App" prompt appears.
3. Tap **Set Default** to trigger `RoleManager` and restore default role status.

---

## 📄 License & Summary
Developed for secure native Android SMS communication adhering to Material Design 3 and Android Telephony best practices.
