/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.DialogFragment;
import android.app.VibrationPickerDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.VibrationPattern;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.VolumePanel;

public class SoundSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SoundSettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_EMERGENCY_TONE_VALUE = 0;

    private static final String KEY_VOLUME_OVERLAY = "volume_overlay";
    private static final String KEY_SILENT_MODE = "silent_mode";
    private static final String KEY_VIBRATE = "vibrate_when_ringing";
    private static final String KEY_VIBRATION = "vibration";
    private static final String KEY_RING_VOLUME = "ring_volume";
    private static final String KEY_INCREASING_RING = "increasing_ring";
    private static final String KEY_MUSICFX = "musicfx";
    private static final String KEY_DTMF_TONE = "dtmf_tone";
    private static final String KEY_SOUND_EFFECTS = "sound_effects";
    private static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String KEY_EMERGENCY_TONE = "emergency_tone";
    private static final String KEY_SOUND_SETTINGS = "sound_settings";
    private static final String KEY_LOCK_SOUNDS = "lock_sounds";
    private static final String KEY_RINGTONE = "ringtone";
    private static final String KEY_NOTIFICATION_SOUND = "notification_sound";
    private static final String KEY_CATEGORY_CALLS = "category_calls_and_notification";
    private static final String KEY_QUIET_HOURS = "quiet_hours";
    private static final String KEY_SAFE_HEADSET_RESTORE = "safe_headset_restore";

    private static final String SILENT_MODE_OFF = "off";
    private static final String SILENT_MODE_VIBRATE = "vibrate";
    private static final String SILENT_MODE_MUTE = "mute";

    private static final String[] NEED_VOICE_CAPABILITY = {
            KEY_RINGTONE, KEY_DTMF_TONE, KEY_CATEGORY_CALLS,
            KEY_EMERGENCY_TONE, KEY_INCREASING_RING
    };

    private static final int MSG_UPDATE_RINGTONE_SUMMARY = 1;
    private static final int MSG_UPDATE_NOTIFICATION_SUMMARY = 2;
    private static final int VIB_OK = 10;
    private static final int VIB_CANCEL = 11;

    private CheckBoxPreference mVibrateWhenRinging;
    private ListPreference mVolumeOverlay;
    private ListPreference mSilentMode;
    private CheckBoxPreference mDtmfTone;
    private CheckBoxPreference mSoundEffects;
    private CheckBoxPreference mHapticFeedback;
    private Preference mMusicFx;
    private CheckBoxPreference mLockSounds;
    private Preference mRingtonePreference;
    private Preference mVibrationPreference;
    private Preference mNotificationPreference;
    private PreferenceScreen mQuietHours;
    private CheckBoxPreference mSafeHeadsetRestore;

    private Runnable mRingtoneLookupRunnable;

    private AudioManager mAudioManager;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateState(false);
            }
        }
    };

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_RINGTONE_SUMMARY:
                mRingtonePreference.setSummary((CharSequence) msg.obj);
                break;
            case MSG_UPDATE_NOTIFICATION_SUMMARY:
                mNotificationPreference.setSummary((CharSequence) msg.obj);
                break;
            case VIB_OK:
                VibrationPattern mPattern = (VibrationPattern) msg.obj;
                if (mPattern == null) {
                    break;
                }
                mVibrationPreference.setSummary(mPattern.getName());
                Settings.System.putString(getContentResolver(),
                        Settings.System.PHONE_VIBRATION, mPattern.getUri().toString());
                break;
            case VIB_CANCEL:
            default:
                break;
            }
        }
    };

    private PreferenceGroup mSoundSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getContentResolver();
        int activePhoneType = TelephonyManager.getDefault().getCurrentPhoneType();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        addPreferencesFromResource(R.xml.sound_settings);

        if (TelephonyManager.PHONE_TYPE_CDMA != activePhoneType) {
            // device is not CDMA, do not display CDMA emergency_tone
            getPreferenceScreen().removePreference(findPreference(KEY_EMERGENCY_TONE));
        }

        mVolumeOverlay = (ListPreference) findPreference(KEY_VOLUME_OVERLAY);
        mVolumeOverlay.setOnPreferenceChangeListener(this);
        int volumeOverlay = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_VOLUME_OVERLAY,
                VolumePanel.VOLUME_OVERLAY_EXPANDABLE);
        mVolumeOverlay.setValue(Integer.toString(volumeOverlay));
        mVolumeOverlay.setSummary(mVolumeOverlay.getEntry());

        mSilentMode = (ListPreference) findPreference(KEY_SILENT_MODE);
        if (!getResources().getBoolean(R.bool.has_silent_mode)) {
            getPreferenceScreen().removePreference(mSilentMode);
            findPreference(KEY_RING_VOLUME).setDependency(null);
        } else {
            mSilentMode.setOnPreferenceChangeListener(this);
        }

        mQuietHours = (PreferenceScreen) findPreference(KEY_QUIET_HOURS);
        if (Settings.System.getInt(resolver, Settings.System.QUIET_HOURS_ENABLED, 0) == 1) {
            mQuietHours.setSummary(getString(R.string.quiet_hours_active_from) + " " + 
                    returnTime(Settings.System.getString(resolver, Settings.System.QUIET_HOURS_START)) 
                    + " " + getString(R.string.quiet_hours_active_to) + " " +
                    returnTime(Settings.System.getString(resolver, Settings.System.QUIET_HOURS_END)));
        } else {
            mQuietHours.setSummary(getString(R.string.quiet_hours_summary));
        }

        mSafeHeadsetRestore = (CheckBoxPreference) findPreference(KEY_SAFE_HEADSET_RESTORE);
        mSafeHeadsetRestore.setPersistent(false);
        mSafeHeadsetRestore.setChecked(Settings.System.getInt(resolver,
                Settings.System.SAFE_HEADSET_VOLUME_RESTORE, 1) != 0);

        mVibrateWhenRinging = (CheckBoxPreference) findPreference(KEY_VIBRATE);
        mVibrateWhenRinging.setPersistent(false);
        mVibrateWhenRinging.setChecked(Settings.System.getInt(resolver,
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0);

        mDtmfTone = (CheckBoxPreference) findPreference(KEY_DTMF_TONE);
        mDtmfTone.setPersistent(false);
        mDtmfTone.setChecked(Settings.System.getInt(resolver,
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0);
        mSoundEffects = (CheckBoxPreference) findPreference(KEY_SOUND_EFFECTS);
        mSoundEffects.setPersistent(false);
        mSoundEffects.setChecked(Settings.System.getInt(resolver,
                Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0);
        mHapticFeedback = (CheckBoxPreference) findPreference(KEY_HAPTIC_FEEDBACK);
        mHapticFeedback.setPersistent(false);
        mHapticFeedback.setChecked(Settings.System.getInt(resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0);
        mLockSounds = (CheckBoxPreference) findPreference(KEY_LOCK_SOUNDS);
        mLockSounds.setPersistent(false);
        mLockSounds.setChecked(Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) != 0);

        mRingtonePreference = findPreference(KEY_RINGTONE);
        mVibrationPreference = findPreference(KEY_VIBRATION);
        mNotificationPreference = findPreference(KEY_NOTIFICATION_SOUND);

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            getPreferenceScreen().removePreference(mVibrateWhenRinging);
            getPreferenceScreen().removePreference(mVibrationPreference);
            getPreferenceScreen().removePreference(mHapticFeedback);
        }

        if (TelephonyManager.PHONE_TYPE_CDMA == activePhoneType) {
            ListPreference emergencyTonePreference =
                (ListPreference) findPreference(KEY_EMERGENCY_TONE);
            emergencyTonePreference.setValue(String.valueOf(Settings.System.getInt(
                resolver, Settings.System.EMERGENCY_TONE, FALLBACK_EMERGENCY_TONE_VALUE)));
            emergencyTonePreference.setOnPreferenceChangeListener(this);
        }

        mSoundSettings = (PreferenceGroup) findPreference(KEY_SOUND_SETTINGS);

        mMusicFx = mSoundSettings.findPreference(KEY_MUSICFX);
        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        mMusicFx.setIntent(i);
        PackageManager p = getPackageManager();
        List<ResolveInfo> ris = p.queryIntentActivities(i, PackageManager.GET_DISABLED_COMPONENTS);
        if (ris.size() == 0) {
            mSoundSettings.removePreference(mMusicFx);
        } else if (ris.size() == 1) {
            mMusicFx.setSummary(ris.get(0).loadLabel(p));
        }

        if (!Utils.isVoiceCapable(getActivity())) {
            for (String prefKey : NEED_VOICE_CAPABILITY) {
                Preference pref = findPreference(prefKey);
                if (pref != null) {
                    getPreferenceScreen().removePreference(pref);
                }
            }
        }

        mRingtoneLookupRunnable = new Runnable() {
            public void run() {
                if (mRingtonePreference != null) {
                    updateRingtoneName(RingtoneManager.TYPE_RINGTONE, mRingtonePreference,
                            MSG_UPDATE_RINGTONE_SUMMARY);
                }
                if (mNotificationPreference != null) {
                    updateRingtoneName(RingtoneManager.TYPE_NOTIFICATION, mNotificationPreference,
                            MSG_UPDATE_NOTIFICATION_SUMMARY);
                }
            }
        };

        lookupVibrationName();
    }

    @Override
    public void onResume() {
        super.onResume();

        updateState(true);
        lookupRingtoneNames();
        lookupVibrationName();

        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        getActivity().registerReceiver(mReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(mReceiver);
    }

    private void setPhoneSilentSettingValue(String value) {
        int ringerMode = AudioManager.RINGER_MODE_NORMAL;
        if (value.equals(SILENT_MODE_MUTE)) {
            ringerMode = AudioManager.RINGER_MODE_SILENT;
        } else if (value.equals(SILENT_MODE_VIBRATE)) {
            ringerMode = AudioManager.RINGER_MODE_VIBRATE;
        }
        mAudioManager.setRingerMode(ringerMode);
    }

    private String getPhoneSilentModeSettingValue() {
        switch (mAudioManager.getRingerMode()) {
        case AudioManager.RINGER_MODE_NORMAL:
            return SILENT_MODE_OFF;
        case AudioManager.RINGER_MODE_VIBRATE:
            return SILENT_MODE_VIBRATE;
        case AudioManager.RINGER_MODE_SILENT:
            return SILENT_MODE_MUTE;
        }
        // Shouldn't happen
        return SILENT_MODE_OFF;
    }

    // updateState in fact updates the UI to reflect the system state
    private void updateState(boolean force) {
        if (getActivity() == null) return;
        ContentResolver resolver = getContentResolver();

        mSilentMode.setValue(getPhoneSilentModeSettingValue());

        if (Settings.System.getInt(resolver, Settings.System.QUIET_HOURS_ENABLED, 0) == 1) {
            mQuietHours.setSummary(getString(R.string.quiet_hours_active_from) + " " +
                    returnTime(Settings.System.getString(resolver, Settings.System.QUIET_HOURS_START))
                    + " " + getString(R.string.quiet_hours_active_to) + " " +
                    returnTime(Settings.System.getString(resolver, Settings.System.QUIET_HOURS_END)));
        } else {
            mQuietHours.setSummary(getString(R.string.quiet_hours_summary));
        }

        mSilentMode.setSummary(mSilentMode.getEntry());
    }

    private void updateRingtoneName(int type, Preference preference, int msg) {
        if (preference == null) return;
        Context context = getActivity();
        if (context == null) return;
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, type);
        CharSequence summary = context.getString(com.android.internal.R.string.ringtone_unknown);
        // Is it a silent ringtone?
        if (ringtoneUri == null) {
            summary = context.getString(com.android.internal.R.string.ringtone_silent);
        } else {
            // Fetch the ringtone title from the media provider
            try {
                Cursor cursor = context.getContentResolver().query(ringtoneUri,
                        new String[] { MediaStore.Audio.Media.TITLE }, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        summary = cursor.getString(0);
                    }
                    cursor.close();
                }
            } catch (SQLiteException sqle) {
                // Unknown title for the ringtone
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(msg, summary));
    }

    private void lookupRingtoneNames() {
        new Thread(mRingtoneLookupRunnable).start();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mVibrateWhenRinging) {
            Settings.System.putInt(getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING,
                    mVibrateWhenRinging.isChecked() ? 1 : 0);
        } else if (preference == mDtmfTone) {
            Settings.System.putInt(getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING,
                    mDtmfTone.isChecked() ? 1 : 0);

        } else if (preference == mSoundEffects) {
            if (mSoundEffects.isChecked()) {
                mAudioManager.loadSoundEffects();
            } else {
                mAudioManager.unloadSoundEffects();
            }
            Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED,
                    mSoundEffects.isChecked() ? 1 : 0);

        } else if (preference == mHapticFeedback) {
            Settings.System.putInt(getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    mHapticFeedback.isChecked() ? 1 : 0);

        } else if (preference == mLockSounds) {
            Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_SOUNDS_ENABLED,
                    mLockSounds.isChecked() ? 1 : 0);

        } else if (preference == mMusicFx) {
            // let the framework fire off the intent
            return false;

        } else if (preference == mSafeHeadsetRestore) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SAFE_HEADSET_VOLUME_RESTORE,
                    mSafeHeadsetRestore.isChecked() ? 1 : 0);
        } else if (preference == mVibrationPreference) {
            String uriString = VibrationPattern.getPhoneVibration(getActivity());
            DialogFragment newFragment = VibrationPickerDialog.newInstance(mHandler, false, uriString);
            newFragment.show(getFragmentManager(), "dialog");
            return true;

        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_EMERGENCY_TONE.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                Settings.System.putInt(getContentResolver(),
                        Settings.System.EMERGENCY_TONE, value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist emergency tone setting", e);
            }
        } else if (preference == mSilentMode) {
            setPhoneSilentSettingValue(objValue.toString());
        } else if (preference == mVolumeOverlay) {
            final int value = Integer.valueOf((String) objValue);
            final int index = mVolumeOverlay.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.MODE_VOLUME_OVERLAY, value);
            mVolumeOverlay.setSummary(mVolumeOverlay.getEntries()[index]);
        }

        return true;
    }

    private String returnTime(String t) {
        if (t == null || t.equals("")) {
            return "";
        }
        int hr = Integer.parseInt(t.trim());
        int mn = hr;

        hr = hr / 60;
        mn = mn % 60;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hr);
        cal.set(Calendar.MINUTE, mn);
        Date date = cal.getTime();
        return DateFormat.getTimeFormat(getActivity().getApplicationContext()).format(date);
    }

    private void lookupVibrationName() {
        String uriString = VibrationPattern.getPhoneVibration(getActivity());
        mVibrationPreference.setSummary(new VibrationPattern(Uri.parse(uriString), getActivity()).getName());
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_sound;
    }
}
