/*
 * Copyright 2014 ShangDao.Ltd  All rights reserved.
 * SiChuan ShangDao.Ltd PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * @BaseApplication.java  2014-2-28 上午10:37:51 - Carson
 * @author YanXu
 * @email:981385016@qq.com
 * @version 1.0
 */

package com.example.oemscandemo;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.DisplayMetrics;
import android.util.Log;

public class BaseApplication extends Application {

    private static BaseApplication mAppInstance;
    public static int mWidth;
    public static int mHeight;
    private static final String LOG_TAG = BaseApplication.class.getSimpleName();

    private static String mVersionCode;

    private static Context mAppContext;

    public static BaseApplication getAppContext() {
        return mAppInstance;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mAppInstance = this;
        mAppContext = getApplicationContext();
        try {
            mVersionCode = getPackageManager().getPackageInfo(getPackageName(),
                    0).versionName;
        } catch (NameNotFoundException e) {
            Log.d(LOG_TAG, "Version not found.");
        }
        initDeviceType();
//		LogcatHelper.getInstance(this).start();  
    }

    private void initDeviceType() {
        DisplayMetrics dis = getResources().getDisplayMetrics();
        mWidth = dis.widthPixels;
        mHeight = dis.heightPixels;

    }


}
