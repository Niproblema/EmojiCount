package com.niproblema.emojicount

import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import java.lang.StringBuilder


class MainActivity : AppCompatActivity() {
    // Chat spinner
    lateinit var chatSelection: Spinner
    lateinit var selectionAdapter: ArrayAdapter<String>
    var threadIDs: ArrayList<String> = ArrayList()

    // Refresh chats
    lateinit var chatRefreshButton: Button

    // Permission dialog
    lateinit var customPermissionListener: PermissionListener

    // Panels
    lateinit var analysisPanel1: EditText
    lateinit var analysisPanel2: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatSelection = findViewById(R.id.chat_selection_spinner)
        selectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item)
        chatSelection.adapter = selectionAdapter
        chatSelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val name = threadIDs.getOrNull(position)
                if (!name.isNullOrEmpty()) {
                    analyseConversation(name)
                }
            }
        }

        analysisPanel1 = findViewById(R.id.analysis_panel_1)
        analysisPanel2 = findViewById(R.id.analysis_panel_2)

        chatRefreshButton = findViewById(R.id.reload_conversations_button)
        chatRefreshButton.setOnClickListener({ _ -> reloadChats(true) })

        var readSMSPermissionListener = DialogOnDeniedPermissionListener.Builder
            .withContext(this)
            .withTitle("Read Text Message Permission")
            .withMessage("Permission is required to read Text messages")
            .build();

        customPermissionListener = PermissionListenerImpl(
            { _ -> reloadChats(false) },
            { p0 -> readSMSPermissionListener.onPermissionDenied(p0) },
            { p1, p2 -> readSMSPermissionListener.onPermissionRationaleShouldBeShown(p1, p2) }
        )
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
    }

    private fun checkPermissions() {
        Dexter
            .withContext(this)
            .withPermission(android.Manifest.permission.READ_SMS)
            .withListener(customPermissionListener)
            .onSameThread()
            .check();
    }


    private fun reloadChats(manual: Boolean) {
        selectionAdapter.clear()
        threadIDs.clear()

        var convoAddresses = ArrayList<String>()

        val dataCursor = contentResolver.query(
            Uri.parse("content://mms-sms/conversations"),
            arrayOf(Telephony.Sms.Inbox.THREAD_ID, Telephony.Sms.Inbox.ADDRESS),
            null,
            null,
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        );

        if (dataCursor != null) {
            if (dataCursor.moveToFirst()) {
                do {
                    convoAddresses.add(dataCursor.getString(dataCursor.getColumnIndex(Telephony.Sms.ADDRESS)))
                    threadIDs.add(dataCursor.getString(dataCursor.getColumnIndex(Telephony.Sms.THREAD_ID)))
                } while (dataCursor.moveToNext())
            } else if (manual) {
                Toast.makeText(this, "No conversations found.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Could not load text conversations.", Toast.LENGTH_LONG).show()
        }
        selectionAdapter.addAll(convoAddresses)
    }


    private fun analyseConversation(threadID: String) {
        val dataCursor = contentResolver.query(
            Uri.parse("content://sms/conversations/" + threadID),   // TODO: add MMS also
            arrayOf(Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.TYPE),
            null,
            null,
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        );

        var inbound = ArrayList<String>()
        var outbound = ArrayList<String>()

        if (dataCursor != null) {
            if (dataCursor.moveToFirst()) {
                do {
                    when (dataCursor.getInt(dataCursor.getColumnIndex(Telephony.Sms.TYPE))) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> inbound.add(
                            dataCursor.getString(
                                dataCursor.getColumnIndex(Telephony.Sms.BODY)
                            )
                        )
                        Telephony.Sms.MESSAGE_TYPE_SENT -> outbound.add(
                            dataCursor.getString(
                                dataCursor.getColumnIndex(Telephony.Sms.BODY)
                            )
                        )
                    }


                } while (dataCursor.moveToNext())
            } else {
                Toast.makeText(this, "No conversations found.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Could not load conversation data.", Toast.LENGTH_LONG).show()
        }

        println("Inbound: "+ inbound.count())
        println("Outbound: "+ outbound.count())
    }

}