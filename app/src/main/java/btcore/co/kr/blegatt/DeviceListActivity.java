
package btcore.co.kr.blegatt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceListActivity extends Activity {
    public BluetoothAdapter mBluetoothAdapter;
    public static final String TAG = "DeviceListActivity";

    private TextView mEmptyList;
    List<BluetoothDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private ServiceConnection onService = null;
    Map<String, Integer> devRssiValues;
    private static final long SCAN_PERIOD = 10000;
    private Handler mHandler;
    private boolean mScanning;
    private Button cancelButton;
    private BluetoothLeScanner mBLEScanner;

    public static boolean connectToggle = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);
        setContentView(R.layout.device_list);
        android.view.WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
        layoutParams.gravity = Gravity.TOP;
        layoutParams.y = 200;
        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        // Checks if Bluetooth LE Scanner is available.
        if (mBLEScanner == null) {
            Toast.makeText(this, "Can not find BLE Scanner", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cancelButton = (Button) findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mScanning == false) scanLeDevice(true);
                else finish();
            }
        });
        populateList();
        mEmptyList = (TextView) findViewById(R.id.empty);


    }

    private void populateList() {
        Log.d(TAG, "populateList");
        deviceList = new ArrayList<BluetoothDevice>();
        deviceAdapter = new DeviceAdapter(this, deviceList);
        devRssiValues = new HashMap<String, Integer>();

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);
        scanLeDevice(true);
    }


    public void scanLeDevice(final boolean enable) {
        //final Button cancelButton = (Button) findViewById(R.id.btn_cancel);
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBLEScanner.startScan(mScanCallback);
                    mBLEScanner.stopScan(mScanCallback);
                    cancelButton.setText(R.string.scan);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBLEScanner.startScan(mScanCallback);
            cancelButton.setText(R.string.cancel);
        } else {
            mScanning = false;
            mBLEScanner.stopScan(mScanCallback);
            cancelButton.setText(R.string.scan);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }

        @Override        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override        public void onScanFailed(int errorCode) {
        }

        private void processResult(final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addDevice(result.getDevice(), result.getRssi());
                }
            });
        }
    };


    private void addDevice(BluetoothDevice device, int rssi) {
        boolean deviceFound = false;

        for (BluetoothDevice listDev : deviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                break;
            }
        }


        devRssiValues.put(device.getAddress(), rssi);
        if (!deviceFound) {
            deviceList.add(device);
            mEmptyList.setVisibility(View.GONE);
            deviceAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    @Override
    public void onStop() {
        super.onStop();
        mBLEScanner.stopScan(mScanCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBLEScanner.startScan(mScanCallback);
    }

    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = deviceList.get(position);
            mBLEScanner.stopScan(mScanCallback);
            Bundle b = new Bundle();
            b.putString(BluetoothDevice.EXTRA_DEVICE, deviceList.get(position).getAddress());

            connectToggle = true;

            Intent result = new Intent();
            result.putExtras(b);
            setResult(Activity.RESULT_OK, result);
            finish();
        }
    };

    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    class DeviceAdapter extends BaseAdapter {
        Context context;
        List<BluetoothDevice> devices;
        LayoutInflater inflater;

        public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.devices = devices;
        }


        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.device_element, null);
            }

            BluetoothDevice device = devices.get(position);
            final TextView tvadd = ((TextView) vg.findViewById(R.id.address));
            final TextView tvname = ((TextView) vg.findViewById(R.id.name));
            final TextView tvpaired = (TextView) vg.findViewById(R.id.paired);
            final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);

            tvrssi.setVisibility(View.VISIBLE);
            byte rssival = (byte) devRssiValues.get(device.getAddress()).intValue();
            if (rssival != 0) {
                tvrssi.setText("Rssi = " + String.valueOf(rssival));
            }

            tvname.setText(device.getName());
            tvadd.setText(device.getAddress());

            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "device::" + device.getName());
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setTextColor(Color.GRAY);
                tvpaired.setVisibility(View.VISIBLE);
                tvpaired.setText(R.string.paired);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);

            } else {
                if(tvname.getText().equals(null) || tvname.getText().equals("")){
                    tvname.setText("n/a");
                }
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setVisibility(View.GONE);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);
            }
            return vg;
        }

    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
