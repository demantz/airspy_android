package com.mantz_it.airspy_android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Airspy USB Library for Android</h1>
 *
 * Module:      Airspy.java
 * Description: The Airspy class represents the Airspy device and
 *              acts as abstraction layer that manages the USB
 *              communication between the device and the application.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2015 Dennis Mantz
 * based on code of libairspy [https://github.com/airspy/host/tree/master/libairspy]:
 *     Copyright (c) 2013, Michael Ossmann <mike@ossmann.com>
 *     Copyright (c) 2012, Jared Boone <jared@sharebrained.com>
 *     Copyright (c) 2014, Youssef Touil <youssef@airspy.com>
 *     Copyright (c) 2014, Benjamin Vernoux <bvernoux@airspy.com>
 *     Copyright (c) 2015, Ian Gilmour <ian@sdrsharp.com>
 *     All rights reserved.
 *     Redistribution and use in source and binary forms, with or without modification,
 *     are permitted provided that the following conditions are met:
 *     - Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     - Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     - Neither the name of Great Scott Gadgets nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class Airspy implements Runnable {

	// Sample types:
	public static final int AIRSPY_SAMPLE_FLOAT32_IQ = 0;		// 2 * 32bit float per sample
	public static final int AIRSPY_SAMPLE_FLOAT32_REAL = 1;		// 1 * 32bit float per sample
	public static final int AIRSPY_SAMPLE_INT16_IQ = 2;			// 2 * 16bit int per sample
	public static final int AIRSPY_SAMPLE_INT16_REAL = 3;		// 1 * 16bit int per sample
	public static final int AIRSPY_SAMPLE_UINT16_REAL = 4;		// 1 * 16bit unsigned int per sample (raw)

	// Attributes to hold the USB related objects:
	private UsbManager usbManager = null;
	private UsbDevice usbDevice = null;
	private UsbInterface usbInterface = null;
	private UsbDeviceConnection usbConnection = null;
	private UsbEndpoint usbEndpointIN = null;
	private UsbEndpoint usbEndpointOUT = null;

	private int receiverMode = AIRSPY_RECEIVER_MODE_OFF;			// current mode of the Airspy
	private int sampleType = AIRSPY_SAMPLE_UINT16_REAL;				// Type of the samples that should be delivered
	private boolean packingEnabled = false;							// is packing currently enabled in the Airspy?
	private boolean rawMode = false;								// if true, the conversion thread is bypassed and the
																	// user will access the usbQueue directly
	private Thread usbThread = null;								// hold the receiver Thread if running
	private int usbQueueSize = 16;									// Size of the usbQueue
	private ArrayBlockingQueue<byte[]> usbQueue = null;				// queue that buffers samples received from the Airspy
	private ArrayBlockingQueue<byte[]> usbBufferPool = null;		// queue that holds spare buffers which can be
																	// reused while receiving  samples from the Airspy
	private int conversionQueueSize = 20;							// Size of the conversionQueue
	private ArrayBlockingQueue<short[]> conversionQueueInt16 = null;		// queue that buffers samples that were processed by
																			// the conversion thread (if sample type is int16)
	private ArrayBlockingQueue<short[]> conversionBufferPoolInt16 = null;	// queue that holds spare buffers which can be
																			// used for conversion processing (if sample type is int16)
	private ArrayBlockingQueue<float[]> conversionQueueFloat = null;		// queue that buffers samples that were processed by
																			// the conversion thread (if sample type is float)
	private ArrayBlockingQueue<float[]> conversionBufferPoolFloat = null;	// queue that holds spare buffers which can be
																			// used for conversion processing (if sample type is float)
	private int usbPacketSize = 1024 * 16;							// Buffer Size of each UsbRequest
	private AirspyInt16Converter int16Converter = null;				// Reference to the int16 converter
	private AirspyFloatConverter floatConverter = null;				// Reference to the float converter

	// startTime (in ms since 1970) and packetCounter for statistics:
	private long receiveStartTime = 0;
	private long receivePacketCounter = 0;

	// Receiver Modes:
	public static final int AIRSPY_RECEIVER_MODE_OFF = 0;
	public static final int AIRSPY_RECEIVER_MODE_RECEIVE = 1;

	// USB Vendor Requests (from airspy_commands.h)
	private static final int AIRSPY_INVALID = 0;
	private static final int AIRSPY_RECEIVER_MODE = 1;
	private static final int AIRSPY_SI5351C_WRITE = 2;
	private static final int AIRSPY_SI5351C_READ = 3;
	private static final int AIRSPY_R820T_WRITE = 4;
	private static final int AIRSPY_R820T_READ = 5;
	private static final int AIRSPY_SPIFLASH_ERASE = 6;
	private static final int AIRSPY_SPIFLASH_WRITE = 7;
	private static final int AIRSPY_SPIFLASH_READ = 8;
	private static final int AIRSPY_BOARD_ID_READ = 9;
	private static final int AIRSPY_VERSION_STRING_READ = 10;
	private static final int AIRSPY_BOARD_PARTID_SERIALNO_READ = 11;
	private static final int AIRSPY_SET_SAMPLERATE = 12;
	private static final int AIRSPY_SET_FREQ = 13;
	private static final int AIRSPY_SET_LNA_GAIN = 14;
	private static final int AIRSPY_SET_MIXER_GAIN = 15;
	private static final int AIRSPY_SET_VGA_GAIN = 16;
	private static final int AIRSPY_SET_LNA_AGC = 17;
	private static final int AIRSPY_SET_MIXER_AGC = 18;
	private static final int AIRSPY_MS_VENDOR_CMD = 19;
	private static final int AIRSPY_SET_RF_BIAS_CMD = 20;
	private static final int AIRSPY_GPIO_WRITE = 21;
	private static final int AIRSPY_GPIO_READ = 22;
	private static final int AIRSPY_GPIODIR_WRITE = 23;
	private static final int AIRSPY_GPIODIR_READ = 24;
	private static final int AIRSPY_GET_SAMPLERATES = 25;
	private static final int AIRSPY_SET_PACKING = 26;

	// Some Constants:
	private static final String LOGTAG = "airspy_android";
	private static final String AIRSPY_USB_PERMISSION = "com.mantz_it.airspy_android.USB_PERMISSION";
	private static final int numUsbRequests = 16;        // Number of parallel UsbRequests

	/**
	 * Initializing the Airspy Instance with a USB Device. This will try to request
	 * the permissions to open the USB device and then create an instance of
	 * the Airspy class and pass it back via the callbackInterface
	 *
	 * @param context           Application context. Used to retrieve System Services (USB)
	 * @param callbackInterface This interface declares two methods that are called if the
	 *                          device is ready or if there was an error
	 * @return false if no Airspy could be found
	 */
	public static boolean initAirspy(Context context, final AirspyCallbackInterface callbackInterface) {
		final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		UsbDevice airspyUsbDvice = null;

		if (usbManager == null) {
			Log.e(LOGTAG, "initAirspy: Couldn't get an instance of UsbManager!");
			return false;
		}

		// Get a list of connected devices
		HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

		if (deviceList == null) {
			Log.e(LOGTAG, "initAirspy: Couldn't read the USB device list!");
			return false;
		}

		Log.i(LOGTAG, "initAirspy: Found " + deviceList.size() + " USB devices.");

		// Iterate over the list. Use the first Device that matches an Airspy
		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();

			Log.d(LOGTAG, "initAirspy: deviceList: vendor=" + device.getVendorId() + " product=" + device.getProductId());

			// Airspy (Vendor ID: 7504 [0x1d50]; Product ID: 24737 [0x60a1] )
			if (device.getVendorId() == 7504 && device.getProductId() == 24737) {
				Log.i(LOGTAG, "initAirspy: Found Airspy at " + device.getDeviceName());
				airspyUsbDvice = device;
			}
		}

		// Check if we found a device:
		if (airspyUsbDvice == null) {
			Log.e(LOGTAG, "initAirspy: No Airspy Device found.");
			return false;
		}

		// Requesting Permissions:
		// First we define a broadcast receiver that handles the permission_granted intend:
		BroadcastReceiver permissionBroadcastReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				if (AIRSPY_USB_PERMISSION.equals(intent.getAction())) {
					UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
						// We have permissions to open the device! Lets init the airspy instance and
						// return it to the calling application.
						Log.d(LOGTAG, "initAirspy: Permission granted for device " + device.getDeviceName());
						try {
							Airspy airspy = new Airspy(usbManager, device);
							Toast.makeText(context, "Airspy at " + device.getDeviceName() + " is ready!", Toast.LENGTH_LONG).show();
							callbackInterface.onAirspyReady(airspy);
						} catch (AirspyUsbException e) {
							Log.e(LOGTAG, "initAirspy: Couldn't open device " + device.getDeviceName());
							Toast.makeText(context, "Couldn't open Airspy device", Toast.LENGTH_LONG).show();
							callbackInterface.onAirspyError("Couldn't open device " + device.getDeviceName());
						}
					} else {
						Log.e(LOGTAG, "initAirspy: Permission denied for device " + device.getDeviceName());
						Toast.makeText(context, "Permission denied to open Airspy device", Toast.LENGTH_LONG).show();
						callbackInterface.onAirspyError("Permission denied for device " + device.getDeviceName());
					}
				}

				// unregister the Broadcast Receiver:
				context.unregisterReceiver(this);
			}
		};

		// Now create a intent to request for the permissions and register the broadcast receiver for it:
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(AIRSPY_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(AIRSPY_USB_PERMISSION);
		context.registerReceiver(permissionBroadcastReceiver, filter);

		// Fire the request:
		usbManager.requestPermission(airspyUsbDvice, mPermissionIntent);
		Log.d(LOGTAG, "Permission request for device " + airspyUsbDvice.getDeviceName() + " was send. waiting...");

		return true;
	}

	/**
	 * Initializing the Airspy Instance with a USB Device.
	 * Note: The application must have reclaimed permissions to
	 * access the USB Device BEFOR calling this constructor.
	 *
	 * @param usbManager Instance of the USB Manager (System Service)
	 * @param usbDevice  Instance of an USB Device representing the Airspy
	 * @throws AirspyUsbException
	 */
	private Airspy(UsbManager usbManager, UsbDevice usbDevice) throws AirspyUsbException {
		// Initialize the class attributes:
		this.usbManager = usbManager;
		this.usbDevice = usbDevice;

		// For detailed trouble shooting: Read out information of the device:
		Log.i(LOGTAG, "constructor: create Airspy instance from " + usbDevice.getDeviceName()
				+ ". Vendor ID: " + usbDevice.getVendorId() + " Product ID: " + usbDevice.getProductId());
		Log.i(LOGTAG, "constructor: device protocol: " + usbDevice.getDeviceProtocol());
		Log.i(LOGTAG, "constructor: device class: " + usbDevice.getDeviceClass()
				+ " subclass: " + usbDevice.getDeviceSubclass());
		Log.i(LOGTAG, "constructor: interface count: " + usbDevice.getInterfaceCount());

		try {
			// Extract interface from the device:
			this.usbInterface = usbDevice.getInterface(0);

			// For detailed trouble shooting: Read out interface information of the device:
			Log.i(LOGTAG, "constructor: [interface 0] interface protocol: " + usbInterface.getInterfaceProtocol()
					+ " subclass: " + usbInterface.getInterfaceSubclass());
			Log.i(LOGTAG, "constructor: [interface 0] interface class: " + usbInterface.getInterfaceClass());
			Log.i(LOGTAG, "constructor: [interface 0] endpoint count: " + usbInterface.getEndpointCount());

			// Extract the endpoint from the device:
			this.usbEndpointIN = usbInterface.getEndpoint(0);
			this.usbEndpointOUT = usbInterface.getEndpoint(1);

			// For detailed trouble shooting: Read out endpoint information of the interface:
			Log.i(LOGTAG, "constructor:     [endpoint 0 (IN)] address: " + usbEndpointIN.getAddress()
					+ " attributes: " + usbEndpointIN.getAttributes() + " direction: " + usbEndpointIN.getDirection()
					+ " max_packet_size: " + usbEndpointIN.getMaxPacketSize());
			Log.i(LOGTAG, "constructor:     [endpoint 1 (OUT)] address: " + usbEndpointOUT.getAddress()
					+ " attributes: " + usbEndpointOUT.getAttributes() + " direction: " + usbEndpointOUT.getDirection()
					+ " max_packet_size: " + usbEndpointOUT.getMaxPacketSize());

			// Open the device:
			this.usbConnection = usbManager.openDevice(usbDevice);

			if (this.usbConnection == null) {
				Log.e(LOGTAG, "constructor: Couldn't open Airspy USB Device: openDevice() returned null!");
				throw (new AirspyUsbException("Couldn't open Airspy USB Device! (device is gone)"));
			}
		} catch (Exception e) {
			Log.e(LOGTAG, "constructor: Couldn't open Airspy USB Device: " + e.getMessage());
			throw (new AirspyUsbException("Error: Couldn't open Airspy USB Device!"));
		}
	}

	/**
	 * This returns the size of the packets that are received by the
	 * application from the airspy (AFTER unpacking was done!).
	 * Note that the size is measured in bytes and does not account
	 * for the type conversion that is done by the Converter classes.
	 * (i.e. the size is correct for all int16 sample_types but has
	 * to be multiplyed by 2 to fit the float32 sample_types!)
	 *
	 * @return Packet size in Bytes
	 */
	public int getUsbPacketSize() {
		if(rawMode || !packingEnabled)
			return usbPacketSize;
		else
			return usbPacketSize * 4 / 3;
	}

	/**
	 * This returns the number of packets (of size getUsbPacketSize()) received since start.
	 *
	 * @return Number of packets (of size getUsbPacketSize()) received since start
	 */
	public long getReceiverPacketCounter() {
		return this.receivePacketCounter;
	}

	/**
	 * This returns the time in milliseconds since receiving was started.
	 *
	 * @return time in milliseconds since receiving was started.
	 */
	public long getReceivingTime() {
		if (this.receiveStartTime == 0)
			return 0;
		return System.currentTimeMillis() - this.receiveStartTime;
	}

	/**
	 * Returns the average rx transfer rate in byte/seconds.
	 *
	 * @return average transfer rate in byte/seconds
	 */
	public long getAverageReceiveRate() {
		long transTime = this.getReceivingTime() / 1000;    // Transfer Time in seconds
		if (transTime == 0)
			return 0;
		return this.getReceiverPacketCounter() * this.getUsbPacketSize() / transTime;
	}

	/**
	 * Returns the current mode of the airspy (off / receiving)
	 *
	 * @return AIRSPY_TRANSCEIVER_MODE_OFF or *_RECEIVE
	 */
	public int getReceiverMode() {
		return receiverMode;
	}

	/**
	 * Converts a byte array into an integer using little endian byteorder.
	 *
	 * @param b      byte array (length 4)
	 * @param offset offset pointing to the first byte in the bytearray that should be used
	 * @return integer
	 */
	private int byteArrayToInt(byte[] b, int offset) {
		return b[offset + 0] & 0xFF | (b[offset + 1] & 0xFF) << 8 |
				(b[offset + 2] & 0xFF) << 16 | (b[offset + 3] & 0xFF) << 24;
	}

	/**
	 * Converts a byte array into a long integer using little endian byteorder.
	 *
	 * @param b      byte array (length 8)
	 * @param offset offset pointing to the first byte in the bytearray that should be used
	 * @return long integer
	 */
	private long byteArrayToLong(byte[] b, int offset) {
		return b[offset + 0] & 0xFF | (b[offset + 1] & 0xFF) << 8 | (b[offset + 2] & 0xFF) << 16 |
				(b[offset + 3] & 0xFF) << 24 | (b[offset + 4] & 0xFF) << 32 | (b[offset + 5] & 0xFF) << 40 |
				(b[offset + 6] & 0xFF) << 48 | (b[offset + 7] & 0xFF) << 56;
	}

	/**
	 * Converts an integer into a byte array using little endian byteorder.
	 *
	 * @param i integer
	 * @return byte array (length 4)
	 */
	private byte[] intToByteArray(int i) {
		byte[] b = new byte[4];
		b[0] = (byte) (i & 0xff);
		b[1] = (byte) ((i >> 8) & 0xff);
		b[2] = (byte) ((i >> 16) & 0xff);
		b[3] = (byte) ((i >> 24) & 0xff);
		return b;
	}

	/**
	 * Converts a long integer into a byte array using little endian byteorder.
	 *
	 * @param i long integer
	 * @return byte array (length 8)
	 */
	private byte[] longToByteArray(long i) {
		byte[] b = new byte[8];
		b[0] = (byte) (i & 0xff);
		b[1] = (byte) ((i >> 8) & 0xff);
		b[2] = (byte) ((i >> 16) & 0xff);
		b[3] = (byte) ((i >> 24) & 0xff);
		b[4] = (byte) ((i >> 32) & 0xff);
		b[5] = (byte) ((i >> 40) & 0xff);
		b[6] = (byte) ((i >> 48) & 0xff);
		b[7] = (byte) ((i >> 56) & 0xff);
		return b;
	}

	/**
	 * Sets the sample type for the Airspy. This affects which converter is used to process the
	 * samples after they were received from the Airspy. This setting can only be changed while
	 * the Airspy is in receiver mode OFF!
	 *
	 * @param sampleType AIRSPY_SAMPLE_INT16_REAL, *_INT16_IQ, *FLOAT_REAL, ...
	 * @return true on success; false if the Airspy is not in receiver mode OFF or invalid sample type
	 */
	public boolean setSampleType(int sampleType) {
		if (receiverMode != AIRSPY_RECEIVER_MODE_OFF) {
			Log.e(LOGTAG, "setSampleType: Airspy is not in receiver mode OFF. Cannot change sample type!");
			return false;
		}

		if (sampleType < 0 || sampleType > 4) {
			Log.e(LOGTAG, "setSampleType: Not a valid sample type: " + sampleType);
			return false;
		}

		this.sampleType = sampleType;
		return true;
	}

	/**
	 * Enables / Disables raw-mode. It raw mode is enabled, the user can access
	 * the usbQueue directly (conversion threads are not started / bypassed).
	 *
	 * Note that the raw mode can only be changed if the Airspy is currently
	 * in receiver mode OFF!
	 *
	 * @param enabled	true to enable raw mode, false to disable raw mode
	 * @return true on success, false on error
	 */
	public boolean setRawMode(boolean enabled) {
		if (receiverMode != AIRSPY_RECEIVER_MODE_OFF) {
			Log.e(LOGTAG, "setRawMode: Airspy is not in receiver mode OFF. Cannot change rawMode!");
			return false;
		}
		this.rawMode = enabled;
		return true;
	}

	/**
	 * @return true if rawMode is enabled; false if not
	 */
	public boolean getRawMode() {
		return rawMode;
	}

	/**
	 * Executes a Request to the USB interface.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @param endpoint USB_DIR_IN or USB_DIR_OUT
	 * @param request  request type (AIRSPY...)
	 * @param value    value to use in the controlTransfer call
	 * @param index    index to use in the controlTransfer call
	 * @param buffer   buffer to use in the controlTransfer call
	 * @return count of received bytes. Negative on error
	 * @throws AirspyUsbException
	 */
	private int sendUsbRequest(int endpoint, int request, int value, int index, byte[] buffer) throws AirspyUsbException {
		int len = 0;

		// Determine the length of the buffer:
		if (buffer != null)
			len = buffer.length;

		// Claim the usb interface
		if (!this.usbConnection.claimInterface(this.usbInterface, true)) {
			Log.e(LOGTAG, "Couldn't claim Airspy USB Interface!");
			throw (new AirspyUsbException("Couldn't claim Airspy USB Interface!"));
		}

		// Send Board ID Read request
		len = this.usbConnection.controlTransfer(
				endpoint | UsbConstants.USB_TYPE_VENDOR,    // Request Type
				request,    // Request
				value,        // Value (unused)
				index,        // Index (unused)
				buffer,        // Buffer
				len,        // Length
				0            // Timeout
		);

		// Release usb interface
		this.usbConnection.releaseInterface(this.usbInterface);

		return len;
	}

	/**
	 * Returns the Board ID of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return Airspy Board ID
	 * @throws AirspyUsbException
	 */
	public byte getBoardID() throws AirspyUsbException {
		byte[] buffer = new byte[1];

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_BOARD_ID_READ, 0, 0, buffer) != 1) {
			Log.e(LOGTAG, "getBoardID: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		return buffer[0];
	}

	/**
	 * Converts the Board ID into a human readable String
	 *
	 * @param boardID boardID to convert
	 * @return Board ID interpretation as String
	 * @throws AirspyUsbException
	 */
	public static String convertBoardIdToString(int boardID) {
		switch (boardID) {
			case 0:
				return "AIRSPY";
			default:
				return "INVALID BOARD ID";
		}
	}

	/**
	 * Returns the Version String of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return Airspy Version String
	 * @throws AirspyUsbException
	 */
	public String getVersionString() throws AirspyUsbException {
		byte[] buffer = new byte[255];
		int len = 0;

		len = this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_VERSION_STRING_READ, 0, 0, buffer);

		if (len < 1) {
			Log.e(LOGTAG, "getVersionString: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		return new String(buffer);
	}


	/**
	 * Returns the Part ID + Serial Number of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return int[2+6] => int[0-1] is Part ID; int[2-5] is Serial No
	 * @throws AirspyUsbException
	 */
	public int[] getPartIdAndSerialNo() throws AirspyUsbException {
		byte[] buffer = new byte[8 + 16];
		int[] ret = new int[2 + 4];

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_BOARD_PARTID_SERIALNO_READ,
				0, 0, buffer) != 8 + 16) {
			Log.e(LOGTAG, "getPartIdAndSerialNo: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		for (int i = 0; i < 6; i++) {
			ret[i] = this.byteArrayToInt(buffer, 4 * i);
		}

		return ret;
	}

	/**
	 * Returns the supported sample rates of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return array of supported sample rates (in Sps)
	 * @throws AirspyUsbException
	 */
	public int[] getSampleRates() throws AirspyUsbException {
		byte[] buffer = new byte[4];
		int count;
		int[] rates;
		int len = 0;

		// First read the number of supported sample rates:
		len = this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_GET_SAMPLERATES, 0, 0, buffer);

		if (len < buffer.length) {
			Log.e(LOGTAG, "getSampleRates: USB Transfer failed (reading count)!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}
		count = byteArrayToInt(buffer, 0);
		Log.d(LOGTAG, "getSampleRates: Airspy supports " + count + " different sample rates!");

		// Now read the actual sample rates:
		buffer = new byte[count * 4]; // every rate is stored in a 32bit int
		rates = new int[count];
		len = this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_GET_SAMPLERATES, 0, count, buffer);

		if (len < buffer.length) {
			Log.e(LOGTAG, "getSampleRates: USB Transfer failed (reading rates)!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		for (int i = 0; i < rates.length; i++) {
			rates[i] = byteArrayToInt(buffer, i * 4);
		}

		return rates;
	}

	/**
	 * Sets the Sample Rate of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    sampRateIdx        Index (see getSampleRates) of the sample rate that should be set (starting from 0)
	 */
	public boolean setSampleRate(int sampRateIdx) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		int len = this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_SAMPLERATE, 0, sampRateIdx, retVal);

		if (len != 1) {
			Log.e(LOGTAG, "setSampleRate: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setSampleRate: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Sets the Mixer Gain of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    gain    Mixer Gain (0-15)
	 */
	public boolean setMixerGain(int gain) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (gain > 15 || gain < 0) {
			Log.e(LOGTAG, "setMixerGain: Mixer gain must be within 0-15!");
			return false;
		}

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_MIXER_GAIN, 0, gain, retVal) != 1) {
			Log.e(LOGTAG, "setMixerGain: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setMixerGain: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Sets the VGA Gain of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    gain    VGA Gain (0-15)
	 */
	public boolean setVGAGain(int gain) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (gain > 15 || gain < 0) {
			Log.e(LOGTAG, "setVGAGain: VGA gain must be within 0-15!");
			return false;
		}

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_VGA_GAIN, 0, gain, retVal) != 1) {
			Log.e(LOGTAG, "setVGAGain: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setVGAGain: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Sets the LNA Gain of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    gain    LNA Gain (0-14)
	 */
	public boolean setLNAGain(int gain) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (gain > 14 || gain < 0) {
			Log.e(LOGTAG, "setLNAGain: LNA gain must be within 0-14!");
			return false;
		}

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_LNA_GAIN, 0, gain, retVal) != 1) {
			Log.e(LOGTAG, "setLNAGain: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setLNAGain: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Enables / Disables the LNA automatic gain control of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    enable    true for enable; false for disable
	 */
	public boolean setLNAAutomaticGainControl(boolean enable) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_LNA_AGC, 0, enable ? 1 : 0, retVal) != 1) {
			Log.e(LOGTAG, "setLNAAutomaticGainControl: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setLNAAutomaticGainControl: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Enables / Disables the Mixer automatic gain control of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    enable    true for enable; false for disable
	 */
	public boolean setMixerAutomaticGainControl(boolean enable) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_MIXER_AGC, 0, enable ? 1 : 0, retVal) != 1) {
			Log.e(LOGTAG, "setMixerAutomaticGainControl: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setMixerAutomaticGainControl: Airspy returned with an error!");
			return false;
		}

		return true;
	}

	/**
	 * Enables / Disables packing for the Airspy. This is only possible if the
	 * receiver mode is currently OFF!
	 * If packing is enabled, the Airspy will compress the samples before sending
	 * them via USB. The Converter threads will unpack the samples automatically.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    enable    true for enable; false for disable
	 */
	public boolean setPacking(boolean enable) throws AirspyUsbException {
		byte[] retVal = new byte[1];

		if (receiverMode != AIRSPY_RECEIVER_MODE_OFF) {
			Log.e(LOGTAG, "setPacking: Airspy is not in receiver mode OFF. Cannot change packing setting!");
			return false;
		}

		if (this.sendUsbRequest(UsbConstants.USB_DIR_IN, AIRSPY_SET_PACKING, 0, enable ? 1 : 0, retVal) != 1) {
			Log.e(LOGTAG, "setPacking: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		if (retVal[0] < 0) {
			Log.e(LOGTAG, "setPacking: Airspy returned with an error!");
			return false;
		}

		this.packingEnabled = enable;

		return true;
	}

	/**
	 * Sets the Frequency of the Airspy.
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    frequency    Frequency in Hz
	 */
	public boolean setFrequency(int frequency) throws AirspyUsbException {
		byte[] freq = intToByteArray(frequency);

		Log.d(LOGTAG, "Tune Airspy to " + frequency + "Hz...");

		if (this.sendUsbRequest(UsbConstants.USB_DIR_OUT, AIRSPY_SET_FREQ, 0, 0, freq) != 4) {
			Log.e(LOGTAG, "setFrequency: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		return true;
	}


	/**
	 * Sets the Receiver Mode of the Airspy (OFF,RX)
	 * <p/>
	 * Note: This function interacts with the USB Hardware and
	 * should not be called from a GUI Thread!
	 *
	 * @return true on success
	 * @throws AirspyUsbException
	 * @param    mode        AIRSPY_RECEIVER_MODE_OFF, *_RECEIVE
	 */
	public boolean setReceiverMode(int mode) throws AirspyUsbException {
		if (mode != AIRSPY_RECEIVER_MODE_OFF && mode != AIRSPY_RECEIVER_MODE_RECEIVE) {
			Log.e(LOGTAG, "Invalid Receiver Mode: " + mode);
			return false;
		}

		this.receiverMode = mode;

		if (this.sendUsbRequest(UsbConstants.USB_DIR_OUT, AIRSPY_RECEIVER_MODE,
				mode, 0, null) != 0) {
			Log.e(LOGTAG, "setReceiverMode: USB Transfer failed!");
			throw (new AirspyUsbException("USB Transfer failed!"));
		}

		return true;
	}

	/**
	 * Starts receiving.
	 *
	 * After calling this function the user should also call getFloatQueue()/getInt16Queue()/getRawQueue()
	 * to read the received samples and getFloatReturnPoolQueue()/getInt16ReturnPoolQueue()/
	 * getRawReturnPoolQueue() to return used buffers to the Airspy class.
	 *
	 * @throws AirspyUsbException
	 */
	public boolean startRX() throws AirspyUsbException {
		// Create the usbQueue that holds samples received from the Airspy
		this.usbQueue = new ArrayBlockingQueue<byte[]>(usbQueueSize);

		// Create another queue that will be used to collect old buffers for reusing them.
		// TODO: maybe this can be optimized: check if the pool already fits the current requirements and don't reallocate it!
		this.usbBufferPool = new ArrayBlockingQueue<byte[]>(usbQueueSize);
		for (int i = 0; i < usbQueueSize; i++)
			this.usbBufferPool.offer(new byte[usbPacketSize]);        // Allocate buffers

		// Create queues for the Conversion Thread and start it (if not in rawMode)
		if(!rawMode) {
			switch (sampleType) {
				case AIRSPY_SAMPLE_FLOAT32_IQ:
				case AIRSPY_SAMPLE_FLOAT32_REAL:
					this.conversionQueueFloat = new ArrayBlockingQueue<float[]>(conversionQueueSize);
					this.conversionBufferPoolFloat = new ArrayBlockingQueue<float[]>(conversionQueueSize);
					for (int i = 0; i < conversionQueueSize; i++)
						this.conversionBufferPoolFloat.offer(new float[getUsbPacketSize()/2]);        // Allocate buffers
					try {
						floatConverter = new AirspyFloatConverter(sampleType, packingEnabled, usbQueue, usbBufferPool, conversionQueueFloat, conversionBufferPoolFloat);
						floatConverter.start();
					} catch (Exception e) {
						Log.e(LOGTAG, "startRX: Cannot create float converter: " + e.getMessage());
						return false;
					}
					break;
				case AIRSPY_SAMPLE_INT16_IQ:
				case AIRSPY_SAMPLE_INT16_REAL:
				case AIRSPY_SAMPLE_UINT16_REAL:
					this.conversionQueueInt16 = new ArrayBlockingQueue<short[]>(conversionQueueSize);
					this.conversionBufferPoolInt16 = new ArrayBlockingQueue<short[]>(conversionQueueSize);
					for (int i = 0; i < conversionQueueSize; i++)
						this.conversionBufferPoolInt16.offer(new short[getUsbPacketSize()/2]);        // Allocate buffers
					try {
						int16Converter = new AirspyInt16Converter(sampleType, packingEnabled, usbQueue, usbBufferPool, conversionQueueInt16, conversionBufferPoolInt16);
						int16Converter.start();
					} catch (Exception e) {
						Log.e(LOGTAG, "startRX: Cannot create int16 converter: " + e.getMessage());
						return false;
					}
					break;
			}
		}

		// Signal the Airspy Device to start receiving:
		this.setReceiverMode(AIRSPY_RECEIVER_MODE_RECEIVE);

		// Start the Thread to queue the received samples:
		this.usbThread = new Thread(this);
		this.usbThread.start();

		// Reset the packet counter and start time for statistics:
		this.receiveStartTime = System.currentTimeMillis();
		this.receivePacketCounter = 0;

		return true;
	}

	/**
	 * Stops receiving
	 *
	 * @throws AirspyUsbException
	 */
	public void stop() throws AirspyUsbException {
		// Signal the Airspy Device to stop receiving:
		this.setReceiverMode(AIRSPY_RECEIVER_MODE_OFF);
	}


	public void run() {
		if (receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE) {
			Log.e(LOGTAG, "run: Invalid receiver mode: " + receiverMode);
			return;
		}

		receiveLoop();
	}

	/**
	 * This method will be executed in a separate Thread after the Airspy starts receiving
	 * Samples. It will return as soon as the transceiverMode changes or an error occurs.
	 */
	private void receiveLoop() {
		UsbRequest[] usbRequests = new UsbRequest[numUsbRequests];
		ByteBuffer buffer;

		try {
			// Create, initialize and queue all usb requests:
			for (int i = 0; i < numUsbRequests; i++) {
				// Get a ByteBuffer for the request from the buffer pool:
				try {
					byte[] tmpBuffer = usbBufferPool.poll(1, TimeUnit.SECONDS);
					if (tmpBuffer == null) {
						// We hit the timeout.
						Log.e(LOGTAG, "receiveLoop: Buffer pool is empty. Stop receiving!");
						this.stop();
						break;
					}
					buffer = ByteBuffer.wrap(tmpBuffer);
				} catch (InterruptedException e) {
					Log.e(LOGTAG, "receiveLoop: Interrupted while waiting on buffers in the pool. Stop receiving!");
					this.stop();
					break;
				}

				// Initialize the USB Request:
				usbRequests[i] = new UsbRequest();
				usbRequests[i].initialize(usbConnection, usbEndpointIN);
				usbRequests[i].setClientData(buffer);

				// Queue the request
				if (!usbRequests[i].queue(buffer, usbPacketSize)) {
					Log.e(LOGTAG, "receiveLoop: Couldn't queue USB Request.");
					this.stop();
					break;
				}
			}

			// Run loop until transceiver mode changes...
			while (this.receiverMode == AIRSPY_RECEIVER_MODE_RECEIVE) {
				// Wait for a request to return. This will block until one of the requests is ready.
				UsbRequest request = usbConnection.requestWait();

				if (request == null) {
					Log.e(LOGTAG, "receiveLoop: Didn't receive USB Request.");
					break;
				}

				// Make sure we got an UsbRequest for the IN endpoint!
				if (request.getEndpoint() != usbEndpointIN)
					continue;

				// Extract the buffer
				buffer = (ByteBuffer) request.getClientData();

				// Increment the packetCounter (for statistics)
				this.receivePacketCounter++;

				// Put the received samples into the usbQueue, so that they can be read by the
				// conversion thread (or the application if in raw mode)
				try {
					if (!this.usbQueue.offer(buffer.array(), 1000, TimeUnit.MILLISECONDS)) {
						// We hit the timeout.
						Log.e(LOGTAG, "receiveLoop: Queue is full. Stop receiving!");
						break;
					}
				} catch (InterruptedException e) {
					Log.e(LOGTAG, "receiveLoop: Interrupted while putting a buffer in the queue. Stop receiving!");
					break;
				}

				// Get a fresh ByteBuffer for the request from the buffer pool:
				try {
					byte[] tmpBuffer = usbBufferPool.poll(10, TimeUnit.SECONDS);
					if (tmpBuffer == null) {
						// We hit the timeout.
						Log.e(LOGTAG, "receiveLoop: Buffer pool is empty. Stop receiving!");
						break;
					}
					buffer = ByteBuffer.wrap(tmpBuffer);
				} catch (InterruptedException e) {
					Log.e(LOGTAG, "receiveLoop: Interrupted while waiting on buffers in the pool. Stop receiving!");
					break;
				}
				request.setClientData(buffer);

				// Queue the request again...
				if (!request.queue(buffer, usbPacketSize)) {
					Log.e(LOGTAG, "receiveLoop: Couldn't queue USB Request.");
					break;
				}
			}
		} catch (AirspyUsbException e) {
			Log.e(LOGTAG, "receiveLoop: USB Error!");
		}

		// Receiving is done. Cancel and close all usb requests:
		for (UsbRequest request : usbRequests) {
			if (request != null) {
				request.cancel();
				//request.close();    <-- This will cause the VM to crash with a SIGABRT when the next transceive starts?!?
			}
		}

		// If the receiverMode is still on RECEIVE, we stop Receiving:
		if (this.receiverMode == AIRSPY_RECEIVER_MODE_RECEIVE) {
			try {
				this.stop();
			} catch (AirspyUsbException e) {
				Log.e(LOGTAG, "receiveLoop: Error while stopping RX!");
			}
		}

		// Stop all converters if running:
		if(int16Converter != null)
			int16Converter.requestStop();
		if(floatConverter != null)
			floatConverter.requestStop();
	}

	/**
	 * Call this after startRX() to get the queue with the received and converted samples (if sample type is int16)
	 * Also get a reference to the int16ReturnPoolQueue by calling getInt16ReturnPoolQueue() to return the buffers!
	 * @return ArrayBlockingQueue which is filled by the conversion thread (with received and converted int16 samples)
	 */
	public ArrayBlockingQueue<short[]> getInt16Queue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(sampleType == AIRSPY_SAMPLE_INT16_IQ || sampleType == AIRSPY_SAMPLE_INT16_REAL || sampleType == AIRSPY_SAMPLE_UINT16_REAL)
			return conversionQueueInt16;
		else
			return null;
	}

	/**
	 * Call this after startRX() to get a queue to the bufferPool. Return every buffer you got from the int16Queue into
	 * the pool after usage (if sample type is int16)
	 * @return ArrayBlockingQueue that is used to collect buffers from the int16Queue after usage
	 */
	public ArrayBlockingQueue<short[]> getInt16ReturnPoolQueue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(sampleType == AIRSPY_SAMPLE_INT16_IQ || sampleType == AIRSPY_SAMPLE_INT16_REAL || sampleType == AIRSPY_SAMPLE_UINT16_REAL)
			return conversionBufferPoolInt16;
		else
			return null;
	}

	/**
	 * Call this after startRX() to get the queue with the received and converted samples (if sample type is float)
	 * Also get a reference to the floatReturnPoolQueue by calling getFloatReturnPoolQueue() to return the buffers!
	 * @return ArrayBlockingQueue which is filled by the conversion thread (with received and converted float samples)
	 */
	public ArrayBlockingQueue<float[]> getFloatQueue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(sampleType == AIRSPY_SAMPLE_FLOAT32_IQ || sampleType == AIRSPY_SAMPLE_FLOAT32_REAL)
			return conversionQueueFloat;
		else
			return null;
	}

	/**
	 * Call this after startRX() to get a queue to the bufferPool. Return every buffer you got from the floatQueue into
	 * the pool after usage (if sample type is float)
	 * @return ArrayBlockingQueue that is used to collect buffers from the floatQueue after usage
	 */
	public ArrayBlockingQueue<float[]> getFloatReturnPoolQueue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(sampleType == AIRSPY_SAMPLE_FLOAT32_IQ || sampleType == AIRSPY_SAMPLE_FLOAT32_REAL)
			return conversionBufferPoolFloat;
		else
			return null;
	}

	/**
	 * Call this after startRX() to get the queue with the received raw samples (if rawMode is enabled)
	 * Also get a reference to the rawReturnPoolQueue by calling getRawReturnPoolQueue() to return the buffers!
	 * @return ArrayBlockingQueue which is filled by the conversion thread (with received raw samples)
	 */
	public ArrayBlockingQueue<byte[]> getRawQueue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(rawMode)
			return usbQueue;
		else
			return null;
	}

	/**
	 * Call this after startRX() to get a queue to the bufferPool. Return every buffer you got from the rawQueue into
	 * the pool after usage (if rawMode is enabled)
	 * @return ArrayBlockingQueue that is used to collect buffers from the rawQueue after usage
	 */
	public ArrayBlockingQueue<byte[]> getRawReturnPoolQueue() {
		if(receiverMode != AIRSPY_RECEIVER_MODE_RECEIVE)
			return null;
		if(rawMode)
			return usbBufferPool;
		else
			return null;
	}

	/**
	 * This method is needed if packing is enabled on the Airspy.
	 * It will read bytes from the source array, unpack them
	 * and write the results to dest. Note the length parameter
	 * specifies the number of bytes that should be written to
	 * the destination array and not the number of bytes that will
	 * be read from the source!
	 * @param src		source array containing at least length * 3/4 packed bytes
	 * @param dest		destination array. Size must be greater or equal to length
	 * @param length	number of bytes that should be written to dest. Must be multiple of 16!
	 */
	public static void unpackSamples(byte[] src, byte[] dest, int length) {
		if (length % 16 != 0) {
			Log.e(LOGTAG, "convertSamplesFloat: length has to be multiple of 16!");
			return;
		}

		if (src.length < 3 * dest.length / 4 || dest.length < length) {
			Log.e(LOGTAG, "convertSamplesFloat: input buffers have invalid length!");
			return;
		}

		for (int i = 0, j = 0; i < length; i += 16, j += 12) {
				/*                 [3]         [2]         [1]           [0]         [7]         [6]           [5]        [4]         [11]          [10]         [9]         [8]
				 * src         [--------][----    ----][--------]    [--------][----    ----][--------]    [--------][----    ----][--------]    [--------][----    ----][--------]
				 *
				 *                  [0]               [1]                 [2]               [3]                [4]                [5]                [6]               [7]
				 * samples [0000-------- ----][0000---- --------][0000-------- ----][0000---- --------][0000-------- ----][0000---- --------][0000-------- ----][0000---- --------]
				 *
				 *            [1]       [0]      [3]       [2]      [5]       [4]      [7]       [6]      [9]       [8]     [11]      [10]      [13]      [12]     [15]      [14]
				 * dest    [0000----|--------][0000----|--------][0000----|--------][0000----|--------][0000----|--------][0000----|--------][0000----|--------][0000----|--------]
				 */
			dest[i] = (byte) ((src[j + 3] << 4) & 0xF0 | (src[j + 2] >> 4) & 0x0F);
			dest[i + 1] = (byte) ((src[j + 3] >> 4) & 0x0F);
			dest[i + 2] = src[j + 1];
			dest[i + 3] = (byte) (src[j + 2] & 0x0F);
			dest[i + 4] = (byte) ((src[j] << 4) & 0xF0 | (src[j + 7] >> 4) & 0x0F);
			dest[i + 5] = (byte) ((src[j] >> 4) & 0x0F);
			dest[i + 6] = src[j + 6];
			dest[i + 7] = (byte) (src[j + 7] & 0x0F);
			dest[i + 8] = (byte) ((src[j + 5] << 4) & 0xF0 | (src[j + 4] >> 4) & 0x0F);
			dest[i + 9] = (byte) ((src[j + 5] >> 4) & 0x0F);
			dest[i + 10] = src[j + 11];
			dest[i + 11] = (byte) (src[j + 4] & 0x0F);
			dest[i + 12] = (byte) ((src[j + 10] << 4) & 0xF0 | (src[j + 9] >> 4) & 0x0F);
			dest[i + 13] = (byte) ((src[j + 10] >> 4) & 0x0F);
			dest[i + 14] = src[j + 8];
			dest[i + 15] = (byte) (src[j + 9] & 0x0F);

			// from airspy.c:
//				output[j + 0] = (input[i] >> 20) & 0xfff;
//				output[j + 1] = (input[i] >> 8) & 0xfff;
//				output[j + 2] = ((input[i] & 0xff) << 4) | ((input[i + 1] >> 28) & 0xf);
//				output[j + 3] = ((input[i + 1] & 0xfff0000) >> 16);
//				output[j + 4] = ((input[i + 1] & 0xfff0) >> 4);
//				output[j + 5] = ((input[i + 1] & 0xf) << 8) | ((input[i + 2] & 0xff000000) >> 24);
//				output[j + 6] = ((input[i + 2] >> 12) & 0xfff);
//				output[j + 7] = ((input[i + 2] & 0xfff));
		}
	}

	/**
	 * This Interface declares a callback method to return a Airspy instance to the application after it was opened
	 * by the initialization routine (asynchronous process because it includes requesting the USB permissions)
	 */
	public interface AirspyCallbackInterface {
		/**
		 * Called by initAirspy() after the device is ready to be used.
		 *
		 * @param airspy Instance of the Airspy that provides access to the device
		 */
		public void onAirspyReady(Airspy airspy);

		/**
		 * Called if there was an error when accessing the device.
		 *
		 * @param message Reason for the Error
		 */
		public void onAirspyError(String message);
	}

	/**
	 * This Exception will be thrown if an Error with the USB communication occurs.
	 */
	public class AirspyUsbException extends Exception {
		private static final long serialVersionUID = 1L;

		public AirspyUsbException(String message) {
			super(message);
		}
	}
}