package permission;

/**
 * 权限请求接口
 * Created by yu on 2019/4/4 0004.
 */

public interface PermissionInterface {
    /**
     * 设置请求码
     * @return
     */
    int getPermissionsRequestCode();

    /**
     * 设置需要请求的权限
     * @return
     */
    String[] getPermissions();

    /**
     * 权限请求成功
     */
    void requestPermissionsSuccess();

    /**
     * 权限请求失败
     */
    void requestPermissionFail();
}
