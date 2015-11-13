package com.mantz_it.airspy_test;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.mantz_it.airspy_android.Airspy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity implements Runnable, Airspy.AirspyCallbackInterface {

	private static final String LOGTAG = "MainActivity";

	// References to the GUI elements:
	private Button bt_openAirspy = null;
	private Button bt_info = null;
	private Button bt_rx = null;
	private Button bt_stop = null;
	private EditText et_sampRateIndex = null;
	private EditText et_freq = null;
	private EditText et_filename = null;
	private SeekBar sb_vgaGain = null;
	private SeekBar sb_lnaGain = null;
	private SeekBar sb_mixerGain = null;
	private CheckBox cb_packing = null;
	private Spinner sp_sampleType = null;
	private TextView tv_output = null;

	// Reference to the airspy instance:
	private Airspy airspy = null;

	private int sampRateIndex = 0;
	private int frequency = 0;
	private String filename = null;
	private int vgaGain = 0;
	private int lnaGain = 0;
	private int mixerGain = 0;
	private boolean packingEnabled = false;
	private int sampleType = 0;

	// The handler is used to access GUI elements from other threads then the GUI thread
	private Handler handler = null;

	// This variable is used to select what the thread should do if it is started
	private int task = -1;
	private static final int PRINT_INFO = 0;
	private static final int RECEIVE = 1;

	private boolean stopRequested = false;	// Used to stop receive thread

	// Folder name for capture files:
	private static final String foldername = "Test_Airspy";

	// logcat process:
	Process logcat;
	File logfile;

	// This method is called on application startup by the Android System:
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Start logging:
		try{
			logfile = new File(Environment.getExternalStorageDirectory() + "/" + foldername, "log.txt");
			logfile.getParentFile().mkdir();	// Create folder
			logcat = Runtime.getRuntime().exec("logcat -f " + logfile.getAbsolutePath());
			Log.i(LOGTAG, "onCreate: log path: " + logfile.getAbsolutePath());
		} catch (Exception e) {
			Log.e(LOGTAG, "onCreate: Failed to start logging!");
		}

		// Create a Handler instance to use in other threads:
		handler = new Handler();

		// Initialize the GUI references:
		bt_info 		= ((Button) this.findViewById(R.id.bt_info));
		bt_rx 			= ((Button) this.findViewById(R.id.bt_rx));
		bt_stop			= ((Button) this.findViewById(R.id.bt_stop));
		bt_openAirspy = ((Button) this.findViewById(R.id.bt_openAirspy));
		et_sampRateIndex = (EditText) this.findViewById(R.id.et_sampRateIndex);
		et_freq 		= (EditText) this.findViewById(R.id.et_freq);
		et_filename 	= (EditText) this.findViewById(R.id.et_filename);
		sb_vgaGain 		= (SeekBar) this.findViewById(R.id.sb_vgaGain);
		sb_lnaGain 		= (SeekBar) this.findViewById(R.id.sb_lnaGain);
		sb_mixerGain 	= (SeekBar) this.findViewById(R.id.sb_mixerGain);
		cb_packing 		= (CheckBox) this.findViewById(R.id.cb_packing);
		sp_sampleType	= (Spinner) this.findViewById(R.id.sp_sampleType);
		tv_output 		= (TextView) findViewById(R.id.tv_output);
		tv_output.setMovementMethod(new ScrollingMovementMethod());	// make it scroll!
		this.toggleButtonsEnabledIfAirspyReady(false);	// Disable all buttons except for 'Open Airspy'

		// Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,R.array.sampleTypes, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sp_sampleType.setAdapter(adapter);
		sp_sampleType.setSelection(3);

		// Print Hello
		String version = "";
		try {
			version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (PackageManager.NameNotFoundException e) {}
		this.tv_output.setText("Test_Airspy (version " + version + ") by Dennis Mantz\n");
	}

	@Override
	protected void onDestroy() {
		if(logcat != null)
			logcat.destroy();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		if (id == R.id.action_help) {
			this.tv_output.setText(Html.fromHtml(getResources().getString(R.string.helpText)));
			return true;
		}
		if (id == R.id.action_showLog) {
			Uri uri = Uri.fromFile(logfile);
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, "text/plain");
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			this.startActivity(intent);
			return true;
		}
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Will append the message to the tv_output TextView. Can be called from
	 * outside the GUI thread because it uses the handler reference to access
	 * the TextView.
	 *
	 * @param msg	Message to print on the screen
	 */
	public void printOnScreen(final String msg)
	{
		handler.post(new Runnable() {
			public void run() {
				tv_output.append(msg);
			}
		});
	}

	/**
	 * Will set all buttons to disabled except for the 'open airspy' button. If
	 * false is given, the behavior toggles. Can be called from
	 * outside the GUI thread because it uses the handler reference to access
	 * the TextView.
	 *
	 * @param enable	if true: 'Open Airspy' will be enabled, all others disabled
	 * 					if false: The other way round
	 */
	public void toggleButtonsEnabledIfAirspyReady(final boolean enable)
	{
		handler.post(new Runnable() {
			public void run() {
				bt_info.setEnabled(enable);
				bt_rx.setEnabled(enable);
				bt_stop.setEnabled(enable);
				bt_openAirspy.setEnabled(!enable);
			}
		});
	}

	/**
	 * Will set 'Info', 'RX' to disabled and 'stop' to enabled (while
	 * receiving is running). If false is given, the behavior toggles.
	 * Can be called from outside the GUI thread because it uses the handler
	 * reference to access the TextView.
	 *
	 * @param enable	if true: 'Stop' will be enabled, all others ('Info', 'RX') disabled
	 * 					if false: The other way round
	 */
	public void toggleButtonsEnabledIfReceiving(final boolean enable)
	{
		handler.post(new Runnable() {
			public void run() {
				bt_info.setEnabled(!enable);
				bt_rx.setEnabled(!enable);
				bt_stop.setEnabled(enable);
			}
		});
	}

	/**
	 * Will read the values from the GUI elements into the corresponding variables
	 */
	public void readGuiElements()
	{
		sampRateIndex = Integer.valueOf(et_sampRateIndex.getText().toString());
		frequency = Integer.valueOf(et_freq.getText().toString());
		filename = et_filename.getText().toString();
		vgaGain = sb_vgaGain.getProgress();
		lnaGain = sb_lnaGain.getProgress();
		mixerGain = sb_mixerGain.getProgress();
		packingEnabled = cb_packing.isChecked();
		sampleType = sp_sampleType.getSelectedItemPosition();
	}

	/**
	 * Is called if the user presses the 'Open Airspy' Button. Will initialize the
	 * Airspy device.
	 *
	 * @param view		Reference to the calling View (in this case bt_openAirspy)
	 */
	public void openAirspy(View view)
	{
		// Initialize the Airspy (i.e. open the USB device, which requires the user to give permissions)
		if (!Airspy.initAirspy(view.getContext(), this))
		{
			tv_output.append("No Airspy could be found!\n");
		}
		// initAirspy() is asynchronous. this.onAirspyReady() will be called as soon as the device is ready.
	}

	/**
	 * Is called if the user presses the 'Info' Button. Will start a Thread that
	 * retrieves the BoardID, Version String, PartID and Serial number from the device
	 * and then print the information on the screen.
	 *
	 * @param view		Reference to the calling View (in this case bt_info)
	 */
	public void info(View view)
	{
		if (airspy != null)
		{
			this.task = PRINT_INFO;
			new Thread(this).start();
		}
	}

	/**
	 * Is called if the user presses the 'RX' Button. Will start a Thread that
	 * sets the Airspy into receiving mode and then save the received samples
	 * to a file. Will run forever until user presses the 'Stop' button.
	 *
	 * @param view		Reference to the calling View (in this case bt_rx)
	 */
	public void rx(View view)
	{
		if (airspy != null)
		{
			this.readGuiElements();
			this.task = RECEIVE;
			this.stopRequested = false;
			new Thread(this).start();
			toggleButtonsEnabledIfReceiving(true);
		}
	}

	/**
	 * Is called if the user presses the 'Stop' Button. Will set the stopRequested
	 * attribute to true, which will cause any running thread to shut down. It will
	 * then set the transceiver mode of the Airspy to OFF.
	 *
	 * @param view		Reference to the calling View (in this case bt_stop)
	 */
	public void stop(View view)
	{
		this.stopRequested = true;
		toggleButtonsEnabledIfReceiving(false);

		if(airspy != null)
		{
			try {
				airspy.stop();
			} catch (Airspy.AirspyUsbException e) {
				printOnScreen("Error (USB)!\n");
				toggleButtonsEnabledIfAirspyReady(false);
			}
		}
	}

	/**
	 * Is called by the airspy_android library after the device is ready.
	 * Was triggered by the initAirspy() call in openAirspy().
	 * See also AirspyCallbackInterface.java
	 *
	 * @param airspy	Instance of the Airspy class that represents the open device
	 */
	@Override
	public void onAirspyReady(Airspy airspy) {
		tv_output.append("Airspy is ready!\n");

		this.airspy = airspy;
		this.toggleButtonsEnabledIfAirspyReady(true);
		this.toggleButtonsEnabledIfReceiving(false);
	}

	/**
	 * Is called by the airspy_android library after a error occurred while opening
	 * the device.
	 * Was triggered by the initAirspy() call in openAirspy().
	 * See also AirspyCallbackInterface.java
	 *
	 * @param message	Short human readable error message
	 */
	@Override
	public void onAirspyError(String message) {
		tv_output.append("Error while opening Airspy: " + message +"\n");
		this.toggleButtonsEnabledIfAirspyReady(false);
	}

	/**
	 * Is called (in a separate Thread) after 'new Thread(this).start()' is
	 * executed in info(), rx().
	 * Will run either infoThread() or receiveThread() depending
	 * on how the task attribute is set.
	 */
	@Override
	public void run() {
		switch(this.task)
		{
			case PRINT_INFO:	infoThread(); break;
			case RECEIVE:		receiveThread(); break;
			default:
		}

	}

	/**
	 * Will run in a separate thread created in info(). Retrieves the BoardID,
	 * Version String, PartID and Serial number from the device
	 * and then print the information on the screen.
	 */
	public void infoThread()
	{
		// Read out boardID, version, partID and serialNo:
		try
		{
			int boardID = airspy.getBoardID();
			printOnScreen("Board ID:   " + boardID + " (" + Airspy.convertBoardIdToString(boardID) + ")\n" );
			printOnScreen("Version:    " + airspy.getVersionString() + "\n");
			int[] tmp = airspy.getPartIdAndSerialNo();
			printOnScreen("Part ID:    0x" + Integer.toHexString(tmp[0]) +
					" 0x" + Integer.toHexString(tmp[1]) +"\n");
			printOnScreen("Serial No:  0x" + Integer.toHexString(tmp[2]) +
					" 0x" + Integer.toHexString(tmp[3]) +
					" 0x" + Integer.toHexString(tmp[4]) +
					" 0x" + Integer.toHexString(tmp[5]) +"\n");
			int[] rates = airspy.getSampleRates();
			printOnScreen("Supported sample rates:\n");
			for(int rate: rates) {
				printOnScreen("\t" + rate + "\n");
			}
			printOnScreen("\n");
		} catch (Airspy.AirspyUsbException e) {
			printOnScreen("Error while reading Board Information!\n");
			this.toggleButtonsEnabledIfAirspyReady(false);
		}
	}

	/**
	 * Will run in a separate thread created in rx(). Sets the Airspy into receiving
	 * mode and then save the received samples to a file. Will run forever until user
	 * presses the 'Stop' button.
	 */
	public void receiveThread()
	{
		int i;
		long lastTransceiverPacketCounter = 0;
		long lastTransceivingTime = 0;

		// vgaGain, mixerGain and lnaGain are still values from 0-100; scale them to the right range:
		vgaGain = (vgaGain * 15) / 100;
		lnaGain = (lnaGain * 14) / 100;
		mixerGain = (mixerGain * 15) / 100;

		try {
			// First set all parameters:
			int[] sampleRates = airspy.getSampleRates();
			if(sampRateIndex < 0 || sampRateIndex >= sampleRates.length) {
				printOnScreen("Sample Rate index " + sampRateIndex + " is out of bounds. Supported Rates are:\n");
				for(i=0; i<sampleRates.length; i++)
					printOnScreen("[" + i + "]: " + sampleRates[i] + " Sps\n");
				toggleButtonsEnabledIfReceiving(false);
				return;
			}
			printOnScreen("Setting Sample Rate to index " + sampRateIndex + " (" + sampleRates[sampRateIndex] + " Sps) ... ");
			airspy.setSampleRate(sampRateIndex);
			printOnScreen("ok.\nSetting Frequency to " + frequency + " Hz ... ");
			airspy.setFrequency(frequency);
			printOnScreen("ok.\nSetting RX VGA Gain to " + vgaGain + " ... ");
			airspy.setVGAGain(vgaGain);
			printOnScreen("ok.\nSetting LNA Gain to " + lnaGain + " ... ");
			airspy.setLNAGain(lnaGain);
			printOnScreen("ok.\nSetting Mixer Gain to " + mixerGain + " ... ");
			airspy.setMixerGain(mixerGain);
			printOnScreen("ok.\nSetting Packing to " + packingEnabled + " ... ");
			airspy.setPacking(packingEnabled);
			printOnScreen("ok.\nSetting Sample Type to " + sampleType + " ... ");
			airspy.setSampleType(sampleType);
			printOnScreen("ok.\nSetting rawMode to false ... ");
			airspy.setRawMode(false);
			printOnScreen("ok.\n\n");

			if(packingEnabled)
				printOnScreen("Packing does not work currently. No idea why!\n\n");

			// Check if external memory is available:
			if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			{
				printOnScreen("External Media Storage not available.\n\n");
				toggleButtonsEnabledIfReceiving(false);
				return;
			}

			// Create a file ...
			// If no filename was given, write to /dev/null
			File file;
			if(filename.equals(""))
				file = new File("/dev/", "null");
			else
				file = new File(Environment.getExternalStorageDirectory() + "/" + foldername, filename);
			File parentDir = file.getParentFile();
			parentDir.mkdir();    // Create folder if it does not exist
			if(!parentDir.isDirectory()) {
				printOnScreen("Error, could not create directory: " + parentDir.getAbsolutePath());
				toggleButtonsEnabledIfReceiving(false);
				return;
			}
			printOnScreen("Saving samples to " + file.getAbsolutePath() + "\n");

			// ... and open it with a buffered output stream
			BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
			ByteBuffer byteBuffer = null;
			ShortBuffer shortBuffer = null;
			FloatBuffer floatBuffer = null;
			ArrayBlockingQueue<short[]> shortQueue = null;
			ArrayBlockingQueue<short[]> shortReturnPoolQueue = null;
			ArrayBlockingQueue<float[]> floatQueue = null;
			ArrayBlockingQueue<float[]> floatReturnPoolQueue = null;
			short[] shortSamples;
			float[] floatSamples;

			// Start Receiving:
			printOnScreen("Start Receiving... \n");
			airspy.startRX();

			switch (sampleType) {
				case Airspy.AIRSPY_SAMPLE_FLOAT32_IQ:
				case Airspy.AIRSPY_SAMPLE_FLOAT32_REAL:
					byteBuffer =  ByteBuffer.allocate(airspy.getUsbPacketSize() * 2);	// *2 because float has 32bit
					byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
					floatBuffer = byteBuffer.asFloatBuffer();
					floatQueue = airspy.getFloatQueue();
					floatReturnPoolQueue = airspy.getFloatReturnPoolQueue();
					break;
				case Airspy.AIRSPY_SAMPLE_INT16_IQ:
				case Airspy.AIRSPY_SAMPLE_INT16_REAL:
				case Airspy.AIRSPY_SAMPLE_UINT16_REAL:
					byteBuffer =  ByteBuffer.allocate(airspy.getUsbPacketSize());
					byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
					shortBuffer = byteBuffer.asShortBuffer();
					shortQueue = airspy.getInt16Queue();
					shortReturnPoolQueue = airspy.getInt16ReturnPoolQueue();
					break;
			}


			// Run until user hits the 'Stop' button
			i = 0;
			while(!this.stopRequested)
			{
				i++;	// only for statistics

				// TODO: update this information (Airspy has different sample types):
				/*  HERE should be the DSP portion of the app. The receivedBytes
				 *  variable now contains a byte array of size airspy.getUsbPacketSize().
				 *  The bytes are interleaved, 16-bit, signed IQ samples (in-phase
				 *  component first, followed by the quadrature component):
				 *
				 *  [--------- first sample ----------]   [-------- second sample --------]
				 *         I                  Q                  I                Q ...
				 *  receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
				 *
				 *  Note: Make sure you read from the queue fast enough, because if it runs
				 *  full, the airspy_android library will abort receiving and go back to
				 *  OFF mode.
				 */

				// Grab one packet from the top of the queue. Will block if queue is
				// empty and timeout after one second if the queue stays empty.
				if(sampleType==Airspy.AIRSPY_SAMPLE_FLOAT32_IQ || sampleType==Airspy.AIRSPY_SAMPLE_FLOAT32_REAL) {
					floatSamples = floatQueue.poll(1000, TimeUnit.MILLISECONDS);
					// We just write the whole packet into the file:
					if(floatSamples != null)
					{
						floatBuffer.position(0);
						floatBuffer.put(floatSamples);
						outputStream.write(byteBuffer.array());

						// IMPORTANT: After we used the receivedBytes buffer and don't need it any more,
						// we should return it to the buffer pool of the airspy! This will save a lot of
						// allocation time and the garbage collector won't go off every second.
						floatReturnPoolQueue.offer(floatSamples);
					}
					else
					{
						printOnScreen("Error: Queue is empty! (This happens most often because the queue ran full"
								+ " which causes the Airspy class to stop receiving. Writing the samples to a file"
								+ " seems to be working to slowly... try a lower sample rate.)\n");
						break;
					}
				}
				else {
					shortSamples = shortQueue.poll(1000, TimeUnit.MILLISECONDS);
					// We just write the whole packet into the file:
					if(shortSamples != null)
					{
						shortBuffer.position(0);
						shortBuffer.put(shortSamples);
						outputStream.write(byteBuffer.array());

						// IMPORTANT: After we used the receivedBytes buffer and don't need it any more,
						// we should return it to the buffer pool of the airspy! This will save a lot of
						// allocation time and the garbage collector won't go off every second.
						shortReturnPoolQueue.offer(shortSamples);
					}
					else
					{
						printOnScreen("Error: Queue is empty! (This happens most often because the queue ran full"
								+ " which causes the Airspy class to stop receiving. Writing the samples to a file"
								+ " seems to be working to slowly... try a lower sample rate.)\n");
						break;
					}
				}

				// print statistics
				if(i%1000 == 0)
				{
					long bytes = (airspy.getReceiverPacketCounter() - lastTransceiverPacketCounter) * airspy.getUsbPacketSize();
					double time = (airspy.getReceivingTime() - lastTransceivingTime)/1000.0;
					printOnScreen( String.format("Current Transfer Rate: %4.1f MB/s\n",(bytes/time)/1000000.0));
					lastTransceiverPacketCounter = airspy.getReceiverPacketCounter();
					lastTransceivingTime = airspy.getReceivingTime();
				}
			}

			// After loop ended: close the file and print more statistics:
			outputStream.close();
			printOnScreen( String.format("Finished! (Average Transfer Rate: %4.1f MB/s\n",
					airspy.getAverageReceiveRate()/1000000.0));
			printOnScreen(String.format("Recorded %d packets (each %d Bytes) in %5.3f Seconds.\n\n",
					airspy.getReceiverPacketCounter(), airspy.getUsbPacketSize(),
					airspy.getReceivingTime()/1000.0));
			toggleButtonsEnabledIfReceiving(false);
		} catch (Airspy.AirspyUsbException e) {
			// This exception is thrown if a USB communication error occurres (e.g. you unplug / reset
			// the device while receiving)
			printOnScreen("error (USB " + e.getMessage() + ")!\n");
			toggleButtonsEnabledIfAirspyReady(false);
		} catch (IOException e) {
			// This exception is thrown if the file could not be opened or write fails.
			printOnScreen("error (File IO: " + e.getMessage() + ")!\n");
			toggleButtonsEnabledIfReceiving(false);
		} catch (InterruptedException e) {
			// This exception is thrown if queue.poll() is interrupted
			printOnScreen("error (Queue)!\n");
			toggleButtonsEnabledIfReceiving(false);
		}
	}

}
