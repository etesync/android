package com.etesync.syncadapter

import android.content.Context
import com.etebase.client.*
import com.etebase.client.Collection
import okhttp3.OkHttpClient
import java.io.File
import java.util.*

/*
File structure:
cache_dir/
    user1/ <--- the name of the user
        stoken <-- the stokens of the collection fetch
        cols/
            UID1/ - The uid of the first col
                ...
            UID2/ - The uid of the second col
                col <-- the col itself
                stoken <-- the stoken of the items fetch
                items/
                    item_uid1 <-- the item with uid 1
                    item_uid2
                    ...
 */
class EtebaseLocalCache private constructor(context: Context, username: String) {
    private val filesDir: File = File(context.filesDir, username)
    private val colsDir: File

    init {
        colsDir = File(filesDir, "cols")
        colsDir.mkdirs()
    }

    private fun getCollectionItemsDir(colUid: String): File {
        val colsDir = File(filesDir, "cols")
        val colDir = File(colsDir, colUid)
        return File(colDir, "items")
    }

    fun clearUserCache() {
        filesDir.deleteRecursively()
    }

    fun saveStoken(stoken: String) {
        val stokenFile = File(filesDir, "stoken")
        stokenFile.writeText(stoken)
    }

    fun loadStoken(): String? {
        val stokenFile = File(filesDir, "stoken")
        return if (stokenFile.exists()) stokenFile.readText() else null
    }


    fun collectionSaveStoken(colUid: String, stoken: String) {
        val colDir = File(colsDir, colUid)
        val stokenFile = File(colDir, "stoken")
        stokenFile.writeText(stoken)
    }

    fun collectionLoadStoken(colUid: String): String? {
        val colDir = File(colsDir, colUid)
        val stokenFile = File(colDir, "stoken")
        return if (stokenFile.exists()) stokenFile.readText() else null
    }

    fun collectionList(colMgr: CollectionManager, withDeleted: Boolean = false): List<CachedCollection> {
        return colsDir.list().map {
            val colDir = File(colsDir, it)
            val colFile = File(colDir, "col")
            val content = colFile.readBytes()
            colMgr.cacheLoad(content)
        }.filter { withDeleted || !it.isDeleted }.map{
            CachedCollection(it, it.meta)
        }
    }

    fun collectionGet(colMgr: CollectionManager, colUid: String): CachedCollection {
        val colDir = File(colsDir, colUid)
        val colFile = File(colDir, "col")
        val content = colFile.readBytes()
        return colMgr.cacheLoad(content).let {
            CachedCollection(it, it.meta)
        }
    }

    fun collectionSet(colMgr: CollectionManager, collection: Collection) {
        val colDir = File(colsDir, collection.uid)
        colDir.mkdir()
        val colFile = File(colDir, "col")
        colFile.writeBytes(colMgr.cacheSaveWithContent(collection))
        val itemsDir = getCollectionItemsDir(collection.uid)
        itemsDir.mkdir()
    }

    fun collectionUnset(colMgr: CollectionManager, colUid: String) {
        val colDir = File(colsDir, colUid)
        colDir.deleteRecursively()
    }

    fun itemList(itemMgr: ItemManager, colUid: String, withDeleted: Boolean = false): List<CachedItem> {
        val itemsDir = getCollectionItemsDir(colUid)
        return itemsDir.list().map {
            val itemFile = File(itemsDir, it)
            val content = itemFile.readBytes()
            itemMgr.cacheLoad(content)
        }.filter { withDeleted || !it.isDeleted }.map {
            CachedItem(it, it.meta)
        }
    }

    fun itemSet(itemMgr: ItemManager, colUid: String, item: Item) {
        val itemsDir = getCollectionItemsDir(colUid)
        val itemFile = File(itemsDir, item.uid)
        itemFile.writeBytes(itemMgr.cacheSaveWithContent(item))
    }

    fun itemUnset(itemMgr: ItemManager, colUid: String, itemUid: String) {
        val itemsDir = getCollectionItemsDir(colUid)
        val itemFile = File(itemsDir, itemUid)
        itemFile.delete()
    }

    companion object {
        private val localCacheCache: HashMap<String, EtebaseLocalCache> = HashMap()

        fun getInstance(context: Context, username: String): EtebaseLocalCache {
            synchronized(localCacheCache) {
                val cached = localCacheCache.get(username)
                if (cached != null) {
                    return cached
                } else {
                    val ret = EtebaseLocalCache(context, username)
                    localCacheCache.set(username, ret)
                    return ret
                }
            }
        }

        fun getEtebase(context: Context, httpClient: OkHttpClient, settings: AccountSettings): Account {
            val client = Client.create(httpClient, settings.uri?.toString())
            return Account.restore(client, settings.etebaseSession!!, null)
        }
    }
}

data class CachedCollection(val col: Collection, val meta: CollectionMetadata)

data class CachedItem(val item: Item, val meta: ItemMetadata)