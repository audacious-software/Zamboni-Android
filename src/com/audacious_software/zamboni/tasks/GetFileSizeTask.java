package com.audacious_software.zamboni.tasks;

import android.content.Context;

import com.audacious_software.zamboni.listeners.DownloadFileListener;
import com.audacious_software.zamboni.utils.HockeyLog;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * <h3>Description</h3>
 *
 * Internal helper class. Determines the size of an externally hosted
 * .apk from the HTTP header.
 *
 **/
public class GetFileSizeTask extends DownloadFileTask {
    private long mSize;

    public GetFileSizeTask(Context context, String urlString, DownloadFileListener notifier) {
        super(context, urlString, notifier);
    }

    @Override
    protected Long doInBackground(Void... args) {
        try {
            URL url = new URL(this.mUrlString);
            URLConnection connection = createConnection(url, MAX_REDIRECTS);
            return (long) connection.getContentLength();
        } catch (IOException e) {
            HockeyLog.error("Failed to get size " + mUrlString, e);
            return 0L;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... args) {
        // Do not display any progress for this task.
    }

    @Override
    protected void onPostExecute(Long result) {
        mSize = result;
        if (mSize > 0L) {
            mNotifier.downloadSuccessful(this);
        } else {
            mNotifier.downloadFailed(this, false);
        }
    }

    public long getSize() {
        return mSize;
    }
}
