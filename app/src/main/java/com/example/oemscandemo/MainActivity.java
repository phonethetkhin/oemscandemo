package com.example.oemscandemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import jb.Preference;
import permission.PermissionHelper;
import permission.PermissionInterface;

public class MainActivity extends Activity implements PermissionInterface {
    private PermissionHelper permissionHelper;
    Context context;

    @Override
    public int getPermissionsRequestCode() {
        return 1000;
    }

    @Override
    public String[] getPermissions() {
        return new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
    }

    @Override
    public void requestPermissionsSuccess() {

    }

    @Override
    public void requestPermissionFail() {
        this.finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissionHelper.requestPermissionsResult(requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionHelper = new PermissionHelper(this, this);
        permissionHelper.requestPermissions();
        context = this;
        if (Preference.getScanSelfopenSupport(BaseApplication.getAppContext(), true)) {
            Intent service = new Intent(context, OemScanService.class);
            context.startService(service);
        }

       /* Button btn = (Button) findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, OemScanActivity.class);
                startActivity(intent);
            }
        });*/
    }


}
