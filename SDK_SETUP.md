# Android SDK Setup Guide

## The Android SDK is not fully configured on this system. Here are the options:

### Option 1: Install Android Studio (Recommended)
1. Download Android Studio from: https://developer.android.com/studio
2. Install and launch Android Studio
3. Open this project in Android Studio
4. Android Studio will automatically download the required SDK components
5. Click "Build > Make Project" or press Ctrl+F9

### Option 2: Install SDK Command Line Tools
```bash
# Download command line tools from:
# https://developer.android.com/studio#command-tools

# Extract and set up:
mkdir -p ~/Android/Sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Set environment variables:
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Accept licenses:
yes | sdkmanager --licenses

# Install required components:
sdkmanager "platform-tools" "platforms;android-34" "build-tools;33.0.1"

# Update local.properties:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Build the app:
./gradlew assembleDebug
```

### Option 3: Use Existing Android Studio SDK
If you have Android Studio installed elsewhere:

1. Find your Android SDK location (usually ~/Android/Sdk or /opt/android-sdk)
2. Update `local.properties`:
   ```
   sdk.dir=/path/to/your/android/sdk
   ```
3. Build: `./gradlew assembleDebug`

## Current SDK Status
The system has a minimal SDK at `/usr/lib/android-sdk` but it's missing:
- Android Platform 34 (API 34)
- Build Tools 33.0.1

## Quick Build for Testing
If you just want to test the code without building, you can:
1. Open the project in Android Studio
2. Let it download dependencies automatically
3. Build and run directly from Android Studio
