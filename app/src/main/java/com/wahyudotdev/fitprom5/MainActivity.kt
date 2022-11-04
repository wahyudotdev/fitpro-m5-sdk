package com.wahyudotdev.fitprom5

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.wahyudotdev.ble.BleConnection
import com.wahyudotdev.ble.MonitoringData
import com.wahyudotdev.fitprom5.databinding.ActivityMainBinding
import com.wahyudotdev.fitprom5.databinding.ItemDeviceBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var rvAdapter: ReactiveListAdapter<ItemDeviceBinding, BluetoothDevice>? = null
    private lateinit var ble: BleConnection
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

        ble = BleConnection(this).apply {
            onDeviceDisconnected {
                runOnUiThread { tos("device disconnected") }
            }
            onDeviceConnected {
                runOnUiThread { tos("device connected") }
            }

            onDeviceDiscovered {
                rvAdapter?.submitList(it.toMutableList())
            }

            onDataReceived {
                runOnUiThread {
                    when (it) {
                        is MonitoringData.HeartRate -> Log.d(
                            "TAG",
                            "heart rate: ${it.spo2}%, ${it.systolic}/${it.diastolic}mmHg, ${it.bpm}bpm"
                        )
                        is MonitoringData.Sports -> Log.d(
                            "TAG",
                            "sports : ${it.steps} steps, ${it.meters} m, ${it.cal} cal"
                        )
                        else -> Log.d("TAG", "unprocessed data")
                    }
                }
            }
        }

        ble.setup {
            ble.startScan()
        }
        binding.btnReadHeart.setOnClickListener {
            ble.readHeartRate()
        }
        binding.btnReadSports.setOnClickListener {
            ble.readSportsData()
        }
    }

    override fun onPause() {
        ble.stopScan()
        super.onPause()
    }
}