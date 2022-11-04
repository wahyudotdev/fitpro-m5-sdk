package com.wahyudotdev.fitprom5

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wahyudotdev.ble.BleConnection
import com.wahyudotdev.fitprom5.databinding.ActivityMainBinding
import com.wahyudotdev.fitprom5.databinding.ItemDeviceBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var rvAdapter: ReactiveListAdapter<ItemDeviceBinding, BluetoothDevice>
    private lateinit var ble: BleConnection
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ble = BleConnection(
            this,
            onDeviceDiscovered = {
                rvAdapter.submitList(it.toMutableList())
            },
            onConnected = null,
            onDisconnected = null,
        )
        ble.setup(
            onDeniedPermission = {
                tos("permission denied : ${it.joinToString(",")}")
            },
            onGrantedPermission = {
                tos("permission granted, start scanning")
                ble.startScan()
            }
        )

        rvAdapter = object : ReactiveListAdapter<ItemDeviceBinding, BluetoothDevice>(R.layout.item_device) {
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
        binding.btnScan.setOnClickListener {

        }
    }

    override fun onPause() {
        ble.stopScan()
        super.onPause()
    }
}