package com.noaa.shuckmanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.alert_label.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

// Request code constants
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val COURSE_LOCATION_PERMISSION_REQUEST_CODE = 3
private const val BLUETOOTH_SCAN_REQUEST_CODE = 4
private const val BLUETOOTH_CONNECT_REQUEST_CODE = 5

// Bounds for possible Maximum Transmission Unit sizes (Note: HM10 can't do anything but the min)
private const val GATT_MIN_MTU_SIZE = 23
private const val GATT_MAX_MTU_SIZE = 517

// Service and Characteristic UUIDs for the HM-10 r/w channel
private const val SHUCKMASTER_SERV_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
private const val SHUCKMASTER_CHAR_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"

// Client Characteristic Configuration descriptor UUID (for setting notifications
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    // Sync pattern; will be used to recognized packets from the stream and sent as preamble of outgoing packets
    private val SYNC_PATTERN = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    // Packet receipt manager
    private val receiver = PacketReceiver()

    // BLE device adapter
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    // Scan filter, restricts scan results to devices with the HM10 r/w service ID
    private val filter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid.fromString(SHUCKMASTER_SERV_UUID))
        .build()

    // Settings for scan, currently enforcing high-power low latency mode
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    // Callback invoked on every new device found during a scan
    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Try to find this device in the current result list
            val index = scanResults.indexOfFirst {
                it.device.address == result.device.address
            }

            if (index != -1) {
                // Device exists, update its entry
                scanResults[index] = result
                scanResultAdapter.notifyItemChanged(index)
            } else {
                // New device, register it to the list
                with (result.device) {
                    Log.i("ScanCallback", "Found BLE Device, name: ${name ?: "unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    // List of scan results
    private val scanResults = mutableListOf<ScanResult>()

    // Scan result adapter, keeps track of scan results list to update UI elements
    private val scanResultAdapter: ScanResultAdapter by lazy {

        // Callback invoked when a scan result in the list is tapped
        ScanResultAdapter(scanResults) { result ->
            runOnUiThread {
                // Create an alert to ask for confirmation in connecting to a device
                val alertBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
                alertBuilder.setTitle("Connect to ${result.device.name} at ${result.device.address}?")
                    .setCancelable(true)
                    .setPositiveButton("OK") { _, _ ->
                        if (isScanning) {
                            stopBleScan()
                        }
                        with (result.device) {
                            Log.w("ScanResultAdapter", "Connecting to $address")
                            connectGatt(applicationContext, false, gattCallback)
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                val alert = alertBuilder.create()
                alert.show()
            }
        }
    }

    private val isLocationPermissionGranted get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread {
                scan_button.text = if (value) "Stop Scan" else "Start Scan"
            }
        }

    // List of received packets
    private val receivedPackets = mutableListOf<Packet>()

    // Adapter for received packets, updates UI element according to received packet list
    private val receivedPacketAdapter: ReceivedPacketAdapter by lazy {
        ReceivedPacketAdapter(receivedPackets)
    }

    // FIFO queue of ByteArrays scheduled to be sent over Bluetooth
    private val writeQueue = ArrayDeque<ByteArray>()

    // GATT callbacks, specific callbacks invoked on relevant events
    private val gattCallback = object: BluetoothGattCallback() {
        // Connection state changed, either intentionally or through an exception
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    connectedDevice = gatt

                    // New device connection, clear list of received packets for this session
                    val len = receivedPackets.size
                    receivedPackets.clear()
                    receivedPacketAdapter.notifyItemRangeRemoved(0, len)

                    // Get a list of services
                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    connectedDevice = null
                    gatt.close()
                }
            } else if (status == 8) {  // TODO find the real status name
                Log.w("BluetoothGattCallback", "Connection with $deviceAddress timed out")
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Connection timed out, please reconnect.")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> }
                        .create()
                        .show()
                }
                connectedDevice = null
                gatt.close()
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress")
                connectedDevice = null
                gatt.close()
            }
        }

        // Set of services for a given device were discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with (gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable()
                // gatt.requestMtu(GATT_MAX_MTU_SIZE)
                val characteristic = getRWCharacteristic()
                if (characteristic != null) {
                    enableNotifications(characteristic)
                }
            }
        }

        // Maximum transmission unit changed
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        // BLE device registered a request to read
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with (characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        // BLE device responded to a request to write to a characteristic
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote characteristic $uuid: ${value.toHexString()}")

                        // we just finished writing, if there are other packets in line to be written, write them as well
                        if (!writeQueue.isEmpty()) {
                            writePacket(writeQueue.pop())
                        } else {
                            // kotlin hates it when this isn't here...
                        }
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH ->
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU")
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED ->
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid")
                    else ->
                        Log.e("BluetoothGattCallback", "Write failed for $uuid, error: $status")
                }
            }
        }

        // BLE device responded to a request to write to a descriptor
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            with (descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS ->
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED ->
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid")
                    else ->
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                }
            }
        }

        // Notifiable characteristic changed on BLE device
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with (characteristic) {
                if (service.uuid == UUID.fromString(SHUCKMASTER_SERV_UUID) &&
                    uuid == UUID.fromString(SHUCKMASTER_CHAR_UUID)) {
                    // Notification belongs to the right characteristic

                    // Give new data to the receiver to parse
                    receiver.putByteArray(value)

                    // If this new data completed any packets, take care of them
                    val received = mutableListOf<Packet>()
                    while (receiver.hasPackets()) {
                        val packet = receiver.getPacket()
                        received.add(packet)
                        onPacketReceived(packet)
                    }

                    runOnUiThread {
                        received.forEach {
                            receivedPackets.add(it)
                            receivedPacketAdapter.notifyItemInserted(receivedPackets.size - 1)
                        }
                    }
                }
            }
        }
    }

    // FIFO queue of HTTP requests in line to be sent to the web server
    val httpRequestQueue = JsonHttpRequestQueue()

    // Callback invoked whenever a complete packet is received
    private fun onPacketReceived(packet: Packet) {
        // Convert the packet data to a buffer, interpreted in little endian
        //TODO: Convert this process into a function to improve efficiency and readability
        val buffer = ByteBuffer.wrap(packet.data).order(ByteOrder.LITTLE_ENDIAN)
        Log.i("ReceivedPacket", "This is the buffer")
        Log.i("ReceivedPacket", "$buffer")
        when(packet.id) {
            PacketType.PING.code -> {
                // received ping
                Log.i("ReceivedPacket", "Received Ping")
            }
            PacketType.HEALTH.code -> {
                // received health
                Log.i("ReceivedPacket", "Received Health - successful")
            }
            PacketType.CONFIG.code -> {
                // received config
                Log.i("ReceivedPacket", "Received Config - successful")
            }
            PacketType.DATA.code -> {
                // received data
                Log.i("ReceivedPacket", "Received Data")
                Log.i("ReceivedPacket", "Data Information")
                Log.i("ReceivedPacket", packet.data.toHexString())
                Log.i("ReceivedPacket", String(packet.data))
                Log.i("ReceivedPacket", "End of data")
                // translate data to a JSON object, then send it
                val entries = mutableListOf<DataEntry>()

                // get ID byte
                val id = if (buffer.hasRemaining()) {

                    buffer.get() //grabs this byte then moves to pointer to the next byte

                } else {
                    0
                }
                Log.i("ReceivedPacket", "This is the id:")
                Log.i("ReceivedPacket", "$id")
                // get number of entries
                val entryCount = if (buffer.hasRemaining()) {
                    buffer.get().toInt()
                } else {
                    0
                }
                Log.i("ReceivedPacket", "This is the number of data:")
                Log.i("ReceivedPacket", "$entryCount")
                Log.i("Data", "$entryCount")

                for (i in 0 until entryCount) {
                    Log.i("Data", "$i")
                    if (buffer.hasRemaining()) {
                        val time = buffer.getInt()
                        val entry = buffer.getFloat()
                        entries.add(DataEntry(time, entry))
                    }
                }

                // get label, minus null terminator if possible
                val label = if (buffer.hasRemaining()) {
                    val len = buffer.remaining() - 1
                    val strArray = ByteArray(len)
                    buffer.get(strArray)
                    strArray.decodeToString()
                } else {
                    "inv"   // no label
                }
                Log.i("ReceivedPacket", "This is the label:")
                Log.i("ReceivedPacket", label)
                // build json packet with entries, send as batch

                val array = JSONArray()
                val dataArray = emptyArray<Float>()
                entries.forEach { it ->
                    //it.entryValue is the actual data value
                    Log.i("ReceivedPacket", "$id\t${it.unixTime}\t${it.entryValue}\t$label")
                    val dataInfo = "$id\t${it.unixTime}\t${it.entryValue}\t$label"
                    print(dataInfo)
                    //receivedPacketAdapter(it.entryValue)
                    val jsonEntry = JSONObject()
                    jsonEntry.put("sensorID", id)
                    jsonEntry.put("date", it.unixTime)
                    jsonEntry.put(label, it.entryValue)
                    array.put(jsonEntry)
                }
                //TODO: Set up the webserver and change the URL
                httpRequestQueue.addRequest(
                    "POST",
                    "http://3.236.166.131:1337/newMeasurement",
                    JSONObject().put("batch", array)
                )
            }
        }
    }

    // Converts a byte array to a hex string for display
    fun ByteArray.toHexString(): String = joinToString(separator = " ", prefix = "0x") { String.format("%02X", it)}

    // Currently connected device, with setters to update UI elements on change
    private var connectedDevice: BluetoothGatt? = null
        set(value) {
            field = value
            runOnUiThread {
                if (value != null) {
                    device_connected_layout.visibility = View.VISIBLE
                    device_scanning_layout.visibility = View.GONE
                    device_name_text.text = "Name: " + (if (value != null) value.device.name else "None")
                    device_addr_text.text = "Address: " + (if (value != null) value.device.address else "None")
                } else {
                    device_connected_layout.visibility = View.GONE
                    device_scanning_layout.visibility = View.VISIBLE
                }
            }
        }

    // Low and high values between which to request data
    private val startDate = Calendar.getInstance()
    private val endDate = Calendar.getInstance()

    // Create date selector buttons
    fun setupDateSetButton(calendar: Calendar, button: Button) {
        button.text = "--/--/----"

        val listener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val format = "MM/dd/yyyy"
            val sdf = SimpleDateFormat(format, Locale.US)
            button.text = sdf.format(calendar.time)
        }

        button.setOnClickListener {
            DatePickerDialog(this@MainActivity,
                listener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    // Requestable data types
    //private val labels = arrayOf("pH", "tp", "voltage", "sa")
    private val labels = arrayOf("pH", "tp", "sa", "co")
    // Current data type to request
    private var currentLabel = labels[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the low and high date buttons
        setupDateSetButton(startDate, start_date_button)
        setupDateSetButton(endDate, end_date_button)

        // Scan button, toggles scanning
        scan_button.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else{
                startBleScan()
            }
        }

        // Disconnects the current device, if any
        device_disconnect_button.setOnClickListener {
            connectedDevice?.disconnect()
        }

        // Sends a ping packet
        device_ping_button.setOnClickListener {
            val buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                .put(SYNC_PATTERN)
                .putShort(1)
                .put(PacketType.PING.code)
            Log.i("Write", buffer.array().toHexString())
            writePacket(buffer.array())
        }

        // Sends a health status packet7
        device_health_button.setOnClickListener {
            val buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                .put(SYNC_PATTERN)
                .putShort(1)
                .put(PacketType.HEALTH.code)
            Log.i("Write", buffer.array().toHexString())
            //Log.i("ReceivedPacket","${buffer.array().joinToString()}")
            writePacket(buffer.array())
        }

        // Configures measurement period and updates current time
        device_config_button.setOnClickListener {
            runOnUiThread {
                // Prompts the user to enter settings, sends command on confirmation
                createPromptAlert(
                    "Configure Measurement Period",
                    "Set the delay between measurements in seconds.",
                    mapOf(
                        "period" to 1.0f
                    ),
                ) { results ->
                    for (entry in results) {
                        with (entry) {
                            Log.i("AlertConfig", "${key}: ${value}")
                        }
                    }
                    val period = results["period"]?.toInt()
                    if (period == null || period <= 0) {
                        // no period, or period out of bounds
                        return@createPromptAlert
                    }

                    // Send config packet with current time
                    val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
                    val buffer = ByteBuffer.allocate(7 + 1 + 8 * 4).order(ByteOrder.LITTLE_ENDIAN)
                        .put(SYNC_PATTERN)
                        .putShort((1 + 1 + 8 * 4).toShort())
                        .put(PacketType.CONFIG.code)
                        .put(0b0000_0011)
                        .putInt((c.time.time / 1000).toInt())
                        .putInt(period)
                        .putInt(0)  // reserved 1
                        .putInt(0)  // reserved 2
                        .putInt(0)  // reserved 3
                        .putInt(0)  // reserved 4
                        .putInt(0)  // reserved 5
                        .putInt(0)  // reserved 6
                    Log.i("Write", buffer.array().toHexString())
                    writePacket(buffer.array())
                }
            }
        }

        // Sets calibration constants on the device
        device_calibrate_button.setOnClickListener {
            runOnUiThread {
                // Prompts the user to enter settings, sends command on confirmation
                createPromptAlert(
                    "Set Calibration Constants ",
                    //"Set the temperature (C), pH, voltage (V), low conductivity (microsiemens), and high conductivity (microsiemens) as measured against the standard solution.",
                    "Set the low conductivity (microsiemens) and high conductivity (microsiemens) as measured against the standard solution, and the measured ocean temperature",
                    mapOf(
                        //follow the same format when adding new calibration measurements
                        "temp" to 0.0f,
                        "ph(low)" to 0.0f,
                        "ph(high)" to 0.0f,
                        "conductivity(low)" to 0.0f,
                        "conductivity(high)" to 0.0f,

                    ),
                ) { results ->
                    for (entry in results) {
                        with (entry) {
                            Log.i("AlertConfig", "$key: $value")
                        }
                    }

                    val temp = results["temp"]
                    //val oceanTemp = results["ocean Temp"]
                    val lowPH = results["ph(low)"]
                    val highPH = results["ph(high)"]
                    val lowCon = results["conductivity(low)"]
                    val highCon = results["conductivity(high)"]
                    //val oceanTemp = results["ocean Temp"]

                    if ( temp == null || lowPH == null || highPH == null || lowCon == null || highCon == null) {
                        return@createPromptAlert
                    }

                    //if (temp == null || ph == null || voltage == null || lowCon == null || highCon == null) {
                    //    return@createPromptAlert
                    //}

                    val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
                    val buffer = ByteBuffer.allocate(7 + 1 + 8 * 4).order(ByteOrder.LITTLE_ENDIAN) //Creating the packet to send the Arduino
                        .put(SYNC_PATTERN) //Putting in the sync pattern byte
                        .putShort((1 + 1 + 8 * 4).toShort()) //Putting in an extra byte TODO: Figure out why this is here and needed
                        .put(PacketType.CONFIG.code) //Putting in the Packet type byte
                        .put(0b0001_1101) //TODO: Figure out what this is and why we need it
                        .putInt((c.time.time / 1000).toInt()) //Putting in the time in byte?
                        .putInt(0)      // Putting in the period byte
                        .putFloat(temp)     // Putting in the temperature byte
                        .putFloat(lowPH)       // Putting in the pH byte
                        .putFloat(highPH)      // Putting in the voltage byte
                        .putFloat(lowCon)
                        .putFloat(highCon)
                        //.putInt(0)      // reserved 4
                        //.putInt(0)      // reserved 5
                        .putInt(0)      // reserved 6

                    Log.i("Write", "lowCon:")
                    Log.i("Write", "$lowCon")
                    Log.i("Write", "highCon:")
                    Log.i("Write", "$highCon")

                    Log.i("Write", buffer.array().toHexString())
                    writePacket(buffer.array())
                }
            }
        }

        // Send a request based on current label and start/end dates
        device_request_button.setOnClickListener {
            val lowDate: Calendar
            val highDate: Calendar
            if (startDate.before(endDate)) {
                lowDate = startDate
                highDate = endDate
            } else {
                lowDate = endDate
                highDate = startDate
            }
            //TODO:Figure_out_how_to_read_data_from_the_bluetooth_and_sd
            val buffer = ByteBuffer.allocate(4 + 2 + 1 + 4 + 4 + currentLabel.length + 1).order(ByteOrder.LITTLE_ENDIAN)
                .put(SYNC_PATTERN)
                .putShort((9 + currentLabel.length + 1).toShort())
                .put(PacketType.DATA.code)
                .putInt((lowDate.time.time / 1000).toInt())
                .putInt((highDate.time.time / 1000).toInt())
                .put(currentLabel.toByteArray()).put(0.toByte())       // null terminated label string
            Log.i("Write", buffer.array().toHexString())
            Log.i("write", "${(lowDate.time.time / 1000)}")
            Log.i("write", "${(highDate.time.time / 1000)}")
            writePacket(buffer.array())
        }

        // Button to test HTTP requests, posts a value to sensor ID 4
        test_http_button.setOnClickListener {
            val time = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            val data = JSONObject()
            data.put("sensorID", 4)
            data.put("test", 69)
            data.put("date", time.time.time / 1000)
            httpRequestQueue.addRequest(
                "POST",
                "http://3.236.166.131:1337/newMeasurement",
                data
            )
        }

        // Request command data type selector
        request_label_spinner.onItemSelectedListener = this
        val arrAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels //here we are creating a drop down list with the strings from the labels array we made earlier
        )
        arrAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        request_label_spinner.adapter = arrAdapter

        connectedDevice = null

        // Sets up UI lists
        setupScanResultsView()
        setupReceivedPacketsView()
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    // Called when an activity returns a result, used here to recognize if Bluetooth was enabled after a prompt
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    // try again if permission denied
                    promptEnableBluetooth()
                }
            }
        }
    }

    // Called when a permission request was responded to, used here to recognize if fine location permission was granted
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    //TODO: Figure out how to enable Bluetooth Connect and Scan Permissions
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
            requestPermission(
                Manifest.permission.BLUETOOTH_SCAN,
                BLUETOOTH_SCAN_REQUEST_CODE
            )
            requestPermission(
                Manifest.permission.BLUETOOTH_CONNECT,
                BLUETOOTH_CONNECT_REQUEST_CODE
            )
        }
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }

        // Prompt the user to grant permission for fine location
        runOnUiThread {
            val alertBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
            alertBuilder.setTitle("Location permission required")
                .setMessage("To scan for BLE devices, apps need to be granted location access.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                    requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION,
                        COURSE_LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            val alert = alertBuilder.create()
            alert.show()
        }
    }

    private fun startBleScan() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            // Android versions higher than Marshmallow require explicit location permission
            requestLocationPermission()
        } else {
            // Starting new scan session, clear scan results
            val len = scanResults.size
            scanResults.clear()
            scanResultAdapter.notifyItemRangeRemoved(0, len)
            bleScanner.startScan(listOf(filter), scanSettings, scanCallback)
            isScanning = true
        }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    // Displays table of services found on a device during scanning
    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristics available")
            return
        }

        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) {
                it.uuid.toString() + " " + it.isNotifiable() + " " + it.isIndicatable()
            }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable")
        }
    }

    // Quick property accessors for characteristics
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    private fun readPacket() {
        val char = getRWCharacteristic()
        if (char?.isReadable() == true) {
            connectedDevice?.readCharacteristic(char)
        }
    }

    // Sends the given payload over Bluetooth, or adds it to the queue if a payload is currently being written.
    // If the payload is larger than the current MTU, the payload will be split into multiple parts and added to the queue
    private fun writePacket(payload: ByteArray) {
        if (payload.size > 20) {
            // payload is too big, split it up and queue the parts
            val buffer = ByteBuffer.allocate(payload.size).put(payload)
            buffer.rewind()

            while (buffer.hasRemaining()) {
                val ba = ByteArray(min(20, buffer.remaining()))
                buffer.get(ba)
                Log.i("Write", ba.toHexString())
                writeQueue.add(ba)
            }
            writePacket(writeQueue.pop())
            return
        }
        val char = getRWCharacteristic() ?: return
        val writeType = when {
            char.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            char.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> error("Characteristic ${char.uuid} cannot be written to")
        }

        connectedDevice?.let { gatt ->
            char.writeType = writeType
            char.value = payload
            gatt.writeCharacteristic(char)
        } ?: error("Not connected to a BLE device")
    }

    // Enables notifications on a characteristic, notifying the app whenever the value changes
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e("ConnectionManager", "${characteristic.uuid} is not notifiable nor indicatable")
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (connectedDevice?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} does not contain the CCC descriptor")
    }

    // Writes to a descriptor
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        connectedDevice?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a device")
    }

    // Gets the r/w characteristic of an HM10
    private fun getRWCharacteristic(): BluetoothGattCharacteristic? {
        val servUUID = UUID.fromString(SHUCKMASTER_SERV_UUID)
        val charUUID = UUID.fromString(SHUCKMASTER_CHAR_UUID)

        return connectedDevice?.getService(servUUID)?.getCharacteristic(charUUID)
    }

    // Sets up scan results list UI element
    private fun setupScanResultsView() {
        scan_results_recycler_view.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    // Sets up received packet list UI element
    private fun setupReceivedPacketsView() {
        device_received_packets.apply {
            adapter = receivedPacketAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = device_received_packets.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    // Creates a prompt that requests an arbitrary number of float values from the user
    private fun createPromptAlert(
        title: String,
        message: String,
        valueMap: Map<String, Float>,
        onAccept: (results: Map<String, Float>) -> Unit) {

        val inflater = LayoutInflater.from(this)
        val layout = LinearLayout(this)
        val viewMap = mutableMapOf<String, View>()

        layout.orientation = LinearLayout.VERTICAL

        // Create a label/text box pair for each desired value, all within a list view
        for (entry in valueMap) {
            viewMap[entry.key] = inflater.inflate(R.layout.alert_label, null).let { v ->
                v.label_text.text = "${entry.key}: "
                v.input_edit_text.setText(entry.value.toString())
                v.input_edit_text.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                v
            }
            layout.addView(viewMap[entry.key])
        }

        AlertDialog
            .Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(layout)
            .setPositiveButton("Ok") { _, _ ->
                val outMap = mutableMapOf<String, Float>()
                for (entry in valueMap) {
                    val view = viewMap[entry.key]
                    if (view != null) {
                        outMap[entry.key] = view.input_edit_text.text.toString().toFloat()
                    }
                }
                onAccept(outMap)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    // Callback invoked when a dropdown element is tapped
    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        // Log.i("ItemSelected", "$position")
        currentLabel = labels[position]
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }
}
