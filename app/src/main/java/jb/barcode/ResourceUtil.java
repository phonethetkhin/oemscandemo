package jb.barcode;

import android.content.Context;

public class ResourceUtil {


    public static int getStringResIDByName(Context context, String name) {
        //Log.v("jiebao","getStringResIDByName context: "+context);
        return context.getResources().getIdentifier(name, "string",
                context.getPackageName());
    }


}
