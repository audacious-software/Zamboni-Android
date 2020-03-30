package com.audacious_software.zamboni.tasks;

import android.content.Context;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.audacious_software.zamboni.Constants;
import com.audacious_software.zamboni.Tracking;
import com.audacious_software.zamboni.UpdateManagerListener;
import com.audacious_software.zamboni.utils.HockeyLog;
import com.audacious_software.zamboni.utils.Util;
import com.audacious_software.zamboni.utils.VersionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

/**
 * <h3>Description</h3>
 *
 * Internal helper class. Checks if a new update is available by
 * fetching version data from Hockeyapp.
 **/
public class CheckUpdateTask extends AsyncTask<Void, String, JSONArray> {
    private static final int MAX_NUMBER_OF_VERSIONS = 25;

    protected Uri mUpdateFeed = null;

    private WeakReference<Context> weakContext = null;
    protected Boolean mandatory = false;
    protected UpdateManagerListener listener;
    private long usageTime = 0;

    public CheckUpdateTask(WeakReference<? extends Context> weakContext, Uri updateFeed) {
        this(weakContext, updateFeed, null);
    }

    public CheckUpdateTask(WeakReference<? extends Context> weakContext, Uri updateFeed, UpdateManagerListener listener) {
        this.mUpdateFeed = updateFeed;
        this.listener = listener;

        Context ctx = null;
        if (weakContext != null) {
            ctx = weakContext.get();
        }

        if (ctx != null) {
            this.weakContext = new WeakReference<>(ctx.getApplicationContext());
            this.usageTime = Tracking.getUsageTime(ctx);
            Constants.loadFromContext(ctx);
        }
    }

    public void attach(WeakReference<? extends Context> weakContext) {
        Context ctx = null;
        if (weakContext != null) {
            ctx = weakContext.get();
        }

        if (ctx != null) {
            this.weakContext = new WeakReference<>(ctx.getApplicationContext());
            Constants.loadFromContext(ctx);
        }
    }

    public void detach() {
        weakContext = null;
    }

    protected int getVersionCode() {
        return Integer.parseInt(Constants.APP_VERSION);
    }

    @Override
    protected JSONArray doInBackground(Void... args) {
        Context context = weakContext != null ? weakContext.get() : null;
        if (context == null) {
            return null;
        }

        try {
            int versionCode = getVersionCode();
            URL url = new URL(this.mUpdateFeed.toString());

            TrafficStats.setThreadStatsTag(Constants.THREAD_STATS_TAG);
            URLConnection connection = createConnection(url);
            connection.connect();

            InputStream inputStream = new BufferedInputStream(connection.getInputStream());
            String jsonString = Util.convertStreamToString(inputStream);

            JSONArray json = new JSONArray(jsonString);

            if (findNewVersion(context, json, versionCode)) {
                json = limitResponseSize(json);
                return json;
            }
        } catch (IOException | JSONException e) {
            if(Util.isConnectedToNetwork(context)) {
                HockeyLog.error("Zamboni", "Could not fetch updates although connected to Internet.", e);
            }
        } finally {
            TrafficStats.clearThreadStatsTag();
        }

        return null;
    }

    protected URLConnection createConnection(URL url) throws IOException {
        URLConnection connection = Util.openHttpsConnection(url);
        connection.addRequestProperty("User-Agent", Constants.SDK_USER_AGENT);
        return connection;
    }

    private boolean findNewVersion(Context context, JSONArray json, int versionCode) {
        try {
            boolean newerVersionFound = false;

            for (int index = 0; index < json.length(); index++) {
                JSONObject entry = json.getJSONObject(index);

                boolean largerVersionCode = (entry.getInt("version") > versionCode);
                boolean newerApkFile = ((entry.getInt("version") == versionCode) && VersionHelper.isNewerThanLastUpdateTime(context, entry.getLong("timestamp")));
                // boolean minRequirementsMet = VersionHelper.compareVersionStrings(entry.getString("minimum_os_version"), VersionHelper.mapGoogleVersion(Build.VERSION.RELEASE)) <= 0;

                // if ((largerVersionCode || newerApkFile) && minRequirementsMet) {
                if (largerVersionCode || newerApkFile) {
                    if (entry.has("mandatory")) {
                        mandatory |= entry.getBoolean("mandatory");
                    }
                    newerVersionFound = true;
                }
            }

            return newerVersionFound;
        } catch (JSONException e) {

            return false;
        }
    }

    private JSONArray limitResponseSize(JSONArray json) {
        JSONArray result = new JSONArray();
        for (int index = 0; index < Math.min(json.length(), MAX_NUMBER_OF_VERSIONS); index++) {
            try {
                result.put(json.get(index));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    @Override
    protected void onPostExecute(JSONArray updateInfo) {
        if (updateInfo != null) {
            HockeyLog.verbose("Zamboni", "Received Update Info");

            if (listener != null) {
                listener.onUpdateAvailable(updateInfo);
            }
        } else {
            HockeyLog.verbose("Zamboni", "No Update Info available");

            if (listener != null) {
                listener.onNoUpdateAvailable();
            }
        }
    }

    protected void cleanUp() {
        this.mUpdateFeed = null;
    }

    private String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 should be available, so just in case
            return "";
        }
    }
}
