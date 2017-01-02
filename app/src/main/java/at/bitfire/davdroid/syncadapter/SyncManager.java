/*
* Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
*/
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.GsonHelper;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.journalmanager.Exceptions;
import at.bitfire.davdroid.journalmanager.JournalEntryManager;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.davdroid.ui.AccountSettingsActivity;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.InvalidCalendarException;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Getter;
import okhttp3.OkHttpClient;

abstract public class SyncManager {

    protected final String SYNC_PHASE_PREPARE = "sync_phase_prepare",
            SYNC_PHASE_QUERY_CAPABILITIES = "sync_phase_query_capabilities",
            SYNC_PHASE_PREPARE_LOCAL = "sync_phase_prepare_local",
            SYNC_PHASE_CREATE_LOCAL_ENTRIES = "sync_phase_create_local_entries",
            SYNC_PHASE_FETCH_ENTRIES = "sync_phase_fetch_entries",
            SYNC_PHASE_APPLY_REMOTE_ENTRIES = "sync_phase_apply_remote_entries",
            SYNC_PHASE_APPLY_LOCAL_ENTRIES = "sync_phase_apply_local_entries",
            SYNC_PHASE_PUSH_ENTRIES = "sync_phase_push_entries",
            SYNC_PHASE_POST_PROCESSING = "sync_phase_post_processing",
            SYNC_PHASE_SAVE_SYNC_TAG = "sync_phase_save_sync_tag";


    protected final NotificationManagerCompat notificationManager;
    protected final String uniqueCollectionId;

    protected final Context context;
    protected final Account account;
    protected final Bundle extras;
    protected final String authority;
    protected final SyncResult syncResult;

    protected final AccountSettings settings;
    protected LocalCollection localCollection;

    protected OkHttpClient httpClient;

    protected JournalEntryManager journal;

    /**
     * remote CTag (uuid of the last entry on the server). We update it when we fetch/push and save when everything works.
     */
    protected String remoteCTag = null;

    /**
     * Syncable local journal entries.
     */
    protected List<JournalEntryManager.Entry> localEntries;

    /**
     * Syncable remote journal entries (fetch from server).
     */
    protected List<JournalEntryManager.Entry> remoteEntries;

    /**
     * sync-able resources in the local collection, as enumerated by {@link #prepareLocal()}
     */
    protected Map<String, LocalResource> localResources;

    public SyncManager(Context context, Account account, AccountSettings settings, Bundle extras, String authority, SyncResult syncResult, String uniqueCollectionId) throws InvalidAccountException {
        this.context = context;
        this.account = account;
        this.settings = settings;
        this.extras = extras;
        this.authority = authority;
        this.syncResult = syncResult;

        // create HttpClient with given logger
        httpClient = HttpClient.create(context, account);

        // dismiss previous error notifications
        this.uniqueCollectionId = uniqueCollectionId;
        notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(uniqueCollectionId, notificationId());
    }

    protected abstract int notificationId();

    protected abstract String getSyncErrorTitle();

    @TargetApi(21)
    public void performSync() {
        String syncPhase = SYNC_PHASE_PREPARE;
        try {
            App.log.info("Sync phase: " + syncPhase);
            prepare();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_QUERY_CAPABILITIES;
            App.log.info("Sync phase: " + syncPhase);
            queryCapabilities();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_PREPARE_LOCAL;
            App.log.info("Sync phase: " + syncPhase);
            prepareLocal();

            /* Create journal entries out of local changes. */
            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_CREATE_LOCAL_ENTRIES;
            App.log.info("Sync phase: " + syncPhase);
            createLocalEntries();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_FETCH_ENTRIES;
            App.log.info("Sync phase: " + syncPhase);
            fetchEntries();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_APPLY_REMOTE_ENTRIES;
            App.log.info("Sync phase: " + syncPhase);
            applyRemoteEntries();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_APPLY_LOCAL_ENTRIES;
            App.log.info("Sync phase: " + syncPhase);
            applyLocalEntries();

            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_PUSH_ENTRIES;
            App.log.info("Sync phase: " + syncPhase);
            pushEntries();

            /* Cleanup and finalize changes */
            if (Thread.interrupted())
                return;
            syncPhase = SYNC_PHASE_POST_PROCESSING;
            App.log.info("Sync phase: " + syncPhase);
            postProcess();

            syncPhase = SYNC_PHASE_SAVE_SYNC_TAG;
            App.log.info("Sync phase: " + syncPhase);
            saveSyncTag();

        } catch (IOException e) {
            App.log.log(Level.WARNING, "I/O exception during sync, trying again later", e);
            syncResult.stats.numIoExceptions++;

        } catch (Exceptions.ServiceUnavailableException e) {
                Date retryAfter =  null; // ((Exceptions.ServiceUnavailableException) e).retryAfter;
                if (retryAfter != null) {
                    // how many seconds to wait? getTime() returns ms, so divide by 1000
                    // syncResult.delayUntil = (retryAfter.getTime() - new Date().getTime()) / 1000;
                }
        } catch (Exception | OutOfMemoryError e) {
            final int messageString;

            if (e instanceof Exceptions.UnauthorizedException) {
                App.log.log(Level.SEVERE, "Not authorized anymore", e);
                messageString = R.string.sync_error_unauthorized;
                syncResult.stats.numAuthExceptions++;
            } else if (e instanceof Exceptions.HttpException) {
                App.log.log(Level.SEVERE, "HTTP Exception during sync", e);
                messageString = R.string.sync_error_http_dav;
                syncResult.stats.numParseExceptions++;
            } else if (e instanceof CalendarStorageException || e instanceof ContactsStorageException) {
                App.log.log(Level.SEVERE, "Couldn't access local storage", e);
                messageString = R.string.sync_error_local_storage;
                syncResult.databaseError = true;
            } else if (e instanceof Exceptions.IntegrityException) {
                App.log.log(Level.SEVERE, "Integrity error", e);
                // FIXME: Make a proper error message
                messageString = R.string.sync_error;
                syncResult.stats.numParseExceptions++;
            } else {
                App.log.log(Level.SEVERE, "Unknown sync error", e);
                messageString = R.string.sync_error;
                syncResult.stats.numParseExceptions++;
            }

            final Intent detailsIntent;
            if (e instanceof Exceptions.UnauthorizedException) {
                detailsIntent = new Intent(context, AccountSettingsActivity.class);
                detailsIntent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account);
            } else {
                detailsIntent = new Intent(context, DebugInfoActivity.class);
                detailsIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e);
                detailsIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account);
                detailsIntent.putExtra(DebugInfoActivity.KEY_AUTHORITY, authority);
                detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase);
            }

            // to make the PendingIntent unique
            detailsIntent.setData(Uri.parse("uri://" + getClass().getName() + "/" + uniqueCollectionId));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setSmallIcon(R.drawable.ic_error_light)
                    .setLargeIcon(App.getLauncherBitmap(context))
                    .setContentTitle(getSyncErrorTitle())
                    .setContentIntent(PendingIntent.getActivity(context, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR);

            String message = context.getString(messageString, syncPhase);
            builder.setContentText(message);


            notificationManager.notify(uniqueCollectionId, notificationId(), builder.build());
        }
    }


    abstract protected void prepare() throws ContactsStorageException, CalendarStorageException;

    abstract protected void processSyncEntry(SyncEntry cEntry) throws IOException, ContactsStorageException, CalendarStorageException, InvalidCalendarException;

    abstract protected void applyLocalEntries() throws IOException, ContactsStorageException, CalendarStorageException, Exceptions.HttpException;

    protected void queryCapabilities() throws IOException, CalendarStorageException, ContactsStorageException {
    }

    protected void fetchEntries() throws Exceptions.HttpException, ContactsStorageException, CalendarStorageException, Exceptions.IntegrityException {
        remoteEntries = journal.getEntries(settings.password(), remoteCTag);

        if (!remoteEntries.isEmpty()) {
            remoteCTag = remoteEntries.get(remoteEntries.size() - 1).getUuid();
        }
    }

    protected void applyRemoteEntries() throws IOException, ContactsStorageException, CalendarStorageException, InvalidCalendarException {
        // Process new vcards from server
        for (JournalEntryManager.Entry entry : remoteEntries) {
            if (Thread.interrupted())
                return;

            App.log.info("Processing " + entry.toString());

            SyncEntry cEntry = SyncEntry.fromJournalEntry(settings.password(), entry);
            App.log.info("Processing resource for journal entry " + entry.getUuid());
            processSyncEntry(cEntry);
        }
    }

    protected void pushEntries() throws Exceptions.HttpException, IOException, ContactsStorageException, CalendarStorageException {
        // upload dirty contacts
        // FIXME: Deal with failure
        if (!localEntries.isEmpty()) {
            journal.putEntries(localEntries, remoteCTag);

            for (LocalResource local : localCollection.getDirty()) {
                App.log.info("Added/changed resource with UUID: " + local.getUuid());
                local.clearDirty(local.getUuid());
            }

            for (LocalResource local : localCollection.getDeleted()) {
                local.delete();
            }

            remoteCTag = localEntries.get(localEntries.size() - 1).getUuid();
        }
    }

    protected void createLocalEntries() throws CalendarStorageException, ContactsStorageException, IOException {
        localEntries = new LinkedList<>();

        // Not saving, just creating a fake one until we load it from a local db
        JournalEntryManager.Entry previousEntry = (remoteCTag != null) ? JournalEntryManager.Entry.getFakeWithUid(remoteCTag) : null;

        for (LocalResource local : processLocallyDeleted()) {
            SyncEntry entry = new SyncEntry(local.getContent(), SyncEntry.Actions.DELETE);
            JournalEntryManager.Entry tmp = new JournalEntryManager.Entry();
            tmp.update(settings.password(), entry.toJson(), previousEntry);
            previousEntry = tmp;
            localEntries.add(previousEntry);
        }

        try {
            for (LocalResource local : localCollection.getDirty()) {
                SyncEntry.Actions action;
                if (local.isLocalOnly()) {
                    action = SyncEntry.Actions.ADD;
                } else {
                    action = SyncEntry.Actions.CHANGE;
                }

                SyncEntry entry = new SyncEntry(local.getContent(), action);
                JournalEntryManager.Entry tmp = new JournalEntryManager.Entry();
                tmp.update(settings.password(), entry.toJson(), previousEntry);
                previousEntry = tmp;
                localEntries.add(previousEntry);
            }
        } catch (FileNotFoundException e) {
            // FIXME: Do something
            e.printStackTrace();
        }
    }

    /**
     * Lists all local resources which should be taken into account for synchronization into {@link #localResources}.
     */
    protected void prepareLocal() throws CalendarStorageException, ContactsStorageException {
        prepareDirty();

        // fetch list of local contacts and build hash table to index file name
        LocalResource[] localList = localCollection.getAll();
        localResources = new HashMap<>(localList.length);
        for (LocalResource resource : localList) {
            App.log.fine("Found local resource: " + resource.getUuid());
            localResources.put(resource.getUuid(), resource);
        }

        remoteCTag = localCollection.getCTag();
    }


    /**
     * Delete unpublished locally deleted, and return the rest.
     * Checks Thread.interrupted() before each request to allow quick sync cancellation.
     */
    protected List<LocalResource> processLocallyDeleted() throws CalendarStorageException, ContactsStorageException {
        // FIXME: This needs refactoring and fixing, it's just not true.
        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        LocalResource[] localList = localCollection.getDeleted();
        List<LocalResource> ret = new ArrayList<>(localList.length);

        for (LocalResource local : localList) {
            if (Thread.interrupted())
                return ret;

            if (!local.isLocalOnly()) {
                App.log.info(local.getUuid() + " has been deleted locally -> deleting from server");
                ret.add(local);
            } else {
                App.log.info("Removing local record #" + local.getId() + " which has been deleted locally and was never uploaded");
                local.delete();
            }

            syncResult.stats.numDeletes++;
        }

        return ret;
    }

    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        // assign file names and UIDs to new contacts so that we can use the file name as an index
        App.log.info("Looking for contacts/groups without file name");
        for (LocalResource local : localCollection.getWithoutFileName()) {
            String uuid = UUID.randomUUID().toString();
            App.log.fine("Found local record #" + local.getId() + " without file name; assigning file name/UID based on " + uuid);
            local.updateFileNameAndUID(uuid);
        }
    }

    /**
     * For post-processing of entries, for instance assigning groups.
     */
    protected void postProcess() throws CalendarStorageException, ContactsStorageException {
    }

    protected void saveSyncTag() throws CalendarStorageException, ContactsStorageException {
        App.log.info("Saving CTag=" + remoteCTag);
        localCollection.setCTag(remoteCTag);
    }


    static class SyncEntry {
        @Getter
        private String content;
        @Getter
        private Actions action;

        enum Actions {
            ADD("ADD"),
            CHANGE("CHANGE"),
            DELETE("DELETE");

            private final String text;

            Actions(final String text) {
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        @SuppressWarnings("unused")
        private SyncEntry() {
        }

        protected SyncEntry(String content, Actions action) {
            this.content = content;
            this.action = action;
        }

        boolean isAction(Actions action) {
            return this.action.equals(action);
        }

        static SyncEntry fromJournalEntry(String keyBase64, JournalEntryManager.Entry entry) {
            return GsonHelper.gson.fromJson(entry.getContent(keyBase64), SyncEntry.class);
        }

        String toJson() {
            return GsonHelper.gson.toJson(this, this.getClass());
        }
    }
}
