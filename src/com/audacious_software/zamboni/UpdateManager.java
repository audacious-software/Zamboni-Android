package com.audacious_software.zamboni;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.audacious_software.zamboni.tasks.CheckUpdateTask;
import com.audacious_software.zamboni.tasks.CheckUpdateTaskWithUI;
import com.audacious_software.zamboni.utils.AsyncTaskUtils;

import java.lang.ref.WeakReference;
import java.util.Date;

/**
 * <h3>Description</h3>
 * <p>
 * The update manager sends version information to HockeyApp and
 * shows an alert dialog if a new version was found.
 **/
public class UpdateManager {
    public static final String INSTALLER_ADB = "adb";
    public static final String INSTALLER_PACKAGE_INSTALLER_NOUGAT = "com.google.android.packageinstaller";
    public static final String INSTALLER_PACKAGE_INSTALLER_NOUGAT2 = "com.android.packageinstaller";

    /**
     * Singleton for update task.
     */
    private static CheckUpdateTask updateTask = null;

    /**
     * Registers new update manager.
     *
     * @param activity      Parent activity.
     */
    public static void register(AppCompatActivity activity, Uri updateFeed) {
        register(activity, updateFeed, true);
    }

    /**
     * Registers new update manager.
     *
     * @param activity         Parent activity.
     * @param isDialogRequired Flag to indicate if a dialog must be shown when any update is available.
     */
    public static void register(AppCompatActivity activity, Uri updateFeed, boolean isDialogRequired) {
        register(activity, null, updateFeed, isDialogRequired);
    }

    /**
     * Registers new update manager.
     *
     * @param activity         parent activity
     * @param listener         implement for callback functions
     * @param isDialogRequired if false, no alert dialog is shown
     */
    public static void register(AppCompatActivity activity, UpdateManagerListener listener, Uri updateFeed, boolean isDialogRequired) {
        Constants.loadFromContext(activity);

        WeakReference<AppCompatActivity> weakActivity = new WeakReference<>(activity);

        if (dialogShown(weakActivity)) {
            return;
        }

        if ((!checkExpiryDate(weakActivity, listener)) && ((listener != null && listener.canUpdateInMarket()) || !installedFromMarket(weakActivity))) {
            startUpdateTask(weakActivity, listener, updateFeed, isDialogRequired);
        }
    }

    /**
     * Registers new update manager.
     *
     * @param appContext    Application context.
     * @param listener      Implement for callback functions.
     */
    public static void registerForBackground(Context appContext, UpdateManagerListener listener, Uri updateFeed) {
        WeakReference<Context> weakContext = new WeakReference<>(appContext);

        if ((!checkExpiryDateForBackground(listener)) && ((listener != null && listener.canUpdateInMarket()) || !installedFromMarket(weakContext))) {
            startUpdateTaskForBackground(weakContext, listener, updateFeed);
        }
    }

    /**
     * Unregisters the update manager
     */
    public static void unregister() {
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask.detach();
            updateTask = null;
        }
    }

    /**
     * Returns true if the build is expired and starts an activity if not
     * handled by the owner of the UpdateManager.
     */
    private static boolean checkExpiryDate(WeakReference<AppCompatActivity> weakActivity, UpdateManagerListener listener) {
        boolean handle = false;

        boolean hasExpired = checkExpiryDateForBackground(listener);
        if (hasExpired) {
            handle = listener.onBuildExpired();
        }

        if ((hasExpired) && (handle)) {
            startExpiryInfoIntent(weakActivity);
        }

        return hasExpired;
    }

    /**
     * Returns true if the build is expired and starts an activity if not
     * handled by the owner of the UpdateManager.
     */
    private static boolean checkExpiryDateForBackground(UpdateManagerListener listener) {
        boolean result = false;

        if (listener != null) {
            Date expiryDate = listener.getExpiryDate();
            result = ((expiryDate != null) && (new Date().compareTo(expiryDate) > 0));
        }

        return result;
    }

    /**
     * Returns true if the build was installed through a market.
     */
    protected static boolean installedFromMarket(WeakReference<? extends Context> weakContext) {
        boolean result = false;

        Context context = weakContext.get();
        if (context != null) {
            try {
                String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
                // if installer string is not null it might be installed by market
                if (!TextUtils.isEmpty(installer)) {
                    result = true;

                    // on Android Nougat and up when installing an app through the package installer (which HockeyApp uses itself), the installer will be
                    // "com.google.android.packageinstaller" or "com.android.packageinstaller" which is also not to be considered as a market installation
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (TextUtils.equals(installer, INSTALLER_PACKAGE_INSTALLER_NOUGAT) || TextUtils.equals(installer, INSTALLER_PACKAGE_INSTALLER_NOUGAT2))) {
                        result = false;
                    }

                    // on some devices (Xiaomi) the installer identifier will be "adb", which is not to be considered as a market installation
                    if (TextUtils.equals(installer, INSTALLER_ADB)) {
                        result = false;
                    }
                }

            } catch (Throwable ignored) {
            }
        }

        return result;
    }

    /**
     * Starts the ExpiryInfoActivity as a new task and finished the current
     * activity.
     */
    private static void startExpiryInfoIntent(WeakReference<AppCompatActivity> weakActivity) {
        if (weakActivity != null) {
            AppCompatActivity activity = weakActivity.get();
            if (activity != null) {
                activity.finish();

                Intent intent = new Intent(activity, ExpiryInfoActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        }
    }

    /**
     * Starts the UpdateTask if not already running. Otherwise attaches the
     * activity to it.
     */
    private static void startUpdateTask(WeakReference<AppCompatActivity> weakActivity, UpdateManagerListener listener, Uri updateFeed, boolean isDialogRequired) {
        if ((updateTask == null) || (updateTask.getStatus() == Status.FINISHED)) {
            updateTask = new CheckUpdateTaskWithUI(weakActivity, updateFeed, listener, isDialogRequired);
            AsyncTaskUtils.execute(updateTask);
        } else {
            updateTask.attach(weakActivity);
        }
    }

    /**
     * Starts the UpdateTask if not already running. Otherwise attaches the
     * activity to it.
     */
    private static void startUpdateTaskForBackground(WeakReference<Context> weakContext, UpdateManagerListener listener, Uri updateFeed) {
        if ((updateTask == null) || (updateTask.getStatus() == Status.FINISHED)) {
            updateTask = new CheckUpdateTask(weakContext, updateFeed, listener);
            AsyncTaskUtils.execute(updateTask);
        } else {
            updateTask.attach(weakContext);
        }
    }

    /**
     * Returns true if the dialog is already shown.
     */
    private static boolean dialogShown(WeakReference<AppCompatActivity> weakActivity) {
        if (weakActivity != null) {
            AppCompatActivity activity = weakActivity.get();
            if (activity != null) {
                Fragment existingFragment = activity.getSupportFragmentManager().findFragmentByTag("hockey_update_dialog");
                return (existingFragment != null);
            }
        }
        return false;
    }
}
