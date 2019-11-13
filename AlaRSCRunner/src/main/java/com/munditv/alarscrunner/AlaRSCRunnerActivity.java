package com.munditv.alarscrunner;

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
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;


/**
 * Created by Dave Smith
 * Double Encore, Inc.
 * MainActivity
 */
public class AlaRSCRunnerActivity extends Activity 
										implements BluetoothAdapter.LeScanCallback, SurfaceHolder.Callback {
    private static final String TAG = "RSCRunnerActivity";

    private static final String DEVICE_NAME = "MI+";

    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    private static final UUID UUID_RSC_RING = getUUID("1810");       // standard uuid for blood pressure by bluetooth gatt spec.
    private static final UUID UUID_GENERIC_ACCESS    = getUUID("1800");       // standard uuid for generic access by bluetooth gatt spec.
    private static final UUID UUID_GENERIC_ATTRIBUTE    = getUUID("1801");  // standard uuid for generic attribute by bluetooth gatt spec.
    private static final UUID UUID_DEVICE_INFORMATION    = getUUID("180a");  // standard uuid for device information by bluetooth gatt spec.
    private static final UUID UUID_BATTERY_SERVICE = getUUID("180f");  // Battery Service
    
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
    
    private static final UUID UUID_BATTERY_LEVEL = getUUID("2a19");
    
    private static final UUID UUID_RUNNING_SPEED_AND_CADENCE = getUUID("1814");
    
    private static final UUID UUID_RSC_MEASUREMENT = getUUID("2a53");
    private static final UUID UUID_RSC_FEATURE = getUUID("2a54");
    private static final UUID UUID_SENSOR_LOCATION = getUUID("2a5d");
    private static final UUID UUID_SC_CONTROL_POINT = getUUID("2a55");
    
    private static final UUID UUID_CUSTOMER_DEF01 = getUUID("ff10");
    private static final UUID UUID_CUSTOMER_DEF02 = getUUID("ff11");
    
    private static final int FIELD_FLAGS = 1;
    private static final int FIELD_INSTANTANEOUS_SPEED = 2;
    private static final int FIELD_INSTANTANEOUS_CADENCE = 3;
    private static final int FIELD_INSTANTANEOUS_STRIDE_LENGTH = 4;
    private static final int FIELD_TOTAL_DISTANCE = 5;

    private static final int FLAG_RSC_MEASUREMENT_INSTANTANEOUS_STRIDE_LENGTH_PRESENT = 0x01;
    private static final int FLAG_RSC_MEASUREMENT_TOTAL_DISTANCE_PRESENT = 0x02;
    private static final int FLAG_RSC_MEASUREMENT_WALKING_OR_RUNNING_STATUS_BITS = 0x04;
    
    private int flagMeasurement;
    private boolean flagISLP;
    private boolean flagTDP;
    private boolean flagWORSB;
        
    private static final int FLAG_RSC_FEATURE_INSTANTANEOUS_STRIDE_LENGTH_MEASUREMENT_SUPPORTED = 0x01;
    private static final int FLAG_RSC_FEATURE_TOTAL_DISTANCE_MEASUREMENT_SUPPORTED = 0x02;
    private static final int FLAG_RSC_FEATURE_WALKING_OR_RUNNING_STATUS_SUPPORTED = 0x04;
    private static final int FLAG_RSC_FEATURE_CALIBRATION_PROCEDURE_SUPPORTED = 0x08;
    private static final int FLAG_RSC_FEATURE_MULTIPLE_SENSOR_LOCATIONS_SUPPORTED = 0x10;
    
    private boolean flagISLMS;
    private boolean flagTDMS;
    private boolean flagWORSS;
    private boolean flagCPS;
    private boolean flagMSLS;
       
    enum sensor_loc {
    	SENSOR_LOCATION_OTHER, 
    	SENSOR_LOCATION_TOP_OF_SHOE, 
    	SENSOR_LOCATION_IN_SHOE,
    	SENSOR_LOCATION_HIP,
    	SENSOR_LOCATION_FRONT_WHEEL,
    	SENSOR_LOCATION_LEFT_CRANK,
    	SENSOR_LOCATION_RIGHT_CRANK,
    	SENSOR_LOCATION_LEFT_PEDAL,
    	SENSOR_LOCATION_RIGHT_PEDAL,
    	SENSOR_LOCATION_FRONT_HUB,           // front hub
    	SENSOR_LOCATION_REAR_DROPOUT,   // rear dropout
    	SENSOR_LOCATION_CHAINSTAY,             // Chainstay
    	SENSOR_LOCATION_REAR_WHEEL,         // Rear Wheel
    	SENSOR_LOCATION_REAR_HUB,              //Rear Hub
    	SENSOR_LOCATION_CHEST,                      // chest
    	SENSOR_LOCATION_RESERVED,
    };
    
    private sensor_loc sensor_location;
    
    private static final int FIELD_OP_CODE = 1;
    private static final int FIELD_CUMULATIVE_VALUE = 2;
    private static final int FIELD_SENSOR_LOCATION_VALUE = 3;
    private static final int FIELD_REQUEST_OP_CODE = 4;
    private static final int FIELD_RESPONSE_VALUE = 5;
    private static final int FIELD_REQUEST_PARAMETER = 6;
    
    enum op_code {
    	OP_CODE_RESERVED,
    	OP_CODE_SET_CUMULATIVE_VALUE,
    	OP_CODE_START_SENSOR_LOCATION_CALIBRATION,
    	OP_CODE_UPDATE_SENSOR_LOCATION,
    	OP_CODE_REQUEST_SUPPORTED_SENSOR_LOCATIONS,
    	OP_CODE_RESPONSE_CODE,
    	OP_CODE_RESERVED2,
    }
    
    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;

    private TextView mSpeed, mDistance, mCadence, mStride;
    private float speed_val, distance_val, cadence_val, stride_val;
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
        setContentView(R.layout.mainscreen);
        setProgressBarIndeterminate(true);

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
        
        // 加入影片的時候, 要加入以下這段程式碼
        /*
         * We are going to display the results in some text fields
         */
        mSpeed = (TextView) findViewById(R.id.text_speed);
        mDistance = (TextView) findViewById(R.id.text_distance);
        mCadence = (TextView) findViewById(R.id.text_cadence);
        mStride = (TextView) findViewById(R.id.text_stride);
        clearDisplayValues();

        movie = Environment.getExternalStorageDirectory() + "/Movies/wb0124_10km.mp4";
		SurfaceView sv = (SurfaceView) findViewById(R.id.view1);
		//SurfaceView sv = new SurfaceView(this);
		sv.getHolder().addCallback( this);
		mThread.start();
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
    }

    @Override
    protected void onStop() {
        super.onStop();
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
    	mSpeed.setText("---"); // 速度
    	mDistance.setText("---"); //累計 距離
    	mCadence.setText("---"); //步伐節奏
    	mStride.setText("---"); //步伐長度
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
    
    public void sendNotify() {
        BluetoothGattCharacteristic chara;
        BluetoothGattDescriptor desc ;
        
        BluetoothGattService service = mConnectedGatt.getService(UUID_RUNNING_SPEED_AND_CADENCE);     
        chara = service.getCharacteristic(UUID_RSC_MEASUREMENT);
        mConnectedGatt.setCharacteristicNotification(chara, true);
        desc = chara.getDescriptor(CONFIG_DESCRIPTOR);
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE );
        mConnectedGatt.writeDescriptor(desc);
    }
    
    public void getSensorLocation() {
        BluetoothGattCharacteristic chara;
        
        BluetoothGattService service = mConnectedGatt.getService(UUID_RUNNING_SPEED_AND_CADENCE);     
        chara = service.getCharacteristic(UUID_SENSOR_LOCATION);
    	mConnectedGatt.readCharacteristic(chara);;
    }
    
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
            		  if(u.equals(UUID_RSC_FEATURE)) {
            			  gatt.readCharacteristic(chara);
            		  }
            	  } // for each characteristic
            } // for each service
         }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            Log.d(TAG, "onCharacteristicRead");
            if(characteristic.getUuid().equals(UUID_RSC_FEATURE)) {
            	int b = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            	Log.d(TAG, "RSC Feature value = " + b);
            	if( (b &FLAG_RSC_FEATURE_INSTANTANEOUS_STRIDE_LENGTH_MEASUREMENT_SUPPORTED) > 0) {
            		Log.d(TAG, "RSC Feature : has instantaneous stride length measurement!");
            		flagISLMS = true;
            	} else {
            		flagISLMS = false;
            	}
            	if( (b &FLAG_RSC_FEATURE_TOTAL_DISTANCE_MEASUREMENT_SUPPORTED) > 0) {
            		Log.d(TAG, "RSC Feature : has total distance measurement!");
            		flagTDMS = true;
            	} else {
            		flagTDMS = false;
            	}
            	if( (b &FLAG_RSC_FEATURE_WALKING_OR_RUNNING_STATUS_SUPPORTED) > 0) {
            		Log.d(TAG, "RSC Feature : has walking or running status supported!");
            		flagWORSS = true;
            	} else {
            		flagWORSS = false;
            	}
            	if( (b &FLAG_RSC_FEATURE_CALIBRATION_PROCEDURE_SUPPORTED) > 0) {
            		Log.d(TAG, "RSC Feature : has calibration procedure supported!");
            		flagCPS=true;
            	} else {
            		flagCPS = false;
            	}
            	if( (b &FLAG_RSC_FEATURE_MULTIPLE_SENSOR_LOCATIONS_SUPPORTED) > 0) {
            		Log.d(TAG, "RSC Feature : has multiple sensor locations supported!");
            		flagMSLS = true;
            		getSensorLocation();
            		return;
            	} else {
            		flagMSLS = false;
            	}
            	sendNotify();
            	return;
            } // UUID_RSC_FEATURE
            
            if(characteristic.getUuid().equals(UUID_SENSOR_LOCATION)) {
            	int b = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            	Log.d(TAG, "RSC Sensor Location value = " + b);
                switch(b) {
                case 0: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_OTHER;
                	Log.d(TAG, "RSC Sensor Location = other");
                	break;
                case 1: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_TOP_OF_SHOE;
                	Log.d(TAG, "RSC Sensor Location = top of shoe");
                	break;
                case 2: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_IN_SHOE;
                	Log.d(TAG, "RSC Sensor Location = in shoe");
                	break;
                case 3: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_HIP;
                	Log.d(TAG, "RSC Sensor Location = hip");
                	break;
                case 4: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_FRONT_WHEEL;
                	Log.d(TAG, "RSC Sensor Location = front wheel");
                	break;
                case 5: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_LEFT_CRANK;
                	Log.d(TAG, "RSC Sensor Location = left crank");
                	break;
                case 6: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_RIGHT_CRANK;
                	Log.d(TAG, "RSC Sensor Location = right crank");
                	break;
                case 7: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_LEFT_PEDAL;
                	Log.d(TAG, "RSC Sensor Location = left pedal");
                	break;
                case 8: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_RIGHT_PEDAL;
                	Log.d(TAG, "RSC Sensor Location = right pedal");
                	break;
                case 9: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_FRONT_HUB;
                  	Log.d(TAG, "RSC Sensor Location = front hub");
              	break;
                case 10: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_REAR_DROPOUT;
                  	Log.d(TAG, "RSC Sensor Location = rear dropout");
                	break;
                case 11: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_CHAINSTAY;
                  	Log.d(TAG, "RSC Sensor Location = chainstay");
                	break;
                case 12: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_REAR_WHEEL;
                  	Log.d(TAG, "RSC Sensor Location = rear whell");
                	break;
                case 13: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_REAR_HUB;
                  	Log.d(TAG, "RSC Sensor Location = rear hub");
                	break;
                case 14: 
                	sensor_location = sensor_loc.SENSOR_LOCATION_CHEST;
                  	Log.d(TAG, "RSC Sensor Location = rear chest");
                	break;
                default:
                	sensor_location = sensor_loc.SENSOR_LOCATION_RESERVED;
                	Log.d(TAG, "RSC Sensor Location = reserved");
                	break;
                }
            	sendNotify();
            	return;
           } // UUID_SENSOR_LOCATION            
            
            
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        }

       @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */
        	Log.d(TAG, "onCharacteristicChanged :" + characteristic.getUuid() );
            if(characteristic.getUuid().equals(UUID_RSC_MEASUREMENT)) {
            	Log.d(TAG, "RSC Measurement Changed");
            	flagMeasurement = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            	Log.d(TAG, "RSC Measurement flag = " + Integer.toHexString(flagMeasurement) );
            	if((flagMeasurement &FLAG_RSC_MEASUREMENT_INSTANTANEOUS_STRIDE_LENGTH_PRESENT)>0) {
            		flagISLP = true;
            	} else {
            		flagISLP = false;
            	}
            	if((flagMeasurement & FLAG_RSC_MEASUREMENT_TOTAL_DISTANCE_PRESENT)>0) {
            		flagTDP = true;
            	} else {
            		flagTDP = false;
            	}
            	if((flagMeasurement & FLAG_RSC_MEASUREMENT_WALKING_OR_RUNNING_STATUS_BITS)>0) {
            		flagWORSB = true;
            	} else {
            		flagWORSB = false;
            	}
            	int speedcount =  characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT16, 1);
            	speed_val = (float) (speedcount/256.0);
            	Log.d(TAG, "RSC Measurement speedcount = " + speedcount);
            	int cadence =  characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 3);
            	cadence_val = (float) (cadence/60.0);
            	Log.d(TAG, "RSC Measurement cadence = " + cadence);
            	int strideLength = 0;
            	int totalDistance = 0;
            	
            	if(flagISLP) {
            		strideLength = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT16, 4);
                	Log.d(TAG, "RSC Measurement strideLength = " + strideLength);
                	stride_val = (float) (strideLength/100.0);
            		if(flagTDP) {
            			totalDistance = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 6);
            			distance_val = (float) (totalDistance/10);
                    	Log.d(TAG, "RSC Measurement totalDistance = " + totalDistance);
            		}
            	} else {
            		if(flagTDP) {
            			totalDistance = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 4);
            			distance_val = (float) (totalDistance/10.0);
                    	Log.d(TAG, "RSC Measurement totalDistance = " + totalDistance);
            		}
            	}
            	mHandler.sendMessage(Message.obtain(null, MSG_DATA_UPDATE, characteristic));
            } // UUID_RSC_MEASUREMENT
      }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
        	Log.d(TAG, "onCharacteristicWrite");
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
    	String str1, str2, str3, str4;
    	// steps_val, distance_val, calorie_val;
    	str1 = Float.toString(speed_val);
    	str2 = Float.toString(distance_val);
    	str3 = Float.toString(cadence_val);
    	str4 = Float.toString(stride_val);
    	// mSteps, mDistance, mCalorie;
    	mSpeed.setText(str1);
    	mDistance.setText(str2);
    	mCadence.setText(str3);
   	    mStride.setText(str4);
    }
    
    // 主畫面改成影片的程式碼
    
    private PlayerThread mPlayer = null;
    private String movie;
    private int delay=0;
    private int index=0;
    private boolean start=true;
    private boolean pause=false;
    private boolean quit = false;
    private int run=10;

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		if (mPlayer == null) {
			mPlayer = new PlayerThread(holder.getSurface());
			mPlayer.start();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		if (mPlayer != null) {
			mPlayer.interrupt();
		}
		
	}
	
	private class PlayerThread extends Thread {
		private MediaExtractor extractor;
		private MediaCodec decoder;
		private Surface surface;
		
		public PlayerThread(Surface surface) {
			this.surface = surface;
			start = true;
		}
		
		public void getDelay() {
			float frame_speed = 2.8f; // 10km/hr = 2.8m/sec
			
			if(speed_val > 0) {
				delay = (int) (frame_speed*30/speed_val);
				pause = false;
			} else {
			    delay = 10;
			    pause = true;
			}
		}

		public void setMovie() {
			if(decoder != null) {
				decoder.stop();
				decoder.release();
				decoder = null;
			}
			if(extractor != null) {
				extractor.release();
				extractor = null;
			}
			
			extractor = new MediaExtractor();
			try {
				extractor.setDataSource(movie);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			for (int i = 0; i < extractor.getTrackCount(); i++) {
				MediaFormat format = extractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith("video/")) {
					Log.d("Decoder", "mime type:" + mime);
					extractor.selectTrack(i);
					try {
                        decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(format, surface, null, 0);
                    } catch(Exception e) {
					    e.printStackTrace();
                    }
					break;
				}
			}

			if (decoder == null) {
				Log.e("DecodeActivity", "Can't find video info!");
				return;
			}
			decoder.start();
		}
				
		@Override
		public void run() {
			setMovie();
			start=true;
			ByteBuffer[] inputBuffers = decoder.getInputBuffers();
			ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			boolean isEOS = false;
			long startMs = System.currentTimeMillis();
			
			extractor.seekTo(5*1000000,0); // shift 5 30secs for start
			
			while (!Thread.interrupted()) {
				if (!isEOS) {
					int inIndex = decoder.dequeueInputBuffer(10000);
					if (inIndex >= 0) {
						ByteBuffer buffer = inputBuffers[inIndex];
						int sampleSize = extractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							// We shouldn't stop the playback at this point, just pass the EOS
							// flag to decoder, we will get it again from the
							// dequeueOutputBuffer
							Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
							decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							isEOS = true;
						} else {
							decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
							extractor.advance();
						}
					}
				}

				int outIndex = decoder.dequeueOutputBuffer(info, 10000);
				//if(run==0) pause = true; 
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d("TreadmillActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
					outputBuffers = decoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d("TreadmillActivity", "New format " + decoder.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d("TreadmillActivity", "dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer buffer = outputBuffers[outIndex];
					//Log.v("TreadmillActivity", "We can't use this buffer but render it due to the API limit, " + buffer);
					getDelay();
					Log.d("TreadmillActivity", "delay="+delay);
					Log.d("TreadmillActivity", "pause="+pause);
					try {
						sleep(delay) ;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					decoder.releaseOutputBuffer(outIndex, true);
					break;
				}

				// All decoded frames have been rendered, we can stop playing now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d("TreadmillActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}

				if(quit) break;

				while(pause) {
					try {
						sleep(100);
						getDelay();
						//Log.d("TreadmillActivity", "while pause");
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				if(!start) break;
			}

			decoder.stop();
			decoder.release();
			extractor.release();
		}

	}

    
    public Thread mThread = new Thread() {
        @Override
        public void run() {
        	threadFlag = true;
            try {
            	Log.d(TAG, "thread run!");
                while(true) {
                    sleep(2000); // 等候notify data input
                }   
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    
}
