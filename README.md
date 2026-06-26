# CipherGram

A secure, end-to-end encrypted (E2EE) messaging application built natively for mobile devices.

## Architecture

* **Framework**: React Native with TypeScript.
* **Backend**: Serverless Firebase integration (Authentication, Cloud Firestore).


* **State Management**: React Hooks.
* **Security**: Client-side symmetric/asymmetric encryption via dedicated cryptographic service.



## Features

* **Real-Time Chat**: Synchronized message streams utilizing Firestore `onSnapshot` listeners.
* **E2EE Payload**: Messages are encrypted locally on the device before transmission. The database only receives ciphertext.
* **Dynamic Thread Management**: Prevents duplicate chat streams by validating existing participant arrays before initializing new connections.
* **Secure Authentication**: Firebase Auth manages user sessions, identities, and access controls.

## Security

Operates on a strictly Zero-Knowledge Architecture. The backend stores only ciphertext and cannot read the contents of the messages. Firestore Security Rules enforce that users can only read or write to threads where their UID explicitly exists in the `participants` array. Encryption keys never leave the client device in plaintext.

## Local Setup

**Prerequisites**: Node.js (v18+), React Native CLI environment, Xcode (iOS) or Android Studio (Android).

1. **Clone repository**:

```bash
git clone <repository-url>
cd CipherGram

```

2. **Install Dependencies**:

```bash
npm install

```

3. **Configure Backend**:
Create a Firebase project, enable Authentication (Email/Password) and Firestore, and place your configuration keys inside `src/services/firebase.ts`.
4. **Run application**:

```bash
# For Android
npx react-native run-android

# For iOS
cd ios && pod install && cd ..
npx react-native run-ios

```

## Deployment Strategy (Sideloading)

1. Navigate to the `android/app` directory.
2. Ensure your `debug.keystore` or release Keystore is properly configured in `build.gradle`.


3. Run the automated GitHub Actions workflow defined in `.github/workflows/deploy-apk.yml` to generate the build, OR run locally:



```bash
cd android
./gradlew assembleRelease

```

4. Transfer the generated `app-release.apk` file to the target Android device and install it manually via the file manager.

## Core Logic Modules

* `LoginScreen.tsx`: Manages Firebase Auth session initialization and UI.


* `ChatListScreen.tsx`: Queries Firestore for existing threads and handles participant validation.


* `ChatScreen.tsx`: Renders active message streams and intercepts plaintext for encryption before sending.


* `encryption.ts`: Core cryptographic engine handling local payload ciphering and decryption.



## Project Structure

```text
CipherGram/
├── .github/workflows/    # Automated CI/CD pipelines (APK deployment)[cite: 2]
├── android/              # Native Android build files and configs[cite: 2]
├── ios/                  # Native iOS workspace and Podfile[cite: 2]
├── src/                  
│   ├── screens/          # React Native UI components[cite: 2]
│   └── services/         # Firebase and Encryption backend logic[cite: 2]
├── App.tsx               # Application entry point[cite: 2]
└── package.json          # Project dependencies and scripts[cite: 2]

```

## 📄 License

This project is released under the MIT License.

---

**Made with ❤️ by [Abdul Hayy Khan**](https://www.google.com/search?q=https://www.linkedin.com/in/abdulhayykhan)
