# Changelog
*NOTE:* may be removed in the future in favor of the fastlane changelog.

## Version 2.4.1
* Fix sync with Tasks.org and OpenTasks - many thanks to @Sch1nken!

## Version 2.4.0
* Update compile and target SDK versions

## Version 2.3.0
* Sync: add an option to sync every 30 minutes
* Update translations

## Version 2.2.4
* Import: fix crashes for some users after import finishes.

## Version 2.2.3
* Fix issues with the Tasks.org integration and subtasks (due to rewriting UIDs).

## Version 2.2.2
* Fix "potential vendor bugs" message constantly showing.

## Version 2.2.1
* Fix crash when importing events and also when syncing legacy events

## Version 2.2.0
* Support resizable activities
* Update ical4android dep - should fix issues with duplicate tasks and events
* Update vcard4android dep
* Update gradle and sdk version
* Update translations

## Version 2.1.5
* Improve error handling in sync and import
* Update translations
* Fix some crashes

## Version 2.1.4
* Event invitations: only send invitations if we are the organizers
* Fix rare crash when pushing changes with EteSync 1.0 accounts

## Version 2.1.3
* Fix crashes on older Android devices
* Fix crashes with some screen not loading for some users.

## Version 2.1.2
* Fix crash when generating email invitations while using a French locale
* Uptdate etebase dep to fix issue with custom urls not ending with a slash.

## Version 2.1.1
* Debug info: fix manually sending of crash reports to have visual feedback.
* Debug info: fix manually sending of crash reports to include more crash information.
* Fixed a few crashes that were happening in some rare cases.

## Version 2.1.0
* Change the crash reporting to not rely on email (use HTTP instead)

## Version 2.0.0
* EteSync 2.0 support \o/

## Version 1.16.2
* Update OkHttp3 dependency.

## Version 1.16.1
* Fix contacts sync on Android 11
* Fix issue with tasks.org crashing because of permissions
* Update cert4android

## Version 1.16.0
* Add support for the Tasks.org task provider - you can now just use Tasks.org without needing to add an account there.

## Version 1.15.1
* Safely ignore temporary gateway timeouts

## Version 1.15.0
* Change default collection names to be more descriptive

## Version 1.14.0
* Make it clearer that account imports import the full account (and not just part of it).

## Version 1.13.0
* Show a snack with info if EteSync is missing permissions.

## Version 1.12.0
* Make sync faster by only fetching entries when journals have changed.

## Version 1.11.3
* Task collection view: add a message about changelog not showing if OpenTasks isn't installed
* Update German translation

## Version 1.11.2
* Member add: change confusing string.

## Version 1.11.1
* Fix crash when initialising database

## Version 1.11.0
* Gracefully handle malformed journal entries (e.g. malformed calendar events)
* Update vcard4android and ical4android dependencies

## Version 1.10.2
* Fix crash when moving the encryption password screen to background

## Version 1.10.1
* Fix crash in DebugInfo page

## Version 1.10.0
* Make it clearer that users should use their existing address book and calendar apps
* Improve the login screen's text for returning users
* Fix file descriptor leak which was causing crashes for some users.
* Invalidate the authToken after logging out.

## Version 1.9.9
* Translation: translate calendar invitation emails to German

## Version 1.9.8
* Upgrade ical4android dependency

## Version 1.9.7
* Re-apply the changes in 1.9.4 because some people were still reporting about temporarily disappearing tasks/events.

## Version 1.9.6
* Removed the deprecated 5 and 10 minutes sync intervals from the sync settings (deprecated by Android)
* Adjusted the sync code to make it more defensive against potential errors (more locking)

## Version 1.9.5
* Fix issue with journal preview showing the wrong dates.
* Revert the fixes in 1.9.4 because they were not actually needed.

## Version 1.9.4
* Hopefully really fix issue with temporarily disappearing tasks/events

## Version 1.9.3
* Fix issue with temporarily disappearing tasks/events
* Add autofill hints in the login screen*
* Update gradle

## Version 1.9.2
* Fix f-droid build

## Version 1.9.1
* Translation: add Norwegian Bokmål (Marius Lindvall)
* Update ical4android

## Version 1.9.0
* Fix database lock issues that some users were experiencing
* Fix a rare issue with setting up database

## Version 1.8.5
* Fix the wording for setting the task list's color.
* Gracefully handle the error case of processing an item that has been processed.

## Version 1.8.4
* Cache concurrent journal fetching (avoid multiple unneeded fetches).

## Version 1.8.3
* Improve debug information when failing to create local entries
* Update ical4android

## Version 1.8.2
* Fix SSL errors for old Android clients that don't default to TLSv1.2
* Update cert4android
* Report SSLProtocolException issues as errors

## Version 1.8.1
* Make reporting of SSL issues lax again. It was catching many issues that are not real issues.

## Version 1.8.0
* Make the raw entries in the change history viewer selectable (so you can copy them)

## Version 1.7.1
* Fix handling of SSL handshake exceptions
* Upgraded vcard4android and ical4android deps to latest
* Improve debug logs

## Version 1.7.0
* Debug info: add calling class information to reports.

## Version 1.6.1
* Fixed contacts import
* Make it easier to report import errors

## Version 1.6.0
* Change journal: make it possible to revert an item to a past state ("undo change").

## Version 1.5.1
* Import: transform EMAIL event reminders to DISPLAY. Email reminders aren't and can't be supported in EteSync due to end-to-end-encryption.

## Version 1.5.0
* Move to the new Android adaptive icons - makes it look nicer on Android 8 and up.
* Contacts import: fix wrong summary for the amount of added imports

## Version 1.4.12
* Import: fix potential crashes in the import process.
* Email invitations: improve signature.

## Version 1.4.11
* Import: use the UUID from the import source (e.g. Google account or file) rather than generating a new one.
  * This prevents duplicates when importing multiple times.
* Make it possible to connect to non-TLS servers on Android P and onwards

## Version 1.4.10
* Fix debug information for failed login attempts
* Improve error message for bad encryption passwords

## Version 1.4.9
* WebView: Gracefully handle unsupported link types.

## Version 1.4.8
* Attempt to fix crash reporting on some devices
* Log broken entries on processing failures to make debugging easier

## Version 1.4.7
* Fix rare crash when listing accounts
* Update requery, kotlin and gradle

## Version 1.4.6
* Import: implement importing tasks from file
* Fix crash when Android kills the import activity while it's still importing

## Version 1.4.5
* Gracefully handle Conflict errors (retry later).
* Fix potential crash when opening and closing the app very quickly.

## Version 1.4.4
* Fix crash when removing accounts on some devices

## Version 1.4.3
* Fix encryption password change not to crash.

## Version 1.4.2
* Fix occasional crash when listing journal members
* Fix spelling mistake in encryption password page.

## Version 1.4.1
* Fix crash on import when the activity is in the background when the import finishes.
* Show notifications for SSL handshake related errors.
* Improve debug info and crash reporting email.
* Add Tutanota to the list of clients that don't support attachments from other apps.
* Fix spelling mistake in encryption password page.

## Version 1.4.0
* Change the sync to also do the initial preparation in chunks - useful for massive syncs
* Fix crash when removing journal members
* Fix certificate manager service leaks
* Improve logging

## Version 1.3.0
* Add notification channels for granular control of app notifications
* Only show the custom certificate popup when using EteSync interactively
* Fix setting changes not being applied (e.g. log to file) by making the sync the same process
* Add a separate setting for verbose logging (was previously tied to log to file) for more granular privacy control
* Upgrade cert4android and refactor httpClient based on upsteram.
* Refactor the logging system based on upstream.

## Version 1.2.6
* Fix collection editing following an encryption password change.

## Version 1.2.5
* Change crash message to be a notification rather than a toast
* View collection: don't crash when trying to view the tasks journal when OpenTasks is not installed

## Version 1.2.4
* Fix issue with tasks causing a lot of syncs for some people
* Fix sync when syncing deleted tasks that have never been synced before
* Fix the sync indicator in the account view to also work for tasks
* Unify the sync interval across all journal types (remove separate sync interval per type)
* Show the number of tasks in the journal view
* Move the OpenTasks installation link to the menu so it looks nicer.

## Version 1.2.3
* Fix import from file when choosing files from special directories rather than the filesystem.
* Fix crash when trying to sync events with invites (for some users)

## Version 1.2.2
* Fix crash during sync for some users

## Version 1.2.1
* Fix the setting controlling change notifications.

## Version 1.2.0
* Add a button to install OpenTasks if isn't installed.
* Warn about clients that don't support email attachments when sending event invites

## Version 1.1.0
* Don't pop up notifications when server is under maintenance.
* Add support for read only journals
* Group memberships: be more defensive with potentially missing members.
* Fastlane: update app name.

## Version 1.0.4
* Fix proguard rules that could cause crashes on some devices

## Version 1.0.3
* Improve import of contact groups
* Update event invitation signature
* Minor UI improvements

## Version 1.0.2
* Fix setting the colour of task lists
* Fix the default color shown in the create journal activity

## Version 1.0.1
* Email invitations: fix rare crash for events with no end date.

## Version 1.0.0
* Implement changing the encryption password
* Add support for importing contat groups from account and file

## Version 0.25.0
* Add support for the new associate account type
* Handle exceptions for read only journals
* Fix date being sometimes incorrect in the journal item preview.

## Version 0.24.1
* Fix issue with Calendars missing from the account view.

## Version 0.24.0
* Fix crash when setting up user info for the first time
* Email invitations: fix invitations not being attached on event updates in some rare cases.
* Email invitations: add a way to send an invite from the journal log.

## Version 0.23.2
* Tasks: add ability to create, edit, deleted and view task journals

## Version 0.23.1
* Catch IllegalStateExceptions in the account changed receiver.

## Version 0.23.0
* Add Tasks support via OpenTasks!
* Minimum Android version is now KitKat (4.4)
* Migrate almost all of the code to Kotlin
* Upgraded vcard4android and ical4android deps to latest (after more than a year!)
* Drop the custom password entry widget in favour of the stock one
* Debug handler now sends the report as body if ProtonMail (doesn't support attachments) is installed
* Fix the shared-to user in shared journals to be case insensitive
* Update HTTPS trusted ciphers list.
* Fix account deletion issues on some devices.

## Version 0.22.6
* Fix rare crash when importing / creating events with a missing or invalid timezone.

## Version 0.22.5
* Fix rare crash when trying to import contacts from an account on the phone.

## Version 0.22.4
* Event invitations: fix issue with times showing wrong across timezones.
* Fix group memberships to show with more Contacts apps
* Fix bug causing groups to get duplicated on modifications rather than just updated.

## Version 0.22.3
* Event invitations: add timezone and location information to email summary.
* Update ical4j dependency.

## Version 0.22.2
* Really fix the crashes because of the missing support lib dependencies
* Fix crash when importing a contact with a remote (not-embedded) picture

## Version 0.22.1
* Fix crashes because of missing support lib dependencies

## Version 0.22.0
* Add a setting to disable the "New Journal Entries" notifications

## Version 0.21.6
* Fix handling of partial dates in the jorunal contact view.

## Version 0.21.5
* Fix issue when viewing journal entries for contacts with malformed (empty but existing) nicknames

## Version 0.21.4
* Fix previous broken build because of bad submodules.

## Version 0.21.3
* Fix issue with events being saved with both duration and an end date.

## Version 0.21.2
* Fix sync when events have attendees and a duration rather than an end date.

## Version 0.21.1
* Trigger F-Droid build

## Version 0.21.0
* Add a notification to send email invites whenever an event has atendees.

## Version 0.20.4
* Restore the webview state after device rotation.

## Version 0.20.3
* Fix adding journal members on some devices.
* Don't crash when trying to delete non-existent records.

## Version 0.20.2
* Make HTTP request/response logging more verbose when logging to file (useful when debugging).

## Version 0.20.1
* Fix issue with contacts sometimes not syncing or syncing very slowly after big and heavy imports.
* Remove some potentially sensitive info from logs (needed now that we suggest sharing logs on crashes).
* ACRA: increase the number of last log-lines shared in crash reports to 500.
* Use ACRA when sharing the debug log from the debug activity (it also shares more info).

## Version 0.20.0
* Automatically generate stack traces on crashes and offer to send them by email. (Powered by ACRA).
* Detect and alert potential vendor specific bugs (namely with Xiaomi devices).
* Import: fix showing of the "import has finished" dialog.
* Import: remove duplicate detection, this didn't work well and was causing issues.
* Contact import: fix potential double-import.
* Make journal ownership tests case insensitive (as emails are).
* Update gradle, support libs and requery, and get rid of lombok.
* Make it more obvious that file-logging notification is persistent.

## Version 0.19.6
* Fix confusing error message when creating/fetching user info.

## Version 0.19.5
* Update store description.

## Version 0.19.4
* Improve error message when using the wrong encryption password on account creation.
* Update target SDK

## Version 0.19.3
* Update Polish translation.

## Version 0.19.2
* Fix a crash on import when app is in the background.
* Fix a few minor leaks.
* Update Polish translation.

## Version 0.19.1
* Update description to include self-hosting information on f-droid.

## Version 0.19.0
* Added support for setting a custom server address (needed for self-hosting support)
* Add support for anniversaries without a year

## Version 0.18.2
* Added fastlane data for f-droid

## Version 0.18.1
* Add support for birthdays without the year.
* Notify the user on journal modification.
* Add a debug option to force the UI language.

## Version 0.18.0
* Add back support for contact groups - thanks to user "359" for reporting this.
* Fix an issue causing local cache corruption in some rare cases - thanks to user "359" for reporting this.
* Clean up the vObject PRODID.
* Update okhttp

## Version 0.17.0
* Download journal in chunks instead of all at once
    * Improves behaviour on slow and unreliable internet connections.
    * Fixes Android cancelling the sync mid-way on some Android versions.
* Fix crash when exiting some activities before content loading has finished.

## Version 0.16.0
* Improve the look and feel of the journal viewer and show contacts and events in a prettier way.
* Increase default RSA key length to 3072 bit.
* Fix an issue with account addition not working in some cases.
* Add a unique constraint on journal UIDs (possible thanks to a bug fix in requery)

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
