package com.etesync.syncadapter.ui.etebase

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.etebase.client.CollectionManager
import com.etesync.syncadapter.*
import com.etesync.syncadapter.ui.BaseActivity
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class CollectionActivity() : BaseActivity() {
    private lateinit var account: Account
    private lateinit var colUid: String
    private val model: AccountCollectionViewModel by viewModels()
    private val itemsModel: ItemsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!
        colUid = intent.extras!!.getString(EXTRA_COLLECTION_UID)!!

        setContentView(R.layout.etebase_collection_activity)

        if (savedInstanceState == null) {
            model.loadCollection(this, account, colUid)
            model.observe(this) {
                itemsModel.loadItems(it)
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, ViewCollectionFragment())
                    .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private val EXTRA_ACCOUNT = "account"
        private val EXTRA_COLLECTION_UID = "collectionUid"

        fun newIntent(context: Context, account: Account, colUid: String): Intent {
            val intent = Intent(context, CollectionActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            intent.putExtra(EXTRA_COLLECTION_UID, colUid)
            return intent
        }
    }
}

class AccountCollectionViewModel : ViewModel() {
    private val collection = MutableLiveData<AccountCollectionHolder>()

    fun loadCollection(context: Context, account: Account, colUid: String) {
        doAsync {
            val settings = AccountSettings(context, account)
            val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
            val etebase = EtebaseLocalCache.getEtebase(context, HttpClient.sharedClient, settings)
            val colMgr = etebase.collectionManager
            val cachedCollection = synchronized(etebaseLocalCache) {
                etebaseLocalCache.collectionGet(colMgr, colUid)!!
            }
            uiThread {
                collection.value = AccountCollectionHolder(
                        etebaseLocalCache,
                        etebase,
                        colMgr,
                        cachedCollection
                )
            }
        }
    }

    fun observe(owner: LifecycleOwner, observer: (AccountCollectionHolder) -> Unit) =
            collection.observe(owner, observer)
}

data class AccountCollectionHolder(val etebaseLocalCache: EtebaseLocalCache, val etebase: com.etebase.client.Account, val colMgr: CollectionManager, val cachedCollection: CachedCollection)

class ItemsViewModel : ViewModel() {
    private val cachedItems = MutableLiveData<List<CachedItem>>()

    fun loadItems(accountCollectionHolder: AccountCollectionHolder) {
        doAsync {
            val col = accountCollectionHolder.cachedCollection.col
            val itemMgr = accountCollectionHolder.colMgr.getItemManager(col)
            val items = accountCollectionHolder.etebaseLocalCache.itemList(itemMgr, col.uid, withDeleted = true)
            uiThread {
                cachedItems.value = items
            }
        }
    }

    fun observe(owner: LifecycleOwner, observer: (List<CachedItem>) -> Unit) =
            cachedItems.observe(owner, observer)
}