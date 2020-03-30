package com.audacious_software.zamboni;

/**
 * <h3>Description</h3>
 *
 * Abstract class for callbacks to be invoked from UpdateActivity
 * and UpdateFragment.
 *
 **/
public interface UpdateInfoListener {
    /**
     * Implement to return the app's current version code.
     *
     * @return current version code
     */
    int getCurrentVersionCode();
}
