package com.wahyudotdev.ble

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import java.nio.ByteBuffer
import java.util.*


@SuppressLint("MissingPermission")
open class BleHelper constructor(
    private val activity: FragmentActivity,
    private val listener: BleListener,
) {

    private var manager: BluetoothManager? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private val devices = ArrayList<BluetoothDevice>()
    private var scanCallback: ScanCallback? = null
    private var selectedGatt: BluetoothGatt? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    companion object {
        val serviceUUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9d")
        val notifyCharacteristicUUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9d")
        val commandCharacteristicUUID: UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9d")
        const val receiveHeartRate = 0x0E.toByte()
        const val receiveSportsDayData = 0x0C.toByte()
    }


    open fun setup(
        onSetupFail: ((List<String>) -> Unit)? = null,
        onSetupSuccess: ((adapter: BluetoothAdapter?) -> Unit)? = null,
    ) {
        manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager?.adapter
        listenBluetoothState()
        onSetupSuccess?.invoke(adapter)
    }

    private fun listenBluetoothState() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action?.equals(BluetoothAdapter.ACTION_STATE_CHANGED) == true) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    listener.onBluetoothStateChanged(state)
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        activity.registerReceiver(broadcastReceiver, filter)
    }

    open fun destroy() {
        stopScan()
        disconnect()
        activity.unregisterReceiver(broadcastReceiver)
    }

    open fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    open fun enableBluetooth(onFail: (() -> Unit)? = null, onSuccess: (() -> Unit)? = null) {
        val register =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    onSuccess?.invoke()
                } else {
                    onFail?.invoke()
                }
            }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        register.launch(intent)
    }

    fun disconnect() {
        selectedGatt?.close()
    }

    fun startScan() {
        scanner = adapter?.bluetoothLeScanner
        devices.clear()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                val filter = devices.find { it.address == result?.device?.address }
                if (filter == null && result?.device != null) {
                    devices.add(result.device)
                }
                listener.onDeviceDiscovered(devices)
            }
        }
        if (!scanning) {
            scanner?.startScan(scanCallback)
            scanning = true
        } else {
            scanning = false
            scanner?.stopScan(scanCallback)
        }
    }

    fun connect(device: BluetoothDevice) {
        scanCallback?.let { it ->
            scanner?.stopScan(it)
            device.connectGatt(activity, true, object : BluetoothGattCallback() {
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    gatt?.getService(serviceUUID)?.getCharacteristic(notifyCharacteristicUUID)
                        ?.let {
                            gatt.setCharacteristicNotification(it, true)
                            val notifyCharacteristics = convertFromInteger(0x2902)
                            val descriptor = it.getDescriptor(notifyCharacteristics)
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                }

                private fun convertFromInteger(i: Int): UUID {
                    val msb = 0x0000000000001000L
                    val lsb = -0x7fffff7fa064cb05L
                    val value = (i and -0x1).toLong()
                    return UUID(msb or (value shl 32), lsb)
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
                ) {
                    super.onDescriptorWrite(gatt, descriptor, status)
                    gatt?.device?.let {
                        listener.onDeviceConnected(it)
                    }
                }

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt?, status: Int, newState: Int
                ) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt?.discoverServices()
                        selectedGatt = gatt
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        selectedGatt = null
                        listener.onDeviceDisconnected()
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    Log.d(
                        "TAG",
                        "onCharacteristicChanged: ${characteristic?.uuid} => ${characteristic?.value}"
                    )
                    characteristic?.value ?: return

                    /*
                    Ketika pengukuran telah selesai dan gelang M6 berhasil mendapatkan data pengukuran,
                    kita akan mendapatkan notifikasi berisi data dengan group Receive Sports Data (0x15),
                    command Receive Heart Rate Data (0x0e) dan payload berisi data pengukuran dalam 12 byte.
                    Untuk payload-nya sendiri cara membacanya adalah sebagai berikut:

                    Byte ke-5 sampai dengan ke-8 adalah waktu pengukuran dalam detik,
                    contoh: 0x00 0x00 0xe6 0xb2 artinya 59058 detik, yang berarti pengukuran
                     dilakukan pada jam 16:24
                    Byte ke-9 adalah nilai SPO2, contoh: 0x61 berarti 97%
                    Byte ke-10 adalah tekanan darah tinggi, contoh: 0x78 berarti 120
                    Byte ke-11 adalah tekanan darah rendah, contoh: 0x55 berarti 85
                    Byte ke-12 adalah detak jantung per menit (BPM), contoh: 0x50 berarti 80 BPM

                    Contoh:
                    Untuk payload: 0xCD 0x00 0x11 0x15 0x01 0x0E 0x00 0x0C 0x2C 0xC9 0x00 0x01 0x00
                     0x00 0xE6 0xB2 0x61 0x78 0x55 0x50
                    Dibaca: 97% SPO2, Tekanan Darah 120/85 dan detak jantung 80 BPM
                     */
                    if (characteristic.value?.get(5) == receiveHeartRate) {
                        val spo2 = characteristic.value?.get(16) ?: 0
                        val diastolic = characteristic.value?.get(17) ?: 0
                        val systolic = characteristic.value?.get(18) ?: 0
                        val bpm = characteristic.value?.get(19) ?: 0
                        listener.onDataReceived(
                            MonitoringData.HeartRate(
                                spo2 = spo2.toInt(),
                                diastolic = diastolic.toInt(),
                                systolic = systolic.toInt(),
                                bpm = bpm.toInt()
                            )
                        )
                    }

                    /*
                    Ketika pengukuran telah selesai dan gelang M6 berhasil mendapatkan data
                    pengukuran, kita akan mendapatkan notifikasi berisi data dengan group Receive
                    Sports Data (0x15), command Receive Sports Day Data (0x0c) dan payload berisi
                    data pengukuran dalam 12 byte.

                    Untuk payload-nya sendiri cara membacanya adalah sebagai berikut:

                    Byte ke-3 sampai dengan ke-6 adalah total langkah, contoh: 0x00 0x00 0x04 0x55
                    berarti 1109 langkah.
                    Byte ke-7 sampai dengan ke-10 adalah jarak dalam meter, contoh: 0x00 0x00 0x03
                    0x24 berarti jarak yang ditempuh 804 meter.
                    Byte ke-11 dan ke-12 adalah kalori yang dibakar dalam cal, contoh: 0x00 0x17
                    berarti 23 cal.

                    Contoh:
                    0xCD 0x00 0x11 0x15 0x01 0x0C 0x00 0x0C 0x00 0x00 0x00 0x00 0x04 0x55 0x00
                    0x00 0x03 0x24 0x00 0x17
                    Dibaca: 1109 langkah, 804 meter dan 23 cal.
                    [-51, 0, 17, 21, 1, 12, 0, 12, 0, 0,
                    0, 0, 0, 107, 0, 0, 0, 59, 0, 2]
                     */
                    if (characteristic.value?.get(5) == receiveSportsDayData) {
                        val steps = characteristic.value.slice(10..13).toByteArray()
                        val meters = characteristic.value.slice(14..17).toByteArray()
                        val cal = characteristic.value.slice(18..19).toMutableList()
                        // akan muncul underflow error ketika hanya ada 2 array
                        // array minimal yg dapat dikonversi adalah 4 array (1 byte) data
                        cal.addAll(0, listOf(0, 0))
                        listener.onDataReceived(
                            MonitoringData.Sports(
                                steps = bytesToInt(steps),
                                meters = bytesToInt(meters),
                                cal = bytesToInt(cal.toByteArray()),
                            )
                        )
                    }
                }
            })
        }
    }

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    private fun bytesToInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).int

    private fun sendData(data: ByteArray) {
        val characteristic =
            selectedGatt?.getService(serviceUUID)
                ?.getCharacteristic(commandCharacteristicUUID)
        characteristic?.value = data
        selectedGatt?.writeCharacteristic(characteristic)
    }

    fun readHeartRate() {
        val data = byteArrayOfInts(0xCD, 0x00, 0x06, 0x12, 0x01, 0x18, 0x00, 0x01, 0x01)
        sendData(data)
    }

    fun readSportsData() {
        val data = byteArrayOfInts(0xCD, 0x00, 0x06, 0x15, 0x01, 0x0D, 0x00, 0x01, 0x01)
        sendData(data)
    }
}