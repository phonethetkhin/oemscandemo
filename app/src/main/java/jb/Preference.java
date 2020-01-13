package jb;

import android.content.Context;
import android.content.SharedPreferences;

import jb.barcode.ResourceUtil;

public class Preference {
    private static SharedPreferences getSP(Context context) {
        return context.getSharedPreferences(
                context.getString(ResourceUtil.getStringResIDByName(context,
                        "app_name")), Context.MODE_PRIVATE);
    }

    /**
     * @param context
     * @return
     */
    public static boolean getIsPlaySound(Context context) {
        return getSP(context).getBoolean("IsPlaySound", true);
    }


    /**
     * @param context
     * @return
     */
    public static String getCustomPrefix(Context context) {
        return getSP(context).getString("CustomPrefix", "");
    }


    /**
     * @param context
     * @return
     */
    public static String getCustomSuffix(Context context) {
        return getSP(context).getString("CustomSuffix", "");
    }



    /**
     * @param context
     * @return
     */
    public static boolean getVibrate(Context context) {
        return getSP(context).getBoolean("Vibrate", true);
    }





    /**
     * 后台扫描输出模式  默认快速扫描
     *
     * @param context
     * @param ScanOutMode 1.快速扫描（文本框） 2.模拟键盘 3.广播
     */
    public static void setScanOutMode(Context context,
                                      int ScanOutMode) {
        getSP(context).edit()
                .putInt("ScanOutMode", ScanOutMode)
                .commit();
    }

    public static int getScanOutMode(Context context) {
        return getSP(context).getInt("ScanOutMode", 1);
    }




    public static boolean getScanSelfopenSupport(Context context,
                                                 boolean defaultValues) {
        return getSP(context)
                .getBoolean("IsScanSelfopenSupport", defaultValues);
    }


}
