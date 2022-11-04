package com.wahyudotdev.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import java.util.*


@SuppressLint("MissingPermission")
open class BleConnection constructor(
    private val activity: FragmentActivity,
    private val onConnected: ((BluetoothDevice) -> Unit)?,
    private val onDisconnected: (() -> Unit)?,
    private val onDeviceDiscovered: ((List<BluetoothDevice>) -> Unit)?,
) {

    private var manager: BluetoothManager? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private val devices = ArrayList<BluetoothDevice>()
    private var scanCallback: ScanCallback? = null

    companion object {
        const val serviceUUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9d"
        const val notifyCharacteristicUUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9d"
        const val commandCharacteristicUUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9d"
    }

    open fun setup(onDeniedPermission: ((List<String>) -> Unit)?, onGrantedPermission: () -> Unit) {
        manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager?.adapter
        scanner = adapter?.bluetoothLeScanner
        PermissionX.init(activity).permissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        ).request { allGranted, _, deniedList ->
            if (allGranted) {
                onGrantedPermission()
            } else {
                onDeniedPermission?.invoke(deniedList)
            }
        }
    }

    open fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    open fun startScan() {
        PermissionX.init(activity).permissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
        ).request { allGranted, _, _ ->

            devices.clear()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    val filter = devices.find { it.address == result?.device?.address }
                    if (filter == null && result?.device != null) {
                        devices.add(result.device)
                    }
                    onDeviceDiscovered?.invoke(devices)
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    Log.e("TAG", "onScanFailed: $errorCode")
                }
            }

            if (allGranted) {
                if (!scanning) {
                    scanner?.startScan(scanCallback)
                    scanning = true
                } else {
                    scanning = false
                    scanner?.stopScan(scanCallback)
                }
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        scanCallback?.let { it ->
            scanner?.stopScan(it)
            device.connectGatt(activity, false, object : BluetoothGattCallback() {
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    gatt?.services?.forEach {
                        it.characteristics.forEach { characteristic ->
                            if (characteristic.uuid.toString() == notifyCharacteristicUUID) {
                                gatt.setCharacteristicNotification(characteristic, true)
                                val notifyCharacteristics = convertFromInteger(0x2902)
                                val descriptor = characteristic.getDescriptor(notifyCharacteristics)
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }

                private fun convertFromInteger(i: Int): UUID {
                    val msb = 0x0000000000001000L
                    val lsb = -0x7fffff7fa064cb05L
                    val value = (i and -0x1).toLong()
                    return UUID(msb or (value shl 32), lsb)
                }

                private fun byteArrayOfInts(vararg ints: Int) =
                    ByteArray(ints.size) { pos -> ints[pos].toByte() }


                override fun onDescriptorWrite(
                    gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
                ) {
                    super.onDescriptorWrite(gatt, descriptor, status)
                    val data = byteArrayOfInts(0xCD, 0x00, 0x06, 0x12, 0x01, 0x18, 0x00, 0x01, 0x01)
                    val characteristic =
                        gatt?.getService(UUID.fromString(serviceUUID))
                            ?.getCharacteristic(UUID.fromString(commandCharacteristicUUID))
                    characteristic?.value = data
                    gatt?.writeCharacteristic(characteristic)
                    gatt?.device?.let {
                        onConnected?.invoke(it)
                    }
                }

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt?, status: Int, newState: Int
                ) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d("TAG", "onConnectionStateChange: connected => ${gatt?.services}")
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("TAG", "onConnectionStateChange: disconnected => $gatt")
                        onDisconnected?.invoke()
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    super.onCharacteristicChanged(gatt, characteristic, value)
                    Log.d("TAG", "onCharacteristicChanged: ${characteristic.uuid} => $value")
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    Log.d("TAG", "onCharacteristicRead: ${characteristic.uuid} => $value")
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    Log.d(
                        "TAG",
                        "onCharacteristicChanged: ${characteristic?.uuid} => ${characteristic?.value}"
                    )
                }
            })
        }
    }

}