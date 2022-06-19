package com.example.pts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.UnsupportedEncodingException;

public class NewPackage extends AppCompatActivity {

    public static final String error = "Scan the NFC chip to start tracking the package";
    public static final String success = "Enter Package Tracking ID to add the parcel to start tracking";

    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    IntentFilter writingTagFilters[];
    boolean writeMode;
    Tag myTag;
    Context context;
    DatabaseReference rootDatabaseref;

    Button cancel, add;
    TextView msg, mac;
    EditText enterID;
    String last_mac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_package);
        cancel = findViewById(R.id.cancel);
        add = findViewById(R.id.add);
        msg = findViewById(R.id.msg);
        mac = findViewById(R.id.test);
        enterID = findViewById(R.id.enterID);
        context = this;

        rootDatabaseref = FirebaseDatabase.getInstance().getReference();

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMainActivity();
            }
        });

        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String id = enterID.getText().toString();
                addNewParcel(id, last_mac);

            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter == null){
            Toast.makeText(this, "this device does not support NFC", Toast.LENGTH_SHORT).show();
        }
        readFromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writingTagFilters = new IntentFilter[] {tagDetected};
    }

    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        setIntent(intent);
        readFromIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    private void readFromIntent(Intent intent){
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
        ||NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
        ||NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)){
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsgs != null){
                Toast.makeText(this, "NFC detected", Toast.LENGTH_SHORT).show();
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++){
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs){
        if (msgs == null || msgs.length == 0) return;

        String text = "";
        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;

        try {
            text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);

        } catch (UnsupportedEncodingException e) {
            Log.e("UnsupportedEncoding", e.toString());
        }
        mac.setText("MAC address: " + text);
        last_mac = text;
        msg.setText(success);
        add.setEnabled(true);
        enterID.setEnabled(true);
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onPause(){
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTagFilters, null);
    }

    private void addNewParcel(String id, String mac){
        try {
             rootDatabaseref.child("parcel").child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                 @Override
                 public void onDataChange(@NonNull DataSnapshot snapshot) {
                     if (snapshot.getValue() != null) {
                         msg.setText("Tracking ID already exists, please enter different tracking ID");
                     }
                     else
                     {
                         rootDatabaseref.child("parcel").child(id).child("mac_address").setValue(mac);
                         rootDatabaseref.child("parcel").child(id).child("latitude").setValue("empty");
                         rootDatabaseref.child("parcel").child(id).child("longitude").setValue("empty");
                         rootDatabaseref.child("parcel").child(id).child("last_update").setValue("empty");
                         openMainActivity();
                     }
                 }
                 @Override
                 public void onCancelled(DatabaseError error){

                 }
             });




        }
        catch(Exception e){
            msg.setText("failed to upload to database");
        }
    }

}