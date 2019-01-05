package com.etesync.syncadapter.ui.importlocal

import android.Manifest
import android.accounts.Account
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.support.v4.app.DialogFragment
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.App
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO
import com.etesync.syncadapter.R
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.LocalAddressBook
import com.etesync.syncadapter.resource.LocalCalendar
import com.etesync.syncadapter.resource.LocalContact
import com.etesync.syncadapter.resource.LocalEvent
import com.etesync.syncadapter.syncadapter.ContactsSyncManager
import com.etesync.syncadapter.ui.Refreshable
import com.etesync.syncadapter.ui.importlocal.ResultFragment.ImportResult
import org.apache.commons.codec.Charsets
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

class ImportFragment : DialogFragment() {

    private var account: Account? = null
    private var info: CollectionInfo? = null
    private var importFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        retainInstance = true

        account = arguments!!.getParcelable(KEY_ACCOUNT)
        info = arguments!!.getSerializable(KEY_COLLECTION_INFO) as CollectionInfo
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            chooseFile()
        } else {
            val data = ImportResult()
            data.e = Exception(getString(R.string.import_permission_required))
            (activity as ResultFragment.OnImportCallback).onImportResult(data)

            dismissAllowingStateLoss()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.import_dialog_title)
        progress.setMessage(getString(R.string.import_dialog_loading_file))
        progress.setCanceledOnTouchOutside(false)
        progress.isIndeterminate = false
        progress.setIcon(R.drawable.ic_import_export_black)
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)

        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions()
            } else {
                chooseFile()
            }
        } else {
            setDialogAddEntries(progress, savedInstanceState.getInt(TAG_PROGRESS_MAX))
        }

        return progress
    }

    private fun setDialogAddEntries(dialog: ProgressDialog, length: Int) {
        dialog.max = length
        dialog.setMessage(getString(R.string.import_dialog_adding_entries))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val dialog = dialog as ProgressDialog

        outState.putInt(TAG_PROGRESS_MAX, dialog.max)
    }

    override fun onDestroyView() {
        val dialog = dialog
        // handles https://code.google.com/p/android/issues/detail?id=17423
        if (dialog != null && retainInstance) {
            dialog.setDismissMessage(null)
        }
        super.onDestroyView()
    }

    fun chooseFile() {
        val intent = Intent()
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.action = Intent.ACTION_GET_CONTENT

        if (info!!.type == CollectionInfo.Type.CALENDAR) {
            intent.type = "text/calendar"
        } else if (info!!.type == CollectionInfo.Type.ADDRESS_BOOK) {
            intent.type = "text/x-vcard"
        }

        val chooser = Intent.createChooser(
                intent, getString(R.string.choose_file))
        try {
            startActivityForResult(chooser, REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            val data = ImportResult()
            data.e = Exception("Failed to open file chooser.\nPlease install one.")

            (activity as ResultFragment.OnImportCallback).onImportResult(data)

            dismissAllowingStateLoss()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE -> if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    // Get the URI of the selected file
                    val uri = data.data
                    App.log.info("Importing uri = " + uri!!.toString())
                    try {
                        importFile = File(com.etesync.syncadapter.utils.FileUtils.getPath(context, uri))

                        Thread(ImportCalendarsLoader()).start()
                    } catch (e: Exception) {
                        App.log.severe("File select error: " + e.localizedMessage)
                    }

                }
            } else {
                dismissAllowingStateLoss()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun loadFinished(data: ImportResult) {
        (activity as ResultFragment.OnImportCallback).onImportResult(data)

        dismissAllowingStateLoss()

        if (activity is Refreshable) {
            (activity as Refreshable).refresh()
        }
    }

    private inner class ImportCalendarsLoader : Runnable {
        private fun finishParsingFile(length: Int) {
            if (activity == null) {
                return
            }

            activity!!.runOnUiThread { setDialogAddEntries(dialog as ProgressDialog, length) }
        }

        private fun entryProcessed() {
            if (activity == null) {
                return
            }

            activity!!.runOnUiThread {
                val dialog = dialog as ProgressDialog

                dialog.incrementProgressBy(1)
            }
        }

        override fun run() {
            val result = loadInBackground()

            activity!!.runOnUiThread { loadFinished(result) }
        }

        fun loadInBackground(): ImportResult {
            val result = ImportResult()

            try {
                val importStream = FileInputStream(importFile!!)

                if (info!!.type == CollectionInfo.Type.CALENDAR) {
                    val events = Event.fromStream(importStream, Charsets.UTF_8)
                    importStream.close()

                    if (events.size == 0) {
                        App.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = events.size.toLong()

                    finishParsingFile(events.size)

                    val provider = context!!.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)
                    val localCalendar: LocalCalendar?
                    try {
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory.INSTANCE, info!!.uid)
                        if (localCalendar == null) {
                            throw FileNotFoundException("Failed to load local resource.")
                        }
                    } catch (e: CalendarStorageException) {
                        App.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    } catch (e: FileNotFoundException) {
                        App.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    }

                    for (event in events) {
                        try {
                            val localEvent = LocalEvent(localCalendar, event, event.uid, null)
                            localEvent.addAsDirty()
                            result.added++
                        } catch (e: CalendarStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }
                } else if (info!!.type == CollectionInfo.Type.ADDRESS_BOOK) {
                    // FIXME: Handle groups and download icon?
                    val downloader = ContactsSyncManager.ResourceDownloader(context!!)
                    val contacts = Contact.fromStream(importStream, Charsets.UTF_8, downloader)

                    if (contacts.size == 0) {
                        App.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = contacts.size.toLong()

                    finishParsingFile(contacts.size)

                    val provider = context!!.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)
                    val localAddressBook = LocalAddressBook.findByUid(context!!, provider!!, account, info!!.uid)

                    for (contact in contacts) {
                        try {
                            val localContact = LocalContact(localAddressBook, contact, null, null)
                            localContact.createAsDirty()
                            result.added++
                        } catch (e: ContactsStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }
                    provider.release()
                }

                return result
            } catch (e: FileNotFoundException) {
                result.e = e
                return result
            } catch (e: InvalidCalendarException) {
                result.e = e
                return result
            } catch (e: IOException) {
                result.e = e
                return result
            } catch (e: ContactsStorageException) {
                result.e = e
                return result
            }

        }
    }

    companion object {
        private val REQUEST_CODE = 6384 // onActivityResult request

        private val TAG_PROGRESS_MAX = "progressMax"

        fun newInstance(account: Account, info: CollectionInfo): ImportFragment {
            val frag = ImportFragment()
            val args = Bundle(1)
            args.putParcelable(KEY_ACCOUNT, account)
            args.putSerializable(KEY_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }
    }
}
