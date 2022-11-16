package com.wahyudotdev.fitprom5

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.permissionx.guolindev.PermissionX
import com.wahyudotdev.ble.BleHelper
import com.wahyudotdev.ble.BleListener
import com.wahyudotdev.ble.LocationState
import com.wahyudotdev.ble.MonitoringData
import com.wahyudotdev.fitprom5.databinding.ActivityMainBinding
import com.wahyudotdev.fitprom5.databinding.ItemDeviceBinding
import java.util.*

class MainActivity : AppCompatActivity(), BleListener {
    private lateinit var binding: ActivityMainBinding
    private var rvAdapter: ReactiveListAdapter<ItemDeviceBinding, BluetoothDevice>? = null
    private lateinit var ble: BleHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rvAdapter =
            object : ReactiveListAdapter<ItemDeviceBinding, BluetoothDevice>(R.layout.item_device) {
                override fun onBindViewHolder(
                    holder: ItemViewHolder<ItemDeviceBinding, BluetoothDevice>,
                    position: Int
                ) {
                    super.onBindViewHolder(holder, position)
                    holder.binding.btnConnect.setOnClickListener {
                        ble.connect(getItem(position))
                    }
                }
            }
        binding.rvBluetooth.adapter = rvAdapter


        // Sebelum inisialisasi kelas ini, pastikan bahwa location sudah aktif
        // Perangkat BLE tidak akan bisa ditemukan jika GPS dalam keadaan OFF
        ble = BleHelper(this, this)

        PermissionX.init(this).permissions(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        ).request { allGranted, _, deniedList ->
            if (allGranted) {
                ble.setup {
                    if (it?.isEnabled == true) {
                        ble.startScan()
                    } else {
                        ble.enableBluetooth()
                    }
                }
            }
        }

        binding.btnReadHeart.setOnClickListener {
            ble.readHeartRate()
        }
        binding.btnReadSports.setOnClickListener {
            ble.readSportsData()
        }
    }

    override fun onDestroy() {
        ble.destroy()
        super.onDestroy()
    }

    override fun onDeviceDiscovered(devices: List<BluetoothDevice>) {
        rvAdapter?.submitList(devices.toMutableList())
    }

    override fun onDataReceived(data: MonitoringData) {
        when (data) {
            is MonitoringData.HeartRate -> Log.d(
                "BLE",
                "heart rate: ${data.spo2}%, ${data.systolic}/${data.diastolic}mmHg, ${data.bpm}bpm"
            )
            is MonitoringData.Sports -> Log.d(
                "BLE",
                "sports : ${data.steps} steps, ${data.meters} m, ${data.cal} cal"
            )
            else -> Log.d("BLE", "unprocessed data")
        }
    }

    override fun onBluetoothStateChanged(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> runOnUiThread { tos("Bluetooth OFF") }
            BluetoothAdapter.STATE_ON -> {
                ble.startScan()
            }
        }
    }

    override fun onLocationStateChanged(state: LocationState) {
        runOnUiThread { tos(state.name) }
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        super.onDeviceConnected(device)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, 2022)
        calendar.set(Calendar.MONTH, 11)
        calendar.set(Calendar.DATE, 14)
        calendar.set(Calendar.HOUR, 16)
        calendar.set(Calendar.MINUTE, 0)
        ble.setDateTime(calendar)
    }
}