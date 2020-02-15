<p align="center">
  <img width="120" src="app/src/main/res/mipmap/ic_launcher.png" />
  <h1 align="center">EteSync - Secure Data Sync</h1>
</p>

Secure, end-to-end encrypted, and privacy respecting sync for your contacts, calendars and tasks (Android client).

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play" 
      height="80" />](https://play.google.com/store/apps/details?id=com.etesync.syncadapter)
[<img src="https://www.etesync.com/static/img/fdroid-badge.fe865d4c8f63.png"
      alt="Get it on F-Droid"
      height="80" />](https://f-droid.org/app/com.etesync.syncadapter)

![GitHub tag](https://img.shields.io/github/tag/etesync/android.svg)
[![Chat on freenode](https://img.shields.io/badge/irc.freenode.net-%23EteSync-blue.svg)](https://webchat.freenode.net/?channels=#etesync)

# Overview

Please see the [EteSync website](https://www.etesync.com) for more information.

EteSync is licensed under the [GPLv3 License](LICENSE).

# Building

EteSync uses `git-submodules`, so cloning the code requires slightly different commands.

1. Clone the repo: `git clone --recurse-submodules https://github.com/etesync/android etesync-android`
2. Change to the directory `cd etesync-android`
3. Open with Android studio or build with gradle:
  1. Android studio (easier): `android-studio .`
  2. Gradle: `./gradlew assembleDebug`
  
To update the code to the latest version, run: `git pull --rebase --recurse-submodules`


Third Party Code
================

EteSync's source code was originally based on [DAVdroid](https://www.davx5.com) but the codebases has since diverged quite significantly.

This project relies on many great third party libraries. Please take a look at the
app's about menu for more information about them and their licenses.
