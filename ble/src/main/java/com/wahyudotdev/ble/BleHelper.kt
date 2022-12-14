package com.wahyudotdev.ble

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.wahyudotdev.ble.parser.BleParser
import com.wahyudotdev.ble.parser.FitProM5
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.absoluteValue


@SuppressLint("MissingPermission")
@Suppress("unused")
open class BleHelper constructor(
    private val activity: FragmentActivity,
    private val listener: BleListener,
    private val parser: BleParser = FitProM5(),
) {
    private var manager: BluetoothManager? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private val devices = ArrayList<BluetoothDevice>()

    private var scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val filter = devices.find { it.address == result?.device?.address }
            if (filter == null && result?.device != null) {
                devices.add(result.device)
            }
            listener.onDeviceDiscovered(devices)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d("TAG", "onScanFailed: $errorCode")
        }
    }
    private val register =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                listener.onBluetoothStateChanged(BluetoothState.TURNING_ON)
            } else {
                listener.onBluetoothStateChanged(BluetoothState.REJECTED)
            }
        }
    private var selectedGatt: BluetoothGatt? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private val _serviceUUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9d")
    private val _notifyCharacteristicUUID: UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9d")
    private val _commandCharacteristicUUID: UUID =
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9d")
    private val _receiveHeartRate = 0x0E.toByte()
    private val _receiveSportsDayData = 0x0C.toByte()
    private var isLocationEnabled = false
    private var isBluetoothEnabled = false

    open val service: UUID
        get() = _serviceUUID

    open val notifyCharacteristic: UUID
        get() = _notifyCharacteristicUUID

    open val commandCharacteristic: UUID
        get() = _commandCharacteristicUUID


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
                    when (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )) {
                        BluetoothAdapter.STATE_OFF -> {
                            listener.onBluetoothStateChanged(BluetoothState.OFF)
                            isBluetoothEnabled = false
                        }
                        BluetoothAdapter.STATE_ON -> {
                            listener.onBluetoothStateChanged(BluetoothState.ON)
                            isBluetoothEnabled = true
                            if (isLocationEnabled) {
                                listener.onBluetoothStateChanged(BluetoothState.READY)
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        activity.registerReceiver(broadcastReceiver, filter)
    }

    // Location
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null


    private val locationCallback = object : LocationCallback() {
        private var isLocationCallbackFired = false
        private var onEnabled: (() -> Unit)? = null
        private var onDisabled: (() -> Unit)? = null
        fun setCallback(onEnabled: (() -> Unit)? = null, onDisabled: (() -> Unit)? = null) {
            this.onEnabled = onEnabled
            this.onDisabled = onDisabled
        }

        override fun onLocationResult(location: LocationResult) {
            isLocationEnabled = true
            if (isBluetoothEnabled) {
                listener.onBluetoothStateChanged(BluetoothState.READY)
            }
            if (!isLocationCallbackFired) {
                isLocationCallbackFired = true
                onEnabled?.invoke()
                listener.onLocationStateChanged(LocationState.ENABLED)
            }
        }

        override fun onLocationAvailability(service: LocationAvailability) {
            if (service.isLocationAvailable) {
                isLocationEnabled = true
                onEnabled?.invoke()
                listener.onLocationStateChanged(LocationState.ENABLED)
                if (isBluetoothEnabled) {
                    listener.onBluetoothStateChanged(BluetoothState.READY)
                }
            } else {
                isLocationEnabled = false
                isLocationCallbackFired = false
                onDisabled?.invoke()
                listener.onLocationStateChanged(LocationState.DISABLED)
                if (isBluetoothEnabled) {
                    listener.onBluetoothStateChanged(BluetoothState.ON)
                }
            }
        }
    }

    open fun enableLocation(onEnabled: (() -> Unit)? = null, onDisabled: (() -> Unit)? = null) {
        if (isLocationEnabled) {
            onEnabled?.invoke()
            if (isBluetoothEnabled) {
                listener.onBluetoothStateChanged(BluetoothState.READY)
            }
            return
        }
        locationCallback.setCallback(onEnabled, onDisabled)
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(activity)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(activity)
            fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
            fusedLocationProviderClient?.requestLocationUpdates(
                locationRequest, locationCallback,
                Looper.getMainLooper()
            )
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(activity, 100)
                    fusedLocationProviderClient =
                        LocationServices.getFusedLocationProviderClient(activity)
                    fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
                    fusedLocationProviderClient?.requestLocationUpdates(
                        locationRequest, locationCallback,
                        Looper.getMainLooper()
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    onDisabled?.invoke()
                }
            }
        }
    }

    open fun destroy() {
        stopScan()
        disconnect()
        activity.unregisterReceiver(broadcastReceiver)
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
    }

    open fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    open fun enableBluetooth(onFail: (() -> Unit)? = null, onSuccess: (() -> Unit)? = null) {
        if (adapter?.isEnabled == true) {
            isBluetoothEnabled = true
            onSuccess?.invoke()
            if (isLocationEnabled) {
                listener.onBluetoothStateChanged(BluetoothState.READY)
            }
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        register.launch(intent)
    }

    open fun disconnect() {
        selectedGatt?.close()
        selectedGatt?.disconnect()
        selectedGatt = null
        listener.onDeviceDisconnected()
    }

    open fun scanFilter(): List<ScanFilter> {
        return listOf(ScanFilter.Builder().build())
    }

    open fun scanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    fun startScan() {
        selectedGatt?.close()
        scanner = adapter?.bluetoothLeScanner
        scanner?.stopScan(scanCallback)
        devices.clear()
        if (!isLocationEnabled) {
            enableLocation(onEnabled = {
                if (adapter?.isEnabled != true) {
                    enableBluetooth()
                }
                scanner?.startScan(scanFilter(), scanSettings(), scanCallback)
            })
        } else if (adapter?.isEnabled == false) {
            enableBluetooth(onSuccess = {
                enableLocation()
            })
        } else {
            scanner?.startScan(scanFilter(), scanSettings(), scanCallback)
        }
    }

    fun getDeviceFromAddress(address: String): BluetoothDevice? {
        return try {
            adapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    fun connect(
        device: BluetoothDevice, owner: LifecycleOwner, connectTimeout: Long = 20000,
        onConnected: (() -> Unit)? = null,
        onTimeOut: ((message: String?) -> Unit)? = null
    ) {
        connect(device, connectTimeout, onConnected) {
            if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                return@connect
            }
            owner.lifecycleScope.launch {
                onTimeOut?.invoke(it)
            }
        }
    }

    fun connect(
        device: BluetoothDevice,
        connectTimeout: Long = 20000,
        onConnected: (() -> Unit)? = null,
        onTimeOut: ((message: String?) -> Unit)? = null
    ) {
        handler.postDelayed({
            onTimeOut?.invoke("timeout")
        }, connectTimeout)
        scanner?.stopScan(scanCallback)
        if (selectedGatt != null) {
            handler.removeCallbacksAndMessages(null)
            listener.onDeviceConnected(selectedGatt!!.device)
            return
        }
        try {
            device.connectGatt(activity, false, object : BluetoothGattCallback() {
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    gatt?.getService(service)?.getCharacteristic(notifyCharacteristic)
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
                        onConnected?.invoke()
                        handler.removeCallbacksAndMessages(null)
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
                    characteristic?.value ?: return
                    parseData(characteristic)
                }
            })
        } catch (e: Exception) {
            onTimeOut?.invoke(e.stackTrace.toString())
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).int

    private fun sendData(data: ByteArray) {
        val characteristic =
            selectedGatt?.getService(service)
                ?.getCharacteristic(commandCharacteristic)
        characteristic?.value = data
        selectedGatt?.writeCharacteristic(characteristic)
    }

    open fun parseData(characteristic: BluetoothGattCharacteristic) {

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
        if (characteristic.value?.get(5) == _receiveHeartRate) {
            val spo2 = characteristic.value?.get(16) ?: 0
            val systolic = characteristic.value?.get(17) ?: 0
            val diastolic = characteristic.value?.get(18) ?: 0
            val bpm = characteristic.value?.get(19) ?: 0
            listener.onDataReceived(
                MonitoringData.HeartRate(
                    spo2 = spo2.toInt().absoluteValue,
                    systolic = systolic.toInt().absoluteValue,
                    diastolic = diastolic.toInt().absoluteValue,
                    bpm = bpm.toInt().absoluteValue
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
        if (characteristic.value?.get(5) == _receiveSportsDayData) {
            val steps = characteristic.value.slice(10..13).toByteArray()
            val meters = characteristic.value.slice(14..17).toByteArray()
            val cal = characteristic.value.slice(18..19).toMutableList()
            // akan muncul underflow error ketika hanya ada 2 array
            // array minimal yg dapat dikonversi adalah 4 array (1 byte) data
            cal.addAll(0, listOf(0, 0))
            listener.onDataReceived(
                MonitoringData.Sports(
                    steps = bytesToInt(steps).absoluteValue,
                    meters = bytesToInt(meters).absoluteValue,
                    cal = bytesToInt(cal.toByteArray()).absoluteValue,
                )
            )
        }
    }

    fun readHeartRate() = sendData(parser.readHeartRate())

    fun readSportsData() = sendData(parser.readSportsData())

    fun setDateTime(dt: Calendar) = sendData(parser.setDateTime(dt))

    fun findDevice() = sendData(parser.findDevice())
}