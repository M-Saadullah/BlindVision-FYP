# BlindVision - Vision Assist App

A Final Year Project (FYP) Android application designed to assist visually impaired users by providing real-time object detection and audio feedback. The app uses TensorFlow Lite for on-device machine learning to detect objects through the camera and announces them via text-to-speech in both English and Urdu.

## 🎯 Features

### Core Functionality
- **Real-time Object Detection**: Detects 80 different object classes using a YOLOX-based TensorFlow Lite model
- **Audio Feedback**: Text-to-speech announcements in English and Urdu
- **Voice Commands**: Speech recognition for language selection and mode activation
- **Gesture Navigation**: Tap-based navigation system for easy access
- **Depth Estimation**: Optional Time-of-Flight (ToF) camera support for distance measurement
- **Multiple Modes**:
  - **Indoor Mode**: Optimized for indoor environments
  - **Outdoor Mode**: Optimized for outdoor environments
  - **Object Finder Mode**: Enhanced object detection and location

### User Experience
- **Bilingual Support**: Seamless switching between English and Urdu
- **Firebase Authentication**: Secure user sign-up and sign-in
- **User Preferences**: Settings and profile management
- **Accessibility First**: Designed with accessibility as the primary focus

## 🛠️ Technology Stack

### Core Technologies
- **Language**: Kotlin
- **ML Framework**: TensorFlow Lite 2.17.0
- **Camera**: Android Camera2 API
- **Backend**: Firebase (Authentication, Realtime Database, Firestore, Analytics)
- **UI**: Material Design Components, Jetpack Compose

### Key Libraries
- `org.tensorflow:tensorflow-lite:2.17.0` - Machine learning inference
- `com.google.ai.edge.litert:litert:1.2.0` - TensorFlow Lite Runtime
- `androidx.camera:camera-core:1.3.0` - Camera functionality
- Firebase BOM 33.9.0 - Backend services
- Android Speech APIs - Text-to-Speech and Speech Recognition

## 📋 Prerequisites

- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: Version 11 or higher
- **Android SDK**: 
  - Minimum SDK: 24 (Android 7.0)
  - Target SDK: 35 (Android 15)
  - Compile SDK: 35
- **Gradle**: 8.9.0 or later
- **Kotlin**: 2.0.21 or later

## 🚀 Getting Started

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/BlindVision-FYP.git
   cd BlindVision-FYP
   ```

2. **Set up Firebase**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Download `google-services.json` and place it in the `app/` directory
   - Enable the following Firebase services:
     - Authentication (Email/Password, Google Sign-In)
     - Realtime Database
     - Firestore
     - Analytics

3. **Configure the ML Model**
   - Ensure `model.tflite` is present in `app/src/main/assets/`
   - The model should be a YOLOX-based model trained on COCO dataset (80 classes)

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run on device**
   - Connect an Android device or start an emulator
   - Run the app from Android Studio or use:
     ```bash
     ./gradlew installDebug
     ```

## 📱 Usage

### First Launch
1. The app starts with a splash screen
2. New users are directed to sign-up, existing users can sign in
3. After authentication, you'll reach the home screen

### Using the App

1. **Language Selection** (on first launch):
   - The app will prompt you to choose a language
   - Say "URDU" to switch to Urdu or "CONTINUE" to stay in English

2. **Mode Selection**:
   - **Single Tap**: Activates Indoor Mode
   - **Double Tap**: Activates Outdoor Mode
   - **Triple Tap**: Activates Object Finder Mode

3. **Object Detection**:
   - Once in camera mode, the app continuously detects objects
   - Detected objects are announced via text-to-speech
   - If ToF camera is available, distance information is also provided

4. **Settings**:
   - Access settings from the menu
   - Manage profile, language preferences, and notifications

## 🏗️ Project Structure

```
BlindVision-FYP/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   └── model.tflite          # ML model file
│   │   │   ├── java/com/example/tflitetest/
│   │   │   │   ├── MainActivity.kt       # Splash/Entry activity
│   │   │   │   ├── HomeActivity.kt       # Main home screen
│   │   │   │   ├── LiveCameraActivity.kt  # Camera & detection
│   │   │   │   ├── SignInActivity.kt     # User authentication
│   │   │   │   ├── SignUpActivity.kt     # User registration
│   │   │   │   ├── SettingsActivity.kt   # App settings
│   │   │   │   ├── ObjectSpeech.kt       # TTS wrapper
│   │   │   │   ├── ml/
│   │   │   │   │   ├── Predictor.kt      # ML inference
│   │   │   │   │   ├── Decoder.kt        # Model decoder
│   │   │   │   │   └── ValTransform.kt   # Image preprocessing
│   │   │   │   └── ui/                   # UI components
│   │   │   └── res/                      # Resources
│   │   └── androidTest/                  # Instrumented tests
│   └── build.gradle.kts                  # App-level build config
├── build.gradle.kts                      # Project-level build config
├── gradle/
│   └── libs.versions.toml                # Dependency versions
└── settings.gradle.kts                   # Project settings
```

## 🔧 Configuration

### Model Configuration
The ML model is configured in `LiveCameraActivity.kt`:
- **Model**: YOLOX-based TensorFlow Lite model
- **Input Size**: 416x416 pixels
- **Classes**: 80 COCO classes
- **Confidence Threshold**: 0.3
- **NMS Threshold**: 0.45

### Camera Settings
- **Primary Camera**: Regular RGB camera for object detection
- **ToF Camera**: Optional depth sensor for distance estimation
- **Image Format**: YUV_420_888
- **Processing**: Real-time frame processing with GPU acceleration

## 🔐 Permissions

The app requires the following permissions:
- `CAMERA` - For capturing images for object detection
- `RECORD_AUDIO` - For speech recognition
- Internet access (for Firebase services)

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## 📊 Performance

The app is optimized for real-time performance:
- **Inference Time**: Optimized with GPU delegate
- **Frame Processing**: Background thread processing
- **Memory Management**: Efficient bitmap recycling
- **Speech Output**: Queue-based TTS to prevent overlap

## 🤝 Contributing

This is a Final Year Project. For contributions or questions:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📝 License

This project is part of a Final Year Project. Please contact the authors for licensing information.

## 👥 Authors

- Final Year Project Team

## 🙏 Acknowledgments

- TensorFlow Lite team for the ML framework
- COCO dataset contributors
- Firebase team for backend services
- Android accessibility community

## 📞 Support

For issues, questions, or feature requests, please open an issue on the GitHub repository.

---

**Note**: This app is designed specifically for visually impaired users and prioritizes accessibility and ease of use. All features are optimized for audio feedback and gesture-based navigation.

