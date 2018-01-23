package btcore.co.kr.blegatt;


import android.app.Service;
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
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.PluralsRes;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import btcore.co.kr.blegatt.utility.ParserUtils;

/**
 * Created by leehaneul on 2018-01-19.
 */

public class BluetoothLeService extends Service {

    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private BluetoothManager mbluetoothManager;
    private BluetoothAdapter mbluetoothAdapter;
    private String mBluetoothDeviceAddress;
    /**
     * Bluetooth Smart 또는 Smart Ready 장치와 통신 할 수 있도록  Bluetooth Gatt 기능을 제공한다.
     * 원격 주병 자치에 연결하려면 BluetoothGattCallback 을 만들고
     * connectGatt(Context, boolean, BluetoothGattCallback) 을 호출하여 이 클래스의 인스턴스를 가져온다.
     * GATT 가능 장치는 Bluetooth 장치 검색 또는 Ble 검색 프로세스를 사용하여 검색 할 수 있습니다.
     */
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.nordicsemi.nrfUART.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.nordicsemi.nrfUART.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.nordicsemi.nrfUART.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_DOES_NOT_SUPPORT_UART";

    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public static String receive_message = null;

    // GATT 이벤트에 대한 콜벡 메소드를 구현합니다.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

                String intentAction;
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    //broadcastUpdate(intentAction);
                    Log.i(TAG, "Connected to GATT server.");
                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());
                }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mConnectionState = STATE_DISCONNECTED;
                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction);
                }
        }
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status){
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, String.format("BluetoothGatt ReadRssi[%d]", rssi));


            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "mBluetoothGatt = " + mBluetoothGatt );

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }


        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            final String data = ParserUtils.parse(characteristic);

            final BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD);
            final boolean notifications = cccd == null || cccd.getValue() == null || cccd.getValue().length != 2 || cccd.getValue()[0] == 0x01;

            if(notifications){

                receive_message = "Notification received from "+ characteristic.getUuid() + ", value: " + data;
                Log.d("Noti received from ", characteristic.getUuid() + ", value: " + data);
                onCharacteristicNotified(gatt, characteristic);
            }



            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);


        }
        protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }


    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
        } else {
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        if (mbluetoothManager == null) {
            mbluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mbluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mbluetoothAdapter = mbluetoothManager.getAdapter();
        if (mbluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }
    public boolean connect(final String address) {
        if (mbluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mbluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }
    public void disconnect() {
        if (mbluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        // mBluetoothGatt.close();
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.w(TAG, "mBluetoothGatt closed");
        mBluetoothDeviceAddress = null;
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        if (mbluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
        mBluetoothGatt.setCharacteristicNotification(characteristic,true);
    }
    public void enableTXNotification()
    {
        if (mBluetoothGatt == null) {
            showMessage("mBluetoothGatt null" + mBluetoothGatt);
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            showMessage("enable TXNotification - Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            showMessage("enable RXNotification - Tx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        else{
            this.readCharacteristic(TxChar);
        }

        if (TxChar == null) {
            showMessage("Tx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar,true);
        mBluetoothGatt.readCharacteristic(TxChar);
        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);

    }

    public void writeRXCharacteristic(byte[] value)
    {


        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        showMessage("mBluetoothGatt null"+ mBluetoothGatt);
        if (RxService == null) {
            showMessage("write RX Characteristic - Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            showMessage("Rx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        RxChar.setValue(value);
        boolean status = mBluetoothGatt.writeCharacteristic(RxChar);

        Log.d(TAG, "write TXchar - status=" + status);
    }

    private void showMessage(String msg) {
        Log.e(TAG, msg);
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


}
