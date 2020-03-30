package com.audacious_software.zamboni;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

/**
 * <h3>Description</h3>
 *
 * Activity to show update information and start the download
 * process if the user taps the corresponding button.
 *
 **/
public class UpdateActivity extends AppCompatActivity {
    public static final String FRAGMENT_CLASS = "fragmentClass";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
        {
            Bundle extras = getIntent().getExtras();
            if (extras == null) {
                finish();
                return;
            }

            String fragmentClass = extras.getString(FRAGMENT_CLASS, UpdateFragment.class.getName());

            Fragment fragment = Fragment.instantiate(this, fragmentClass, extras);

            this.getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, fragment, UpdateFragment.FRAGMENT_TAG)
                    .commit();
        }

        setTitle(R.string.hockeyapp_update_title);
    }
}
