package com.etesync.syncadapter.ui.etebase

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.etebase.client.Collection
import com.etebase.client.FetchOptions
import com.etebase.client.ItemMetadata
import com.etebase.client.exceptions.EtebaseException
import com.etesync.syncadapter.Constants.*
import com.etesync.syncadapter.R
import com.etesync.syncadapter.syncadapter.requestSync
import com.etesync.syncadapter.ui.BaseActivity
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class NewAccountWizardActivity : BaseActivity() {
    private lateinit var account: Account
    private val model: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!

        setContentView(R.layout.etebase_collection_activity)

        if (savedInstanceState == null) {
            setTitle(R.string.account_wizard_collections_title)
            model.loadAccount(this, account)
            supportFragmentManager.commit {
                replace(R.id.fragment_container, WizardCheckFragment())
            }
        }
    }

    companion object {
        private val EXTRA_ACCOUNT = "account"

        fun newIntent(context: Context, account: Account): Intent {
            val intent = Intent(context, NewAccountWizardActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, account)
            return intent
        }
    }
}


fun reportErrorHelper(context: Context, e: Throwable) {
    AlertDialog.Builder(context)
            .setIcon(R.drawable.ic_info_dark)
            .setTitle(R.string.exception)
            .setMessage(e.localizedMessage)
            .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
}

class WizardCheckFragment : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val loadingModel: LoadingViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.account_wizard_check, container, false)

        if (savedInstanceState == null) {
            if (container != null) {
                initUi(inflater, ret)
                model.observe(this, {
                    checkAccountInit()
                })
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View) {
        val button = v.findViewById<Button>(R.id.button_retry)
        val progress = v.findViewById<ProgressBar>(R.id.loading)
        button.setOnClickListener {
            checkAccountInit()
        }
        loadingModel.observe(this, {
            if (it) {
                progress.visibility = View.VISIBLE
                button.visibility = View.GONE
            } else {
                progress.visibility = View.GONE
                button.visibility = View.VISIBLE
            }
        })
    }

    private fun checkAccountInit() {
        val colMgr = model.value?.colMgr ?: return
        loadingModel.setLoading(true)
        doAsync {
            try {
                val collections = colMgr.list(COLLECTION_TYPES, FetchOptions().limit(1))
                uiThread {
                    if (collections.data.size > 0) {
                        activity?.finish()
                    } else {
                        parentFragmentManager.commit {
                            replace(R.id.fragment_container, WizardFragment())
                        }
                    }
                }
            } catch (e: Exception) {
                uiThread {
                    reportErrorHelper(requireContext(), e)
                }
            } finally {
                uiThread {
                    loadingModel.setLoading(false)
                }
            }
        }
    }
}

class WizardFragment : Fragment() {
    private val model: AccountViewModel by activityViewModels()
    private val loadingModel: LoadingViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ret = inflater.inflate(R.layout.account_wizard_collections, container, false)

        if (savedInstanceState == null) {
            if (container != null) {
                initUi(inflater, ret)
            }
        }

        return ret
    }

    private fun initUi(inflater: LayoutInflater, v: View) {
        v.findViewById<Button>(R.id.button_create).setOnClickListener {
            createCollections()
        }

        v.findViewById<Button>(R.id.button_skip).setOnClickListener {
            activity?.finish()
        }

        val buttons = v.findViewById<View>(R.id.buttons_holder)
        val progress = v.findViewById<ProgressBar>(R.id.loading)
        loadingModel.observe(this, {
            if (it) {
                progress.visibility = View.VISIBLE
                buttons.visibility = View.GONE
            } else {
                progress.visibility = View.GONE
                buttons.visibility = View.VISIBLE
            }
        })
    }

    private fun createCollections() {
        val accountHolder = model.value ?: return
        val colMgr = accountHolder.colMgr
        loadingModel.setLoading(true)

        doAsync {
            try {
                val baseMeta = listOf(
                    Pair(ETEBASE_TYPE_ADDRESS_BOOK, "My Contacts"),
                    Pair(ETEBASE_TYPE_CALENDAR, "My Calendar"),
                    Pair(ETEBASE_TYPE_TASKS, "My Tasks"),
                )

                baseMeta.forEach {
                    val meta = ItemMetadata()
                    meta.name = it.second
                    meta.mtime = System.currentTimeMillis()

                    val col = colMgr.create(it.first, meta, "")
                    uploadCollection(accountHolder, col)
                }
                requestSync(requireContext(), accountHolder.account)
                activity?.finish()
            } catch (e: EtebaseException) {
                uiThread {
                    reportErrorHelper(requireContext(), e)
                }
            } finally {
                uiThread {
                    loadingModel.setLoading(false)
                }
            }
        }
    }

    private fun uploadCollection(accountHolder: AccountHolder, col: Collection) {
        val etebaseLocalCache = accountHolder.etebaseLocalCache
        val colMgr = accountHolder.colMgr
        colMgr.upload(col)
        synchronized(etebaseLocalCache) {
            etebaseLocalCache.collectionSet(colMgr, col)
        }
    }
}