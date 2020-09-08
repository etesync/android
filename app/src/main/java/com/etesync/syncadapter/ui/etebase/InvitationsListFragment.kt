package com.etesync.syncadapter.ui.etebase

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.ListFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.etebase.client.CollectionAccessLevel
import com.etebase.client.FetchOptions
import com.etebase.client.SignedInvitation
import com.etebase.client.Utils
import com.etesync.syncadapter.R
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import java.util.concurrent.Future


class InvitationsListFragment : ListFragment(), AdapterView.OnItemClickListener {
    private val model: AccountViewModel by activityViewModels()
    private val invitationsModel: InvitationsViewModel by viewModels()

    private var emptyTextView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.invitations_list, container, false)

        //This is instead of setEmptyText() function because of Google bug
        //See: https://code.google.com/p/android/issues/detail?id=21742
        emptyTextView = view.findViewById<TextView>(android.R.id.empty)
        return view
    }

    private fun setListAdapterInvitations(invitations: List<SignedInvitation>) {
        val context = context
        if (context != null) {
            val listAdapter = InvitationsListAdapter(context)
            setListAdapter(listAdapter)

            listAdapter.addAll(invitations)

            emptyTextView!!.setText(R.string.invitations_list_empty)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.observe(this) {
            invitationsModel.loadInvitations(it)
        }

        invitationsModel.observe(this) {
            setListAdapterInvitations(it)
        }

        listView.onItemClickListener = this
    }

    override fun onDestroyView() {
        super.onDestroyView()

        invitationsModel.cancelLoad()
    }

    override fun onItemClick(parent: AdapterView<*>, view_: View, position: Int, id: Long) {
        val invitation = listAdapter?.getItem(position) as SignedInvitation
        val fingerprint = Utils.prettyFingerprint(invitation.fromPubkey)
        val view = layoutInflater.inflate(R.layout.invitation_alert_dialog, null)
        view.findViewById<TextView>(R.id.body).text = getString(R.string.invitations_accept_reject_dialog)
        view.findViewById<TextView>(R.id.fingerprint).text = fingerprint

        AlertDialog.Builder(requireContext())
                .setTitle(R.string.invitations_title)
                .setIcon(R.drawable.ic_email_black)
                .setView(view)
                .setNegativeButton(R.string.invitations_reject) { dialogInterface, i ->
                    invitationsModel.reject(model.value!!, invitation)
                }
                .setPositiveButton(R.string.invitations_accept) { dialogInterface, i ->
                    invitationsModel.accept(model.value!!, invitation)
                }
                .show()
        return
    }

    internal inner class InvitationsListAdapter(context: Context) : ArrayAdapter<SignedInvitation>(context, R.layout.invitations_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            var v = _v
            if (v == null)
                v = LayoutInflater.from(context).inflate(R.layout.invitations_list_item, parent, false)

            val invitation = getItem(position)!!

            val tv = v!!.findViewById<View>(R.id.title) as TextView
            // FIXME: Should have a sensible string here
            tv.text = "Invitation ${position}"

            // FIXME: Also mark admins
            val readOnly = v.findViewById<View>(R.id.read_only)
            readOnly.visibility = if (invitation.accessLevel == CollectionAccessLevel.ReadOnly) View.VISIBLE else View.GONE

            return v
        }
    }
}

class InvitationsViewModel : ViewModel() {
    private val invitations = MutableLiveData<List<SignedInvitation>>()
    private var asyncTask: Future<Unit>? = null

    fun loadInvitations(accountCollectionHolder: AccountHolder) {
        asyncTask = doAsync {
            val ret = LinkedList<SignedInvitation>()
            val invitationManager = accountCollectionHolder.etebase.invitationManager
            var iterator: String? = null
            var done = false
            while (!done) {
                val chunk = invitationManager.listIncoming(FetchOptions().iterator(iterator).limit(30))
                iterator = chunk.stoken
                done = chunk.isDone

                ret.addAll(chunk.data)
            }

            uiThread {
                invitations.value = ret
            }
        }
    }

    fun accept(accountCollectionHolder: AccountHolder, invitation: SignedInvitation) {
        doAsync {
            val invitationManager = accountCollectionHolder.etebase.invitationManager
            invitationManager.accept(invitation)
            val ret = invitations.value!!.filter { it != invitation }

            uiThread {
                invitations.value = ret
            }
        }
    }

    fun reject(accountCollectionHolder: AccountHolder, invitation: SignedInvitation) {
        doAsync {
            val invitationManager = accountCollectionHolder.etebase.invitationManager
            invitationManager.reject(invitation)
            val ret = invitations.value!!.filter { it != invitation }

            uiThread {
                invitations.value = ret
            }
        }
    }

    fun cancelLoad() {
        asyncTask?.cancel(true)
    }

    fun observe(owner: LifecycleOwner, observer: (List<SignedInvitation>) -> Unit) =
            invitations.observe(owner, observer)
}