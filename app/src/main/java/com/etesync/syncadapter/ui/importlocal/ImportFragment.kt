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
import androidx.fragment.app.DialogFragment
import at.bitfire.ical4android.*
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.*
import com.etesync.syncadapter.syncadapter.ContactsSyncManager
import com.etesync.syncadapter.ui.Refreshable
import com.etesync.syncadapter.ui.importlocal.ResultFragment.ImportResult
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class ImportFragment : DialogFragment() {

    private lateinit var account: Account
    private lateinit var info: CollectionInfo
    private var inputStream: InputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        retainInstance = true

        account = arguments!!.getParcelable(KEY_ACCOUNT)!!
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
        Logger.log.info("Adding entries. Total: ${length}")
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

        when (info.type) {
            CollectionInfo.Type.CALENDAR -> intent.type = "text/calendar"
            CollectionInfo.Type.TASKS -> intent.type = "text/calendar"
            CollectionInfo.Type.ADDRESS_BOOK -> intent.type = "text/x-vcard"
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
            REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        val uri = data.data!!
                        Logger.log.info("Starting import into ${info.uid} from file ${uri}")
                        try {
                            inputStream = activity!!.contentResolver.openInputStream(uri)

                            Thread(ImportEntriesLoader()).start()
                        } catch (e: Exception) {
                            Logger.log.severe("File select error: ${e.message}")

                            val importResult = ImportResult()
                            importResult.e = e

                            (activity as ResultFragment.OnImportCallback).onImportResult(importResult)

                            dismissAllowingStateLoss()
                        }

                    }
                } else {
                    dismissAllowingStateLoss()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun loadFinished(data: ImportResult) {
        (activity as ResultFragment.OnImportCallback).onImportResult(data)

        Logger.log.info("Finished import")

        dismissAllowingStateLoss()

        if (activity is Refreshable) {
            (activity as Refreshable).refresh()
        }
    }

    private inner class ImportEntriesLoader : Runnable {
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
                val context = context!!
                val importReader = InputStreamReader(inputStream)

                if (info.type == CollectionInfo.Type.CALENDAR) {
                    val events = Event.fromReader(importReader, null)
                    importReader.close()

                    if (events.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = events.size.toLong()

                    finishParsingFile(events.size)

                    val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)!!
                    val localCalendar: LocalCalendar?
                    try {
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory, info.uid!!)
                        if (localCalendar == null) {
                            throw FileNotFoundException("Failed to load local resource.")
                        }
                    } catch (e: CalendarStorageException) {
                        Logger.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    } catch (e: FileNotFoundException) {
                        Logger.log.info("Fail" + e.localizedMessage)
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
                } else if (info.type == CollectionInfo.Type.TASKS) {
                    val tasks = Task.fromReader(importReader)
                    importReader.close()

                    if (tasks.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = tasks.size.toLong()

                    finishParsingFile(tasks.size)

                    val provider = TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)!!
                    val localTaskList: LocalTaskList?
                    try {
                        localTaskList = LocalTaskList.findByName(account, provider, LocalTaskList.Factory, info.uid!!)
                        if (localTaskList == null) {
                            throw FileNotFoundException("Failed to load local resource.")
                        }
                    } catch (e: FileNotFoundException) {
                        Logger.log.info("Fail" + e.localizedMessage)
                        result.e = e
                        return result
                    }

                    for (task in tasks) {
                        try {
                            val localTask = LocalTask(localTaskList, task, task.uid, null)
                            localTask.addAsDirty()
                            result.added++
                        } catch (e: CalendarStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }
                } else if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                    val oldUidToNewId = HashMap<String?, Long>()
                    val downloader = ContactsSyncManager.ResourceDownloader(context)
                    val contacts = Contact.fromReader(importReader, downloader)

                    if (contacts.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = contacts.size.toLong()

                    finishParsingFile(contacts.size)

                    val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)!!
                    val localAddressBook = LocalAddressBook.findByUid(context, provider, account, info.uid!!)
                    if (localAddressBook == null) {
                        throw FileNotFoundException("Failed to load local address book.")
                    }

                    for (contact in contacts.filter { contact -> !contact.group }) {
                        try {
                            val localContact = LocalContact(localAddressBook, contact, null, null)
                            localContact.createAsDirty()
                            // If uid is null, so be it. We won't be able to process the group later.
                            oldUidToNewId[contact.uid] = localContact.id!!

                            // Apply categories
                            val batch = BatchOperation(localAddressBook.provider!!)
                            for (category in contact.categories) {
                                localContact.addToGroup(batch, localAddressBook.findOrCreateGroup(category))
                            }
                            batch.commit()

                            result.added++
                        } catch (e: ContactsStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }

                    for (contact in contacts.filter { contact -> contact.group }) {
                        try {
                            val localGroup = LocalGroup(localAddressBook, contact, null, null)
                            val memberIds = contact.members.mapNotNull { memberUid ->
                                oldUidToNewId[memberUid]
                            }
                            localGroup.createAsDirty(memberIds)

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
