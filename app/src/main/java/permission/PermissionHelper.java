package permission;

import android.app.Activity;
import android.content.pm.PackageManager;

/**
 * 动态权限帮助类
 * Created by Administrator on 2019/4/4 0004.
 */

public class PermissionHelper {
    private Activity mActivity;
    private PermissionInterface mPermissionInterface;

    public PermissionHelper(Activity mActivity, PermissionInterface mPermissionInterface) {
        this.mActivity = mActivity;
        this.mPermissionInterface = mPermissionInterface;
    }

    /**
     * 开始请求权限
     * 方法内部已经对Android M 或以上版本进行了判断，外部使用不再需要重复判断
     * 如果设备还不是M或以上版本，deniedPermissions为null，则也会回调到requestPermissionsSuccess方法。
     */
    public void requestPermissions() {
        String[] deniedPermissions = PermissionUtil.getDeniedPermissions(mActivity, mPermissionInterface.getPermissions());
        if (deniedPermissions != null && deniedPermissions.length > 0) {
            PermissionUtil.requestPermissions(mActivity, deniedPermissions, mPermissionInterface.getPermissionsRequestCode());
        } else {
            mPermissionInterface.requestPermissionsSuccess();
        }
    }

    public boolean requestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == mPermissionInterface.getPermissionsRequestCode()) {
            boolean isAllGranted = true;//是否全部权限已授权
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) {
                //已全部授权
                mPermissionInterface.requestPermissionsSuccess();
            } else {
                //权限有缺失
                mPermissionInterface.requestPermissionFail();
            }
            return true;
        }
        return false;
    }
}
