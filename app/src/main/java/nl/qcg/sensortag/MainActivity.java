package nl.qcg.sensortag;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Sensortag";

    private TextView mTextMessage;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_start:
                    mTextMessage.setText(R.string.title_start);
                    open();
                    return true;
                case R.id.navigation_stop:
                    mTextMessage.setText(R.string.title_stop);
                    close();
                    return true;
                case R.id.navigation_reserve:
                    mTextMessage.setText(R.string.title_reserve);
                    return true;
            }
            return false;
        }
    };

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice rdev;
    private BluetoothGatt client;
    private BluetoothGattCharacteristic dataReg;
    private BluetoothGattCharacteristic enableReg;
    private BluetoothGattCharacteristic notifyReg;
    private BluetoothGattCharacteristic periodReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextMessage = findViewById(R.id.message);
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        open();
    }

    @Override
    protected void onDestroy() {
        close();
        super.onDestroy();
    }

    private void close() {
        Log.e(TAG,"close");

        if (client == null) {
            return;
        }
        client.close();
        client = null;
    }

    private void open()
    {
        close(); // to be sure.

        Log.e(TAG,"open");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if( bluetoothManager != null ) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Log.e(TAG,"BT disabled");
            return ;
        }

        // your mac address
        rdev = bluetoothAdapter.getRemoteDevice("xx:xx:xx:xx:xx:xx");

        BluetoothGattCallback callback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch( newState ) {
                    case BluetoothProfile.STATE_CONNECTED :
                        Log.e(TAG,"connected");
                        if( !gatt.discoverServices() ) {
                            Log.e("","start discovery failed");
                        }
                        break ;
                    case BluetoothProfile.STATE_DISCONNECTED :
                        Log.e(TAG,"disconnected");
                        break ;

                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status)
            {
                if( status !=  BluetoothGatt.GATT_SUCCESS ) {
                    Log.e(TAG,"discovery failed");
                    return ;
                }
                register( gatt.getServices() );

                state = 0 ;
                next();
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if( status == BluetoothGatt.GATT_SUCCESS ) {
                    //characteristic.getValue();
                    Log.e(TAG,"read sucess "+characteristic.getUuid()+" "+ Arrays.toString(characteristic.getValue()));
                    next();
                } else {
                    Log.e(TAG,"read fail "+characteristic.getUuid());
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if( status == BluetoothGatt.GATT_SUCCESS ) {
                    Log.e(TAG,"write sucess "+characteristic.getUuid()+" "+ Arrays.toString(characteristic.getValue()));
                    next();
                } else {
                    Log.e(TAG,"write fail "+characteristic.getUuid());
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.e(TAG,"changed: "+characteristic.getUuid()+" "+ Arrays.toString(characteristic.getValue()));
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            }
        };

        client = rdev.connectGatt(this, true, callback);

        client.connect();
    }

    private int state = 0 ;

    private void next()
    {
        switch ( state ) {
            case 0 :
                state++ ;
                enableReg.setValue(new byte[]{0x1});
                client.writeCharacteristic(enableReg);
                break ;
            case 1 :
                state++ ;
                periodReg.setValue(new byte[]{(byte)200});
                client.writeCharacteristic(periodReg);
                break ;
            case 2 :
                state++ ;
                client.setCharacteristicNotification(dataReg,true);
                BluetoothGattDescriptor desc = dataReg.getDescriptor(notifyDesc2902UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                client.writeDescriptor(desc);
                break;
            case 3 :
                state++ ;
                break;
        }

    }

    private UUID dataRegUUID = UUID.fromString("f000aa01-0451-4000-b000-000000000000");
    private UUID enableRegUUID = UUID.fromString("f000aa02-0451-4000-b000-000000000000");
    private UUID periodRegUUID = UUID.fromString("f000aa03-0451-4000-b000-000000000000");

    private UUID notifyDesc2902UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private void register(List<BluetoothGattService> services)
    {
        for( BluetoothGattService svc : services ) {
            Log.e(TAG,"svc: "+svc+" "+svc.getUuid());
            for( BluetoothGattCharacteristic characteristic : svc.getCharacteristics() ) {
                Log.e(TAG,"ch: "+characteristic+" "+characteristic.getUuid());

                UUID uuid = characteristic.getUuid();
                if( uuid.equals(dataRegUUID) ) {
                    dataReg = characteristic;
                    Log.e(TAG,"data reg found");
                }
                if( uuid.equals(enableRegUUID) ) {
                    enableReg = characteristic;
                    Log.e(TAG,"enable reg found");
                }
                if( uuid.equals(periodRegUUID) ) {
                    periodReg = characteristic;
                    Log.e(TAG,"period reg found");
                }

                for( BluetoothGattDescriptor desc : characteristic.getDescriptors() ) {
                    Log.e(TAG,"  desc: "+desc+" "+desc.getUuid());
                }
                //chara.getUuid().equals();;
            }
        }
    }
}
