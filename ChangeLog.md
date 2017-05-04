# Changelog

## Version 0.15.0
* Add support for multiple address books (adjusted from the DAVdroid solution).
    * This works around the Android limitation of one address book per account.
* Change the fingerprint format to be numeric instead of hex (thanks to Dominik Schürmann for the suggestion)
* Fix UUID generation - in some cases weird UUIDs were generated for events.
* Fix opening of dashboard in the external browser.
* Fix a rare crash on the login page.
* Fix potential crash when viewing journals before they have been sync.

## Version 0.14.0
* Add support for sharing journals and using shared journals.
    * This change includes viewing and verifying user's public key fingerprints, as well as automatic upload of encrypted private key.
* Add support for read-only journals (not controlling them, just treating existing read-only ones as such)
* Add icons to menu operations.
* Update dependencies (e.g. okhttp, requery, ical4android)

## Version 0.13.0
* Added a showcase wizard to showcase some features on first use.
* Make the sync more robust in case of interruptions.
* Changed the allowed TLS ciphers to only include a secure list.
* Changed the minimum required version to Android 4.1 (sdk version 16)
* Show a message when system-wide auto-sync is disabled
* Set correct PROID for Contacts, Events and Tasks
* Added many tests for the crypto and service.
* Code cleanups and refactoring in the sync manager.
* Update {cert,ical,vcard}4android to latest.
* Minor fixes

## Version 0.12.0
* Add import from local account (import calendars/contacts)
* Update the crypto protocol to version 2.
* Refactoring

## Version 0.11.1
* Fix potential crash when updating the app.

## Version 0.11.0
* Add import from file (vCard/iCal).
* Fix bug preventing from re-adding a removed account.
* Fix issue with some entries marked as "ADD" instead of "CHANGE" in some cases.
* Fix issues with embedded webview not showing all pages it should.
* Always log deletes, even when not previously added to server.
* Refresh the collection view when editing/deleting.
* Login: add a "forgot password" link.

## Version 0.10.0
* Open FAQ, user guide and signup page inside the app
* Add calendar/contacts view and edits screens
* Show the change journal on calendar/contacts view screen
* Journals are now cached locally
* Setup account page: added more info about the encryption password.
* Update German translation

## Version 0.9.2
* Reword some parts of the UI
* Add a "Coming soon" section for the Change Journal

## Version 0.9.1
* Add links to the usage guide.
* Open the account's dashboard when getting a "UserInactive" exception.
* Change how notifications launch activities.
* Fix issue with only one of the notifications being clickable.
* Shorten notification error title so account name is visible.
* Internal changes to how HttpExceptions are handled.
* Update strings.

## Version 0.9.0
* Rename the Android package to EteSync to avoid clashes with DAVdroid
* Optimise proguard rules and fix warnings
* Implement sha256 using bouncy-castle.
* Pin gradle plugin version to 2.2.3.

## Version 0.8.1
* Request permissions on app launch instead of only when needed.
* Update vcard4droid and ical4droid.

## Version 0.8.0
* Initial release.
