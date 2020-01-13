package com.example.oemscandemo;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.hsm.barcode.DecodeOptions;
import com.hsm.barcode.DecodeResult;
import com.hsm.barcode.DecodeWindowing.DecodeWindow;
import com.hsm.barcode.DecodeWindowing.DecodeWindowMode;
import com.hsm.barcode.Decoder;
import com.hsm.barcode.DecoderConfigValues.LightsMode;
import com.hsm.barcode.DecoderConfigValues.OCRMode;
import com.hsm.barcode.DecoderConfigValues.OCRTemplate;
import com.hsm.barcode.DecoderConfigValues.SymbologyFlags;
import com.hsm.barcode.DecoderConfigValues.SymbologyID;
import com.hsm.barcode.DecoderException;
import com.hsm.barcode.DecoderListener;
import com.hsm.barcode.ExposureValues.ExposureMode;
import com.hsm.barcode.ExposureValues.ExposureSettings;
import com.hsm.barcode.SymbologyConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import jb.Preference;

public class OemScanService extends Service implements DecoderListener {

    protected static final String TAG = "OemScanService";
    private long clicknowTime = 0;
    private long clicklastTime = 0;
    public Decoder m_Decoder = null;                // Decoder object
    public DecodeResult m_decResult = null;            // Result object
    public int g_nImageWidth = 0;            // Global image width
    public int g_nImageHeight = 0;            // Global image height
    private static boolean bOkToScan = false;            // Flag to start scanning
    private static boolean bDecoding = false;            // Flag to start decoding
    private static boolean bRunThread = false;            // Flag to run thread
    private static boolean bThreadDone = true;            // Flag to signal thread done
    private static boolean bAppRetainsPreferences = false;  // Retain preference settings when changing activities
    private static final int AUS_POST = 1;
    private static final int JAPAN_POST = 3;
    private static final int KIX = 4;
    private static final int PLANETCODE = 5;
    private static final int POSTNET = 6;
    private static final int ROYAL_MAIL = 7;
    private static final int UPU_4_STATE = 9;
    private static final int USPS_4_STATE = 10;
    private static final int US_POSTALS = 29;
    private static final int CANADIAN = 30;
    private static boolean bWaitMultiple = false;    // flag for single or multiple decode
    private int g_nMultiReadResultCount = 0;        // For tracking # of multiread results
    private int g_nMaxMultiReadCount = 0;        // Maximum multiread count
    private static int g_nDecodeTimeout = 10000;        // Decode timeout 10 seconds
    private static boolean g_bContinuousScanEnabled = false;        // Continuous scan option
    private static boolean g_bContinuousScanStarted = false;  // Continuous scan started (TODO: test me)
    private static String g_strFileSaveType = "pgm";        // File save type extension
    private static int g_nTotalDecodeTime = 0;    // Used to capture total decode time (for averaging)
    private static int g_nNumberOfDecodes = 0;    // Used to capture total decodes
    public static long decodeTime = 0;                    // Time for decode
    private ScanListener mScanListener;

    private boolean isStopConnect = false;
    private boolean islockScreen = false;

    private Intent scanDataIntent;
    private Intent EditTextintent;
    public boolean g_bKeepGoing = true;        // for trigger callback
    public boolean bTriggerReleased = true;

    private boolean isConnect = false;
    private OemBeepManager mBeepManager;
    public Binder myBinder = new MyBinder();
    private static File ss = new File("/proc/jbcommon/gpio_control/se4710");
    private static File ss_isCameraOpen = new File("/proc/jbcommon/gpio_control/isCameraOpen");
    private String openCamera = "31";
    private readFileThread readFilethread = null;
    private Context mContext;

    private boolean scanEnbale = false;

    public class MyBinder extends Binder {
        public OemScanService getService() {
            return OemScanService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public void onCreate() {
        super.onCreate();

        mContext = getBaseContext();
        IntentFilter jbScanFilter = new IntentFilter();
//		jbScanFilter.addAction("com.jb.action.SCAN_SWITCH");
//		jbScanFilter.addAction("com.jb.action.START_SCAN");
//		jbScanFilter.addAction("com.jb.action.STOP_SCAN");
//		jbScanFilter.addAction("com.jb.action.START_SCAN_CONTINUE");
//		jbScanFilter.addAction("com.jb.action.STOP_SCAN_CONTINUE");
        jbScanFilter.addAction("com.jb.action.F4key");
        jbScanFilter.addAction(Intent.ACTION_SCREEN_ON);
        jbScanFilter.addAction(Intent.ACTION_SCREEN_OFF);
        jbScanFilter.addAction("com.android.HT380KCameraUse");
        this.registerReceiver(f4Receiver, jbScanFilter);
        mBeepManager = new OemBeepManager(this);
        m_decResult = new DecodeResult();
        m_Decoder = new Decoder();
        //  openScan();
        setScanOutMode(this, 3);

//		readFilethread = new readFileThread ();
//		readFilethread.run =true;
//		readFilethread.start();

        registerReceiverScanBroadcast();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mScanBroadcast != null)
            unregisterReceiver(mScanBroadcast);
    }

    private void openScan() {

        ScanSleepHandler.removeCallbacks(ScanSleepRunnable);
        ScanSleepHandler.postDelayed(ScanSleepRunnable, scanSleepTime);

        if (!scanEnbale)
            return;
        try {
            writeFile(ss, "1");
            //m_Decoder.disconnectDecoderLibrary();
            m_Decoder.connectDecoderLibrary();
            isConnect = true;
            g_nImageWidth = m_Decoder.getImageWidth();
            g_nImageHeight = m_Decoder.getImageHeight();

            // Start "decode thread"
            if (bRunThread == false) {
                new Thread(new Task()).start();
                bRunThread = true;
            }

            int g_nExposureSettings[] =
                    {
                            ExposureSettings.DEC_ES_FIXED_EXP, 25,
                    };

            try {
                m_Decoder.setExposureSettings(g_nExposureSettings);
                m_Decoder.setExposureMode(ExposureMode.FIXED);
            } catch (DecoderException e) {
                Log.e("kaka", "OemScanService DecoderException error1:" + e.toString());
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            bAppRetainsPreferences = prefs.getBoolean("sym_default", false);
            Log.e("kaka", "bAppRetainsPreferences=" + bAppRetainsPreferences);
            if (!bAppRetainsPreferences) {
                bAppRetainsPreferences = true;

                // Configure preference settings to defaults...
                Log.d(TAG, "Configure preference settings to defaults...");

                SetSymbologyPreferences(true);
                try {
                    enableAllSymbologies();
                    SetSymbologyPreferences(false);
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                SetOcrPreferences(true);
                SetSymbologySettings();
                SetOcrSettings();
                SetDecodingSettings();
                SetScanningSettings();
                SetApplicationSettings();

                Editor editor = prefs.edit();
                editor.putBoolean("sym_default", true);
                editor.commit();
            }

            // Re-configure preferences based on user preferences...
            Log.d(TAG, "Configure preferences based on user settings...");

            SetSymbologySettings();
            SetOcrPreferences(true);
            SetOcrSettings();
            SetDecodingSettings();
            SetScanningSettings();
            SetApplicationSettings();
            SetSymbologyPreferences(false);
            // TODO: Enable feature if we are able to connect
            // FIXME: If multiread enabled?
            m_Decoder.setDecoderListeners(this);
        } catch (DecoderException e) {
            Log.e("kaka", "OemScanService DecoderException error:" + e.toString());
        }

    }

    ScanBroadcast mScanBroadcast;
    String ScanBroadcast_ACTION_OPEN = "com.jbservice.action.OPEN_SCAN";
    String ScanBroadcast_ACTION_START = "com.jbservice.action.START_SCAN";
    String ScanBroadcast_ACTION_CLOSE = "com.jbservice.action.STOP_SCAN";
    String ScanBroadcast_ACTION_SCANRESULT = "com.jbservice.action.GET_SCANDATA";
    int scanSleepTime = 60000 * 1;
    Handler ScanSleepHandler = new Handler();
    Runnable ScanSleepRunnable = new Runnable() {
        @Override
        public void run() {
            release();
            Log.d("kaka", "ScanSleepRunnable");
        }
    };

    public class ScanBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ScanBroadcast_ACTION_OPEN)) {
                if (!scanEnbale) {
                    scanEnbale = true;
                    openScan();
                    Log.d("kaka", "ScanBroadcast_ACTION_OPEN");
                }
            } else if (action.equals(ScanBroadcast_ACTION_CLOSE)) {
                if (scanEnbale) {
                    scanEnbale = false;
                    release();

                    Log.d("kaka", "ScanBroadcast_ACTION_CLOSE");
                }
            } else if (action.equals(ScanBroadcast_ACTION_START)) {
                onClickScan(null);
            }
        }
    }

    public void setScanEnbale(boolean enbale) {
        scanEnbale = enbale;
        if (scanEnbale) {
            openScan();
        } else {
            release();
        }
    }

    public void registerReceiverScanBroadcast() {
        mScanBroadcast = new ScanBroadcast();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ScanBroadcast_ACTION_OPEN);
        intentFilter.addAction(ScanBroadcast_ACTION_CLOSE);
        intentFilter.addAction(ScanBroadcast_ACTION_START);
        registerReceiver(mScanBroadcast, intentFilter);
    }


    private synchronized static void writeFile(File file, String value) {

        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(value.getBytes());
            outputStream.flush();
            outputStream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private class readFileThread extends Thread {
        public boolean run;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            while (run) {
                openCamera = readFile(ss);
//				if(openCamera.equals("31")&& bcr==null){
//					Log.v(TAG,"readFileThread395 bcr: "+bcr);
//					try {
//						Thread.sleep(2000);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					Log.v(TAG,"readFileThread402 bcr: "+bcr);
//					openBcr();
//				} else
                if (openCamera.equals("30") && m_Decoder != null && isConnect) {
                    release();
                }
            }
        }

    }


    private void release() {
        // TODO Auto-generated method stub
        Log.d(TAG, "release------->");
        ScanSleepHandler.removeCallbacks(ScanSleepRunnable);
        StopScanning();
        Log.d(TAG, "stop scanning--");
        if (bDecoding) Log.d(TAG, "waiting for scan stop...");
        while (bDecoding) ;
        if (!bDecoding) Log.d(TAG, "...done waiting for scan stop");
        try {
            Log.d(TAG, "disconnectDecoderLibrary<-------");
            m_Decoder.disconnectDecoderLibrary();
            isConnect = false;
            Log.d(TAG, "disconnectDecoderLibrary<-------");
            //g_nImageHeight = 0;
            //g_nImageWidth = 0;
        } catch (DecoderException e) {
            e.printStackTrace();
        }

        bThreadDone = true; // signal we will wait for tread to stop
        bRunThread = false;    // signal to stop thread

        // wait for thread to stop
        while (!bThreadDone) Log.d(TAG, "waiting for thread to stop...");

        // m_Decoder = null;
        Log.d(TAG, "release<-------");
    }

    public synchronized static String readFile(File file) {

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[1];
            inputStream.read(buffer);
            String read = bytesToHexString(buffer);
            inputStream.close();
            return read;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static String bytesToHexString(byte[] src) {

        StringBuilder stringBuilder = new StringBuilder("");

        if (src == null || src.length <= 0) {
            return null;
        }
        try {
            for (int i = 0; i < src.length; i++) {

                int v = src[i] & 0xFF;

                String hv = Integer.toHexString(v);

                if (hv.length() < 2) {
                    stringBuilder.append(0);
                }
                stringBuilder.append(hv);

            }
            return stringBuilder.toString();
        } catch (StackOverflowError e) {
            Log.e("jiebao", "Exception e: " + e);
            return "";
        }
    }

    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    /**
     * Called when the user clicks the Scan button
     */
    public void onClickScan(View view) {

        if (!bOkToScan) {
            // Let the user know they can stop scanning by pressing scan button again
            if (g_bContinuousScanEnabled)
                Toast.makeText(getApplicationContext(), "Press scan button to stop continuous scanning.", Toast.LENGTH_LONG).show();
            processScanButtonPress();

            if (bWaitMultiple)
                bTriggerReleased = true; // release trigger so it can restart
        } else
            StopScanning();
    }

    /**
     * Decode Thread
     */
    public static boolean getTopActivity(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
//	    if(cn.getPackageName().equals("com.android.camera.CameraL"))
//	    	return true;
//	    else
//	    	return false;

        return false;
//	    return cn.getClassName();
    }

    class Task implements Runnable {
        @Override
        public void run() {
            // local vars
            long decodeStartTime = 0;
            long decodeEndTime = 0;
            bThreadDone = false;
            Log.e(TAG, "***** DECODE THREAD IS RUNNING *****");
            while (bRunThread) { // forever?

                try {
                    Thread.sleep(50); // TODO: sleep for 50 ms before doing again?

                    if (isStopConnect == true) {
                        //Log.e("kaka","Task implements Runnable isStopConnect == true");
                        continue;
                    }

                    if (bOkToScan) {
                        //Log. d(TAG, "OK to scan...");
                        if (!g_bContinuousScanEnabled)
                            bOkToScan = false; // don't scan again until told to

                        synchronized (this) {
                            if (!bDecoding) {
                                bDecoding = true;
                                decodeStartTime = System.currentTimeMillis();
                                try {
                                    if (!bWaitMultiple) {
                                        m_Decoder.waitForDecodeTwo(g_nDecodeTimeout, m_decResult);    // wait for decode with results arg
                                    } else {
                                        g_nMultiReadResultCount = 0;
                                        m_Decoder.waitMultipleDecode(g_nDecodeTimeout);                    // wait for multiple
                                    }
                                } catch (DecoderException e) {
                                    //HandleDecoderException(e);
                                    e.printStackTrace();
                                }

                                decodeEndTime = System.currentTimeMillis();
                                decodeTime = decodeEndTime - decodeStartTime;

                                if (!bWaitMultiple) {

                                    if (m_decResult.length > 0 && (!TextUtils.isEmpty(m_decResult.barcodeData))) {

                                        String prefix = Preference.getCustomPrefix(OemScanService.this);
                                        String suffix = Preference.getCustomSuffix(OemScanService.this);
                                        if (!TextUtils.isEmpty(prefix)) {
                                            m_decResult.barcodeData = prefix + m_decResult.barcodeData;
                                            m_decResult.length += prefix.length();
                                        }

                                        if (!TextUtils.isEmpty(suffix)) {
                                            m_decResult.barcodeData = m_decResult.barcodeData + suffix;
                                            m_decResult.length += suffix.length();
                                        }

                                    }


                                    if (m_decResult.length > 0) {
                                        Log.d("kaka", "dddd:" + m_decResult);
                                        if (m_decResult.barcodeData.length() != 0) {
                                            houtai_result("3", m_decResult.barcodeData);
                                        } else {
                                            String m_strDecodedData = "";
                                            m_decResult.byteBarcodeData = m_Decoder.getBarcodeByteData();
                                            int BytesInLabel = 0;
                                            do {
                                                m_strDecodedData += String.format("%02x", m_decResult.byteBarcodeData[BytesInLabel] & 0xff);
                                                BytesInLabel++;
                                            } while (BytesInLabel < m_decResult.byteBarcodeData.length);

                                            houtai_result("3", m_strDecodedData);
                                        }
                                    }

                                    mBeepManager.play();

                                    if (mScanListener != null) {
                                        mScanListener.DisplayDecodeResults();
                                    }

                                }

                                bDecoding = false;
                            }
                        }
//						m_Decoder.stopScanning();
//						//release();
//						StopScanning();

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (DecoderException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            bThreadDone = true;
            bRunThread = false;

            Log.d(TAG, "!!!!! DECODE THREAD HAS STOPPED RUNNING !!!!!");
        }
    }


    public void houtai_result(String codeid, String data) {

        Log.d("kaka", "houtai_result:" + data + "/" + getScanOutMode());
        if (data != null && !data.trim().equals("")) {
            int out_mode = getScanOutMode();
            switch (out_mode) {
                case 3:
                    sendScanBroadcast(codeid, data);
                    break;
                case 2:
                    //simulatekey(data);
                    break;
                case 1:
                    //sendEditTextBroadcast(data);
                    break;
                default:
                    break;
            }
        }
    }


    /**
     * 将内容发送广播
     *
     * @param dataStr
     */
    public void sendScanBroadcast(String codeid, String dataStr) {
        // TODO Auto-generated method stub
        Intent scanDataIntent = new Intent("com.jbservice.action.GET_SCANDATA");
        scanDataIntent.putExtra("data", dataStr);
        scanDataIntent.putExtra("codetype", codeid);
        this.sendBroadcast(scanDataIntent);
    }


    public void setOnScanListener(ScanListener scanListener) {
        this.mScanListener = scanListener;

    }

    @SuppressWarnings("deprecation")
    void SetSymbologySettings() //throws DecoderException
    {
        Log.d(TAG, "SetSymbologySettings++");

        int flags = 0;                                            // flags config
        int min = 0;                                            // minimum length config
        int max = 0;                                            // maximum length config
        int postal_config = 0;                                    // postal config
        String temp;                                            // temp string for converting string to int
        SymbologyConfig symConfig = new SymbologyConfig(0);        // symbology config
        int min_default, max_default;
        String strMinDefault = null;
        String strMaxDefault = null;
        boolean bNotSupported = false;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        for (int i = 0; i < SymbologyID.SYM_ALL; i++) {

            symConfig.symID = i;                                // symID
            //if( i != SymbologyID.SYM_OCR &&
            //	i != SymbologyID.SYM_POSTALS )
            //m_Decoder.getSymbologyConfig(symConfig,false); // gets the current symConfig
            flags = 0;                                            // reset the flags

            // Set appropriate sym config mask...
            switch (i) {
                // Flag & Range:
                case SymbologyID.SYM_AZTEC:
                case SymbologyID.SYM_CODABAR:
                case SymbologyID.SYM_CODE11:
                case SymbologyID.SYM_CODE128:
                case SymbologyID.SYM_GS1_128:
                case SymbologyID.SYM_CODE39:
                    //case SymbologyID.SYM_CODE49: 		// not supported
                case SymbologyID.SYM_CODE93:
                case SymbologyID.SYM_COMPOSITE:
                case SymbologyID.SYM_DATAMATRIX:
                case SymbologyID.SYM_INT25:
                case SymbologyID.SYM_MAXICODE:
                case SymbologyID.SYM_MICROPDF:
                case SymbologyID.SYM_PDF417:
                case SymbologyID.SYM_QR:
                case SymbologyID.SYM_RSS:
                case SymbologyID.SYM_IATA25:
                case SymbologyID.SYM_CODABLOCK:
                case SymbologyID.SYM_MSI:
                case SymbologyID.SYM_MATRIX25:
                case SymbologyID.SYM_KOREAPOST:
                case SymbologyID.SYM_STRT25:
                    //case SymbologyID.SYM_PLESSEY: 	// not supported
                case SymbologyID.SYM_CHINAPOST:
                case SymbologyID.SYM_TELEPEN:
                    //case SymbologyID.SYM_CODE16K: 	// not supported
                    //case SymbologyID.SYM_POSICODE:	// not supported
                case SymbologyID.SYM_HANXIN:
                    //case SymbologyID.SYM_GRIDMATRIX:	// not supported
                    try {
                        m_Decoder.getSymbologyConfig(symConfig); // gets the current symConfig
                        min_default = m_Decoder.getSymbologyMinRange(i);
                        strMinDefault = Integer.toString(min_default);
                        max_default = m_Decoder.getSymbologyMaxRange(i);
                        strMaxDefault = Integer.toString(max_default);
                    } catch (DecoderException e) {
                        //HandleDecoderException(e);
                        e.printStackTrace();
                    }
                    symConfig.Mask = SymbologyFlags.SYM_MASK_FLAGS | SymbologyFlags.SYM_MASK_MIN_LEN | SymbologyFlags.SYM_MASK_MAX_LEN;
                    break;
                // Flags Only:
                case SymbologyID.SYM_EAN8:
                case SymbologyID.SYM_EAN13:
                case SymbologyID.SYM_POSTNET:
                case SymbologyID.SYM_UPCA:
                case SymbologyID.SYM_UPCE0:
                case SymbologyID.SYM_UPCE1:
                case SymbologyID.SYM_ISBT:
                case SymbologyID.SYM_BPO:
                case SymbologyID.SYM_CANPOST:
                case SymbologyID.SYM_AUSPOST:
                case SymbologyID.SYM_JAPOST:
                case SymbologyID.SYM_PLANET:
                case SymbologyID.SYM_DUTCHPOST:
                case SymbologyID.SYM_TLCODE39:
                case SymbologyID.SYM_TRIOPTIC:
                case SymbologyID.SYM_CODE32:
                case SymbologyID.SYM_COUPONCODE:
                case SymbologyID.SYM_USPS4CB:
                case SymbologyID.SYM_IDTAG:
                    //case SymbologyID.SYM_LABEL:		// not supported
                case SymbologyID.SYM_US_POSTALS1:
                    try {
                        m_Decoder.getSymbologyConfig(symConfig); // gets the current symConfig
                    } catch (DecoderException e) {
                        //e.printStackTrace();
                    }
                    symConfig.Mask = SymbologyFlags.SYM_MASK_FLAGS;
                    break;
                // default:
                default:
                    // invalid / not supported
                    bNotSupported = true;
                    break;
            }

            // Set symbology config...
            switch (i) {
                case SymbologyID.SYM_AZTEC:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_aztec_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_aztec_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_aztec_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODABAR:
                    // enable, check char, start/stop transmit, codabar concatenate
                    flags |= sharedPrefs.getBoolean("sym_codabar_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_codabar_check_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_codabar_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_codabar_start_stop_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_START_STOP_XMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_codabar_concatenate_enable", false) ? SymbologyFlags.SYMBOLOGY_CODABAR_CONCATENATE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_codabar_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_codabar_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODE11:
                    // enable, check char
                    flags |= sharedPrefs.getBoolean("sym_code11_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_code11_check_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_code11_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_code11_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODE128:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_code128_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_code128_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_code128_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_GS1_128:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_gs1_128_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_gs1_128_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_gs1_128_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODE39:
                    // enable, check char, start/stop transmit, append, full ascii
                    flags |= sharedPrefs.getBoolean("sym_code39_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_code39_check_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_code39_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_code39_start_stop_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_START_STOP_XMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_code39_append_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE_APPEND_MODE : 0;
                    flags |= sharedPrefs.getBoolean("sym_code39_fullascii_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE_FULLASCII : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_code39_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_code39_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODE49:
                case SymbologyID.SYM_PLESSEY:
                case SymbologyID.SYM_CODE16K:
                case SymbologyID.SYM_POSICODE:
                case SymbologyID.SYM_LABEL:
                    // not supported
                    break;
                case SymbologyID.SYM_GRIDMATRIX:
                    flags |= sharedPrefs.getBoolean("sym_gridmatrix_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_gridmatrix_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_gridmatrix_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODE93:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_code93_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_code93_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_code93_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_COMPOSITE:
                    // enable, composit upc
                    flags |= sharedPrefs.getBoolean("sym_composite_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_composite_upc_enable", false) ? SymbologyFlags.SYMBOLOGY_COMPOSITE_UPC : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_composite_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_composite_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_DATAMATRIX:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_datamatrix_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_datamatrix_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_datamatrix_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_EAN8:
                    // enable, check char transmit, addenda separator, 2 digit addena, 5 digit addena, addena required
                    flags |= sharedPrefs.getBoolean("sym_ean8_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean8_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean8_addenda_separator_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean8_2_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean8_5_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean8_addenda_required_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED : 0;
                    break;
                case SymbologyID.SYM_EAN13:
                    // enable, check char transmit, addenda separator, 2 digit addena, 5 digit addena, addena required
                    flags |= sharedPrefs.getBoolean("sym_ean13_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean13_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean13_addenda_separator_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean13_2_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean13_5_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_ean13_addenda_required_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED : 0;
                    break;
                case SymbologyID.SYM_INT25:
                    // enable, check enable, check transmit
                    flags |= sharedPrefs.getBoolean("sym_int25_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_int25_check_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_int25_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_int25_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_int25_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_MAXICODE:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_maxicode_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_maxicode_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_maxicode_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_MICROPDF:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_micropdf_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_micropdf_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_micropdf_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_PDF417:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_pdf417_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_pdf417_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_pdf417_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_QR:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_qr_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_qr_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_qr_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_HANXIN:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_hanxin_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_hanxin_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_hanxin_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_RSS:
                    // rss enable, rsl enable, rse enable
                    flags |= sharedPrefs.getBoolean("sym_rss_rss_enable", false) ? SymbologyFlags.SYMBOLOGY_RSS_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_rss_rsl_enable", false) ? SymbologyFlags.SYMBOLOGY_RSL_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_rss_rse_enable", false) ? SymbologyFlags.SYMBOLOGY_RSE_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_rss_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_rss_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_UPCA:
                    // enable, check transmit, sys num transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    flags |= sharedPrefs.getBoolean("sym_upca_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_sys_num_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_NUM_SYS_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_addenda_separator_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_2_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_5_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_upca_addenda_required_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED : 0;
                    flags |= sharedPrefs.getBoolean("sym_translate_upca_to_ean13_enable", false) ? SymbologyFlags.SYMBOLOGY_UPCA_TRANSLATE_TO_EAN13 : 0;
                    break;
                case SymbologyID.SYM_UPCE1:
                    // upce1 enable
                    flags |= sharedPrefs.getBoolean("sym_upce1_upce1_enable", false) ? SymbologyFlags.SYMBOLOGY_UPCE1_ENABLE : 0;
                    break;
                case SymbologyID.SYM_UPCE0:
                    // enable, upce expanded, char char transmit, num sys transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    flags |= sharedPrefs.getBoolean("sym_upce0_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_upce_expanded_enable", false) ? SymbologyFlags.SYMBOLOGY_EXPANDED_UPCE : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_sys_num_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_NUM_SYS_TRANSMIT : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_addenda_separator_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_2_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_5_digit_addenda_enable", false) ? SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA : 0;
                    flags |= sharedPrefs.getBoolean("sym_upce0_addenda_required_enable", false) ? SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED : 0;
                    break;
                case SymbologyID.SYM_ISBT:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_isbt_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_IATA25:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_iata25_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_iata25_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_iata25_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CODABLOCK:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_codablock_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_codablock_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_codablock_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;

                /* Post Symbology Config */
                case SymbologyID.SYM_POSTNET:
                    Log.d(TAG, "Configure SYM_POSTNET");
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == POSTNET) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    Log.d(TAG, "SYM_POSTNET postal_config = " + postal_config);
                    Log.d(TAG, "SYM_POSTNET flags = " + flags);
                    // check transmit
                    flags |= sharedPrefs.getBoolean("sym_postnet_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    break;
                case SymbologyID.SYM_JAPOST:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == JAPAN_POST) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_PLANET:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == PLANETCODE) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_DUTCHPOST:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == KIX) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_US_POSTALS1:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == US_POSTALS) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_USPS4CB:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == USPS_4_STATE) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_IDTAG:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == UPU_4_STATE) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_BPO:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == ROYAL_MAIL) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_CANPOST:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == CANADIAN) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_AUSPOST:
                    // enable
                    temp = sharedPrefs.getString("sym_post_config", "0");
                    postal_config = Integer.parseInt(temp);
                    flags |= (postal_config == AUS_POST) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // Bar output
                    sharedPrefs.getBoolean("sym_auspost_bar_output_enable", false);
                    // Interpret Mode
                    temp = sharedPrefs.getString("sym_aus_interpret_mode", "0");
                    postal_config = Integer.parseInt(temp);
                    switch (postal_config) {
                        // Numeric N Table:
                        case 1:
                            flags |= SymbologyFlags.SYMBOLOGY_AUS_POST_NUMERIC_N_TABLE;
                            break;
                        // Alphanumeric C Table:
                        case 2:
                            flags |= SymbologyFlags.SYMBOLOGY_AUS_POST_ALPHANUMERIC_C_TABLE;
                            break;
                        // Combination N & C Tables:
                        case 3:
                            flags |= SymbologyFlags.SYMBOLOGY_AUS_POST_COMBINATION_N_AND_C_TABLES;
                            break;
                        default:
                            break;
                    }
                    break;
                /* ===================== */

                case SymbologyID.SYM_MSI:
                    // enable, check transmit
                    flags |= sharedPrefs.getBoolean("sym_msi_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_msi_check_transmit_enable", false) ? SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT : 0;
                    Log.d(TAG, "sym msi flags = " + flags);
                    // min, max
                    temp = sharedPrefs.getString("sym_msi_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_msi_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_TLCODE39:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_tlcode39_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_MATRIX25:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_matrix25_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_matrix25_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_matrix25_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_KOREAPOST:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_koreapost_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_koreapost_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_koreapost_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_TRIOPTIC:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_trioptic_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_CODE32:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_code32_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;
                case SymbologyID.SYM_STRT25:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_strt25_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_strt25_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_strt25_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_CHINAPOST:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_chinapost_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_chinapost_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_chinapost_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_TELEPEN:
                    // enable, telepen old style
                    flags |= sharedPrefs.getBoolean("sym_telepen_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    flags |= sharedPrefs.getBoolean("sym_telepen_telepen_old_style_enable", false) ? SymbologyFlags.SYMBOLOGY_TELEPEN_OLD_STYLE : 0;
                    // min, max
                    temp = sharedPrefs.getString("sym_telepen_min", strMinDefault);
                    min = Integer.parseInt(temp);
                    temp = sharedPrefs.getString("sym_telepen_max", strMaxDefault);
                    max = Integer.parseInt(temp);
                    break;
                case SymbologyID.SYM_COUPONCODE:
                    // enable
                    flags |= sharedPrefs.getBoolean("sym_couponcode_enable", false) ? SymbologyFlags.SYMBOLOGY_ENABLE : 0;
                    break;

                default:
                    symConfig.Mask = 0; // will not setSymbologyConfig
                    break;
            }

            if (bNotSupported) {
                bNotSupported = false; // // do nothing, but reset flag
            }
            if (symConfig.Mask == (SymbologyFlags.SYM_MASK_FLAGS | SymbologyFlags.SYM_MASK_MIN_LEN | SymbologyFlags.SYM_MASK_MAX_LEN)) // Flags & Range
            {
                symConfig.Flags = flags;
                symConfig.MinLength = min;
                symConfig.MaxLength = max;
                try {
                    m_Decoder.setSymbologyConfig(symConfig);
                } catch (DecoderException e) {
                    Log.d(TAG, "1 EXCEPTION SYMID = " + i);
                    //HandleDecoderException(e);
                    e.printStackTrace();
                }
            } else if (symConfig.Mask == (SymbologyFlags.SYM_MASK_FLAGS)) // Flag Only
            {
                symConfig.Flags = flags;
                try {
                    m_Decoder.setSymbologyConfig(symConfig);
                } catch (DecoderException e) {
                    Log.d(TAG, "2 EXCEPTION SYMID = " + i);
                    //HandleDecoderException(e);
                    e.printStackTrace();
                }
            } else {
                // invalid
            }
        }

        Log.d(TAG, "SetSymbologySettings--");
    }


    /**
     * Sets the OCR settings based on user preferences
     *
     * @throws DecoderException
     */
    void SetOcrSettings() throws DecoderException {
        Log.d(TAG, "SetOcrSettings++");
        int ocr_mode = 0;
        int ocr_template = 0;

        String temp;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // mode (enable)
        temp = sharedPrefs.getString("sym_ocr_mode_config", "0");
        ocr_mode = Integer.parseInt(temp);
        // ocr template
        temp = sharedPrefs.getString("sym_ocr_template_config", "0");
        ocr_template = Integer.parseInt(temp);
        // user defined template
        temp = sharedPrefs.getString("sym_ocr_user_template", "1,3,7,7,7,7,7,7,7,7,0");
        String[] separated = temp.split(",");
        byte[] ocr_user_defined_template = new byte[separated.length];

        int i = 0;
        do {
            ocr_user_defined_template[i] = Byte.parseByte(separated[i]);
            i++;
        }
        while (i != separated.length);

        Log.d(TAG, "ocr mode = " + ocr_mode);
        Log.d(TAG, "ocr template config = " + ocr_template);
        Log.d(TAG, "ocr user template string = " + temp);
        for (i = 0; i < ocr_user_defined_template.length; i++)
            Log.d(TAG, "ocr user template bytes[" + i + "] = " + ocr_user_defined_template[i]);

        m_Decoder.setOCRMode(ocr_mode);
        m_Decoder.setOCRTemplates(ocr_template);
        m_Decoder.setOCRUserTemplate(ocr_user_defined_template);

        Log.d(TAG, "SetOcrSettings--");
    }

    /**
     * Sets the Decoder settings based on user preferences
     *
     * @throws DecoderException
     */
    void SetDecodingSettings() throws DecoderException {
        Log.d(TAG, "SetDecodingSettings++");

        String temp;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Decode Timeout
        temp = sharedPrefs.getString("decoding_pref_decode_timeout", Integer.toString(10));
        g_nDecodeTimeout = Integer.parseInt(temp) * 1000; // sec to ms

        // Windowing
        DecodeWindow myWindow = new DecodeWindow();
        boolean enable_windowing = false;
        boolean bDebugWindowMode = false;
        int nMode = DecodeWindowMode.DECODE_WINDOW_MODE_DISABLED;

        Log.d(TAG, "nMode = " + nMode);

        enable_windowing = sharedPrefs.getBoolean("decode_centering_enable", false);

        temp = sharedPrefs.getString("decode_centering_mode", "2");
        nMode = Integer.parseInt(temp);
        temp = sharedPrefs.getString("decode_window_upper_left_x", "0");
        myWindow.UpperLeftX = Integer.parseInt(temp);
        temp = sharedPrefs.getString("decode_window_upper_left_y", "0");
        myWindow.UpperLeftY = Integer.parseInt(temp);
        temp = sharedPrefs.getString("decode_window_lower_right_x", "0");
        myWindow.LowerRightX = Integer.parseInt(temp);
        temp = sharedPrefs.getString("decode_window_lower_right_y", "0");
        myWindow.LowerRightY = Integer.parseInt(temp);
        bDebugWindowMode = sharedPrefs.getBoolean("decode_debug_window_enable", false);

        if (enable_windowing) {
            Log.d(TAG, "Centering is enabled");

            Log.d(TAG, "enable the mode... nMode = " + nMode);
            // enable the mode
            m_Decoder.setDecodeWindowMode(nMode);

            Log.d(TAG, "set the window... myWindow.UpperLeftX = " + myWindow.UpperLeftX);
            // set the window
            m_Decoder.setDecodeWindow(myWindow);

            Log.d(TAG, "set the debug window");
            // set the debug window
            //if(bDebugWindowMode) nMode = DecodeWindowShowWindow.DECODE_WINDOW_SHOW_WINDOW_WHITE; // white
            //	m_Decoder.setShowDecodeWindow(nMode);

        } else {
            // disable windowing
            m_Decoder.setDecodeWindowMode(nMode);
        }


        // Decode Search Limit
        temp = sharedPrefs.getString("decode_time_limit", "800");
        m_Decoder.setDecodeAttemptLimit(Integer.parseInt(temp));

        // WaitForDecode timeout only
        temp = sharedPrefs.getString("decode_wait_for_decode_config", "0");
        bWaitMultiple = (Integer.parseInt(temp) == 1) ? true : false;

        // Multiread count
        temp = sharedPrefs.getString("decode_multiread_count", "1");
        g_nMaxMultiReadCount = Integer.parseInt(temp);
        DecodeOptions decOpt = new DecodeOptions();
        decOpt.DecAttemptLimit = -1; // ignore
        decOpt.VideoReverse = -1; // ignore
        decOpt.MultiReadCount = g_nMaxMultiReadCount;
        m_Decoder.setDecodeOptions(decOpt);


        //Log. d(TAG, "SetDecodingSettings--");
    }

    /**
     * Sets the Scanning settings based on user preferences
     *
     * @throws DecoderException
     * @throws NumberFormatException
     */
    void SetScanningSettings() throws NumberFormatException, DecoderException {
        Log.d(TAG, "SetScanningSettings++");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int myLightsMode = LightsMode.ILLUM_AIM_ON;

        Log.d(TAG, "myLightsMode = " + myLightsMode);

        /* Lights Mode */
        String lightsModeString = prefs.getString("lightsConfig", "3");
        myLightsMode = Integer.parseInt(lightsModeString);
        m_Decoder.setLightsMode(myLightsMode);

        g_bContinuousScanEnabled = prefs.getBoolean("continous_scan_enable", false);

        Log.d(TAG, "SetScanningSettings--");
    }

    /**
     * Sets the Application settings based on user preferences
     */
    void SetApplicationSettings() {
        Log.d(TAG, "SetApplicationSettings++");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        /* File Save Type */
        g_strFileSaveType = prefs.getString("app_pref_file_save_type", "pgm");
        Log.d(TAG, "filesavetype = " + g_strFileSaveType);

        Log.d(TAG, "SetApplicationSettings--");
    }

    /**
     * Sets Application preferences based on settings
     */
    void SetApplicationPreferences(boolean bDefault) {
        Log.d(TAG, "SetApplicationPreferences++");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.edit();

        /* Scan Button */
        // ignored ... this is retained

        /* File Save Type */
        // ignored ... this is retained
    }

    /**
     * Enables all symbologies
     *
     * @throws DecoderException
     */
    void enableAllSymbologies() throws DecoderException {
        if (m_Decoder != null) {
            m_Decoder.enableSymbology(SymbologyID.SYM_ALL);
        }
    }

    /**
     * Disables all symbologies
     *
     * @throws DecoderException
     */
    void disableAllSymbologies() throws DecoderException {
        if (m_Decoder != null) {
            m_Decoder.disableSymbology(SymbologyID.SYM_ALL);
        }
    }

    /**
     * Sets default preferences based on "HSMDecoderAPI" settings
     *
     * @throws DecoderException
     */
    @SuppressWarnings("deprecation")
    void SetSymbologyPreferences(boolean bDefault)// throws DecoderException
    {
        Log.d(TAG, "SetSymbologyPreferences++");

        SymbologyConfig symConfig = new SymbologyConfig(0);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        Editor editor = prefs.edit();

        for (int i = 0; i < SymbologyID.SYM_ALL; i++) {

            symConfig.symID = i; // TODO: move me?

            try {
                if (bDefault)
                    m_Decoder.getSymbologyConfigDefaults(symConfig);
                else
                    m_Decoder.getSymbologyConfig(symConfig);
            } catch (DecoderException e) {
                // Exceptions are OK here since we are only "getting"
                Log.d(TAG, "SymId " + i + " " + e.getMessage());
            }

            switch (i) {
                case SymbologyID.SYM_AZTEC:
                    // enable, min, max
                    editor.putBoolean("sym_aztec_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_aztec_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_aztec_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODABAR:
                    // enable, check enable, start/stop transmit, codabar concatenate, min, max
                    editor.putBoolean("sym_codabar_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_codabar_check_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_codabar_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_codabar_start_stop_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_START_STOP_XMIT) > 0 ? true : false);
                    editor.putBoolean("sym_codabar_concatenate_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CODABAR_CONCATENATE) > 0 ? true : false);
                    editor.putString("sym_codabar_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_codabar_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODE11:
                    // enable, check enable, min, max
                    editor.putBoolean("sym_code11_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_code11_check_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_ENABLE) > 0 ? true : false);
                    editor.putString("sym_code11_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_code11_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODE128:
                    // enable, min, max
                    editor.putBoolean("sym_code128_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_code128_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_code128_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_GS1_128:
                    // enable, min, max
                    editor.putBoolean("sym_gs1_128_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_gs1_128_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_gs1_128_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODE39:
                    // enable, check enable, start/stop transmit, append, fullascii
                    editor.putBoolean("sym_code39_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_code39_check_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_code39_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_code39_start_stop_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_START_STOP_XMIT) > 0 ? true : false);
                    editor.putBoolean("sym_code39_append_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE_APPEND_MODE) > 0 ? true : false);
                    editor.putBoolean("sym_code39_fullascii_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE_FULLASCII) > 0 ? true : false);
                    editor.putString("sym_code39_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_code39_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODE49:
                case SymbologyID.SYM_PLESSEY:
                case SymbologyID.SYM_CODE16K:
                case SymbologyID.SYM_POSICODE:
                case SymbologyID.SYM_LABEL:
                    // not supported
                    break;
                case SymbologyID.SYM_GRIDMATRIX:
                    editor.putBoolean("sym_gridmatrix_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_gridmatrix_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_gridmatrix_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CODE93:
                    // enable, min, max
                    editor.putBoolean("sym_code93_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_code93_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_code93_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_COMPOSITE:
                    // enable, composite upc, min, max
                    editor.putBoolean("sym_composite_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_composite_upc_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_COMPOSITE_UPC) > 0 ? true : false);
                    editor.putString("sym_composite_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_composite_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_DATAMATRIX:
                    // enable, min, max
                    editor.putBoolean("sym_datamatrix_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_datamatrix_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_datamatrix_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_EAN8:
                    // enable, check transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    editor.putBoolean("sym_ean8_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_ean8_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_ean8_addenda_separator_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR) > 0 ? true : false);
                    editor.putBoolean("sym_ean8_2_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_ean8_5_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_ean8_addenda_required_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_EAN13:
                    // enable, check transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    editor.putBoolean("sym_ean13_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_ean13_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_ean13_addenda_separator_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR) > 0 ? true : false);
                    editor.putBoolean("sym_ean13_2_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_ean13_5_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_ean13_addenda_required_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_INT25:
                    // enable, check enable, check transmit enable, min, max
                    editor.putBoolean("sym_int25_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_int25_check_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_int25_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putString("sym_int25_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_int25_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_MAXICODE:
                    // enable, min, max
                    editor.putBoolean("sym_maxicode_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_maxicode_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_maxicode_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_MICROPDF:
                    // enable, min, max
                    editor.putBoolean("sym_micropdf_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_micropdf_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_micropdf_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_PDF417:
                    // enable, min, max
                    editor.putBoolean("sym_pdf417_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_pdf417_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_pdf417_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_QR:
                    // enable, min, max
                    editor.putBoolean("sym_qr_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_qr_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_qr_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_HANXIN:
                    // enable, min, max
                    editor.putBoolean("sym_hanxin_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_hanxin_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_hanxin_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_RSS:
                    // rss enable, rsl enable, rse enable, min, max
                    editor.putBoolean("sym_rss_rss_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_RSS_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_rss_rsl_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_RSL_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_rss_rse_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_RSE_ENABLE) > 0 ? true : false);
                    editor.putString("sym_rss_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_rss_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_UPCA:
                    // enable, check transmit, sys num transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    editor.putBoolean("sym_upca_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_upca_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_upca_sys_num_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_NUM_SYS_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_upca_addenda_separator_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR) > 0 ? true : false);
                    editor.putBoolean("sym_upca_2_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_upca_5_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_upca_addenda_required_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED) > 0 ? true : false);
                    editor.putBoolean("sym_translate_upca_to_ean13_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_UPCA_TRANSLATE_TO_EAN13) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_UPCE1:
                    // upce1 enable
                    editor.putBoolean("sym_upce1_upce1_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_UPCE1_ENABLE) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_UPCE0:
                    // enable, upce expanded, char char transmit, num sys transmit, addenda separator, 2 digit addenda, 5 digit addenda, addenda required
                    editor.putBoolean("sym_upce0_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_upce_expanded_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_EXPANDED_UPCE) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_sys_num_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_NUM_SYS_TRANSMIT) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_addenda_separator_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_SEPARATOR) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_2_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_2_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_5_digit_addenda_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_5_DIGIT_ADDENDA) > 0 ? true : false);
                    editor.putBoolean("sym_upce0_addenda_required_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ADDENDA_REQUIRED) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_ISBT:
                    // enable
                    editor.putBoolean("sym_isbt_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_IATA25:
                    // enable, min, max
                    editor.putBoolean("sym_iata25_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_iata25_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_iata25_max", Integer.toString(symConfig.MaxLength));
                case SymbologyID.SYM_CODABLOCK:
                    // enable, min, max
                    editor.putBoolean("sym_codablock_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_codablock_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_codablock_max", Integer.toString(symConfig.MaxLength));
                    break;

                /* Post Symbology Config */
                case SymbologyID.SYM_POSTNET:
                    // check transmit
                    editor.putBoolean("sym_postnet_check_transmit_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_TRANSMIT) > 0 ? true : false);
                case SymbologyID.SYM_JAPOST:
                case SymbologyID.SYM_PLANET:
                case SymbologyID.SYM_DUTCHPOST:
                case SymbologyID.SYM_US_POSTALS1:
                case SymbologyID.SYM_USPS4CB:
                case SymbologyID.SYM_IDTAG:
                case SymbologyID.SYM_BPO:
                case SymbologyID.SYM_CANPOST:
                case SymbologyID.SYM_AUSPOST:
                    // enable (config)
                    editor.putString("sym_post_config", "0"); // i know this is disabled (no_postals) by default - another way?

                    if (i == SymbologyID.SYM_AUSPOST) {
                        // Default Bar Width & Interpret Mode (both off)
                        editor.putBoolean("sym_auspost_bar_output_enable", false);
                        editor.putString("sym_aus_interpret_mode", "0");
                    }

                    break;
                /* ===================== */

                case SymbologyID.SYM_MSI:
                    // enable, check enable, min, max
                    editor.putBoolean("sym_msi_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_msi_check_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_CHECK_ENABLE) > 0 ? true : false);
                    editor.putString("sym_msi_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_msi_max", Integer.toString(symConfig.MaxLength));
                case SymbologyID.SYM_TLCODE39:
                    // enable
                    editor.putBoolean("sym_tlcode39_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_MATRIX25:
                    // enable, min, max
                    editor.putBoolean("sym_matrix25_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_matrix25_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_matrix25_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_KOREAPOST:
                    // enable, min, max
                    editor.putBoolean("sym_koreapost_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_koreapost_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_koreapost_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_TRIOPTIC:
                    // enable
                    editor.putBoolean("sym_trioptic_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_CODE32:
                    // enable
                    editor.putBoolean("sym_code32_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    break;
                case SymbologyID.SYM_STRT25:
                    // enable, min, max
                    editor.putBoolean("sym_strt25_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_strt25_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_strt25_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_CHINAPOST:
                    // enable, min, max
                    editor.putBoolean("sym_chinapost_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putString("sym_chinapost_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_chinapost_max", Integer.toString(symConfig.MaxLength));
                case SymbologyID.SYM_TELEPEN:
                    // enable, telepen old style, min, max
                    editor.putBoolean("sym_telepen_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    editor.putBoolean("sym_telepen_telepen_old_style_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_TELEPEN_OLD_STYLE) > 0 ? true : false);
                    editor.putString("sym_telepen_min", Integer.toString(symConfig.MinLength));
                    editor.putString("sym_telepen_max", Integer.toString(symConfig.MaxLength));
                    break;
                case SymbologyID.SYM_COUPONCODE:
                    // enable
                    editor.putBoolean("sym_couponcode_enable", (symConfig.Flags & SymbologyFlags.SYMBOLOGY_ENABLE) > 0 ? true : false);
                    break;
                default:
                    break;
            }

            editor.commit();
        }

        // OCR Config (disabled, user, "1,3,7,7,7,7,7,7,7,7,0")
        editor.putBoolean("sym_ocr_enable", false);
        editor.putString("sym_ocr_mode_config", Integer.toString(OCRMode.OCR_OFF));
        editor.putString("sym_ocr_template_config", Integer.toString(OCRTemplate.USER));
        editor.putString("sym_ocr_user_template", "1,3,7,7,7,7,7,7,7,7,0");
        editor.commit();

        Log.d(TAG, "SetSymbologyPreferences--");
    }

    /**
     * Sets default OCR preferences based on "HSMDecoderAPI" settings
     *
     * @throws
     */
    void SetOcrPreferences(boolean bDefault) {
        Log.d(TAG, "SetOcrPreferences++");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Editor editor = prefs.edit();

        boolean bOk = true;
        int default_ocr_mode = -1;
        int default_template = -1;
        byte[] default_ocr_user_template = null;
        String default_ocr_user_template_string = null;
        try {
            default_ocr_mode = m_Decoder.getOCRMode();
            default_template = m_Decoder.getOCRTemplates();
            default_ocr_user_template = m_Decoder.getOCRUserTemplate();

            for (int i = 0; i < default_ocr_user_template.length; i++)
                Log.d(TAG, "default_ocr_user_template[" + i + "] = " + default_ocr_user_template[i]);

            Log.d(TAG, "default_ocr_mode = " + default_ocr_mode);
            Log.d(TAG, "default_template = " + default_template);

            // Convert 'default_ocr_user_template_string' to printable string...
            StringBuilder sb = new StringBuilder();
            for (byte b : default_ocr_user_template) {
                sb.append(String.format("%x,", b & 0xff));
            }
            sb.deleteCharAt(sb.length() - 1);
            Log.d(TAG, "sb = " + sb);
            default_ocr_user_template_string = sb.toString();

            Log.d(TAG, "default_ocr_user_template_string = " + default_ocr_user_template_string);
        } catch (DecoderException e) {
            bOk = false;
            //HandleDecoderException(e);
            e.printStackTrace();
        }
        //catch(UnsupportedEncodingException e)
        //{
        //	e.printStackTrace();
        //}

        if (bOk) {
            editor.putBoolean("sym_ocr_enable", false);
            editor.putString("sym_ocr_mode_config", Integer.toString(default_ocr_mode));
            editor.putString("sym_ocr_template_config", Integer.toString(default_template));
            editor.putString("sym_ocr_user_template", default_ocr_user_template_string);
        } else {
            Log.d(TAG, "!! FAILED TO GET OCR SETTINGS !!");

            // OCR Config (disabled, user, "1,3,7,7,7,7,7,7,7,7,0")
            editor.putBoolean("sym_ocr_enable", false);
            editor.putString("sym_ocr_mode_config", Integer.toString(OCRMode.OCR_OFF));
            editor.putString("sym_ocr_template_config", Integer.toString(OCRTemplate.USER));
            editor.putString("sym_ocr_user_template", "1,3,7,7,7,7,7,7,7,7,0");
        }
        editor.commit();

        //Log. d(TAG, "SetOcrPreferences--");
    }

    /**
     * Sets default Decoder preferences based on "HSMDecoderAPI" settings
     */
    void SetDecodingPreferences(boolean bDefault) {
        Log.d(TAG, "SetDecodingPreferences++");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Editor editor = prefs.edit();

        /* Decode Timeout */
        editor.putString("decoding_pref_decode_timeout", Integer.toString(10));

        int nMode = DecodeWindowMode.DECODE_WINDOW_MODE_DISABLED;
        int nDebugWindow = 0;
        DecodeWindow myWindow = new DecodeWindow();
        boolean bOk = true;
        try {
            Log.d(TAG, "getDecodeWindow");
            m_Decoder.getDecodeWindow(myWindow);
            Log.d(TAG, "getDecodeWindowMode");
            nMode = m_Decoder.getDecodeWindowMode();
            Log.d(TAG, "getShowDecodeWindow");
            nDebugWindow = m_Decoder.getShowDecodeWindow();
            //bOk = false;
        } catch (DecoderException e) {
            //HandleDecoderException(e);
            e.printStackTrace();
            bOk = false;
        }

        if (!bOk) // safety
        {
            // Window
            editor.putString("decode_window_upper_left_x", Integer.toString(386));    // #define IT6000_DEFAULT_DECODE_WINDOW_ULX       386
            editor.putString("decode_window_upper_left_y", Integer.toString(290));    // #define IT6000_DEFAULT_DECODE_WINDOW_ULY       290
            editor.putString("decode_window_lower_right_x", Integer.toString(446));    // #define IT6000_DEFAULT_DECODE_WINDOW_LRX       446
            editor.putString("decode_window_lower_right_y", Integer.toString(350)); // #define IT6000_DEFAULT_DECODE_WINDOW_LRY       350
            // App Enable
            editor.putBoolean("decode_centering_enable", false);
            // Mode
            editor.putString("decode_centering_mode", Integer.toString(0)); // default is actually off
            // Debug Window
            editor.putBoolean("decode_debug_window_enable", false);
        } else {
            // Window
            editor.putString("decode_window_upper_left_x", Integer.toString(myWindow.UpperLeftX));
            editor.putString("decode_window_upper_left_y", Integer.toString(myWindow.UpperLeftY));
            editor.putString("decode_window_lower_right_x", Integer.toString(myWindow.LowerRightX));
            editor.putString("decode_window_lower_right_y", Integer.toString(myWindow.LowerRightY));
            // App Enable
            editor.putBoolean("decode_centering_enable", (nMode > 0) ? true : false); // if nMode > 0, centering is enabled
            // Mode
            editor.putString("decode_centering_mode", Integer.toString(nMode)); // default is actually off, but 2 is typical if enabled
            // Debug Window
            editor.putBoolean("decode_debug_window_enable", (nDebugWindow > 0) ? true : false); // only support off and white
        }

        /* Decode Search Limit */
        editor.putString("decode_search_limit", Integer.toString(800));

        /* WaitForDecode */
        editor.putString("decode_wait_for_decode_config", Integer.toString(0)); // wait for single by default

        /* Multiread Count */
        editor.putString("decode_multiread_count", Integer.toString(2));


        editor.commit();

        //Log. d(TAG, "SetDefaultDecoderPreferences--");
    }

    /**
     * Sets default Decoder preferences based on "HSMDecoderAPI" settings
     */
    void SetScanningPreferences(boolean bDefault) {
        Log.d(TAG, "SetScanningPreferences++");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Editor editor = prefs.edit();

        /* Lights Mode */
        editor.putString("lightsConfig", Integer.toString(3));
        editor.commit();

        /* Continuous Scan */
        editor.putBoolean("continous_scan_enable", false);
        editor.commit();

        //Log. d(TAG, "SetDefaultScanningPreferences--");
    }

    private BroadcastReceiver f4Receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Bundle bundle = intent.getExtras();

            if (intent.hasExtra("F4key")) {

                Log.e("jiebao", "F4key islockScreen " + islockScreen + " isStopConnect " + isStopConnect);
                Log.e("kaka", "F4key islockScreen " + islockScreen + " isStopConnect " + isStopConnect);
                if (intent.getStringExtra("F4key").equals("down") && !islockScreen) {


                    clicknowTime = System.currentTimeMillis();
                    if (isStopConnect == true) {
                        return;
                    }

                    if (clicknowTime - clicklastTime > 200) {
                        clicklastTime = clicknowTime;
                        if (!bOkToScan) {
                            // Let the user know they can stop scanning by pressing scan button again
                            if (g_bContinuousScanEnabled)
                                Toast.makeText(getApplicationContext(), "Press scan button to stop continuous scanning.", Toast.LENGTH_LONG).show();
                            if (bDecoding == false) {
                                processScanButtonPress();
                                if (bWaitMultiple)
                                    bTriggerReleased = true; // release trigger so it can restart
                            } else {
                                Log.e(TAG, "f4 Receiver but do nothing ");
                            }

                        } else {
                            StopScanning();
                        }
                    }

                } else if (intent.getStringExtra("F4key").equals("up")) {

                }

            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // 开屏
                Log.e("jiebao", "ACTION_SCREEN_ON");
                islockScreen = false;

                isStopConnect = false;
                openScan();

            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // 锁屏
                Log.e("jiebao", "ACTION_SCREEN_OFF");
                islockScreen = true;

                isStopConnect = true;
                try {
                    m_Decoder.stopScanning();
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                release();

            } else if ("com.android.HT380KCameraUse".equals(intent.getAction())) {
                String value = intent.getStringExtra("data");
                Log.e(TAG, "com.android.HT380KCameraUse Receive :" + value);
                if (value.equals("1")) {
                    isStopConnect = true;
                    try {
                        m_Decoder.stopScanning();
                    } catch (DecoderException e) {
                        e.printStackTrace();
                    }
                    release();
                } else if (value.equals("0")) {
                    isStopConnect = false;
                    openScan();
                }
            }
        }
    };


    public String getCurrentAppPackage() {
        String result = "";
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (android.os.Build.VERSION.SDK_INT < 21) {
            // 如果没有就用老版本
            List<RunningTaskInfo> runningTaskInfos = manager.getRunningTasks(1);
            if (runningTaskInfos != null && runningTaskInfos.size() > 0) {
                result = runningTaskInfos.get(0).topActivity.getPackageName();
            }
        } else {
            List<RunningAppProcessInfo> runningApp = manager.getRunningAppProcesses();
            if (runningApp != null && runningApp.size() > 0) {
                result = runningApp.get(0).processName;
            }
        }
        if (TextUtils.isEmpty(result)) {
            result = "";
        }
        return result;
    }

    /**
     * Processes the scan button press
     */
    void processScanButtonPress() {
        Log.v(TAG, "processScanButtonPress ss_isCameraOpen:" + readFile(ss_isCameraOpen));
        if (!scanEnbale)
            return;

        if (isConnect == false && m_Decoder != null && readFile(ss_isCameraOpen).equals("30")) {
            try {
                if (bRunThread == false) {
                    new Thread(new Task()).start();
                    bRunThread = true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                writeFile(ss, "1");
                m_Decoder.connectDecoderLibrary();
                isConnect = true;
            } catch (DecoderException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            StartScanning();
        } else if (m_Decoder != null && readFile(ss_isCameraOpen).equals("30")) {
            StartScanning();
        }
    }

    /**
     * Starts scanning - enables flag to start scanning (decoding)
     */
    void StartScanning() {
        if (bOkToScan == false && bTriggerReleased == true) {
            if (bWaitMultiple)
                bTriggerReleased = false; // need to wait for trigger to be released
            try {
                //扫码之前获取扫码头属性
                SetSymbologySettings();
                SetOcrSettings();
                SetDecodingSettings();
                SetScanningSettings();
                SetApplicationSettings();
                //	enableAllSymbologies();// caleb.liao
            } catch (NumberFormatException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (DecoderException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            bOkToScan = true;
            g_bKeepGoing = true; // KeepGoing is true for trigger callback

            ScanSleepHandler.removeCallbacks(ScanSleepRunnable);
            ScanSleepHandler.postDelayed(ScanSleepRunnable, scanSleepTime);
        } else {
            Log.d(TAG, "unable to start scanning");
        }
    }

    /**
     * Stops scanning - disables flag to stop scanning / cancel decode (decoding)
     */
    void StopScanning() {
        bTriggerReleased = true;
        bOkToScan = false;
        g_bKeepGoing = false; // KeepGoing is false for trigger callback
        g_nTotalDecodeTime = 0;
        g_nNumberOfDecodes = 0;
    }


    /**
     * Callback to keep scanning (i.e. trigger callback)
     */
    @Override
    public boolean onKeepGoingCallback() {
        Log.d(TAG, "onKeepGoingCallback");

        Log.d(TAG, "g_bKeepGoing = " + g_bKeepGoing);

        return (g_bKeepGoing);
    }

    /**
     * Callback when multiple decode results are available
     */
    @Override
    public boolean onMultiReadCallback() {
        Log.d(TAG, "onMultipleDecodeResults");

        // Do something with the results
        if (mScanListener != null)
            mScanListener.DisplayMultireadResults();

        // Give the UI thread time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Stop scanning if max count is acheived
        Log.d(TAG, "g_nMultiReadResultCount=" + g_nMultiReadResultCount + ",g_nMaxMultiReadCount=" + g_nMaxMultiReadCount);
        if (g_nMultiReadResultCount == g_nMaxMultiReadCount) {
            Log.d(TAG, "MAX MULTI!!");
            return false;
        }

        return true;
    }

    protected void onDestory() {
        Log.d(TAG, "onDestory");

        StopScanning();

        if (bDecoding) Log.d(TAG, "waiting for scan stop...");
        while (bDecoding) ;
        if (!bDecoding) Log.d(TAG, "...done waiting for scan stop");
        try {
            Log.d(TAG, "disconnectDecoderLibrary------》");
            m_Decoder.disconnectDecoderLibrary();
            writeFile(ss, "0");
            isConnect = false;
            Log.d(TAG, "disconnectDecoderLibrary《-------");
            //g_nImageHeight = 0;
            //g_nImageWidth = 0;
        } catch (DecoderException e) {
            e.printStackTrace();
            ;
        }

        bThreadDone = true; // signal we will wait for tread to stop
        bRunThread = false;    // signal to stop thread

        // wait for thread to stop
        while (!bThreadDone) Log.d(TAG, "waiting for thread to stop...");

        Log.d(TAG, "m_Decoder null++");
        m_Decoder = null;
        Log.d(TAG, "m_Decoder null--");
        if (null != f4Receiver) {
            this.unregisterReceiver(f4Receiver);
        }
        readFilethread.run = false;
        if (null != readFilethread) {
            notifyReader();
            readFilethread = null;
        }
    }

    private void notifyReader() {
        if (readFilethread != null && readFilethread.isAlive()) {
            readFilethread.interrupt();
        }
    }

    public int getScanOutMode() {
        return Preference.getScanOutMode(this);
    }

    public void setScanOutMode(Context context,
                               int ScanOutMode) {
        Preference.setScanOutMode(context, ScanOutMode);
    }
}
