package com.etesync.syncadapter.model;

import java.util.LinkedList;
import java.util.List;

import io.requery.Column;
import io.requery.Convert;
import io.requery.Converter;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Persistable;
import io.requery.PostLoad;
import io.requery.ReferentialAction;
import io.requery.Table;
import io.requery.sql.EntityDataStore;

public class JournalModel {
    // FIXME: Add unique constraint on the uid + service combination. Can't do it at the moment because requery is broken.
    @Entity
    @Table(name = "Journal")
    public static abstract class Journal {
        @Key
        @Generated
        int id;

        @Column(length = 64, nullable = false)
        String uid;

        @Convert(CollectionInfoConverter.class)
        CollectionInfo info;

        String owner;

        byte[] encryptedKey;

        @Index(value = "uid_unique")
        long service;

        boolean deleted;

        @PostLoad
        void afterLoad() {
            this.info.serviceID = service;
            this.info.uid = uid;
        }

        public Journal() {
            this.deleted = false;
        }

        public Journal(CollectionInfo info) {
            this();
            this.info = info;
            this.uid = info.uid;
            this.service = info.serviceID;
        }

        public static List<JournalEntity> getJournals(EntityDataStore<Persistable> data, long service) {
            return data.select(JournalEntity.class).where(JournalEntity.SERVICE.eq(service).and(JournalEntity.DELETED.eq(false))).get().toList();
        }

        public static List<CollectionInfo> getCollections(EntityDataStore<Persistable> data, long service) {
            List<CollectionInfo> ret = new LinkedList<>();

            List<JournalEntity> journals = getJournals(data, service);
            for (JournalEntity journal : journals) {
                // FIXME: For some reason this isn't always being called, manually do it here.
                journal.afterLoad();
                ret.add(journal.getInfo());
            }

            return ret;
        }

        public static JournalEntity fetch(EntityDataStore<Persistable> data, String url) {
            JournalEntity ret = data.select(JournalEntity.class).where(JournalEntity.UID.eq(url)).limit(1).get().firstOrNull();
            if (ret != null) {
                // FIXME: For some reason this isn't always being called, manually do it here.
                ret.afterLoad();
            }
            return ret;
        }

        public static JournalEntity fetchOrCreate(EntityDataStore<Persistable> data, CollectionInfo collection) {
            JournalEntity journalEntity = fetch(data, collection.uid);
            if (journalEntity == null) {
                journalEntity = new JournalEntity(collection);
            } else {
                journalEntity.setInfo(collection);
            }
            return journalEntity;
        }

        public String getLastUid(EntityDataStore<Persistable> data) {
            EntryEntity last = data.select(EntryEntity.class).where(EntryEntity.JOURNAL.eq(this)).orderBy(EntryEntity.ID.desc()).limit(1).get().firstOrNull();
            if (last != null) {
                return last.getUid();
            }

            return null;
        }
    }

    @Entity
    @Table(name = "Entry", uniqueIndexes = "entry_unique_together")
    public static abstract class Entry {
        @Key
        @Generated
        int id;

        @Index("entry_unique_together")
        @Column(length = 64, nullable = false)
        String uid;

        @Convert(SyncEntryConverter.class)
        SyncEntry content;

        @Index("entry_unique_together")
        @ForeignKey(update = ReferentialAction.CASCADE)
        @ManyToOne
        Journal journal;
    }

    static class CollectionInfoConverter implements Converter<CollectionInfo, String> {
        @Override
        public Class<CollectionInfo> getMappedType() {
            return CollectionInfo.class;
        }

        @Override
        public Class<String> getPersistedType() {
            return String.class;
        }

        @Override
        public Integer getPersistedSize() {
            return null;
        }

        @Override
        public String convertToPersisted(CollectionInfo value) {
            return value == null ? null : value.toJson();
        }

        @Override
        public CollectionInfo convertToMapped(Class<? extends CollectionInfo> type, String value) {
            return value == null ? null : CollectionInfo.fromJson(value);
        }
    }


    static class SyncEntryConverter implements Converter<SyncEntry, String> {
        @Override
        public Class<SyncEntry> getMappedType() {
            return SyncEntry.class;
        }

        @Override
        public Class<String> getPersistedType() {
            return String.class;
        }

        @Override
        public Integer getPersistedSize() {
            return null;
        }

        @Override
        public String convertToPersisted(SyncEntry value) {
            return value == null ? null : value.toJson();
        }

        @Override
        public SyncEntry convertToMapped(Class<? extends SyncEntry> type, String value) {
            return value == null ? null : SyncEntry.fromJson(value);
        }
    }
}
