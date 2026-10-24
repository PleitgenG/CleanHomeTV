CLEAN HOME - custom home screen for Syinix 32T730 (Android 6 TV)
==================================================================

A TV cannot "run" a zip. This zip is the SOURCE CODE. It gets turned into one
file, CleanHome.apk, which is what you install on the TV. Two ways to make it:

--- OPTION A: no software needed (free, ~5 minutes, uses a phone or PC) -------
1. Make a free account at github.com and create a new empty repository.
2. Extract this zip and upload ALL its contents into that repository
   (including the hidden ".github" folder - on github.com use
   "Add file > Upload files" and drag the extracted FOLDER CONTENTS in).
3. Open the "Actions" tab. "Build APK" starts by itself (or click it > Run workflow).
4. When it is green (about 3 minutes), open the run and download the
   "CleanHome-APK" artifact. Unzip it: inside is app-debug.apk.

--- OPTION B: Android Studio on a PC ------------------------------------------
Open this folder in Android Studio > Build > Build APK(s).
Or in a terminal:  ./gradlew assembleDebug
APK ends up in app/build/outputs/apk/debug/app-debug.apk

--- INSTALL ON THE TV ---------------------------------------------------------
1. Put app-debug.apk on a USB stick. 
2. On the TV: Play Store > install a file manager (e.g. "X-plore File Manager"
   or "File Commander").
3. TV Settings > Security & restrictions > Unknown sources > allow the
   file manager.
4. Plug in the USB, open the APK from the file manager, Install.
   (Alternative with a PC on the same Wi-Fi: enable Developer options on the
   TV, then  adb connect <tv-ip>  and  adb install app-debug.apk )
5. Press HOME on the remote. Android asks which home screen to use:
   choose "Clean Home" and "Always".

--- USING IT ------------------------------------------------------------------
- Arrow keys move, OK opens.
- HOLD OK (or press MENU) on any app: Pin/Unpin, App info, Uninstall.
- Top-right: Search (Google), Play Store, Settings.
- Favorites row is pre-filled with YouTube, Netflix, Prime, Play Movies,
  Spotify, Play Store if you have them. Max 7.

--- GOING BACK TO THE ORIGINAL HOME SCREEN -------------------------------------
Settings > Apps > Clean Home > Clear defaults (or Uninstall). Press Home and
pick the original launcher again.

--- NOTES ---------------------------------------------------------------------
- Some Syinix firmware hides the "which home screen" chooser. If Home keeps
  opening the original screen, Clean Home still appears in your app list
  and can be opened manually; tell me and we can add a workaround.
- Built for Android 6 (API 23) and up.
- Google app versions on Android 6 are old; update them in the Play Store.
