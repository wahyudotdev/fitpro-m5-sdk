package com.wahyudotdev.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log


@SuppressLint("MissingPermission")
interface BleListener {

    fun onDeviceDiscovered(devices: List<BluetoothDevice>)
    fun onDeviceConnected(device: BluetoothDevice) {
        Log.d("BLE", "onDeviceConnected: ${device.address}")
    }

    fun onDeviceDisconnected() {
        Log.d("BLE", "onDeviceDisconnected")
    }

    fun onDataReceived(data: MonitoringData)

    fun onBluetoothStateChanged(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> Log.d("BLE", "onBluetoothStateChanged: Bluetooth OFF")
            BluetoothAdapter.STATE_ON -> Log.d("BLE", "onBluetoothStateChanged: Bluetooth ON")
        }
    }
}