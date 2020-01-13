package com.example.oemscandemo;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import jb.Preference;


public class OemBootBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		// TODO Auto-generated method stub

		//Log.e("jiebao", "OemBootBroadcastReceiver " + Preference.getScanSelfopenSupport(BaseApplication.getAppContext(), true));

		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			if (Preference.getScanSelfopenSupport(BaseApplication.getAppContext(), true)) {
				Intent service = new Intent(context, OemScanService.class);
				context.startService(service);
			}
		}

	}
}
