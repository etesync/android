package com.etesync.syncadapter.model

import io.requery.Persistable
import io.requery.sql.Configuration
import io.requery.sql.EntityDataStore

class MyEntityDataStore(configuration: Configuration): EntityDataStore<Persistable>(configuration) {

}