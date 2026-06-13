# 🔐 CipherGram

#### *An advanced client-side End-to-End Encrypted (E2EE) alternate chat shell built dynamically with Jetpack Compose for Android.*

CipherGram is designed as an ultra-secure, offline-first chat client utilizing modern Android hardware-backed cryptography and modular sandboxing. It allows users to manage messaging threads securely, preview media inline using a modern glassmorphic interface, and utilize localized database persistence seamlessly.

---

## 🛠️ Key Architectural Features

### 1. Local Cryptographic Storage Layer
CipherGram takes security at rest seriously. Sensitive credentials, manual session variables (such as Access Tokens, User-Agent strings, and User IDs), or message strings can be encrypted on-device.
* **Hardware-Backed Cryptography**: Configured with a dedicated `LocalCryptoEngine` that uses the official Java Security Cryptographic KeyStore (`AndroidKeyStore`).
* **AES-GCM (256-bit) Parameters**: Operates standard AES-GCM encryption with `AES/GCM/NoPadding`, utilizing a 12-byte initialization vector (IV) and a 128-bit authentication tag size.
* **EncryptedSharedPreferences Integration**: Standard configuration variables are stored with hardware cryptographic isolation to prevent memory or disk leakage.

### 2. Client-Side E2EE Text-Tunneling Engine
For end-to-end security, CipherGram features dynamic text packaging allowing encrypted payloads to safely transit chat pipelines:
* **Zero Trust Transport**: Converts plain texts instantly inside the sender state wrapper into Base64 ciphertext prefixed with our signature cryptographic marker (`LENC:`).
* **Decoupled Key Exchange Integration**: Eliminates downstream reliance on server-side capabilities, performing local decryption matching directly within our sandboxed attachment engine before render.

### 3. Jetpack Media3 Inline Rendering
The visual message history is enhanced with sleek media renderers:
* **Glassmorphic Styling**: Visually dynamic list cards styled with rounded container shapes and Material 3 layouts.
* **ExoPlayer Integration**: Employs deep Media3 `ExoPlayer` integration embedded through an `AndroidView` surface layout.
* **Mute/Play Control Loops**: High-performance thread layout with tap gestures designed for mute/playback loop states.

---

## 🚀 Automated CI/CD Workflow Documentation

CipherGram utilizes an automated, highly secure DevOps pipeline configured via GitHub Actions (`.github/workflows/deploy-apk.yml`):

### How the Pipeline Works
* **Workflow Trigger**: The pipeline executes exclusively when a standard semantic versioning Git tag matching the pattern `v*.*.*` is pushed directly to the repository (e.g., `v1.0.0`).
* **Clean runner Environment**: Runs the build on an isolated `ubuntu-latest` container.
* **Java/Gradle Infrastructure**: Boots up **JDK 17** via the official `actions/setup-java` (Temurin distribution) with intelligent, built-in Gradle build-caching enabled.
* **High-Security Keystore Decoding**: Safely extracts the base64-stored Keystore secret (`secrets.ANDROID_KEYSTORE`), decodes it gracefully within runtime memory, and stores it inside a temporary workspace path (`.signing/my-upload-key.jks`) for use by the compilation step.
* **Release Artifact Creation**: Compiles a optimized, signed, aligned, and zip-aligned release `.apk` via `./gradlew assembleRelease` and `r0adkll/sign-android-release`.
* **GitHub Release Deployment**: Deploys the release assets directly to GitHub Releases. The action generates automated release notes detailing changes since the prior tag release.

---

## 📖 User Manual & Operator Guide

### 1. How to Securely Download and Install CipherGram
1. Go to your GitHub repository's **Releases** tab.
2. Download the verified and signed compilation asset: `CipherGram.apk`.
3. Transfer the file to your Android device or build it using the stream preview.
4. On your mobile device, open your File Manager application, select the downloaded `.apk` file, and enable "Install from Unknown Sources" if prompted to complete the side-loading process safely.

### 2. Initial Setup & Onboarding Navigation
1. **Interactive Config Panel**: Upon launching the app, you will be greeted by the secure configuration screen.
2. **Standard vs. Advanced Config Mode**:
   * **Standard Session Mode**: Open the **Automated Browser Sync** sheet to sign in securely to the official platform end points inside a sandboxed WebView, capturing session cookies automatically. To keep authentication pure, **no passwords** are ever logged; only approved auth keys are localized.
   * **Manual Value Input**: Switch to manual config if you already have access parameters. Enter your *Access Token*, *User-Agent string*, and *User ID* into the masked, highly responsive input text fields.
3. **Launch Sandbox Mode**: Toggle "Launch Sandbox" to immediately bypass network steps. This instantiates a mock layout populated with default cryptographic test streams, ideal for evaluating ExoPlayer media and E2EE.

### 3. Messaging and Media Interlocution
* Tap on any chat room on the thread list dashboard.
* Click the **Encryption Active** toggle in the text entry bar to enforce full KeyStore GCM ciphertext packaging.
* Tap on audio waveform players or loop video playback blocks natively to verify the multimedia playback systems.

---

## 🔒 Secrets & Keystore Configuration Blueprint

To enable automated release signing in GitHub Actions, you must generate a Java keystore and format it as a Base64 string to register as highly secure environment secrets:

### 🛠️ Terminal Base64 Conversion
Run the following standard console execution string on your terminal depending on your development platform:

**macOS / Linux terminal**:
```bash
base64 -i my-upload-key.jks -o keystore-base64.txt
```
*Alternatively, using `base64` wrapping flags:*
```bash
base64 -w 0 my-upload-key.jks > keystore-base64.txt
```

**PowerShell (Windows)**:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) > keystore-base64.txt
```

### GitHub Secrets Registration Matrix
Copy the contents of `keystore-base64.txt` and populate your GitHub repository secrets securely under:

| GitHub Secret Key | Description |
| ------------------ | ----------- |
| `ANDROID_KEYSTORE` | Paste the exact Base64 string from `keystore-base64.txt`. |
| `KEY_ALIAS` | The alias given to your signing key during keystore creation. |
| `KEYSTORE_PASSWORD` | The primary master password for your `.jks` file. |
| `KEY_PASSWORD` | The password configured for the key itself inside your keystore. |

---

## ⚙️ Manual Action Verification Checklist

To ensure the automated setup can write release artifacts without authentication bottlenecks:
1. Navigate to **Settings** > **Actions** > **General** inside the repository browser interface.
2. Scroll to the **Workflow permissions** configuration block.
3. Explicitly toggle the radio button to **"Read and write permissions"**.
4. Check **"Allow GitHub Actions to create and approve pull requests"** and select **Save**.

---

## 📄 License

This project is open-source and available for educational and commercial use under the MIT License.

---

**Made with ❤️ by [Abdul Hayy Khan](https://www.linkedin.com/in/abdulhayykhan/)**
