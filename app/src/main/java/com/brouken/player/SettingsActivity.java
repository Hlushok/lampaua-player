package com.brouken.player;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

public class SettingsActivity extends AppCompatActivity {
    public static final String EXTRA_MEDIA_LANGUAGES = "mediaLanguages";

    static RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= 35) {
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            LinearLayout layout = findViewById(R.id.settings_layout);
            layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        0);
                if (recyclerView != null) {
                    recyclerView.setPadding(0,0,0, windowInsets.getSystemWindowInsetBottom());
                }
                windowInsets.consumeSystemWindowInsets();
                return windowInsets;
            });
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public RecyclerView.LayoutManager onCreateLayoutManager() {
            return new LinearLayoutManager(getContext()) {
                @Override
                protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state,
                                                         @NonNull int[] extraLayoutSpace) {
                    extraLayoutSpace[0] = extraLayoutSpace[1] = getHeight() * 2;
                }
            };
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            boolean hadAllowSystemFrameRateKey =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .contains("allowSystemFrameRate");

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference preferenceAutoPiP = findPreference("autoPiP");
            if (preferenceAutoPiP != null) {
                preferenceAutoPiP.setEnabled(Utils.isPiPSupported(this.getContext()));
            }
            Preference preferenceFrameRateMatching = findPreference("frameRateMatching");
            if (preferenceFrameRateMatching != null) {
                preferenceFrameRateMatching.setEnabled(Build.VERSION.SDK_INT >= 23);
            }
            SwitchPreferenceCompat preferenceAllowSystemFrameRate = findPreference("allowSystemFrameRate");
            if (preferenceAllowSystemFrameRate != null) {
                preferenceAllowSystemFrameRate.setEnabled(Build.VERSION.SDK_INT >= 30);
                if (!hadAllowSystemFrameRateKey) {
                    preferenceAllowSystemFrameRate.setChecked(!Utils.isTvBox(getContext()));
                }
            }
            ListPreference listPreferenceFileAccess = findPreference("fileAccess");
            if (listPreferenceFileAccess != null) {
                List<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_entries)));
                List<String> values = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_values)));
                if (Build.VERSION.SDK_INT < 30) {
                    int index = values.indexOf("mediastore");
                    entries.remove(index);
                    values.remove(index);
                }
                if (!Utils.hasSAFChooser(getContext().getPackageManager())) {
                    int index = values.indexOf("saf");
                    entries.remove(index);
                    values.remove(index);
                }
                listPreferenceFileAccess.setEntries(entries.toArray(new String[0]));
                listPreferenceFileAccess.setEntryValues(values.toArray(new String[0]));
            }

            Preference preferenceLanguageAudio = findPreference("languageAudio");
            if (preferenceLanguageAudio != null) {
                LinkedHashMap<String, String> languages = getLanguages();
                updateLanguageSummary(preferenceLanguageAudio, languages);
                preferenceLanguageAudio.setOnPreferenceClickListener(preference -> {
                    AudioLanguagePriorityDialog.show(requireContext(),
                            AudioLanguagePriority.parse(Prefs.getLanguageAudio(requireContext())),
                            languages, pinnedLanguages(), picked -> {
                                Prefs.setLanguageAudio(requireContext(),
                                        AudioLanguagePriority.serialize(picked));
                                updateLanguageSummary(preference, languages);
                            });
                    return true;
                });
            }

            Preference currentVersion = findPreference("currentVersion");
            if (currentVersion != null) {
                currentVersion.setSummary(BuildConfig.VERSION_NAME);
            }
            Preference checkUpdate = findPreference("checkUpdateNow");
            if (checkUpdate != null) {
                checkUpdate.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), R.string.update_checking, Toast.LENGTH_SHORT).show();
                        Updater.find(getActivity(), true, info -> getActivity().runOnUiThread(() -> {
                            if (info == null) {
                                Toast.makeText(getActivity(), R.string.update_none, Toast.LENGTH_SHORT).show();
                            } else {
                                UpdateUi.showAvailableDialog(getActivity(), info,
                                        () -> Updater.skip(getActivity(), info), false);
                            }
                        }));
                    }
                    return true;
                });
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            recyclerView = getListView();
            recyclerView.setBackgroundColor(getResources().getColor(R.color.ua_navy));
            int side = (int) (18 * getResources().getDisplayMetrics().density + 0.5f);
            int vertical = (int) (6 * getResources().getDisplayMetrics().density + 0.5f);
            recyclerView.setPadding(side, vertical, side, vertical);
            recyclerView.setClipToPadding(false);
            recyclerView.addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@NonNull View child) {
                            if (child.isFocusable()) {
                                child.setBackgroundResource(R.drawable.ua_preference_item_background);
                            }
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(@NonNull View child) { }
                    });
        }

        private void updateLanguageSummary(Preference preference,
                                           LinkedHashMap<String, String> languages) {
            List<String> selected = AudioLanguagePriority.parse(Prefs.getLanguageAudio(requireContext()));
            if (selected.isEmpty()) {
                preference.setSummary(R.string.pref_language_audio_none);
                return;
            }
            List<String> labels = new ArrayList<>();
            for (String code : selected) {
                String label = languages.get(code);
                labels.add(label == null ? code : label);
            }
            preference.setSummary(TextUtils.join(", ", labels));
        }

        private List<String> pinnedLanguages() {
            List<String> pinned = new ArrayList<>(Arrays.asList(Utils.getDeviceLanguages()));
            String[] media = requireActivity().getIntent()
                    .getStringArrayExtra(EXTRA_MEDIA_LANGUAGES);
            if (media != null) {
                for (String value : media) {
                    String language = AudioLanguagePriority.normalize(value);
                    if (language != null && !pinned.contains(language)) pinned.add(language);
                }
            }
            return pinned;
        }

        LinkedHashMap<String, String> getLanguages() {
            LinkedHashMap<String, String> languages = new LinkedHashMap<>();
            for (Locale locale : Locale.getAvailableLocales()) {
                try {
                    // MissingResourceException: Couldn't find 3-letter language code for zz
                    String key = AudioLanguagePriority.normalize(locale.toLanguageTag());
                    if (key == null || languages.containsKey(key)) continue;
                    String language = locale.getDisplayLanguage();
                    int length = language.offsetByCodePoints(0, 1);
                    if (!language.isEmpty()) {
                        language = language.substring(0, length).toUpperCase(locale) + language.substring(length);
                    }
                    String value = language + " [" + key + "]";
                    languages.put(key, value);
                } catch (MissingResourceException e) {
                    e.printStackTrace();
                }
            }
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            Utils.orderByValue(languages, collator::compare);
            return languages;
        }
    }
}
