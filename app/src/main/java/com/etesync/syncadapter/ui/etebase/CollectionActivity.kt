package com.etesync.syncadapter.ui.etebase

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.etebase.client.CollectionManager
import com.etebase.client.ItemMetadata
import com.etesync.syncadapter.*
import com.etesync.syncadapter.ui.BaseActivity
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class CollectionActivity() : BaseActivity() {
    private lateinit var account: Account
    private val model: AccountViewModel by viewModels()
    private val collectionModel: CollectionViewModel by viewModels()
    private val itemsModel: ItemsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!
        val colUid = intent.extras!!.getString(EXTRA_COLLECTION_UID)
        val colType = intent.extras!!.getString(EXTRA_COLLECTION_TYPE)

        setContentView(R.layout.etebase_fragment_activity)

        if (savedInstanceState == null) {
            model.loadAccount(this, account)
            if (colUid != null) {
                model.observe(this) {
                    collectionModel.loadCollection(it, colUid)
                    collectionModel.observe(this) { cachedCollection ->
                        itemsModel.loadItems(it, cachedCollection)
                    }
                }
                supportFragmentManager.commit {
                    replace(R.id.fragment_container, ViewCollectionFragment())
                }
            } else if (colType != null) {
                model.observe(this) {
                    doAsync {
                        val meta = ItemMetadata()
                        meta.name = ""
                        val cachedCollection = CachedCollection(it.colMgr.create(colType, meta, ""), meta, colType)
                        uiThread {
                            supportFragmentManager.commit {
                                replace(R.id.fragment_container, EditCollectionFragment.newInstance(cachedCollection, true))
                            }
                        }
                    }
                }
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private val EXTRA_ACCOUNT = "account"
        private val EXTRA_COLLECTION_UID = "collectionUid"
        private val EXTRA_COLLECTION_TYPE = "collectionType"

        fun newIntent(context: Context, account: Account, colUid: String): Intent {
            val intent = Intent(context, CollectionActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            intent.putExtra(EXTRA_COLLECTION_UID, colUid)
            return intent
        }

        fun newCreateCollectionIntent(context: Context, account: Account, colType: String): Intent {
            val intent = Intent(context, CollectionActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            intent.putExtra(EXTRA_COLLECTION_TYPE, colType)
            return intent
        }
    }
}

class AccountViewModel : ViewModel() {
    private val holder = MutableLiveData<AccountHolder>()

    fun loadAccount(context: Context, account: Account) {
        doAsync {
            val settings = AccountSettings(context, account)
            val etebaseLocalCache = EtebaseLocalCache.getInstance(context, account.name)
            val httpClient = HttpClient.Builder(context).setForeground(true).build().okHttpClient
            val etebase = EtebaseLocalCache.getEtebase(context, httpClient, settings)
            val colMgr = etebase.collectionManager
            uiThread {
                holder.value = AccountHolder(
                        account,
                        etebaseLocalCache,
                        etebase,
                        colMgr
                )
            }
        }
    }

    fun observe(owner: LifecycleOwner, observer: (AccountHolder) -> Unit) =
            holder.observe(owner, observer)

    val value: AccountHolder?
        get() = holder.value
}

data class AccountHolder(val account: Account, val etebaseLocalCache: EtebaseLocalCache, val etebase: com.etebase.client.Account, val colMgr: CollectionManager)

class CollectionViewModel : ViewModel() {
    private val collection = MutableLiveData<CachedCollection>()

    fun loadCollection(accountHolder: AccountHolder, colUid: String) {
        doAsync {
            val etebaseLocalCache = accountHolder.etebaseLocalCache
            val colMgr = accountHolder.colMgr
            val cachedCollection = synchronized(etebaseLocalCache) {
                etebaseLocalCache.collectionGet(colMgr, colUid)!!
            }
            uiThread {
                collection.value = cachedCollection
            }
        }
    }

    fun observe(owner: LifecycleOwner, observer: (CachedCollection) -> Unit) =
            collection.observe(owner, observer)

    val value: CachedCollection?
        get() = collection.value
}

class ItemsViewModel : ViewModel() {
    private val cachedItems = MutableLiveData<List<CachedItem>>()

    fun loadItems(accountCollectionHolder: AccountHolder, cachedCollection: CachedCollection) {
        doAsync {
            val col = cachedCollection.col
            val itemMgr = accountCollectionHolder.colMgr.getItemManager(col)
            val items = accountCollectionHolder.etebaseLocalCache.itemList(itemMgr, col.uid, withDeleted = true)
            uiThread {
                cachedItems.value = items
            }
        }
    }

    fun observe(owner: LifecycleOwner, observer: (List<CachedItem>) -> Unit) =
            cachedItems.observe(owner, observer)

    val value: List<CachedItem>?
        get() = cachedItems.value
}


class LoadingViewModel : ViewModel() {
    private val loading = MutableLiveData<Boolean>()

    fun setLoading(value: Boolean) {
        loading.value = value
    }

    fun observe(owner: LifecycleOwner, observer: (Boolean) -> Unit) =
            loading.observe(owner, observer)
}