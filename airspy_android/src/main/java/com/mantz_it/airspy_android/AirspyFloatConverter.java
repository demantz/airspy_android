package com.mantz_it.airspy_android;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Airspy USB Library for Android</h1>
 *
 * Module:      AirspyInt16Converter.java
 * Description: This class offers methods to convert the samples from the Airspy device
 *              to various FLOAT32 types.
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
public class AirspyFloatConverter extends Thread{

	private static final String LOGTAG = "AirspyFloatConverter";
	private boolean stopRequested = false;
	private int sampleType = -1;
	private boolean packingEnabled = false;
	private ArrayBlockingQueue<byte[]> inputQueue;         // Queue from which the input samples are taken
	private ArrayBlockingQueue<byte[]> inputReturnQueue;   // Queue to return the used input buffers to the pool
	private ArrayBlockingQueue<float[]> outputQueue;       // Queue to deliver the converted samples
	private ArrayBlockingQueue<float[]> outputPoolQueue;   // Queue from which the output buffers are taken
	private int len = 0;
	private int firIndex = 0;
	private int delayIndex = 0;
	private float avg;
	private float hbc;
	private float firQueue[];
	private float delayLine[];
	private static final int SIZE_FACTOR = 16;
	private static final float SCALE = 0.01f;

	// Hilbert Kernel with zeros removed
	public static final float HB_KERNEL_FLOAT[] = {
			-0.000998606272947510f,
			0.001695637278417295f,
			-0.003054430179754289f,
			0.005055504379767936f,
			-0.007901319195893647f,
			0.011873357051047719f,
			-0.017411159379930066f,
			0.025304817427568772f,
			-0.037225225204559217f,
			0.057533286997004301f,
			-0.102327462004259350f,
			0.317034472508947400f,
			0.317034472508947400f,
			-0.102327462004259350f,
			0.057533286997004301f,
			-0.037225225204559217f,
			0.025304817427568772f,
			-0.017411159379930066f,
			0.011873357051047719f,
			-0.007901319195893647f,
			0.005055504379767936f,
			-0.003054430179754289f,
			0.001695637278417295f,
			-0.000998606272947510f
	};

	/**
	 * Constructor for the float Converter
	 * @param sampleType		Desired sample type of the output samples (Airspy.AIRSPY_SAMPLE_FLOAT32_IQ or *_FLOAT32_REAL
	 * @param packingEnabled	Indicates if the input samples are packed
	 * @param inputQueue		Queue from which the input samples are taken
	 * @param inputReturnQueue	Queue to return the used input buffers to the pool
	 * @param outputQueue		Queue to deliver the converted samples
	 * @param outputPoolQueue	Queue from which the output buffers are taken
	 * @throws Exception if the sample type does not match a float based type
	 */
	public AirspyFloatConverter(int sampleType, boolean packingEnabled, ArrayBlockingQueue<byte[]> inputQueue,
								ArrayBlockingQueue<byte[]> inputReturnQueue, ArrayBlockingQueue<float[]> outputQueue,
								ArrayBlockingQueue<float[]> outputPoolQueue) throws Exception {
		if(sampleType != Airspy.AIRSPY_SAMPLE_FLOAT32_IQ && sampleType != Airspy.AIRSPY_SAMPLE_FLOAT32_REAL) {
			Log.e(LOGTAG, "constructor: Invalid sample type: " + sampleType);
			throw new Exception("Invalid sample type: " + sampleType);
		}
		this.sampleType = sampleType;
		this.packingEnabled = packingEnabled;
		this.inputQueue = inputQueue;
		this.inputReturnQueue = inputReturnQueue;
		this.outputQueue = outputQueue;
		this.outputPoolQueue = outputPoolQueue;
		this.len = HB_KERNEL_FLOAT.length;
		this.hbc = 0.5f;
		this.delayLine = new float[this.len / 2];
		this.firQueue = new float[this.len * SIZE_FACTOR];
	}

	/**
	 * Converts a byte array (little endian, unsigned-12bit-integer) to a float array (signed-32bit-float)
	 *
	 * @param src   input samples (little endian, unsigned-12bit-integer); min. twice the size of output
	 * @param dest  output samples (signed-32bit-float); min. of size 'count'
	 * @param count number of samples to process
	 */
	public static void convertSamplesFloat(byte[] src, float[] dest, int count) {
		if (src.length < 2 * count || dest.length < count) {
			Log.e(LOGTAG, "convertSamplesFloat: input buffers have invalid length: src=" + src.length + " dest=" + dest.length);
			return;
		}
		for (int i = 0; i < count; i++) {
				/*   src[2i+1] src[2i]
				 *  [--------|--------]
				 *      (xxxx xxxxxxxx) -2048
				 *                      * (1/2048) // normalize to [-1;1)
				 */
			dest[i] = ((((src[2 * i + 1] & 0x0F) << 8) + (src[2 * i] & 0xFF)) - 2048) * (1f / 2048f);
		}
	}

	public void requestStop() {
		this.stopRequested = true;
	}

	private void firInterleaved(float[] samples) {
		float acc;
		int idxKernel, idx1, idx2;

		for (int i = 0; i < samples.length; i += 2)
		{
			firQueue[firIndex] = samples[i];
			acc = 0;

			// Convolution
			idxKernel = 0;
			idx1 = firIndex;
			idx2 = firIndex + len - 1;
			for(; idxKernel < (len/2)-4; idxKernel+=4, idx1+=4, idx2-=4) {
				acc +=    HB_KERNEL_FLOAT[idxKernel] * (firQueue[idx1] + firQueue[idx2])
						+ HB_KERNEL_FLOAT[idxKernel+1] * (firQueue[idx1+1] + firQueue[idx2-1])
						+ HB_KERNEL_FLOAT[idxKernel+2] * (firQueue[idx1+2] + firQueue[idx2-2])
						+ HB_KERNEL_FLOAT[idxKernel+3] * (firQueue[idx1+3] + firQueue[idx2-3]);
			}
			// Rest of the convolution: ( if kernel length is not dividable by 2*4 )
			for(; idxKernel < len/2; idxKernel++, idx1++, idx2--) {
				acc += HB_KERNEL_FLOAT[idxKernel] * (firQueue[idx1] + firQueue[idx2]);
			}

			if (--firIndex < 0) {
				firIndex = len * (SIZE_FACTOR - 1);
				System.arraycopy(firQueue, 0, firQueue, firIndex + 1, len - 1);
			}

			samples[i] = acc;
		}
	}

	private void delayInterleaved(float[] samples, int offset) {
		int halfLen = len >> 1;
		float res;

		for (int i = offset; i < samples.length; i += 2) {
			res = delayLine[delayIndex];
			delayLine[delayIndex] = samples[i];
			samples[i] = res;

			if (++delayIndex >= halfLen) {
				delayIndex = 0;
			}
		}
	}

	private void removeDC(float[] samples) {
		for (int i = 0; i < samples.length; i++)
		{
			samples[i] = samples[i] - avg;
			avg += SCALE * samples[i];
		}
	}

	private void translateFs4(float[] samples) {
		for (int i = 0; i < samples.length; i += 4) {
			samples[i] = -samples[i];
			samples[i + 1] = -samples[i + 1] * hbc;
			//samples[i + 2] = samples[i + 2];
			samples[i + 3] = samples[i + 3] * hbc;
		}

		firInterleaved(samples);
		delayInterleaved(samples, 1);
	}

	public void processSamplesFloat(float[] samples) {
		removeDC(samples);
		translateFs4(samples);
	}

	public void run() {
		byte[] inputBuffer;
		byte[] origInputBuffer;
		byte[] packingBuffer = null;
		float[] outputBuffer = null;

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
				case Airspy.AIRSPY_SAMPLE_FLOAT32_IQ:
					convertSamplesFloat(inputBuffer, outputBuffer, outputBuffer.length);
					processSamplesFloat(outputBuffer);
					break;

				case Airspy.AIRSPY_SAMPLE_FLOAT32_REAL:
					convertSamplesFloat(inputBuffer, outputBuffer, outputBuffer.length);
					break;
			}

			// Finally we return the buffers to the corresponding queues:
			inputReturnQueue.offer(origInputBuffer);
			outputQueue.offer(outputBuffer);
		}
	}
}
