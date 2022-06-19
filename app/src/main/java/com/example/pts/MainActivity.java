package com.example.pts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.List;

// ---------

import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_FINE_LOCATION = 99;
    private LocationCallback locationCallBack;
    TextView tv_updates, count;
    Switch sw_locationupdates;
    ListView lv;
    BroadcastReceiver mBroadcastReceiver;
    BluetoothAdapter bluetoothAdapter;
    Button add;
    ArrayList<String> detectedMacAddresses = new ArrayList<>();
    int numberOfPackages = 0;
    DatabaseReference rootDatabaseref;


    int MIN_UPDATE_INTERVAL = 5000;
    int UPDATE_INTERVAL = 15000;
    int BLUETOOTH_RESET = 60000;

    CountDownTimer timer = new CountDownTimer(BLUETOOTH_RESET, 1000) {
        @Override
        public void onTick(long l) {
        }
        @Override
        public void onFinish() {
            this.start();
            bluetoothAdapter.cancelDiscovery();
            bluetoothAdapter.startDiscovery();
            detectedMacAddresses = new ArrayList<>();
        }
    };

    @Override
    protected void onResume(){
        super.onResume();

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 4);
        }
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 3);
        }

        bluetoothAdapter.startDiscovery();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals(BluetoothDevice.ACTION_FOUND)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device.getAddress() != null){
                        Log.i("BLUETOOTH", device.getAddress());
                        detectedMacAddresses.add(device.getAddress());
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    // shutting down bluetooth services
    @Override
    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }

    //API for location services
    FusedLocationProviderClient fusedLocationProviderClient;

    //location request
    LocationRequest locationRequest;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialization

        // Bluetooth initialization
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // UI elements
        // lv = findViewById(R.id.mac_list);
        sw_locationupdates = findViewById(R.id.sw_locationsupdates);
        add = findViewById(R.id.add);
        count = findViewById(R.id.count);

        //database
        rootDatabaseref = FirebaseDatabase.getInstance().getReference();

        //timer for resetting bluetooth modules
        timer.start();

        //location request setup
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(MIN_UPDATE_INTERVAL);
        locationRequest.setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY);

        //getting location update
        locationCallBack = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                updateUIValues(locationResult.getLastLocation());
                updateDatabase(locationResult.getLastLocation());

            }
        };

        if(bluetoothAdapter == null){
            Toast.makeText(MainActivity.this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        }
        else{
            if(!bluetoothAdapter.isEnabled()){
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, 1);
            }
        }

        //GPS and cell tower switch

        sw_locationupdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sw_locationupdates.isChecked()) {
                    //turn on location tracking
                    startLocationUpdates();
                } else {
                    //turn off location tracking
                    stopLocationUpdates();
                }
            }
        });
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNewPackageActivity();
            }
        });

        updateGPS();
    }

    private void updateDatabase(Location location) {

        rootDatabaseref.child("parcel").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getValue() != null){
                    numberOfPackages = 0;
                    for (DataSnapshot ds : snapshot.getChildren()){
                        String id = ds.getKey();
                        String value = ds.getValue().toString();
                        String tmp_mac = ds.child("mac_address").getValue().toString();

                        if (macDetected(tmp_mac)){
                            // update db
                            Log.d("firebase", "DETECTED A PACKAGE: " + tmp_mac);
                            String time = Calendar.getInstance().getTime().toString();
                            rootDatabaseref.child("parcel").child(id).child("longitude").setValue(location.getLongitude());
                            rootDatabaseref.child("parcel").child(id).child("latitude").setValue(location.getLatitude());
                            rootDatabaseref.child("parcel").child(id).child("last_update").setValue(time);

                            rootDatabaseref.child("update").child(id).child(time).child("longitude").setValue(location.getLongitude());
                            rootDatabaseref.child("update").child(id).child(time).child("latitude").setValue(location.getLatitude());
                            rootDatabaseref.child("update").child(id).child(time).child("speed").setValue(location.getSpeed());

                            numberOfPackages++;
                         }
                    }
                    count.setText("Number of tracked packages: " + numberOfPackages);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private boolean macDetected(String tmp_mac) {
        for (int i = 0; i < detectedMacAddresses.size(); i++){
            if (detectedMacAddresses.get(i).equals(tmp_mac)){
                return true;
            }
        }
        return false;
    }

    private void openNewPackageActivity() {
        Intent intent = new Intent(this, NewPackage.class);
        startActivity(intent);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallBack, null);
    }


    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallBack);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){
            case PERMISSIONS_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    updateGPS();
                }
                else {
                    Toast.makeText(this,
                            "This app requires permission to be granted to work properly",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void updateGPS(){
        //get permissions
        // get current location
        //update  UI

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            // permissions already provided
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    updateUIValues(location);
                }
            });
        }
        else{
            // user needs to verify permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
            }
        }
    }

    private void updateUIValues(Location location) {
        return;
    }



}