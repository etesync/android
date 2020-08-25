package com.etesync.syncadapter

import android.content.Context
import com.etebase.client.*
import com.etebase.client.Collection
import java.io.File

/*
File structure:
cache_dir/
    user1/ <--- the name of the user
        cols/
            UID1/ - The uid of the first col
                ...
            UID2/ - The uid of the second col
                col <-- the col itself
                items/
                    item_uid1 <-- the item with uid 1
                    item_uid2
                    ...
 */
class EtebaseLocalCache private constructor(context: Context, username: String) {
    private val filesDir: File
    private val colsDir: File

    init {
        filesDir = File(context.filesDir, username)
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

    fun collectionList(colMgr: CollectionManager): List<Collection> {
        return colsDir.list().map {
            val colFile = File(it, "col")
            val content = colFile.readBytes()
            colMgr.cacheLoad(content)
        }
    }

    fun collectionSet(colMgr: CollectionManager, collection: Collection) {
        val colDir = File(colsDir, collection.uid)
        colDir.mkdir()
        val colFile = File(colDir, "col")
        colFile.writeBytes(colMgr.cacheSave(collection))
        val itemsDir = getCollectionItemsDir(collection.uid)
        itemsDir.mkdir()
    }

    fun collectionUnset(colMgr: CollectionManager, colUid: String) {
        val colDir = File(colsDir, colUid)
        colDir.deleteRecursively()
    }

    fun itemList(itemMgr: ItemManager, colUid: String): List<Item> {
        val itemsDir = getCollectionItemsDir(colUid)
        return itemsDir.list().map {
            val itemFile = File(it)
            val content = itemFile.readBytes()
            itemMgr.cacheLoad(content)
        }
    }

    fun itemSet(itemMgr: ItemManager, colUid: String, item: Item) {
        val itemsDir = getCollectionItemsDir(colUid)
        val itemFile = File(itemsDir, item.uid)
        itemFile.writeBytes(itemMgr.cacheSave(item))
    }

    fun itemUnset(itemMgr: ItemManager, colUid: String, itemUid: String) {
        val itemsDir = getCollectionItemsDir(colUid)
        val itemFile = File(itemsDir, itemUid)
        itemFile.delete()
    }

    companion object {
        fun getInstance(context: Context, username: String): EtebaseLocalCache {
            return EtebaseLocalCache(context, username)
        }
    }
}
