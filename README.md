# 🎵 Sayan Music & Movies

**Private Android music and movie player • Offline-first • No app login**

**Developed by:** **Eng. Sayan Lodh**  
**CSE (Computer Science Engineer) & AI Developer**

> Import and play your own media, organize custom playlists, use a seek bar, enjoy automatic next-track playback, listen with the screen locked, and set a sleep timer. It includes official links to YouTube, YouTube Music and JioSaavn, plus direct online media links that you are authorized to use.

## Features

| Feature | Status |
|---|---|
| Local music via Android file picker (MP3, M4A, AAC, WAV, FLAC, OGG and other device-supported formats) | Included |
| Local video playback (MP4, WebM, MKV and other device-supported formats) | Included |
| Native playback with AndroidX Media3 / ExoPlayer | Included |
| **Background audio** + Android media notification/lock-screen controls | Included |
| **Automatically play the next song** + Repeat All/One/Off + Shuffle | Included |
| Track seek bar, Previous, Next, Play/Pause | Included |
| Search music library | Included |
| Create custom-named playlists; add/remove tracks; play entire playlist | Included |
| Favorite songs | Included |
| Sleep timer: 5, 10, 15, 30, 45, 60, 90 minutes; cancel option | Included |
| Local library stored in SQLite; no app sign-in | Included |
| Open **official** YouTube, YouTube Music and JioSaavn | Included (opens their official app/site) |
| Play authorized direct HTTPS audio/video URLs inside app | Included (subject to format/server support) |
| Download **authorized direct HTTPS MP3 files** into Downloads | Included |
| Dedicated in-app Guide and developer credit | Included |
| Native `.apk` build via GitHub Actions | Included |

### Important streaming limitations

This is **not** an unofficial YouTube/JioSaavn music extractor. YouTube and JioSaavn open via their official services; they are not silently played in the background or converted to MP3 in this app. The app only downloads **direct MP3 files that you have rights to download**. The player itself has no login; external providers may require their own login/subscription. Some formats/streams require codecs or server support; DRM-protected content is not supported.

## How to make the APK using GitHub (no Android Studio needed)

1. **Extract** `Sayan_Music_Movies_GitHub_Ready.zip` on your computer.
2. Create a new GitHub repository, e.g. `Sayan_Music_Movies`.
3. Click **Add file → Upload files**. Upload **the extracted files/folders**, not the ZIP itself and not an extra parent folder.
4. Check that the root contains `settings.gradle`, `build.gradle`, `app/`, and `.github/workflows/main.yml`.
5. Click **Commit changes**.
6. Go to **Actions → Build Sayan Music & Movies APK → Run workflow** (or use the run created by the push).
7. Open the successful run → **Summary → Artifacts** → click `Sayan-Music-Movies-Installable-Debug-APK`.
8. Extract the artifact ZIP. Install `app-debug.apk` on your Android phone.

**A debug APK is already signed for installation**, using the runner's temporary Android debug key. It is for testing/personal use. Since that temporary key can change between GitHub builds, installing an update over an older debug build may fail; uninstall the old app first (this erases its local app data), or configure permanent release signing below.

### Optional: create a permanently release-signed APK

For consistent future updates you need to create and securely keep **your own keystore**. Once created, add these four **repository secrets** under **Settings → Secrets and variables → Actions**:

- `SAYAN_KEYSTORE_B64`: base64 representation of the keystore bytes (single line)
- `SAYAN_STORE_PASSWORD`: your keystore password
- `SAYAN_KEY_ALIAS`: your key alias
- `SAYAN_KEY_PASSWORD`: your key password

The same YAML workflow then additionally uploads `Sayan-Music-Movies-Signed-Release-APK`. **Never upload your private keystore or passwords into the repository.** To install future APKs as updates, reuse the **same** signing key and application ID. Debug and release-signed variants cannot upgrade one another without uninstalling.

## Software versions

- **Java 17**
- **Gradle 8.9**
- **Android Gradle Plugin 8.7.3**
- **compileSdk / targetSdk 35**
- **minSdk 26** (Android 8.0+; designed for Android 16 as well)
- **AndroidX Media3 1.5.1**

GitHub automatically supplies Java, Gradle and Android SDK via the workflow. External Maven dependencies are downloaded during build.

## How to use

**Library:** Import music or videos using Android's document picker. Tap Play. Use seek, previous/next, shuffle, repeat.  
**Playlists:** Create a name → use `+ Playlist` beside a song → open playlist → Play all.  
**Online:** Tap YouTube/JioSaavn for official playback, or supply an authorized direct HTTPS media URL. For an allowed MP3 download, tick the rights checkbox and download to your phone's Downloads directory. Import the resulting file afterward.  
**Guide:** Explains all player features, shows Sleep Timer settings, and credits the developer.

## Folder layout

```text
.github/workflows/main.yml     GitHub APK build (and optional release signing)
settings.gradle                     Root Gradle settings
build.gradle                        Android plugin version
app/build.gradle                    App and Media3 dependencies
app/src/main/AndroidManifest.xml    App, internet and media service permissions
app/src/main/java/com/sayanlodh/mediabox/
  MainActivity.java                 User interface, local files, online URLs
  LibraryDb.java                    SQLite tracks/playlists/favorites
  Track.java                        Media model
  PlaybackService.java              Background media, media session and sleep timer
app/src/main/res/                   Theme and app icon
README.md                           You are here
```

## Privacy & caveats

- No app login. Media paths, playlist names and favorites stay in on-device SQLite storage.
- Imported media files are **not copied**; Android file-picker permissions are used so your own local files can play. Moving or deleting the source may break playback.
- The app requests internet access for direct links and optional online downloads, plus foreground media playback so music can continue when the app isn't visible.
- Authorized HTTPS MP3 downloads go to Android Downloads using the system Download Manager.
- This is a first build of an original app. **Compile and test on the target Android 16 device**; no claim is made that GitHub Actions has successfully compiled or that every device/codec supports all media formats.
