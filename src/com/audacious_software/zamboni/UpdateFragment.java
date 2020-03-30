package com.audacious_software.zamboni;


import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.DialogFragment;

import com.audacious_software.zamboni.listeners.DownloadFileListener;
import com.audacious_software.zamboni.tasks.DownloadFileTask;
import com.audacious_software.zamboni.tasks.GetFileSizeTask;
import com.audacious_software.zamboni.utils.AsyncTaskUtils;
import com.audacious_software.zamboni.utils.PermissionsUtil;
import com.audacious_software.zamboni.utils.Util;
import com.audacious_software.zamboni.utils.VersionHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * <h3>Description</h3>
 *
 * Fragment to show update information and start the download
 * process if the user taps the corresponding button.
 *
 **/
public class UpdateFragment extends DialogFragment implements View.OnClickListener, UpdateInfoListener {

    /**
     * The URL of the APK to offer as download
     */
    public static final String FRAGMENT_URL = "url";

    /**
     * Metadata about the update
     */
    public static final String FRAGMENT_VERSION_INFO = "versionInfo";

    /**
     * Show as dialog
     */
    public static final String FRAGMENT_DIALOG = "dialog";

    public static final String FRAGMENT_TAG = "hockey_update_dialog";

    /**
     * JSON string with info for each version.
     */
    private String mVersionInfo;

    /**
     * HockeyApp URL as a string.
     */
    private String mUrlString;

    /**
     * Creates a new instance of the fragment.
     *
     * @param versionInfo JSON string with info for each version.
     * @return Instance of Fragment
     */
    @SuppressWarnings("unused")
    static public UpdateFragment newInstance(JSONObject versionInfo, boolean dialog) {
        Bundle arguments = new Bundle();

        try {
            arguments.putString(FRAGMENT_URL, versionInfo.getString("app_url"));
            arguments.putString(FRAGMENT_VERSION_INFO, versionInfo.toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        arguments.putBoolean(FRAGMENT_DIALOG, dialog);

        UpdateFragment fragment = new UpdateFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();

        // To properly support landscape dialog
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null)
        {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    /**
     * Called when the fragment is starting. Sets the instance arguments
     * and the style of the fragment.
     *
     * @param savedInstanceState Data it most recently supplied in
     *                           onSaveInstanceState(Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes.
        setRetainInstance(true);

        Bundle arguments = getArguments();
        this.mUrlString = arguments.getString(FRAGMENT_URL);
        this.mVersionInfo = arguments.getString(FRAGMENT_VERSION_INFO);
        boolean dialog = arguments.getBoolean(FRAGMENT_DIALOG);
        setShowsDialog(dialog);
    }

    /**
     * Creates the root view of the fragment, set title, the version number and
     * the listener for the download button.
     *
     * @return The fragment's root view.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = getLayoutView();

        // Helper for version management.
        VersionHelper versionHelper = new VersionHelper(getActivity(), mVersionInfo, this);

        AppCompatTextView nameLabel = view.findViewById(R.id.label_title);
        nameLabel.setText(Util.getAppName(getActivity()));
        nameLabel.setContentDescription(nameLabel.getText());

        final AppCompatTextView versionLabel = view.findViewById(R.id.label_version);
        final String versionString = String.format(getString(R.string.hockeyapp_update_version), versionHelper.getVersionString());
        final String fileDate = versionHelper.getFileDateString(container.getContext());

        String appSizeString = getString(R.string.hockeyapp_update_unknown_size);
        long appSize = versionHelper.getFileSizeBytes();
        if (appSize >= 0L) {
            appSizeString = String.format(Locale.US, "%.2f", appSize / (1024.0f * 1024.0f)) + " MB";
        } else {
            GetFileSizeTask task = new GetFileSizeTask(getActivity(), mUrlString, new DownloadFileListener() {
                @Override
                public void downloadSuccessful(DownloadFileTask task) {
                    if (task instanceof GetFileSizeTask) {
                        long appSize = ((GetFileSizeTask) task).getSize();
                        String appSizeString = String.format(Locale.US, "%.2f", appSize / (1024.0f * 1024.0f)) + " MB";
                        versionLabel.setText(getString(R.string.hockeyapp_update_version_details_label, versionString, fileDate, appSizeString));
                    }
                }
            });
            AsyncTaskUtils.execute(task);
        }
        versionLabel.setText(getString(R.string.hockeyapp_update_version_details_label, versionString, fileDate, appSizeString));

        AppCompatButton updateButton = view.findViewById(R.id.button_update);
        updateButton.setOnClickListener(this);

        WebView webView = view.findViewById(R.id.web_update_details);
        webView.clearCache(true);
        webView.destroyDrawingCache();
        webView.loadDataWithBaseURL(Constants.BASE_URL, versionHelper.getReleaseNotes(false), "text/html", "utf-8", null);

        return view;
    }

    @Override
    public void onDestroyView() {
        // To properly support orientation change
        Dialog dialog = getDialog();
        if (dialog != null && getRetainInstance()) {
            dialog.setDismissMessage(null);
        }
        super.onDestroyView();
    }

    /**
     * Called when the download button is tapped. Starts the download task and
     * disables the button to avoid multiple taps.
     */
    @Override
    public void onClick(View view) {
        prepareDownload();
    }

    /**
     * Returns the current version of the app.
     *
     * @return The version code as integer.
     */
    public int getCurrentVersionCode() {
        int currentVersionCode = -1;
        try {
            currentVersionCode = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), PackageManager.GET_META_DATA).versionCode;
        } catch (NameNotFoundException | NullPointerException ignored) {
        }
        return currentVersionCode;
    }

    private void showError(final int message) {
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.hockeyapp_dialog_error_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.hockeyapp_dialog_positive_button, null)
                .create();
        alertDialog.show();
    }

    private static String[] requiredPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return permissions.toArray(new String[0]);
    }

    protected void prepareDownload() {
        Context context = getActivity();
        if (!Util.isConnectedToNetwork(context)) {
            showError(R.string.hockeyapp_error_no_network_message);
            return;
        }

        String[] permissions = requiredPermissions();
        int[] permissionsState = PermissionsUtil.permissionsState(context, permissions);
        if (!PermissionsUtil.permissionsAreGranted(permissionsState)) {
            showError(R.string.hockeyapp_error_no_external_storage_permission);
            return;
        }

        if (!PermissionsUtil.isUnknownSourcesEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } else {
                showError(R.string.hockeyapp_error_install_form_unknown_sources_disabled);
            }
            return;
        }

        startDownloadTask();

        if (getShowsDialog()) {
            dismiss();
        }
    }

    /**
     * Starts the download task and sets the listener for a successful
     * download, a failed download, and configuration strings.
     */

    protected void startDownloadTask() {
        Context context = getActivity();
        if (context == null) {
            return;
        }

        Log.e("ZAMBONI", "START DOWNLOAD: " + this.mUrlString);

        AsyncTaskUtils.execute(new DownloadFileTask(context, this.mUrlString, new DownloadFileListener() {
            public void downloadFailed(DownloadFileTask task, Boolean userWantsRetry) {
                if (userWantsRetry) {
                    startDownloadTask();
                }
            }

            public void downloadSuccessful(DownloadFileTask task) {
                // Do nothing as the fragment is already dismissed
            }
        }));
    }

    /**
     * Creates and returns a new instance of the update view.
     *
     * @return Update view
     */
    public View getLayoutView() {
        LinearLayout layout = new LinearLayout(getActivity());
        LayoutInflater.from(getActivity()).inflate(R.layout.hockeyapp_fragment_update, layout);
        return layout;
    }
}
