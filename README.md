# CipherGram

![CipherGram Cover](https://github.com/abdulhayykhan/CipherGram/assets/placeholder/cover.png)

CipherGram is a cutting-edge Android messaging application that combines the aesthetics of **Cyber Tech Glassmorphism** with military-grade security. Built natively for Android using Kotlin and Jetpack Compose, CipherGram allows users to log in securely with their Meta (Facebook/Instagram) accounts and instantly begin end-to-end encrypted (E2EE) chats with friends. 

With an infrastructure powered by Google Firebase and a responsive, dynamic Light/Dark mode interface, CipherGram guarantees your messages remain strictly between you and your recipient.

---

## 🚀 Key Features

* **End-to-End Encryption (E2EE)**: True on-device encryption using Elliptic Curve Diffie-Hellman (ECDH) key exchange and AES-256-GCM. Your private keys never leave your phone. Firebase only ever stores scrambled ciphertext.
* **Meta Identity Integration**: Seamless Facebook Login handles user authentication, directly bridging to Firebase Auth.
* **Cyber Tech Glassmorphism UI**: A gorgeous, state-of-the-art interface featuring dynamic pulsating borders, frosted glass panels, and cyber-neon accents that smoothly adapt to your device's Light or Dark mode.
* **Real-time Synchronization**: Messages, user searches, and online statuses synchronize instantly using Firebase Cloud Firestore.
* **Local Caching**: App preferences and user data are cached locally via Jetpack DataStore to minimize network requests.
* **Media Rich**: Supports Instagram profile scraping and reel previews directly within the chat interface.

---

## 📖 User Manual & Guide

Welcome to CipherGram! Here is everything you need to know to securely chat with your friends.

### 1. Installation & Login
1. **Launch the App**: Open CipherGram on your Android device. You will be greeted by the cyber-tech login portal.
2. **Facebook Authentication**: Tap the **"Login with Facebook"** button. This will redirect you to Meta's secure authorization page.
3. **Approve Permissions**: Allow CipherGram access to your public profile and email. Once approved, you will be securely redirected back to the app and logged in automatically!

### 2. Navigating the App
Upon logging in, you will be presented with the **Thread List** (your inbox).
* **Top Bar**: Displays your profile name and the secure encryption lock indicator.
* **Thread List**: All of your active conversations. Threads are displayed with frosted-glass backgrounds. Tap on any conversation to enter the chat.
* **Dark/Light Mode**: The app will automatically adapt to your phone's system theme. To change between the deep Obsidian Dark Mode and the frosted Silver Light Mode, toggle your phone's system settings.

### 3. Starting a New Secure Chat
1. Tap the **Floating Action Button (FAB)** (the bright, pulsating plus icon at the bottom right) to start a new chat.
2. The **Search Screen** will open. Type the exact username of the friend you wish to chat with. *(Note: Your friend must have logged into CipherGram at least once to be searchable).*
3. Tap on their name in the search results to generate a cryptographic tunnel and open a new chat thread!

### 4. Sending Encrypted Messages
1. In the **Chat Screen**, type your message in the bottom text field. 
2. **Encryption in Action**: As soon as you hit "Send," the app generates a unique symmetric AES key, encrypts the message locally on your CPU, and sends the scrambled ciphertext to the cloud. 
3. **Decryption**: The recipient's phone will automatically pull the ciphertext and decrypt it locally using their private key. No server or middleman can ever read your messages!

---

## 🛠 Developer Setup & Deployment Guide

Want to build and compile this app from source? Follow these steps exactly:

### Prerequisites
* **Android Studio Ladybug** (or newer)
* **JDK 11+**
* An active **Google Account** (for Firebase)
* A **Meta Developer Account** (for Facebook Login)

### Phase 1: Android Studio Setup
1. Clone this repository:
   ```bash
   git clone https://github.com/abdulhayykhan/CipherGram.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync. If it fails, ensure your Android Gradle Plugin (AGP) version matches your Gradle distribution version (AGP `8.8.0` is recommended for Gradle `9.0.0`).
4. Generate your local debug `SHA-1` Key:
   * Open the Gradle panel on the right side of Android Studio.
   * Click the "Execute Gradle Task" (Elephant) icon.
   * Run the command: `signingReport`. Copy the `SHA1` output.

### Phase 2: Firebase Configuration
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Add an Android App with the package name `com.aistudio.ciphergram.xtzqjp`.
3. Enter the `SHA-1` key you generated in Phase 1.
4. Download the `google-services.json` file and place it in the `app/` directory of the project.
5. In Firebase, go to **Build > Authentication > Sign-in method** and enable **Facebook**.
6. You will need your Meta App ID and App Secret (see Phase 3 below).
7. Copy the **OAuth Redirect URI** provided by Firebase at the bottom of the Facebook setup box.

### Phase 3: Meta for Developers (Facebook Login)
1. Go to [Meta for Developers](https://developers.facebook.com/) and create a new App (Type: Consumer or None).
2. Get your **App ID** and **App Secret** (from App Settings > Basic) and paste them into Firebase.
3. Set up **Facebook Login** under "Use Cases" or "Products".
4. Paste the **OAuth Redirect URI** (from Firebase) into the **Valid OAuth Redirect URIs** box and save.
5. Go to **App Settings > Advanced** and copy your **Client Token**.
6. Open `app/src/main/res/values/strings.xml` in Android Studio and inject your keys:
   ```xml
   <string name="facebook_app_id">YOUR_APP_ID</string>
   <string name="fb_login_protocol_scheme">fbYOUR_APP_ID</string>
   <string name="facebook_client_token">YOUR_CLIENT_TOKEN</string>
   ```

### Phase 4: Firestore Setup
1. In Firebase, go to **Firestore Database** and create a database.
2. Start in **Test Mode** (or configure secure rules that only allow authenticated users to read/write their own thread documents).

### Phase 5: Build & Run
1. With all credentials injected, click the **Run** (Play) button in Android Studio.
2. The app will compile and launch on your connected device or emulator!

---

## 📜 Architecture & Security Tech Stack

* **Language**: Kotlin
* **UI Toolkit**: Jetpack Compose (Material 3)
* **Cryptography**: `javax.crypto` (AES/GCM/NoPadding, ECDH P-256)
* **Backend Cloud**: Firebase Authentication, Cloud Firestore
* **Local Storage**: Jetpack DataStore (Preferences)
* **Image Loading**: Coil Compose
* **Asynchronous Operations**: Kotlin Coroutines & Flow

*CipherGram is built with an absolute commitment to privacy. The code ensures that plaintext payloads are never temporarily cached to disk before encryption, and ephemeral keys are wiped from memory immediately after decryption.*
