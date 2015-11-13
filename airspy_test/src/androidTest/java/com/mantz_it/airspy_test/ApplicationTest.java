package com.mantz_it.airspy_test;

import android.app.Application;
import android.test.ApplicationTestCase;

import com.mantz_it.airspy_android.Airspy;
import com.mantz_it.airspy_android.AirspyFloatConverter;
import com.mantz_it.airspy_android.AirspyInt16Converter;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
	public ApplicationTest() {
		super(Application.class);
	}

	public void testAirspyConvertSamplesInt16() {
		byte[] input = {
				(byte) 0xFF, (byte) 0xFF,	// 0
				(byte) 0xFE, (byte) 0xFF,	// 1
				(byte) 0xFF, (byte) 0x0F,	// 2
				(byte) 0xFE, (byte) 0x0F,	// 3
				(byte) 0x00, (byte) 0x00,	// 4
				(byte) 0x01, (byte) 0x00,	// 5
				(byte) 0x00, (byte) 0xF0,	// 6
				(byte) 0x00, (byte) 0x08,	// 7
				(byte) 0x03, (byte) 0x08};	// 8
		short[] expected = {
				Short.MAX_VALUE-0x0F,		// 0
				Short.MAX_VALUE-0x1F,		// 1
				Short.MAX_VALUE-0x0F,		// 2
				Short.MAX_VALUE-0x1F,		// 3
				Short.MIN_VALUE,			// 4
				Short.MIN_VALUE+0x10,		// 5
				Short.MIN_VALUE,			// 6
				0,							// 7
				0x30};					// 8
		short[] output = new short[expected.length];
		AirspyInt16Converter.convertSamplesInt16(input, output, output.length);
		System.out.print("Testing input buffer: ");
		printUnsignedArray(input);

		for(int i=0; i<output.length; i++) {
			System.out.print("Testing index " + i + " ... ");
			assertEquals(expected[i], output[i]);
			System.out.println("passed!");
		}
	}

	public void testAirspyConvertSamplesInt16_2() {
		byte[] input = new byte[512];
		short[] expected = {-16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816,
				-13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280,
				-15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840,
				-12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304,
				-14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768,
				-15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328,
				-13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792,
				-14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352,
				-16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816,
				-13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280,
				-15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304, -14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840,
				-12800, 20096, -12544, 20352, -16384, 16512, -16128, 16768, -15872, 17024, -15616, 17280, -15360, 17536, -15104, 17792, -14848, 18048, -14592, 18304,
				-14336, 18560, -14080, 18816, -13824, 19072, -13568, 19328, -13312, 19584, -13056, 19840, -12800, 20096, -12544, 20352,};					// 8
		short[] output = new short[expected.length];

		for(int i=0; i<input.length; i++) {
			input[i] = (byte) (i*3 + i);
		}

		AirspyInt16Converter.convertSamplesInt16(input, output, output.length);
		System.out.print("Testing input buffer: ");
		printUnsignedArray(input);

		for(int i=0; i<output.length; i++) {
			System.out.print("Testing index " + i + " ... ");
			assertEquals(expected[i], output[i]);
			System.out.println("passed!");
		}
	}


	public void testAirspyConvertSamplesFloat() {
		byte[] input = {
				(byte) 0xFF, (byte) 0xFF,	// 0
				(byte) 0xFE, (byte) 0xFF,	// 1
				(byte) 0xFF, (byte) 0x0F,	// 2
				(byte) 0xFE, (byte) 0x0F,	// 3
				(byte) 0x00, (byte) 0x00,	// 4
				(byte) 0x01, (byte) 0x00,	// 5
				(byte) 0x00, (byte) 0xF0,	// 6
				(byte) 0x00, (byte) 0x08,	// 7
				(byte) 0x03, (byte) 0x08};	// 8
		float[] expected = {
				0.9995f,					// 0
				0.9990f,					// 1
				0.9995f,					// 2
				0.9990f,					// 3
				-1f,						// 4
				-0.9995f,					// 5
				-1f,						// 6
				0,							// 7
				0.0014f};					// 8
		float[] output = new float[expected.length];
		AirspyFloatConverter.convertSamplesFloat(input, output, output.length);
		System.out.print("Testing input buffer: ");
		printUnsignedArray(input);

		for(int i=0; i<output.length; i++) {
			System.out.print("Testing index " + i + " ... ");
			assertTrue(floatEquals(expected[i], output[i]));
			System.out.println("passed!");
		}
	}

	public void testUnpackSamples() {
		byte[] input = {
				(byte) 0x12,
				(byte) 0x34,
				(byte) 0x56,
				(byte) 0x78,
				(byte) 0x9A,
				(byte) 0xBC,
				(byte) 0xDE,
				(byte) 0xF0,
				(byte) 0x12,
				(byte) 0x34,
				(byte) 0x56,
				(byte) 0x78};
		byte[] expected = {
				(byte) 0x85,
				(byte) 0x07,
				(byte) 0x34,
				(byte) 0x06,
				(byte) 0x2F,
				(byte) 0x01,
				(byte) 0xDE,
				(byte) 0x00,
				(byte) 0xC9,
				(byte) 0x0B,
				(byte) 0x78,
				(byte) 0x0A,
				(byte) 0x63,
				(byte) 0x05,
				(byte) 0x12,
				(byte) 0x04};
		byte[] output = new byte[expected.length];
		Airspy.unpackSamples(input, output, 16);
		for(int i=0; i<16; i++){
			System.out.print("Testing ["+i+"]: output=" + output[i] + " expected="+expected[i] + " ... ");
			assertEquals(expected[i], output[i]);
			System.out.println("passed!");
		}
	}

	public void testConverterInt16IQ() {
		short[] input = new short[512];

		for(int i=0; i<input.length; i++) {
			input[i] = (short) (i*128 + i);
		}

		short[] expected = {
			0, 0, -1, 0, 0, 0, -3, 0, 5, 0, -10, 0, 17, 0, -29, 0, 46, 0, -73, 0,
			112, 0, -177, 0, 319, -65, -376, 189, 403, -310, -416, 424, 417, -535, -414, 641, 404, -743, -393, 840,
			379, -935, -366, 1024, 351, -1111, -339, 1193, 323, -1273, -312, 1349, 298, -1423, -287, 1493, 274, -1561, -264, 1625,
			253, -1688, -243, 1747, 233, -1804, -224, 1859, 214, -1912, -207, 1962, 197, -2011, -190, 2057, 182, -2102, -175, 2144,
			167, -2186, -161, 2225, 154, -2263, -149, 2299, 142, -2335, -137, 2368, 131, -2400, -126, 2431, 120, -2461, -116, 2489,
			111, -2516, -107, 2542, 102, -2568, -99, 2591, 94, -2615, -91, 2636, 86, -2658, -84, 2678, 79, -2698, -77, 2717,
			73, -2735, -71, 2752, 67, -2769, -65, 2784, 62, -2800, -60, 2814, 57, -2829, -56, 2842, 52, -2856, -51, 2867,
			48, -2880, -47, 2891, 45, -2902, -44, 2913, 41, -2923, -40, 2932, 37, -2942, -37, 2951, 34, -2960, -34, 2968,
			32, -2976, -31, 2983, 29, -2991, -29, 2997, 27, -3005, -27, 3010, 25, -3017, -25, 3023, 23, -3029, -23, 3034,
			21, -3039, -21, 3044, 19, -3049, -19, 3053, 18, -3058, -18, 3062, 16, -3067, -17, 3070, 15, -3075, -15, 3078,
			14, -3082, -14, 3084, 13, -3088, -13, 3091, 12, -3094, -12, 3096, 11, -3100, -11, 3102, 9, -3105, -10, 3107,
			9, -3109, -9, 3111, 8, -3114, -9, 3115, 8, -3118, -8, 3119, 7, -3121, -8, 3123, 6, -3125, -7, 3126,
			6, -3128, -7, 3129, 5, -3131, -6, 3132, 5, -3133, -5, 3134, 5, -3136, -5, 3137, 4, -3138, -5, 3139,
			4, -3140, -4, 3141, 3, -3142, -4, 3142, 3, -3144, -4, 3144, 3, -3145, -4, 3146, 2, -3147, -3, 3147,
			2, -3148, -3, 3148, 2, -3150, -3, 3150, 2, -3151, -3, 3151, 2, -3152, -3, 3152, 1, -3153, -2, 3153,
			1, -3154, -2, 3154, 1, -3155, -2, 3154, 1, -3155, -2, 3155, 1, -3156, -2, 3156, 1, -3157, -2, 3156,
			1, -3157, -2, 3157, 0, -3158, -2, 3158, 0, -3158, -2, 3158, 0, -3159, -2, 3159, 0, -3159, -2, 3159,
			0, -3160, -1, 3159, 0, -3160, -1, 3160, 0, -3160, -1, 3160, 0, -3161, -1, 3160, 0, -3161, -1, 3161,
			0, -3161, -1, 3161, 0, -3161, -1, 3161, 0, -3162, -1, 3161, 0, -3162, -1, 3161, 0, -3162, -1, 3162,
			0, -3162, -1, 3162, 0, -3162, -1, 3162, 0, -3163, -1, 3162, 0, -3163, -1, 3162, 0, -3163, -1, 3162,
			0, -3163, -1, 3162, 0, -3163, -1, 3163, 0, -3163, -1, 3163, 0, -3163, -1, 3163, 0, -3163, -1, 3163,
			0, -3163, -1, 3163, 0, -3163, -1, 3163, 0, -3163, -1, 3163, 0, -3164, -1, 3163, 0, -3164, -1, 3163,
			0, -3164, -1, 3163, 0, -3164, -1, 3163, 0, -3164, -1, 3163, 0, -3164, 0, 3163, 0, -3164, -1, 3163,
			0, -3164, -1, 3163, 0, -3164, -1, 3163, 0, -3164, -1, 3163, 0, -3164, -1, 3163, 0, -3164, -1, 3163,
			0, -3164, -1, 3163, 0, -3164, -1, 3164, 0, -3164, -1, 3164, 0, -3164, 0, 3164, -1, -3164, 0, 3164,
			-1, -3164, 0, 3164, 0, -3164, -1, 3164, 0, -3164, -1, 3164};

		AirspyInt16Converter converter = null;
		try {
			converter = new AirspyInt16Converter(Airspy.AIRSPY_SAMPLE_INT16_IQ,false,null,null,null,null);
		} catch (Exception e) {
			System.out.println("EXCEPTION CAUGHT: " + e.getMessage());
		}
		assertNotNull(converter);
		converter.processSamplesInt16(input);

		System.out.println("[index]:\texpected\toutput");
		for(int i=0; i<input.length; i++) {
			//System.out.println("[" + i + "]:\t" + expected[i] + "\t" + input[i]);
			assertEquals(expected[i], input[i]);
		}
	}

	public void testConverterFloatIQ() {
		float[] input = new float[512];

		for(int i=0; i<input.length; i++) {
			input[i] = (float) (i*128f + i);
		}

		float[] expected = {
				0.000000f, 0.000000f, -0.256352f, 0.000000f, 0.942890f, 0.000000f, -2.399868f, 0.000000f, 5.125652f, 0.000000f, -9.825539f, 0.000000f,
				17.479908f, 0.000000f, -29.451576f, 0.000000f, 47.681004f, 0.000000f, -75.103752f, 0.000000f, 116.750160f, 0.000000f, -183.836288f, 0.000000f,
				330.973328f, -64.500000f, -393.796448f, 191.571457f, 429.100830f, -316.114166f, -448.933289f, 438.178497f, 458.815063f, -557.813782f, -462.004150f, 675.068237f,
				460.660156f, -789.989380f, -456.294922f, 902.623596f, 449.988220f, -1013.016418f, -442.509216f, 1121.212402f, 434.394867f, -1227.255249f, -426.006775f, 1331.187866f,
				417.529236f, -1433.052246f, -409.220398f, 1532.889526f, 401.076965f, -1630.739990f, -393.095520f, 1726.643311f, 385.272888f, -1820.638062f, -377.606018f, 1912.762451f,
				370.091583f, -2003.053467f, -362.726807f, 2091.547607f, 355.508636f, -2178.280762f, -348.434021f, 2263.288086f, 341.500061f, -2346.603516f, -334.704071f, 2428.261230f,
				328.043640f, -2508.293945f, -321.515503f, 2586.733887f, 315.117310f, -2663.612793f, -308.846558f, 2738.961914f, 302.700439f, -2812.811523f, -296.676758f, 2885.191650f,
				290.772919f, -2956.131348f, -284.986755f, 3025.659180f, 279.315338f, -3093.803711f, -273.756897f, 3160.592041f, 268.309174f, -3226.051270f, -262.969727f, 3290.208008f,
				257.736755f, -3353.087891f, -252.607697f, 3414.716309f, 247.580780f, -3475.118408f, -242.653946f, 3534.318359f, 237.825043f, -3592.340576f, -233.092529f, 3649.208008f,
				228.454132f, -3704.943848f, -223.907578f, 3759.570312f, 219.452057f, -3813.109863f, -215.084732f, 3865.583984f, 210.804596f, -3917.013916f, -206.609650f, 3967.420410f,
				202.498230f, -4016.823730f, -198.468506f, 4065.243896f, 194.518997f, -4112.700684f, -190.648071f, 4159.212891f, 186.853775f, -4204.799805f, -183.135712f, 4249.479004f,
				179.491196f, -4293.269531f, -175.918991f, 4336.188477f, 172.418671f, -4378.252930f, -168.987381f, 4419.480957f, 165.624420f, -4459.888184f, -162.328964f, 4499.491211f,
				159.098618f, -4538.306641f, -155.932266f, 4576.349609f, 152.829346f, -4613.634766f, -149.787567f, 4650.178711f, 146.807144f, -4685.995117f, -143.885498f, 4721.098633f,
				141.022476f, -4755.503906f, -138.215591f, 4789.224609f, 135.465393f, -4822.273926f, -132.769165f, 4854.665527f, 130.127243f, -4886.412598f, -127.537674f, 4917.527832f,
				124.999489f, -4948.023926f, -122.512115f, 4977.913086f, 120.074181f, -5007.207520f, -117.684761f, 5035.918945f, 115.343002f, -5064.059082f, -113.047585f, 5091.639160f,
				110.798203f, -5118.670410f, -108.593262f, 5145.164062f, 106.432220f, -5171.130371f, -104.314133f, 5196.579590f, 102.238350f, -5221.522461f, -100.204315f, 5245.969238f,
				98.210175f, -5269.929688f, -96.255745f, 5293.413086f, 94.340416f, -5316.429199f, -92.462967f, 5338.987305f, 90.622978f, -5361.096680f, -88.819550f, 5382.766113f,
				87.051834f, -5404.003906f, -85.319733f, 5424.819336f, 83.621780f, -5445.220703f, -81.957405f, 5465.215820f, 80.326591f, -5484.812988f, -78.727982f, 5504.020020f,
				77.161224f, -5522.845215f, -75.625595f, 5541.295410f, 74.120834f, -5559.378418f, -72.646072f, 5577.102051f, 71.200264f, -5594.472656f, -69.783676f, 5611.497559f,
				68.395020f, -5628.184082f, -67.033783f, 5644.538086f, 65.700096f, -5660.566895f, -64.392654f, 5676.276855f, 63.111252f, -5691.673828f, -61.855480f, 5706.764648f,
				60.624638f, -5721.555664f, -59.418480f, 5736.051758f, 58.235847f, -5750.259766f, -57.077347f, 5764.184570f, 55.941452f, -5777.833008f, -54.827812f, 5791.208984f,
				53.737114f, -5804.319336f, -52.667355f, 5817.168945f, 51.618801f, -5829.761719f, -50.592278f, 5842.104492f, 49.585705f, -5854.202148f, -48.599045f, 5866.058594f,
				47.631924f, -5877.679688f, -46.683502f, 5889.069336f, 45.754108f, -5900.231445f, -44.843506f, 5911.171875f, 43.950859f, -5921.894531f, -43.076332f, 5932.404297f,
				42.219044f, -5942.704102f, -41.378632f, 5952.799805f, 40.554943f, -5962.693359f, -39.748199f, 5972.390625f, 38.957016f, -5981.895508f, -38.181702f, 5991.209961f,
				37.422474f, -6000.339844f, -36.677456f, 6009.288086f, 35.947926f, -6018.058594f, -35.232651f, 6026.654297f, 34.531704f, -6035.079102f, -33.843918f, 6043.335938f,
				33.170742f, -6051.428711f, -32.510883f, 6059.360352f, 31.863907f, -6067.133789f, -31.229561f, 6074.752930f, 30.607700f, -6082.220703f, -29.998287f, 6089.539062f,
				29.401619f, -6096.711914f, -28.816357f, 6103.742188f, 28.242790f, -6110.632812f, -27.680855f, 6117.385742f, 27.130360f, -6124.004883f, -26.590969f, 6130.492188f,
				26.061836f, -6136.850586f, -25.543154f, 6143.083008f, 25.033890f, -6149.190430f, -24.535683f, 6155.175781f, 24.047842f, -6161.042969f, -23.568972f, 6166.792969f,
				23.100014f, -6172.428711f, -22.640816f, 6177.952148f, 22.190084f, -6183.366211f, -21.748726f, 6188.671875f, 21.316162f, -6193.872070f, -20.892513f, 6198.969727f,
				20.476025f, -6203.965820f, -20.068333f, 6208.861328f, 19.669445f, -6213.660156f, -19.277992f, 6218.363281f, 18.894295f, -6222.972656f, -18.518318f, 6227.491211f,
				18.149939f, -6231.918945f, -17.788908f, 6236.258789f, 17.434536f, -6240.512695f, -17.087305f, 6244.681641f, 16.747210f, -6248.766602f, -16.413992f, 6252.771484f,
				16.086576f, -6256.696289f, -15.766959f, 6260.542969f, 15.452888f, -6264.312500f, -15.145083f, 6268.007812f, 14.842741f, -6271.628906f, -14.548022f, 6275.177734f,
				14.258858f, -6278.656250f, -13.976735f, 6282.066406f, 13.698181f, -6285.408203f, -13.425323f, 6288.683594f, 13.159090f, -6291.894531f, -12.895308f, 6295.041016f,
				12.639343f, -6298.123047f, -12.388180f, 6301.146484f, 12.142130f, -6304.109375f, -11.899221f, 6307.011719f, 11.663452f, -6309.857422f, -11.429669f, 6312.646484f,
				11.202121f, -6315.378906f, -10.979919f, 6318.056641f, 10.761461f, -6320.683594f, -10.546636f, 6323.255859f, 10.336773f, -6325.777344f, -10.131880f, 6328.250000f,
				9.930668f, -6330.671875f, -9.733883f, 6333.046875f, 9.540248f, -6335.375000f, -9.349779f, 6337.656250f, 9.163829f, -6339.890625f, -8.982336f, 6342.082031f,
				8.803799f, -6344.230469f, -8.627977f, 6346.335938f, 8.455311f, -6348.398438f, -8.288118f, 6350.419922f, 8.123929f, -6352.402344f, -7.962116f, 6354.345703f,
				7.802615f, -6356.250000f, -7.645899f, 6358.115234f, 7.494190f, -6359.943359f, -7.344238f, 6361.734375f, 7.197956f, -6363.490234f, -7.054563f, 6365.210938f,
				6.915055f, -6366.898438f, -6.778359f, 6368.550781f, 6.645219f, -6370.171875f, -6.514186f, 6371.761719f, 6.384854f, -6373.320312f, -6.257181f, 6374.847656f,
				6.131486f, -6376.343750f, -6.009104f, 6377.808594f, 5.889138f, -6379.246094f, -5.772439f, 6380.654297f, 5.657561f, -6382.035156f, -5.543521f, 6383.386719f,
				5.433731f, -6384.712891f, -5.325897f, 6386.011719f, 5.218781f, -6387.285156f, -5.115513f, 6388.533203f, 5.012856f, -6389.757812f, -4.911867f, 6390.955078f,
				4.814037f, -6392.128906f, -4.718596f, 6393.279297f, 4.627031f, -6394.408203f, -4.534530f, 6395.515625f, 4.443304f, -6396.599609f, -4.355855f, 6397.662109f,
				4.268830f, -6398.703125f, -4.183186f, 6399.724609f, 4.100256f, -6400.724609f, -4.018931f, 6401.705078f};

		AirspyFloatConverter converter = null;
		try {
			converter = new AirspyFloatConverter(Airspy.AIRSPY_SAMPLE_FLOAT32_IQ,false,null,null,null,null);
		} catch (Exception e) {
			System.out.println("EXCEPTION CAUGHT: " + e.getMessage());
		}
		assertNotNull(converter);
		converter.processSamplesFloat(input);

		System.out.println("[index]:\texpected\toutput");
		for(int i=0; i<input.length; i++) {
			//System.out.println("[" + i + "]:\t" + expected[i] + "\t" + input[i]);
			assertTrue("[" + i + "]: expected=" + expected[i] + " input=" + input[i], floatEquals(expected[i], input[i]));
		}
	}

	public void printArray(byte[] array) {
		System.out.print("[");
		for (int i = 0; i < array.length; i++) {
			System.out.print(" " + array[i]);
		}
		System.out.println("]");
	}

	public void printUnsignedArray(byte[] array) {
		System.out.print("[");
		for (int i = 0; i < array.length; i++) {
			System.out.print(" " + (array[i] & 0xff));
		}
		System.out.println("]");
	}

	public void printArray(float[] array) {
		System.out.print("[");
		for (int i = 0; i < array.length; i++) {
			System.out.print(" " + array[i]);
		}
		System.out.println("]");
	}

	public boolean floatEquals(float expected, float actual) {
		return actual < expected+0.0001 && actual > expected-0.0001;
	}
}