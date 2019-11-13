package com.munditv.rsctest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Dave Smith
 * Double Encore, Inc.
 * RSCTestActivity
 */
public class RSCTestActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "RSCTestActivity";

    private static final String DEVICE_NAME = "Bracelet";

    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    private static final UUID UUID_RSC_RING = getUUID("1810");       // standard uuid for blood pressure by bluetooth gatt spec.
    private static final UUID UUID_GENERIC_ACCESS    = getUUID("1800");       // standard uuid for generic access by bluetooth gatt spec.
    private static final UUID UUID_GENERIC_ATTRIBUTE    = getUUID("1801");  // standard uuid for generic attribute by bluetooth gatt spec.
    private static final UUID UUID_DEVICE_INFORMATION    = getUUID("180a");  // standard uuid for device information by bluetooth gatt spec.
    
    private static final UUID UUID_GAP_DEVICE_NAME = getUUID("2a00");
    private static final UUID UUID_GAP_APPEARANCE = getUUID("2a01");
    private static final UUID UUID_GAP_PERIPHERAL_PRIVACY_FLAG = getUUID("2a02");
    private static final UUID UUID_GAP_RECONNECTION_ADDRESS = getUUID("2a03");
    private static final UUID UUID_GAP_PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS = getUUID("2a04");
    
    private static final UUID UUID_GATT_SERVICE_CHANGED = getUUID("2a05");
    
    private static final UUID UUID_DI_MANUFACTURER_NAME_STRING = getUUID("2a29");
    private static final UUID UUID_DI_MODEL_NUMBER_STRING = getUUID("2a24");
    private static final UUID UUID_DI_SERIAL_NUMBER_STRING = getUUID("2a25");
    private static final UUID UUID_DI_HARDWARE_REVISION_STRING = getUUID("2a27");
    private static final UUID UUID_DI_FIRMWARE_REVISION_STRING = getUUID("2a26");
    private static final UUID UUID_DI_SOFTWARE_REVISION_STRING = getUUID("2a28");
    private static final UUID UUID_DI_SYSTEM_ID = getUUID("2a23");
    private static final UUID UUID_DI_IEEE11073_20601_REGULATORY_CERTIFICATION_DATA_LIST = getUUID("2a2a");
    private static final UUID UUID_DI_PNP_ID = getUUID("2a50");
    
    private static final UUID UUID_RSC_SERVICE_1 = getUUID_TI("ff10");
    private static final UUID UUID_RSC_SERVICE_2 = getUUID_TI("ff00");
    private static final UUID UUID_RSC_CHARACTERIC_0 = getUUID_TI("ff11");
    private static final UUID UUID_RSC_CHARACTERIC_1 = getUUID_TI("ff01");
    private static final UUID UUID_RSC_CHARACTERIC_2 = getUUID_TI("ff02");
    private static final UUID UUID_RSC_CHARACTERIC_3 = getUUID_TI("ff03");
    private static final UUID UUID_RSC_CHARACTERIC_4 = getUUID_TI("ff04");
    private static final UUID UUID_RSC_CHARACTERIC_5 = getUUID_TI("ff05");
    private static final UUID UUID_RSC_CHARACTERIC_6 = getUUID_TI("ff06");
    private static final UUID UUID_RSC_CHARACTERIC_7 = getUUID_TI("ff07");
    private static final UUID UUID_RSC_CHARACTERIC_8 = getUUID_TI("ff08");
    private static final UUID UUID_RSC_CHARACTERIC_9 = getUUID_TI("ff09");
    
    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;

    private TextView mSteps, mDistance, mCalorie;
    private float steps_val, distance_val, calorie_val;
    private boolean threadFlag = false;

    //private ProgressDialog mProgress;

    private static final UUID getUUID(String u)
    {
    	String str = "0000"+u+"-0000-1000-8000-00805f9b34fb";
    	UUID uuid = UUID.fromString(str);
    	    	
    	return uuid;
    }
    
    private static final UUID getUUID_TI(String u)
    {
    	String str = "f000"+u+"-0451-4000-b000-000000000000";
    	UUID uuid = UUID.fromString(str);      
    	    	
    	return uuid;
    }
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);
        setProgressBarIndeterminate(true);

        /*
         * We are going to display the results in some text fields
         */
        mSteps = (TextView) findViewById(R.id.text_steps);
        mDistance = (TextView) findViewById(R.id.text_distance);
        mCalorie = (TextView) findViewById(R.id.text_calorie);

        /*
         * Bluetooth in Android 4.3 is accessed via the BluetoothManager, rather than
         * the old static BluetoothAdapter.getInstance()
         */
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();

        /*
         * A progress dialog will be needed while the connection process is
         * taking place
         */
        //mProgress = new ProgressDialog(this);
        //mProgress.setIndeterminate(true);
        //mProgress.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Make sure dialog is hidden
        //mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
        if(threadFlag) {
        	mThread.stop();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(threadFlag) {
        	mThread.stop();
        }
        //Disconnect from any active tag connection
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);
        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to "+device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                //Display progress UI
               // mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to "+device.getName()+"..."));
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDisplayValues() {
    	//mSteps, mDistance, mCalorie;
    	mSteps.setText("---");
    	mDistance.setText("---");
    	mCalorie.setText("---");
    }


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        mBluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500);
    }

    private void stopScan() {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    /* BluetoothAdapter.LeScanCallback */

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        /*
         * We are looking for SensorTag devices only, so validate the name
         * that each device reports before adding it to our collection
         */
        if (device.getName().contains(DEVICE_NAME)) {
            mDevices.put(device.hashCode(), device);
            //Update the overflow menu
            invalidateOptionsMenu();
        }
    }
    
    public void writeData() {
        BluetoothGattCharacteristic chara;
        BluetoothGattDescriptor desc ;
        Log.d(TAG, "writeData");
        BluetoothGattService service = mConnectedGatt.getService(UUID_RSC_SERVICE_2);     
        Log.d(TAG, "get UUID_RSC_CHARACTERIC_6"); 
        chara = service.getCharacteristic(UUID_RSC_CHARACTERIC_6);
        byte[] code = { 0x65, 0x74, 0x2D, 0x37 };
        chara.setValue(code);
        if( mConnectedGatt.writeCharacteristic(chara)) {
    	   Log.d(TAG, "write code="+ code);
        }
       /*
        chara = service.getCharacteristic(UUID_RSC_CHARACTERIC_9);
        mConnectedGatt.setCharacteristicNotificatio                       n(chara, true);
		desc = chara.getDescriptor(CONFIG_DESCRIPTOR);
		desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE );
		mConnectedGatt.writeDescriptor(desc);
       */
    }

    public void getData() {
        BluetoothGattCharacteristic chara;
        BluetoothGattDescriptor desc ;
        
        BluetoothGattService service = mConnectedGatt.getService(UUID_RSC_SERVICE_2);     
        Log.d(TAG, "get UUID_RSC_CHARACTERIC_7 : " + UUID_RSC_CHARACTERIC_7 ); 
        chara = service.getCharacteristic(UUID_RSC_CHARACTERIC_7);
        mConnectedGatt.readCharacteristic(chara);
    }
        
    public Thread mThread = new Thread() {
        @Override
        public void run() {
        	threadFlag = true;
            try {
            	Log.d(TAG, "thread run!");
                while(true) {
                    sleep(2000);
                	getData();
                }   
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    /*
     * In this callback, we've created a bit of a state machine to enforce that only
     * one characteristic be read or written at a time until all of our sensors
     * are enabled and we are registered to get notifications.
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;

        private void reset() { mState = 0; }

        private void advance() { mState++; }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
            }
        }

        private List<BluetoothGattService>  services;
        private List<BluetoothGattCharacteristic> charas;
        private boolean flag_callback = false;
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered: "+status);
            //mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            reset();
            services =  gatt.getServices();
            for(BluetoothGattService service : services) 
            {
            	  UUID uuid = service.getUuid();
            	  Log.d(TAG, "onServicesDiscovered service uuid=" + uuid);
            	  charas = service.getCharacteristics();
            	  for(BluetoothGattCharacteristic chara : charas) 
            	  {
            		  UUID u=chara.getUuid();
            		  Log.d(TAG, "onServicesDiscovered characteristic uuid=" + u);
            		  if(u.equals(UUID_RSC_CHARACTERIC_6)) {
                  		  gatt.readCharacteristic(chara);
            		  }
            	  }
            }
         }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            Log.d(TAG, "onCharacteristicRead");
            if(UUID_RSC_CHARACTERIC_7.equals(characteristic.getUuid())) {
            	int field1 =  characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            	int field2 =  characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 4);
            	float field3 = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 8)/10;
            	float field4 = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 12)/10;
            	Log.d(TAG, "field1 = " + field1);
            	Log.d(TAG, "field2 = " + field2);
            	Log.d(TAG, "field3 = " + field3);
            	Log.d(TAG, "field3 = " + field4);          
            	steps_val = field2;
            	distance_val = field3;
            	calorie_val = field4;
            	mHandler.sendMessage(Message.obtain(null, MSG_DATA_UPDATE, characteristic));
            }
            if(UUID_RSC_CHARACTERIC_6.equals(characteristic.getUuid())) {
                writeData();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //After writing the enable flag, next we read the initial value
            //readNextSensor(gatt);
        	mThread.start();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */
        	Log.d(TAG, "onCharacteristicChanged");
            if(UUID_RSC_CHARACTERIC_7.equals(characteristic.getUuid())) {
            	int field1 =  characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            	int field2 =  characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 4);
            	float field3 = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 8)/10;
            	float field4 = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 12)/10;
            	Log.d(TAG, "field1 = " + field1);
            	Log.d(TAG, "field2 = " + field2);
            	Log.d(TAG, "field3 = " + field3);
            	Log.d(TAG, "field3 = " + field4);          
            	steps_val = field2;
            	distance_val = field3;
            	calorie_val = field4;
            	mHandler.sendMessage(Message.obtain(null, MSG_DATA_UPDATE, characteristic));
          }
      }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
            //advance();
            //enableNextSensor(gatt);
        	Log.d(TAG, "onCharacteristicWrite");
        	getData();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };
    
    
    private static final int MSG__TIME_SYNC_UPDATE= 0;
    private static final int MSG_DATA_UPDATE= 1; 
   
    private Handler mHandler = new Handler() {
    	
        @Override
        public void handleMessage(Message msg) {
        	switch(msg.what) {
        	case MSG__TIME_SYNC_UPDATE:
        		updateDisplayValues();
        		break;
        	case MSG_DATA_UPDATE:
        		updateDisplayValues();
        		break;
        	}
        }
    };
    
    
    // display the values
    private void updateDisplayValues() {
    	String str1, str2, str3;
    	// steps_val, distance_val, calorie_val;
    	str1 = Float.toString(steps_val);
    	str2 = Float.toString(distance_val);
    	str3 = Float.toString(calorie_val);
    	// mSteps, mDistance, mCalorie;
    	mSteps.setText(str1);
    	mDistance.setText(str2);
    	mCalorie.setText(str3);
   	
    }
}
