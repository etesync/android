package com.etesync.syncadapter.ui.importlocal;

import android.Manifest;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalContact;
import com.etesync.syncadapter.resource.LocalEvent;
import com.etesync.syncadapter.ui.Refreshable;

import org.apache.commons.codec.Charsets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;
import static com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO;
import static com.etesync.syncadapter.ui.importlocal.ResultFragment.ImportResult;

public class ImportFragment extends DialogFragment {
    private static final int REQUEST_CODE = 6384; // onActivityResult request

    private static final String TAG_PROGRESS_MAX = "progressMax";

    private Account account;
    private CollectionInfo info;
    private File importFile;

    public static ImportFragment newInstance(Account account, CollectionInfo info) {
        ImportFragment frag = new ImportFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(KEY_ACCOUNT, account);
        args.putSerializable(KEY_COLLECTION_INFO, info);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
        setRetainInstance(true);

        account = getArguments().getParcelable(KEY_ACCOUNT);
        info = (CollectionInfo) getArguments().getSerializable(KEY_COLLECTION_INFO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            chooseFile();
        } else {
            ImportResult data = new ImportResult();
            data.e = new Exception(getString(R.string.import_permission_required));
            ((ResultFragment.OnImportCallback) getActivity()).onImportResult(data);

            dismissAllowingStateLoss();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
        }, 0);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setTitle(R.string.import_dialog_title);
        progress.setMessage(getString(R.string.import_dialog_loading_file));
        progress.setCanceledOnTouchOutside(false);
        progress.setIndeterminate(false);
        progress.setIcon(R.drawable.ic_import_export_black);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions();
            } else {
                chooseFile();
            }
        } else {
            setDialogAddEntries(progress, savedInstanceState.getInt(TAG_PROGRESS_MAX));
        }

        return progress;
    }

    private void setDialogAddEntries(ProgressDialog dialog, int length) {
        dialog.setMax(length);
        dialog.setMessage(getString(R.string.import_dialog_adding_entries));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        final ProgressDialog dialog = (ProgressDialog) getDialog();

        outState.putInt(TAG_PROGRESS_MAX, dialog.getMax());
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();
        // handles https://code.google.com/p/android/issues/detail?id=17423
        if (dialog != null && getRetainInstance()) {
            dialog.setDismissMessage(null);
        }
        super.onDestroyView();
    }

    public void chooseFile() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setAction(Intent.ACTION_GET_CONTENT);

        if (info.type.equals(CollectionInfo.Type.CALENDAR)) {
            intent.setType("text/calendar");
        } else if (info.type.equals(CollectionInfo.Type.ADDRESS_BOOK)) {
            intent.setType("text/x-vcard");
        }

        Intent chooser = Intent.createChooser(
                intent, getString(R.string.choose_file));
        try {
            startActivityForResult(chooser, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            ImportResult data = new ImportResult();
            data.e = new Exception("Failed to open file chooser.\nPlease install one.");

            ((ResultFragment.OnImportCallback) getActivity()).onImportResult(data);

            dismissAllowingStateLoss();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
                        App.log.info("Importing uri = " + uri.toString());
                        try {
                            importFile = new File(com.etesync.syncadapter.utils.FileUtils.getPath(getContext(), uri));

                            new Thread(new ImportCalendarsLoader()).start();
                        } catch (Exception e) {
                            App.log.severe("File select error: " + e.getLocalizedMessage());
                        }
                    }
                } else {
                    dismissAllowingStateLoss();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void loadFinished(ImportResult data) {
        ((ResultFragment.OnImportCallback) getActivity()).onImportResult(data);

        dismissAllowingStateLoss();

        if (getActivity() instanceof Refreshable) {
            ((Refreshable) getActivity()).refresh();
        }
    }

    private class ImportCalendarsLoader implements Runnable {
        private void finishParsingFile(final int length) {
            if (getActivity() == null) {
                return;
            }

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setDialogAddEntries((ProgressDialog) getDialog(), length);
                }
            });
        }

        private void entryProcessed() {
            if (getActivity() == null) {
                return;
            }

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final ProgressDialog dialog = (ProgressDialog) getDialog();

                    dialog.incrementProgressBy(1);
                }
            });
        }

        @Override
        public void run() {
            final ImportResult result = loadInBackground();

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadFinished(result);
                }
            });
        }

        public ImportResult loadInBackground() {
            ImportResult result = new ImportResult();

            try {
                FileInputStream importStream = new FileInputStream(importFile);

                if (info.type.equals(CollectionInfo.Type.CALENDAR)) {
                    final Event[] events = Event.fromStream(importStream, Charsets.UTF_8);
                    importStream.close();

                    if (events.length == 0) {
                        App.log.warning("Empty/invalid file.");
                        result.e = new Exception("Empty/invalid file.");
                        return result;
                    }

                    result.total = events.length;

                    finishParsingFile(events.length);

                    ContentProviderClient provider = getContext().getContentResolver().acquireContentProviderClient(CalendarContract.CONTENT_URI);
                    LocalCalendar localCalendar;
                    try {
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory.INSTANCE, info.uid);
                        if (localCalendar == null) {
                            throw new FileNotFoundException("Failed to load local resource.");
                        }
                    } catch (CalendarStorageException | FileNotFoundException e) {
                        App.log.info("Fail" + e.getLocalizedMessage());
                        result.e = e;
                        return result;
                    }

                    for (Event event : events) {
                        try {
                            LocalEvent localEvent = localCalendar.getByUid(event.uid);
                            if (localEvent != null) {
                                localEvent.updateAsDirty(event);
                                result.updated++;
                            } else {
                                localEvent = new LocalEvent(localCalendar, event, event.uid, null);
                                localEvent.addAsDirty();
                                result.added++;
                            }
                        } catch (CalendarStorageException e) {
                            e.printStackTrace();
                        }

                        entryProcessed();
                    }
                } else if (info.type.equals(CollectionInfo.Type.ADDRESS_BOOK)) {
                    // FIXME: Handle groups and download icon?
                    final Contact[] contacts = Contact.fromStream(importStream, Charsets.UTF_8, null);

                    if (contacts.length == 0) {
                        App.log.warning("Empty/invalid file.");
                        result.e = new Exception("Empty/invalid file.");
                        return result;
                    }

                    result.total = contacts.length;

                    finishParsingFile(contacts.length);

                    ContentProviderClient provider = getContext().getContentResolver().acquireContentProviderClient(ContactsContract.RawContacts.CONTENT_URI);
                    LocalAddressBook localAddressBook = LocalAddressBook.findByUid(getContext(), provider, account, info.uid);

                    for (Contact contact : contacts) {
                        try {
                            LocalContact localContact = (LocalContact) localAddressBook.getByUid(contact.uid);
                            if (localContact != null) {
                                localContact.updateAsDirty(contact);
                                result.updated++;
                            } else {
                                localContact = new LocalContact(localAddressBook, contact, contact.uid, null);
                                localContact.createAsDirty();
                                result.added++;
                            }
                        } catch (ContactsStorageException e) {
                            e.printStackTrace();
                        }

                        entryProcessed();
                    }
                    provider.release();
                }

                return result;
            } catch (FileNotFoundException e) {
                result.e = e;
                return result;
            } catch (InvalidCalendarException | IOException | ContactsStorageException e) {
                result.e = e;
                return result;
            }
        }
    }
}
