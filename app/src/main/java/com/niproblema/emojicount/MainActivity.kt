package com.niproblema.emojicount

import android.annotation.SuppressLint
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.provider.FontRequest
import androidx.core.text.getSpans
import androidx.core.view.get
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.FontRequestEmojiCompatConfig
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import java.lang.StringBuilder
import androidx.emoji2.text.EmojiSpan
import androidx.emoji2.widget.EmojiEditText

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
    lateinit var analysisPanel1: EmojiEditText
    lateinit var analysisPanel2: EmojiEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)

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
                    val result = analyseConversation(name)
                    populateResultView(result)
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


    @SuppressLint("Range")
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


    @SuppressLint("RestrictedApi", "Range")
    private fun analyseConversation(threadID: String) : List<EmojiEntry> {
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

        Log.i("COUNT", "Inbound: "+ inbound.count());
        Log.i("COUNT", "Outbound: "+ outbound.count());

        val occuranceMap = HashMap<Int, EmojiEntry>()
        val emojiCompatInstance = EmojiCompat.get()

        for(inText in inbound){
            val processed = emojiCompatInstance.process(inText, 0, inText.length-1, Integer.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_ALL)
            if(processed is Spannable){
                val spans = processed.getSpans(0, processed.length, EmojiSpan::class.java)
                for (span in spans){
                    var emojiText = processed.subSequence(processed.getSpanStart(span), processed.getSpanEnd(span))
                    if(!occuranceMap.containsKey(span.metadata.id)) {
                        occuranceMap[span.metadata.id] = EmojiEntry(span, emojiText);
                    }
                    occuranceMap[span.metadata.id]!!.CountIngoing++
                }
            }
        }

        for(outText in outbound){
            val processed = emojiCompatInstance.process(outText, 0, outText.length-1, Integer.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_ALL)
            if(processed is Spannable){
                val spans = processed.getSpans(0, processed.length, EmojiSpan::class.java)
                for (span in spans){
                    var emojiText = processed.subSequence(processed.getSpanStart(span), processed.getSpanEnd(span))
                    if(!occuranceMap.containsKey(span.metadata.id)) {
                        occuranceMap[span.metadata.id] = EmojiEntry(span, emojiText);
                    }
                    occuranceMap[span.metadata.id]!!.CountOutgoing++
                }
            }
        }
        Log.i("COUNT", "#Different emojies used: "+occuranceMap.size)
        return occuranceMap.values.sortedByDescending { it.CountOutgoing+it.CountIngoing }
    }

    private fun populateResultView(result: List<EmojiEntry>) {
        val ingoingResult = SpannableStringBuilder()
        val outgoingResult = SpannableStringBuilder()

        for(entry in result){
            ingoingResult.appendLine(entry.EmojiText.toString()+": " + entry.CountIngoing)
            outgoingResult.appendLine(entry.EmojiText.toString()+": " + entry.CountOutgoing)
        }

        analysisPanel1.text = ingoingResult;
        analysisPanel2.text = outgoingResult;
    }
}