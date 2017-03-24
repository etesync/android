# Changelog

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
