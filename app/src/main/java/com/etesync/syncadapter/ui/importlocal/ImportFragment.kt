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
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import at.bitfire.ical4android.*
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import com.etesync.syncadapter.CachedCollection
import com.etesync.syncadapter.Constants.*
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.resource.*
import com.etesync.syncadapter.syncadapter.ContactsSyncManager
import com.etesync.syncadapter.ui.Refreshable
import com.etesync.syncadapter.ui.importlocal.ResultFragment.ImportResult
import com.etesync.syncadapter.utils.TaskProviderHandling
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class ImportFragment : DialogFragment() {
    private lateinit var account: Account
    private lateinit var uid: String
    private lateinit var enumType: CollectionInfo.Type

    private var inputStream: InputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        retainInstance = true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            chooseFile()
        } else {
            val data = ImportResult()
            data.e = Exception(getString(R.string.import_permission_required))
            onImportResult(data)

            dismissAllowingStateLoss()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        if (SDK_INT <= 32) {
            requestPermissions(kotlin.arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 0)
        }
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

        when (enumType) {
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

            onImportResult(data)

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
                        Logger.log.info("Starting import into ${uid} from file ${uri}")
                        try {
                            inputStream = activity!!.contentResolver.openInputStream(uri)

                            Thread(ImportEntriesLoader()).start()
                        } catch (e: Exception) {
                            Logger.log.severe("File select error: ${e.message}")

                            val importResult = ImportResult()
                            importResult.e = e

                            onImportResult(importResult)

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
        onImportResult(data)

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

                if (enumType == CollectionInfo.Type.CALENDAR) {
                    val events = Event.eventsFromReader(importReader, null)
                    importReader.close()

                    if (events.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = events.size.toLong()

                    finishParsingFile(events.size)

                    val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)
                    if (provider == null) {
                        result.e = Exception("Failed to acquire calendar content provider.")
                        return result
                    }

                    val localCalendar: LocalCalendar?
                    try {
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory, uid)
                        if (localCalendar == null) {
                            result.e = FileNotFoundException("Failed to load local resource.")
                            return result
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
                            var localEvent = localCalendar.findByUid(event.uid!!)
                            if (localEvent != null) {
                                localEvent.updateAsDirty(event)
                                result.updated++
                            } else {
                                localEvent = LocalEvent(localCalendar, event, event.uid, null)
                                localEvent.addAsDirty()
                                result.added++
                            }
                        } catch (e: CalendarStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }
                } else if (enumType == CollectionInfo.Type.TASKS) {
                    val tasks = Task.tasksFromReader(importReader)
                    importReader.close()

                    if (tasks.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = tasks.size.toLong()

                    finishParsingFile(tasks.size)

                    val provider = TaskProviderHandling.getWantedTaskSyncProvider(requireContext())
                            .let {
                                if (it == null) {
                                    result.e = Exception("Failed to acquire tasks content provider.")
                                    null
                                } else {
                                    TaskProvider.acquire(context, it)
                                }
                            }

                    provider?.let {
                        val localTaskList: LocalTaskList?
                        try {
                            localTaskList = LocalTaskList.findByName(account, it, LocalTaskList.Factory, uid)
                            if (localTaskList == null) {
                                result.e = FileNotFoundException("Failed to load local resource.")
                                return result
                            }
                        } catch (e: FileNotFoundException) {
                            Logger.log.info("Fail" + e.localizedMessage)
                            result.e = e
                            return result
                        }

                        for (task in tasks) {
                            try {
                                var localTask = localTaskList.findByUid(task.uid!!)
                                if (localTask != null) {
                                    localTask.updateAsDirty(task)
                                    result.updated++
                                } else {
                                    localTask = LocalTask(localTaskList, task, task.uid, null)
                                    localTask.addAsDirty()
                                    result.added++
                                }
                            } catch (e: CalendarStorageException) {
                                e.printStackTrace()
                            }

                            entryProcessed()
                        }
                    }
                } else if (enumType == CollectionInfo.Type.ADDRESS_BOOK) {
                    val uidToLocalId = HashMap<String?, Long>()
                    val downloader = ContactsSyncManager.ResourceDownloader(context)
                    val contacts = Contact.fromReader(importReader, downloader)

                    if (contacts.isEmpty()) {
                        Logger.log.warning("Empty/invalid file.")
                        result.e = Exception("Empty/invalid file.")
                        return result
                    }

                    result.total = contacts.size.toLong()

                    finishParsingFile(contacts.size)

                    val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI)
                    if (provider == null) {
                        result.e = Exception("Failed to acquire contacts content provider.")
                        return result
                    }

                    val localAddressBook = LocalAddressBook.findByUid(context, provider, account, uid)
                    if (localAddressBook == null) {
                        result.e = FileNotFoundException("Failed to load local address book.")
                        return result
                    }

                    for (contact in contacts.filter { contact -> !contact.group }) {
                        try {
                            var localContact = localAddressBook.findByUid(contact.uid!!) as LocalContact?

                            if (localContact != null) {
                                localContact.updateAsDirty(contact)
                                result.updated++
                            } else {
                                localContact = LocalContact(localAddressBook, contact, contact.uid, null)
                                localContact.createAsDirty()
                                result.added++
                            }

                            uidToLocalId[contact.uid] = localContact.id!!

                            // Apply categories
                            val batch = BatchOperation(localAddressBook.provider!!)
                            for (category in contact.categories) {
                                localContact.addToGroup(batch, localAddressBook.findOrCreateGroup(category))
                            }
                            batch.commit()
                        } catch (e: ContactsStorageException) {
                            e.printStackTrace()
                        }

                        entryProcessed()
                    }

                    for (contact in contacts.filter { contact -> contact.group }) {
                        try {
                            val memberIds = contact.members.mapNotNull { memberUid ->
                                uidToLocalId[memberUid]
                            }

                            val group = contact
                            var localGroup: LocalGroup? = localAddressBook.findByUid(group.uid!!) as LocalGroup?

                            if (localGroup != null) {
                                localGroup.updateAsDirty(group, memberIds)
                                result.updated++
                            } else {
                                localGroup = LocalGroup(localAddressBook, group, group.uid, null)
                                localGroup.createAsDirty(memberIds)
                                result.added++
                            }
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

    fun onImportResult(importResult: ImportResult) {
        val fragment = ResultFragment.newInstance(importResult)
        parentFragmentManager.commit(true) {
            add(fragment, "importResult")
        }
    }

    companion object {
        private val REQUEST_CODE = 6384 // onActivityResult request

        private val TAG_PROGRESS_MAX = "progressMax"

        fun newInstance(account: Account, info: CollectionInfo): ImportFragment {
            val ret = ImportFragment()
            ret.account = account
            ret.uid = info.uid!!
            ret.enumType = info.enumType!!
            return ret
        }

        fun newInstance(account: Account, cachedCollection: CachedCollection): ImportFragment {
            val enumType = when (cachedCollection.collectionType) {
                ETEBASE_TYPE_CALENDAR -> CollectionInfo.Type.CALENDAR
                ETEBASE_TYPE_TASKS -> CollectionInfo.Type.TASKS
                ETEBASE_TYPE_ADDRESS_BOOK -> CollectionInfo.Type.ADDRESS_BOOK
                else -> throw Exception("Got unsupported collection type")
            }
            val ret = ImportFragment()
            ret.account = account
            ret.uid = cachedCollection.col.uid
            ret.enumType = enumType
            return ret
        }
    }
}
