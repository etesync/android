package com.etesync.syncadapter.ui;

import android.Manifest;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderClient;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.R;
import com.etesync.syncadapter.model.CollectionInfo;
import com.etesync.syncadapter.resource.LocalAddressBook;
import com.etesync.syncadapter.resource.LocalCalendar;
import com.etesync.syncadapter.resource.LocalContact;
import com.etesync.syncadapter.resource.LocalEvent;

import org.apache.commons.codec.Charsets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;
import lombok.ToString;

import static com.etesync.syncadapter.Constants.KEY_ACCOUNT;
import static com.etesync.syncadapter.Constants.KEY_COLLECTION_INFO;

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
            getFragmentManager().beginTransaction()
                    .add(ResultFragment.newInstance(data), null)
                    .commitAllowingStateLoss();

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
            getFragmentManager().beginTransaction()
                    .add(ResultFragment.newInstance(data), null)
                    .commitAllowingStateLoss();

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
        getFragmentManager().beginTransaction()
                .add(ResultFragment.newInstance(data), null)
                .commitAllowingStateLoss();

        dismissAllowingStateLoss();
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
                @Cleanup FileInputStream importStream = new FileInputStream(importFile);

                if (info.type.equals(CollectionInfo.Type.CALENDAR)) {
                    final Event[] events = Event.fromStream(importStream, Charsets.UTF_8);

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
                        localCalendar = LocalCalendar.findByName(account, provider, LocalCalendar.Factory.INSTANCE, info.url);
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
                    LocalAddressBook localAddressBook = new LocalAddressBook(account, provider);

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
                }

                return result;
            } catch (FileNotFoundException e) {
                result.e = e;
                return result;
            } catch (InvalidCalendarException | IOException e) {
                result.e = e;
                return result;
            }
        }
    }

    @ToString
    static class ImportResult implements Serializable {
        long total;
        long added;
        long updated;
        Exception e;

        boolean isFailed() {
            return (e != null);
        }

        long getSkipped() {
            return total - (added + updated);
        }
    }

    public static class ResultFragment extends DialogFragment {
        private static final String KEY_RESULT = "result";
        private ImportResult result;

        private static ResultFragment newInstance(ImportResult result) {
            Bundle args = new Bundle();
            args.putSerializable(KEY_RESULT, result);
            ResultFragment fragment = new ResultFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            result = (ImportResult) getArguments().getSerializable(KEY_RESULT);
        }

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int icon;
            int title;
            String msg;
            if (result.isFailed()) {
                icon = R.drawable.ic_error_dark;
                title = R.string.import_dialog_failed_title;
                msg = result.e.getLocalizedMessage();
            } else {
                icon = R.drawable.ic_import_export_black;
                title = R.string.import_dialog_title;
                msg = getString(R.string.import_dialog_success, result.total, result.added, result.updated, result.getSkipped());
            }
            return new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setIcon(icon)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // dismiss
                        }
                    })
                    .create();
        }
    }
}
