package com.etesync.syncadapter.model

import io.requery.Persistable
import io.requery.meta.Attribute
import io.requery.sql.Configuration
import io.requery.sql.EntityDataStore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MyEntityDataStore(configuration: Configuration): EntityDataStore<Persistable>(configuration) {
    val lock = ReentrantLock()

    override fun <K : Any?, E : Persistable?> insert(entity: E, keyClass: Class<K>?): K {
        lock.withLock {
            return super.insert(entity, keyClass)
        }
    }

    override fun <E : Persistable?> update(entity: E): E {
        lock.withLock {
            return super.update(entity)
        }
    }

    override fun <E : Persistable?> update(entity: E, vararg attributes: Attribute<*, *>?): E {
        lock.withLock {
            return super.update(entity, *attributes)
        }
    }

    override fun <E : Persistable?> upsert(entity: E): E {
        lock.withLock {
            return super.upsert(entity)
        }
    }

    override fun <E : Persistable?> refresh(entity: E): E {
        lock.withLock {
            return super.refresh(entity)
        }
    }

    override fun <E : Persistable?> refresh(entity: E, vararg attributes: Attribute<*, *>?): E {
        lock.withLock {
            return super.refresh(entity, *attributes)
        }
    }

    override fun <E : Persistable?> refreshAll(entity: E): E {
        lock.withLock {
            return super.refreshAll(entity)
        }
    }

    override fun <E : Persistable?> delete(entity: E): Void {
        lock.withLock {
            return super.delete(entity)
        }
    }
}