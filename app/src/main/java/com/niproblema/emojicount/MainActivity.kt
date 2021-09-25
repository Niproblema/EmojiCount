package com.niproblema.emojicount

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import androidx.emoji2.text.EmojiSpan
import androidx.emoji2.widget.EmojiEditText
import android.provider.ContactsContract

import android.content.ContentUris
import android.database.Cursor
import android.text.method.ScrollingMovementMethod


class MainActivity : AppCompatActivity() {
    // Chat spinner
    lateinit var chatSelection: Spinner
    lateinit var selectionAdapter: ArrayAdapter<String>
    var threadIDs: ArrayList<String> = ArrayList()
    var threadIDSelection : String? = null

    // Analyze chats
    private lateinit var chatAnalyzeButton: Button

    // Message count label
    private lateinit var messageCountTextView : TextView

    // Permission dialog
    private lateinit var customPermissionListener: PermissionListener

    // Panels
    private lateinit var analysisPanel1: EmojiEditText
    private lateinit var analysisPanel2: EmojiEditText

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
                threadIDSelection  = threadIDs.getOrNull(position)
                countMessages(threadIDSelection)
            }
        }
        messageCountTextView = findViewById(R.id.message_count_textView)

        analysisPanel1 = findViewById(R.id.analysis_panel_1)
        analysisPanel2 = findViewById(R.id.analysis_panel_2)
        analysisPanel1.setOnScrollChangeListener { _, x, y, _, _ ->
            analysisPanel2.scrollTo(x, y)
        }
        analysisPanel2.setOnScrollChangeListener{ _, x, y, _, _ ->
            analysisPanel1.scrollTo(x, y)
        }

        chatAnalyzeButton = findViewById(R.id.reload_conversations_button)
        chatAnalyzeButton.setOnClickListener { _ ->
            val selectedID = threadIDSelection
            if (!selectedID.isNullOrEmpty()) {
                populateResultView(analyseConversation(selectedID))
            }else{
                Toast.makeText(this, "No conversation selected.", Toast.LENGTH_LONG).show()
            }
        }

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
            Uri.parse("content://mms-sms/conversations?simple=true"),   // Uri.parse("content://mms-sms/conversations"),
            null,                                                      // arrayOf(Telephony.Sms.Inbox.THREAD_ID, Telephony.Sms.Inbox.ADDRESS),
            null,
            null,
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        );

        for (i in 0 until dataCursor!!.getColumnCount()) {
            Log.w("column names", dataCursor.getColumnName(i).toString())
        }

        if (dataCursor != null) {
            if (dataCursor.moveToFirst()) {
                do {
                    convoAddresses.add(getContactByRecipientId( dataCursor.getString(dataCursor.getColumnIndex("recipient_ids")).toLong()))
                    threadIDs.add(dataCursor.getString(dataCursor.getColumnIndex(Telephony.Sms._ID)))
                } while (dataCursor.moveToNext())
            } else if (manual) {
                Toast.makeText(this, "No conversations found.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Could not load text conversations.", Toast.LENGTH_LONG).show()
        }
        selectionAdapter.addAll(convoAddresses)
    }

    private fun countMessages(threadID: String?) {
        if(threadID.isNullOrEmpty()){
            messageCountTextView.setText(resources.getText(R.string.message_count_label_text))
        }else {
            val (inbound, outbound) = FetchConversationMessages(threadID)
            messageCountTextView.setText(
                resources.getText(R.string.message_count_label_text)
                    .toString() + " Received: " + inbound.size + " Sent: " + outbound.size
            )
        }
    }

    @SuppressLint("RestrictedApi", "Range")
    private fun analyseConversation(threadID: String) : List<EmojiEntry> {
        val (inbound, outbound) = FetchConversationMessages(threadID)

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

    @SuppressLint("RestrictedApi", "Range")
    private fun FetchConversationMessages(threadID: String): Pair<ArrayList<String>, ArrayList<String>> {
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

        Log.i("COUNT", "Inbound: " + inbound.count());
        Log.i("COUNT", "Outbound: " + outbound.count());
        return Pair(inbound, outbound)
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

    private fun getContactByRecipientId(recipientId: Long): String {
        var contact: String = ""
        val uri = ContentUris.withAppendedId(
            Uri.parse("content://mms-sms/canonical-address"),
            recipientId
        )
        val cursor: Cursor = contentResolver.query(uri, null, null, null, null)
            ?: return "UnknownID: $recipientId"

        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                contact = getContactByPhoneNumber(cursor.getString(0))
            }
        }
        return contact
    }


    @SuppressLint("Range")
    private fun getContactByPhoneNumber(phoneNumber: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NORMALIZED_NUMBER
        )
        val cursor: Cursor = contentResolver.query(uri, projection, null, null, null) ?: return phoneNumber
        var name: String? = null
        var nPhoneNumber = phoneNumber
        try {
            if (cursor.moveToFirst()) {
                //nPhoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.NORMALIZED_NUMBER))
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        } finally {
            cursor.close()
        }
        if(name != null){
            return name;
        }else{
            return nPhoneNumber
        }
    }
}