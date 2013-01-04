
package com.android.settings.cyanogenmod;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class About extends SettingsPreferenceFragment {

    public static final String TAG = "About";

    Preference mTPUrl;
    Preference mTPGUrl;
    Preference mCMUrl;
    Preference mAOKPUrl;
    Preference mPAUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about_rom);
        mTPUrl = findPreference("teampassion_group");
        mTPGUrl = findPreference("teampassion_google_group");
        mCMUrl = findPreference("cm_site");
        mAOKPUrl = findPreference("aokp_site");
        mPAUrl = findPreference("pa_site");

        PreferenceGroup devsGroup = (PreferenceGroup) findPreference("devs");
        ArrayList<Preference> devs = new ArrayList<Preference>();
        for (int i = 0; i < devsGroup.getPreferenceCount(); i++) {
            devs.add(devsGroup.getPreference(i));
        }
        devsGroup.removeAll();
        devsGroup.setOrderingAsAdded(true);
        Collections.shuffle(devs);
        for(int i = 0; i < devs.size(); i++) {
            Preference p = devs.get(i);
            p.setOrder(i);

            devsGroup.addPreference(p);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mTPUrl) {
            launchUrl("https://www.facebook.com/groups/238731752913934/");
        } else if (preference == mTPGUrl) {
            launchUrl("https://plus.google.com/u/0/communities/102590330720242644572");
        } else if (preference == mCMUrl) {
            launchUrl("http://www.cyanogenmod.org/about");
        } else if (preference == mAOKPUrl) {
            launchUrl("http://aokp.co/");
        } else if (preference == mPAUrl) {
            launchUrl("http://www.paranoid-rom.com/about");
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void launchUrl(String url) {
        Uri uriUrl = Uri.parse(url);
        Intent donate = new Intent(Intent.ACTION_VIEW, uriUrl);
        getActivity().startActivity(donate);
    }
}
