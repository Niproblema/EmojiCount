package com.niproblema.emojicount

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import java.util.jar.Manifest


class MainActivity : AppCompatActivity() {

    lateinit var chatSelection : Spinner
    lateinit var selectionAdapter: ArrayAdapter<String>
    lateinit var readSMSPermissionListener : PermissionListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatSelection = findViewById(R.id.chat_selection_spinner)
        selectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item )
        chatSelection.adapter = selectionAdapter

//        readSMSPermissionListener  = PermissionListener
//        {
//
//        };
        readSMSPermissionListener = DialogOnDeniedPermissionListener.Builder
            .withContext(this)
            .withTitle("Read Text Message Permission")
            .withMessage("Permission is required to read Text messages")
            .build();

        Permiss


    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
        reloadChats(false)
    }

    private fun checkPermissions(){
        Dexter
            .withContext(this)
            .withPermission(android.Manifest.permission.READ_SMS)
            .withListener(readSMSPermissionListener)
            .onSameThread()
            .check();
    }


    private fun reloadChats(manual : Boolean){
        val cursor = contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null);
        selectionAdapter.clear()

        if (cursor != null) {
            if(cursor.moveToFirst()){
                do{
                    selectionAdapter.add(cursor.toString())
                }while(cursor.moveToNext())
            }else if(manual){
                Toast.makeText(this, "No conversations found.", Toast.LENGTH_LONG).show();
            }
        }else{
            Toast.makeText(this, "Could not load text conversations.", Toast.LENGTH_LONG).show()
        }
    }

}