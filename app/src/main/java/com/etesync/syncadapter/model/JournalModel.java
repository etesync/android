package com.etesync.syncadapter.model;

import io.requery.CascadeAction;
import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.OneToMany;
import io.requery.OneToOne;
import io.requery.ReferentialAction;

public class JournalModel {
    @Entity
    public static abstract class Journal {
        @Key
        @Generated
        int id;

        @Column(length = 64, unique = true, nullable = false)
        String uid;
    }

    @Entity
    public static abstract class Entry {
        @Key
        @Generated
        int id;

        @Column(length = 64, unique = true, nullable = false)
        String uid;

        String content;

        @Index("journal_index")
        @ForeignKey(update = ReferentialAction.CASCADE)
        @ManyToOne
        Journal journal;
    }
}
