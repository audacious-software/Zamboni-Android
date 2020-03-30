package com.audacious_software.zamboni.listeners;

import com.audacious_software.zamboni.tasks.DownloadFileTask;

/**
 * <h3>Description</h3>
 *
 * Abstract class for callbacks to be invoked from the DownloadFileTask.
 **/
public abstract class DownloadFileListener {
    public void downloadFailed(DownloadFileTask task, Boolean userWantsRetry) {
    }

    public void downloadSuccessful(DownloadFileTask task) {
    }
}
