package com.etesync.syncadapter

import android.content.Context
import com.etebase.client.*
import com.etebase.client.Collection
import com.etebase.client.exceptions.EtebaseException
import com.etebase.client.exceptions.UrlParseException
import okhttp3.OkHttpClient
import java.io.File
import java.util.*

class EtebaseLocalCache private constructor(context: Context, username: String) {
    private val fsCache: FileSystemCache = FileSystemCache.create(context.filesDir.absolutePath, username)
    private val filesDir: File = File(context.filesDir, username)
    private val colsDir: File = File(filesDir, "cols")

    private fun getCollectionItemsDir(colUid: String): File {
        val colsDir = File(filesDir, "cols")
        val colDir = File(colsDir, colUid)
        return File(colDir, "items")
    }

    private fun clearUserCache() {
        fsCache.clearUserCache()
    }

    fun saveStoken(stoken: String) {
        fsCache.saveStoken(stoken)
    }

    fun loadStoken(): String? {
        return fsCache.loadStoken()
    }

    fun collectionSaveStoken(colUid: String, stoken: String) {
        fsCache.collectionSaveStoken(colUid, stoken)
    }

    fun collectionLoadStoken(colUid: String): String? {
        return fsCache.collectionLoadStoken(colUid)
    }

    fun collectionList(colMgr: CollectionManager, withDeleted: Boolean = false): List<CachedCollection> {
        return fsCache._unstable_collectionList(colMgr).filter {
            withDeleted || !it.isDeleted
        }.map{
            CachedCollection(it, it.meta)
        }
    }

    fun collectionGet(colMgr: CollectionManager, colUid: String): CachedCollection {
        return fsCache.collectionGet(colMgr, colUid).let {
            CachedCollection(it, it.meta)
        }
    }

    fun collectionSet(colMgr: CollectionManager, collection: Collection) {
        fsCache.collectionSet(colMgr, collection)
    }

    fun collectionUnset(colMgr: CollectionManager, colUid: String) {
        try {
            fsCache.collectionUnset(colMgr, colUid)
        } catch (e: UrlParseException) {
            // Ignore, as it just means the file doesn't exist
        }
    }

    fun itemList(itemMgr: ItemManager, colUid: String, withDeleted: Boolean = false): List<CachedItem> {
        return fsCache._unstable_itemList(itemMgr, colUid).filter {
            withDeleted || !it.isDeleted
        }.map {
            CachedItem(it, it.meta, it.contentString)
        }
    }

    fun itemGet(itemMgr: ItemManager, colUid: String, itemUid: String): CachedItem? {
        // Need the try because the inner call doesn't return null on missing, but an error
        val ret = try {
            fsCache.itemGet(itemMgr, colUid, itemUid)
        } catch (e: EtebaseException) {
            return null
        }
        return ret.let {
            CachedItem(it, it.meta, it.contentString)
        }
    }

    fun itemSet(itemMgr: ItemManager, colUid: String, item: Item) {
        fsCache.itemSet(itemMgr, colUid, item)
    }

    fun itemUnset(itemMgr: ItemManager, colUid: String, itemUid: String) {
        fsCache.itemUnset(itemMgr, colUid, itemUid)
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

        fun clearUserCache(context: Context, username: String) {
            val localCache = getInstance(context, username)
            localCache.clearUserCache()
            localCacheCache.remove(username)
        }

        // FIXME: If we ever cache this we need to cache bust on changePassword
        fun getEtebase(context: Context, httpClient: OkHttpClient, settings: AccountSettings): Account {
            val client = Client.create(httpClient, settings.uri?.toString())
            return Account.restore(client, settings.etebaseSession!!, null)
        }
    }
}

data class CachedCollection(val col: Collection, val meta: CollectionMetadata)

data class CachedItem(val item: Item, val meta: ItemMetadata, val content: String)