package com.x.hrbeep.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class BleHeartRateRepository(
    private val context: Context,
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun scanHeartRateDevices(scanDurationMs: Long = 8_000L): Flow<List<BleDeviceCandidate>> = callbackFlow {
        val adapter = bluetoothAdapter ?: run {
            close(IllegalStateException("Bluetooth adapter unavailable."))
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            close(IllegalStateException("Bluetooth LE scanner unavailable."))
            return@callbackFlow
        }

        val devices = linkedMapOf<String, BleDeviceCandidate>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return

                devices[result.device.address] = BleDeviceCandidate(
                    address = result.device.address,
                    name = name,
                    rssi = result.rssi,
                )
                trySend(devices.values.sortedBy { it.name.lowercase() })
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("BLE scan failed with code $errorCode."))
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, callback)

        val timeoutJob = launch {
            kotlinx.coroutines.delay(scanDurationMs)
            close()
        }

        awaitClose {
            timeoutJob.cancel()
            scanner.stopScan(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun observeHeartRateMonitor(deviceAddress: String): Flow<HeartRateMonitorUpdate> = callbackFlow {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
            close(IllegalArgumentException("Device $deviceAddress not found."))
            return@callbackFlow
        }

        var bluetoothGatt: BluetoothGatt? = null

        fun emitBatteryLevel(value: ByteArray?) {
            val batteryLevel = parseBatteryLevelPercent(value)
                ?: return
            trySend(HeartRateMonitorUpdate(batteryLevelPercent = batteryLevel))
        }

        fun readBatteryLevel(gatt: BluetoothGatt) {
            val batteryCharacteristic = gatt
                .getService(BATTERY_SERVICE_UUID)
                ?.getCharacteristic(BATTERY_LEVEL_UUID)
                ?: return

            gatt.readCharacteristic(batteryCharacteristic)
        }

        fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!notificationEnabled) {
                close(IOException("Failed to enable heart-rate notifications."))
                return
            }

            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor == null) {
                close(IOException("Heart-rate descriptor missing."))
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        fun handleCharacteristicValue(value: ByteArray) {
            runCatching { HeartRateParser.parse(value) }
                .onSuccess { sample -> trySend(HeartRateMonitorUpdate(heartRateSample = sample)) }
                .onFailure { throwable -> close(throwable) }
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(IOException("GATT connection failed with status $status."))
                    return
                }

                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        if (!gatt.discoverServices()) {
                            close(IOException("Failed to discover GATT services."))
                        }
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        close(IOException("Heart-rate strap disconnected."))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(IOException("Service discovery failed with status $status."))
                    return
                }

                val service: BluetoothGattService? = gatt.getService(HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                if (characteristic == null) {
                    close(IOException("Heart Rate Measurement characteristic missing."))
                    return
                }

                enableNotifications(gatt, characteristic)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(IOException("Failed to subscribe to HR notifications: $status."))
                    return
                }

                if (descriptor.characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                    readBatteryLevel(gatt)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                    emitBatteryLevel(characteristic.value)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                    emitBatteryLevel(value)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleCharacteristicValue(characteristic.value ?: return)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleCharacteristicValue(value)
            }
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback)
        }

        awaitClose {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
    }

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        internal fun parseBatteryLevelPercent(value: ByteArray?): Int? =
            value
                ?.firstOrNull()
                ?.toInt()
                ?.and(0xFF)
                ?.takeIf { it in 0..100 }

        private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
