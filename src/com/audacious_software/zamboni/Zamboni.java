package com.audacious_software.zamboni;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Zamboni {
    private static Zamboni sInstance = null;

    private Context mContext = null;

    public static Zamboni getInstance(Context context) {
        if (Zamboni.sInstance == null) {
            Zamboni.sInstance = new Zamboni(context);
        }

        return Zamboni.sInstance;
    }

    private Zamboni(final Context context) {
        this.mContext = context.getApplicationContext();
    }

    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             return this.mContext.getPackageManager().canRequestPackageInstalls();
        }

        return true;
    }

    public void requestRequiredPermissions(Activity activity, int requestCode) {
        activity.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), requestCode);
    }
}
