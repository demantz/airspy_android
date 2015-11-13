package com.mantz_it.airspy_android;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Airspy USB Library for Android</h1>
 *
 * Module:      AirspyInt16Converter.java
 * Description: This class offers methods to convert the samples from the Airspy device
 *              to various INT16 types.
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
public class AirspyInt16Converter extends Thread{
	private static final String LOGTAG = "AirspyInt16Converter";
	private boolean stopRequested = false;
	private int sampleType = -1;
	private boolean packingEnabled = false;
	private ArrayBlockingQueue<byte[]> inputQueue;         // Queue from which the input samples are taken
	private ArrayBlockingQueue<byte[]> inputReturnQueue;   // Queue to return the used input buffers to the pool
	private ArrayBlockingQueue<short[]> outputQueue;       // Queue to deliver the converted samples
	private ArrayBlockingQueue<short[]> outputPoolQueue;   // Queue from which the output buffers are taken
	private int len = 0;
	private int firIndex = 0;
	private int delayIndex = 0;
	private short oldX = 0;
	private short oldY = 0;
	private int oldE = 0;
	private int firQueue[];
	private short delayLine[];
	private static final int SIZE_FACTOR = 16;

	// Hilbert kernel with zeros removed:
	public static final short HB_KERNEL_INT16[] = {
			-33,
			56,
			-100,
			166,
			-259,
			389,
			-571,
			829,
			-1220,
			1885,
			-3353,
			10389,
			10389,
			-3353,
			1885,
			-1220,
			829,
			-571,
			389,
			-259,
			166,
			-100,
			56,
			-33
	};

	/**
	 * Constructor for the int16 Converter
	 * @param sampleType		Desired sample type of the output samples (Airspy.AIRSPY_SAMPLE_INT16_IQ, *_INT16_REAL or *_UINT16_REAL
	 * @param packingEnabled	Indicates if the input samples are packed
	 * @param inputQueue		Queue from which the input samples are taken
	 * @param inputReturnQueue	Queue to return the used input buffers to the pool
	 * @param outputQueue		Queue to deliver the converted samples
	 * @param outputPoolQueue	Queue from which the output buffers are taken
	 * @throws Exception if the sample type does not match a int16 based type
	 */
	public AirspyInt16Converter(int sampleType, boolean packingEnabled, ArrayBlockingQueue<byte[]> inputQueue,
								ArrayBlockingQueue<byte[]> inputReturnQueue, ArrayBlockingQueue<short[]> outputQueue,
								ArrayBlockingQueue<short[]> outputPoolQueue) throws Exception {
		if(sampleType != Airspy.AIRSPY_SAMPLE_INT16_IQ && sampleType != Airspy.AIRSPY_SAMPLE_INT16_REAL && sampleType != Airspy.AIRSPY_SAMPLE_UINT16_REAL) {
			Log.e(LOGTAG, "constructor: Invalid sample type: " + sampleType);
			throw new Exception("Invalid sample type: " + sampleType);
		}
		this.sampleType = sampleType;
		this.packingEnabled = packingEnabled;
		this.inputQueue = inputQueue;
		this.inputReturnQueue = inputReturnQueue;
		this.outputQueue = outputQueue;
		this.outputPoolQueue = outputPoolQueue;
		this.len = HB_KERNEL_INT16.length;
		this.delayLine = new short[this.len / 2];
		this.firQueue = new int[this.len * SIZE_FACTOR];
	}

	/**
	 * Converts a byte array (little endian, unsigned-12bit-integer) to a short array (signed-16bit-integer)
	 *
	 * @param src   input samples (little endian, unsigned-12bit-integer); min. twice the size of output
	 * @param dest  output samples (signed-16bit-integer); min. of size 'count'
	 * @param count number of samples to process
	 */
	public static void convertSamplesInt16(byte[] src, short[] dest, int count) {
		if (src.length < 2 * count || dest.length < count) {
			Log.e(LOGTAG, "convertSamplesInt16: input buffers have invalid length: src=" + src.length + " dest=" + dest.length);
			return;
		}
		for (int i = 0; i < count; i++) {
				/*   src[2i+1] src[2i]
				 *  [--------|--------]
				 *      (xxxx xxxxxxxx) -2048
				 *                      << 4
				 *  [xxxxxxxx|xxxxxxxx] dest[i]
				 */
			dest[i] = (short) (((((src[2 * i + 1] & 0x0F) << 8) + (src[2 * i] & 0xFF)) - 2048) << 4);
		}
	}

	/**
	 * Converts a byte array (little endian, unsigned-12bit-integer) to a short array (unsigned-16bit-integer)
	 *
	 * @param src   input samples (little endian, unsigned-12bit-integer); min. twice the size of output
	 * @param dest  output samples (unsigned-16bit-integer); min. of size 'count'
	 * @param count number of samples to process
	 */
	public static void convertSamplesUint16(byte[] src, short[] dest, int count) {
		if (src.length < 2 * count || dest.length < count) {
			Log.e(LOGTAG, "convertSamplesUint16: input buffers have invalid length: src=" + src.length + " dest=" + dest.length);
			return;
		}
		for (int i = 0; i < count; i++) {
				/*   src[2i+1] src[2i]
				 *  [--------|--------]
				 *      (xxxx xxxxxxxx)
				 *                      << 4
				 *  [xxxxxxxx|xxxxxxxx] dest[i]
				 */
			dest[i] = (short) ((((src[2 * i + 1] & 0x0F) << 8) + (src[2 * i] & 0xFF)) << 4);
		}
	}

	public void requestStop() {
		this.stopRequested = true;
	}

	private void firInterleaved(short[] samples) {
		int acc;

		for (int i = 0; i < samples.length; i += 2) {
			firQueue[firIndex] = samples[i];
			acc = 0;

			// Auto vectorization works on VS2012, VS2013 and GCC
			for (int j = 0; j < len; j++) {
				acc += HB_KERNEL_INT16[j] * firQueue[firIndex + j];
			}

			if (--firIndex < 0) {
				firIndex = len * (SIZE_FACTOR - 1);
				System.arraycopy(firQueue, 0, firQueue, firIndex + 1, len - 1);
			}

			samples[i] = (short) (acc >> 15);
		}
	}

	private void delayInterleaved(short[] samples, int offset) {
		int halfLen = len >> 1;
		short res;

		for (int i = offset; i < samples.length; i += 2) {
			res = delayLine[delayIndex];
			delayLine[delayIndex] = samples[i];
			samples[i] = res;

			if (++delayIndex >= halfLen) {
				delayIndex = 0;
			}
		}
	}

	private void removeDC(short[] samples) {
		int u;
		short x, y, w, s;

		for (int i = 0; i < samples.length; i++) {
			x = samples[i];
			w = (short) (x - oldX);
			u = oldE + oldY * 32100;
			s = (short) (u >> 15);
			y = (short) (w + s);
			oldE = u - (s << 15);
			oldX = x;
			oldY = y;
			samples[i] = y;
		}
	}

	private void translateFs4(short[] samples) {
		for (int i = 0; i < samples.length; i += 4) {
			samples[i] = (short) -samples[i];
			samples[i + 1] = (short) (-samples[i + 1] >> 1);
			//samples[i + 2] = samples[i + 2];
			samples[i + 3] = (short) (samples[i + 3] >> 1);
		}

		firInterleaved(samples);
		delayInterleaved(samples, 1);
	}

	public void processSamplesInt16(short[] samples) {
		removeDC(samples);
		translateFs4(samples);
	}

	public void run() {
		byte[] inputBuffer;
		byte[] origInputBuffer;
		byte[] packingBuffer = null;
		short[] outputBuffer = null;

		while (!stopRequested) {
			// First we get a fresh set of input and output buffers from the queues:
			try {
				outputBuffer = outputPoolQueue.poll(1000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// Note: If the output buffer pool (filled by the user) is empty and the timeout is hit,
				// we just wait again. After some time the Airspy class will stop because its usbQueue
				// will run full.
				Log.e(LOGTAG, "run: Interrupted while waiting for buffers in the output pool. Lets wait for another round...");
				continue;
			}
			if(outputBuffer == null) {
				Log.e(LOGTAG, "run: No output buffers available in the pool. Let's query it again...");
				continue;
			}

			try {
				origInputBuffer = inputQueue.poll(1000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Log.e(LOGTAG, "run: Interrpted while waiting for buffers in the input queue. Stop!");
				stopRequested = true;
				continue;
			}
			if(origInputBuffer == null) {
				Log.e(LOGTAG, "run: No input buffers available in the queue. Stop!");
				stopRequested = true;
				continue;
			}

			// Maybe unpack the samples first:
			if (packingEnabled) {
				int unpackedLength = origInputBuffer.length * 4 / 3;
				if (packingBuffer == null || packingBuffer.length != unpackedLength)
					packingBuffer = new byte[unpackedLength];
				Airspy.unpackSamples(origInputBuffer, packingBuffer, unpackedLength);
				inputBuffer = packingBuffer;
			} else {
				inputBuffer = origInputBuffer;
			}

			// Next we do the processing for the conversion:
			switch (sampleType) {
				case Airspy.AIRSPY_SAMPLE_INT16_IQ:
					convertSamplesInt16(inputBuffer, outputBuffer, outputBuffer.length);
					processSamplesInt16(outputBuffer);
					break;

				case Airspy.AIRSPY_SAMPLE_INT16_REAL:
					convertSamplesInt16(inputBuffer, outputBuffer, outputBuffer.length);
					break;

				case Airspy.AIRSPY_SAMPLE_UINT16_REAL:
					convertSamplesUint16(inputBuffer, outputBuffer, outputBuffer.length);
					break;
			}

			// Finally we return the buffers to the corresponding queues:
			inputReturnQueue.offer(origInputBuffer);
			outputQueue.offer(outputBuffer);
		}
	}
}