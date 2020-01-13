package com.example.oemscandemo;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;

import jb.Preference;

public class OemBeepManager {

    private static final String TAG = "BeepManagerSdl";
    private final float BEEP_VOLUME = 0.3f;
    private final long VIBRATE_DURATION = 200L;

    private boolean playBeep = false;
    private boolean vibrate = false;

    private Context mContext;
    private int loadId1;
    private SoundPool mSoundPool;
    private Vibrator mVibrator;

    public OemBeepManager(Context context, boolean playBeep, boolean vibrate) {
        super();
        this.mContext = context;
        this.playBeep = playBeep;
        this.vibrate = vibrate;
        initial();
    }

    public OemBeepManager(Context context) {
        super();
        this.mContext = context;
        this.playBeep = Preference.getIsPlaySound(context);
        this.vibrate = Preference.getVibrate(context);
        initial();
    }


    private void initial() {

        if (null == mSoundPool) {
            mSoundPool = new SoundPool(5, AudioManager.STREAM_RING, 0);
        }

        loadId1 = mSoundPool.load(mContext, getRawResIDByName(mContext, "beep"), 1);
        // initialVibrator
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public static int getRawResIDByName(Context context, String name) {
        return context.getResources().getIdentifier(name, "raw",
                context.getPackageName());
    }

    public void play() {
        // playMusic
        // if (playBeep && !km.inKeyguardRestrictedInputMode()) {
        // mMediaPlayer.start();
        // }
        if (Preference.getIsPlaySound(mContext)) {
            // mMediaPlayer.start();
            // Parameter 1：Play the ID value of the loaded effect
            // Parameter 2：Left channel volume(range = 0.0 to 1.0)
            // Parameter 3：Right channel volume(range = 0.0 to 1.0)
            // Parameter 4：Priority of special effects music playback，Because you can play multiple special effects music at the same time
            // Parameter 5：Whether to loop，0 Play only once(0 = no loop, -1 = loop forever)
            // Parameter 6：Speed of special effect music playback，1F is normal playback，range 0.5 To 2.0
            mSoundPool.play(loadId1, 0.5f, 0.5f, 1, 0, 2.0f);
        }

        if (Preference.getVibrate(mContext)) {
            mVibrator.vibrate(VIBRATE_DURATION);
        }

    }

}
