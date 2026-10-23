# 🌙 Moon Player

An offline music player (MP3, FLAC, M4A/AAC, OGG/Opus, WAV) with a terminal-inspired aesthetic, turntable visualizer, queue management, listening statistics, and folder linking.

---

## 🚀 GitHub Actions: Auto-Build & Release APK

This repository includes an automated CI/CD pipeline in [`.github/workflows/build-and-release.yml`](.github/workflows/build-and-release.yml) to automatically compile, package, and publish the Android APK.

### 1. Trigger via Git Tag (Recommended for Releases)
Pushing any Git tag starting with `v` (e.g., `v1.0.0`) automatically builds the APK and creates a **GitHub Release** with the APK asset and SHA256 checksums attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 2. Manual Trigger via GitHub Actions UI
You can build and release anytime without creating a tag locally:
1. Navigate to the **Actions** tab in your GitHub repository.
2. Select **"Build & Release APK"** from the left sidebar.
3. Click **"Run workflow"**.
4. Optionally provide a `Release Tag` (e.g. `v1.0.1`) and `Release Title`.
5. Click **"Run workflow"**.

### 3. Automatic Artifact on Push
Every push to `main` or `master` triggers a build and uploads the resulting `.apk` to the **Artifacts** section of the workflow run, allowing you to test commits without creating public releases.

---

## 🛠 Local Build

To build the APK locally:

```bash
# Debug APK
./gradlew assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```
