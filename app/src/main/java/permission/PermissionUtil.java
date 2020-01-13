package permission;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;

/**
 * 动态权限请求工具类
 * Created by Administrator on 2019/4/4 0004.
 */

public class PermissionUtil {
    /**
     * 判断是否有某个权限
     *
     * @param context
     * @param permission
     * @return
     */
    public static boolean hasPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 弹出对话框请求权限
     *
     * @param activity
     * @param permissions
     * @param reqestCode
     */
    public static void requestPermissions(Activity activity, String[] permissions, int reqestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(permissions, reqestCode);
        }
    }

    /**
     * 返回缺失的权限
     *
     * @param context
     * @param permissions
     * @return null意味着没有缺少权限
     */
    public static String[] getDeniedPermissions(Context context, String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> deniedPermissionList = new ArrayList<>();
            for (String permisson : permissions) {
                if (context.checkSelfPermission(permisson) != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissionList.add(permisson);
                }
            }
            int size = deniedPermissionList.size();
            if (size > 0) {
                return deniedPermissionList.toArray(new String[deniedPermissionList.size()]);
            }
        }
        return null;
    }
}
