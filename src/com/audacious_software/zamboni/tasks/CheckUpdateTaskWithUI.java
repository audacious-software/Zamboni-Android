package com.audacious_software.zamboni.tasks;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.audacious_software.zamboni.R;
import com.audacious_software.zamboni.UpdateActivity;
import com.audacious_software.zamboni.UpdateFragment;
import com.audacious_software.zamboni.UpdateManagerListener;
import com.audacious_software.zamboni.utils.HockeyLog;
import com.audacious_software.zamboni.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * <h3>Description</h3>
 *
 * Internal helper class. Checks if a new update is available by
 * fetching version data from Hockeyapp.
 *
 **/
public class CheckUpdateTaskWithUI extends CheckUpdateTask {

    private WeakReference<AppCompatActivity> mWeakActivity = null;
    private AlertDialog mDialog = null;
    protected boolean mIsDialogRequired = false;

    public CheckUpdateTaskWithUI(WeakReference<AppCompatActivity> weakActivity, Uri updateFeed, UpdateManagerListener listener, boolean isDialogRequired) {
        super(weakActivity, updateFeed, listener);

        this.mWeakActivity = weakActivity;
        this.mIsDialogRequired = isDialogRequired;
    }

    @Override
    public void detach() {
        super.detach();

        mWeakActivity = null;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    protected void onPostExecute(JSONArray updateInfo) {
        super.onPostExecute(updateInfo);

        if ((updateInfo != null) && (mIsDialogRequired)) {
            showDialog(mWeakActivity.get(), updateInfo);
        }
    }

    private void showDialog(final AppCompatActivity activity, final JSONArray updateInfo) {
        if ((activity == null) || (activity.isFinishing())) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.hockeyapp_update_dialog_title);

        if (!mandatory) {
            builder.setMessage(R.string.hockeyapp_update_dialog_message);
            builder.setNegativeButton(R.string.hockeyapp_update_dialog_negative_button, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    cleanUp();
                    if (null != listener) {
                        listener.onCancel();
                    }
                }
            });

            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cleanUp();
                    if (null != listener) {
                        listener.onCancel();
                    }
                }
            });

            builder.setPositiveButton(R.string.hockeyapp_update_dialog_positive_button, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    boolean useUpdateDialog = listener != null
                            ? listener.useUpdateDialog(activity)
                            : Util.runsOnTablet(activity);
                    if (useUpdateDialog) {
                        showUpdateFragment(activity, updateInfo);
                    } else {
                        startUpdateIntent(activity, updateInfo, false);
                    }
                }
            });

            mDialog = builder.create();
            mDialog.show();
        } else {
            String appName = Util.getAppName(activity);
            String toast = activity.getString(R.string.hockeyapp_update_mandatory_toast, appName);
            Toast.makeText(activity, toast, Toast.LENGTH_LONG).show();
            startUpdateIntent(activity, updateInfo, true);
        }
    }

    private void showUpdateFragment(AppCompatActivity activity, final JSONArray updateInfo) {
        if (activity != null) {
            FragmentTransaction fragmentTransaction = activity.getSupportFragmentManager().beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            Fragment existingFragment = activity.getSupportFragmentManager().findFragmentByTag(UpdateFragment.FRAGMENT_TAG);
            if (existingFragment != null) {
                fragmentTransaction.remove(existingFragment);
            }
            fragmentTransaction.addToBackStack(null);

            // Create and show the dialog
            Class<? extends UpdateFragment> fragmentClass = UpdateFragment.class;
            if (listener != null) {
                fragmentClass = listener.getUpdateFragmentClass();
            }

            try {
                Method method = fragmentClass.getMethod("newInstance", String.class, String.class, boolean.class);
                DialogFragment updateFragment = (DialogFragment) method.invoke(null, updateInfo, true);
                updateFragment.show(fragmentTransaction, UpdateFragment.FRAGMENT_TAG);

            } catch (Exception e) { // can't catch ReflectiveOperationException here because not targeting API level 19 or later
                HockeyLog.error("An exception happened while showing the update fragment", e);
            }
        }
    }

    private void startUpdateIntent(AppCompatActivity activity, final JSONArray updateInfo, Boolean finish) {
        if (activity != null) {
            Class<? extends UpdateFragment> fragmentClass = UpdateFragment.class;
            if (listener != null) {
                fragmentClass = listener.getUpdateFragmentClass();
            }

            Intent intent = new Intent();
            intent.setClass(activity, UpdateActivity.class);
            intent.putExtra(UpdateActivity.FRAGMENT_CLASS, fragmentClass.getName());
            intent.putExtra(UpdateFragment.FRAGMENT_VERSION_INFO, updateInfo.toString());

            try {
                intent.putExtra(UpdateFragment.FRAGMENT_URL, updateInfo.getJSONObject(0).getString("app_url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            intent.putExtra(UpdateFragment.FRAGMENT_DIALOG, false);
            activity.startActivity(intent);

            if (finish) {
                activity.finish();
            }
        }

        cleanUp();
    }

    @Override
    protected void cleanUp() {
        super.cleanUp();
        mWeakActivity = null;
        mDialog = null;
    }
}
