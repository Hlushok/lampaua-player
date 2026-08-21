package com.brouken.player;

import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.OneShotPreDrawListener;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brouken.player.together.AliasGenerator;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

public class SettingsActivity extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    public static final String EXTRA_MEDIA_LANGUAGES = "mediaLanguages";
    public static final String EXTRA_SCROLL_TO = "scrollTo";

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
        final CharSequence rootTitle = getTitle();
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(rootTitle);
            }
        });

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

    @Override
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                           @NonNull final PreferenceScreen preferenceScreen) {
        final SettingsFragment fragment = new SettingsFragment();
        final Bundle arguments = new Bundle();
        arguments.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT,
                preferenceScreen.getKey());
        fragment.setArguments(arguments);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(preferenceScreen.getKey())
                .commit();
        setTitle(preferenceScreen.getTitle());
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            finish();
        }
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
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
            Preference preferenceHoldSpeed = findPreference("holdSpeed");
            if (preferenceHoldSpeed != null && Utils.isTvBox(getContext())) {
                preferenceHoldSpeed.setVisible(false);
            }
            boolean tvBox = Utils.isTvBox(getContext());
            Preference showRotation = findPreference("showButtonRotation");
            Preference showLock = findPreference("showButtonLock");
            Preference showPiP = findPreference("showButtonPiP");
            if (showRotation != null) showRotation.setVisible(!tvBox);
            if (showLock != null) showLock.setVisible(!tvBox);
            if (showPiP != null) showPiP.setVisible(Utils.isPiPSupported(getContext()));

            final EditTextPreference preferenceNick = findPreference("togetherNick");
            final Preference preferenceNickRandom = findPreference("togetherNickRandom");
            if (preferenceNick != null) {
                if (TextUtils.isEmpty(preferenceNick.getText())) {
                    preferenceNick.setText(AliasGenerator.random());
                }
                preferenceNick.setOnPreferenceChangeListener((preference, value) -> {
                    if (value == null || value.toString().trim().isEmpty()) {
                        preferenceNick.setText(AliasGenerator.random());
                        return false;
                    }
                    return true;
                });
            }
            if (preferenceNick != null && preferenceNickRandom != null) {
                preferenceNickRandom.setOnPreferenceClickListener(preference -> {
                    preferenceNick.setText(AliasGenerator.random());
                    return true;
                });
            }

            final EditTextPreference preferencePassword = findPreference("togetherPassword");
            final SwitchPreferenceCompat preferencePublic = findPreference("togetherPublic");
            if (preferencePassword != null) {
                preferencePassword.setOnBindEditTextListener(editText ->
                        editText.setInputType(InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_VARIATION_PASSWORD));
                preferencePassword.setSummaryProvider(preference -> {
                    final String value = preferencePassword.getText();
                    return TextUtils.isEmpty(value)
                            ? getString(R.string.pref_together_password_none) : "••••••";
                });
                preferencePassword.setOnPreferenceChangeListener((preference, value) -> {
                    if ((value == null || value.toString().isEmpty())
                            && preferencePublic != null && preferencePublic.isChecked()) {
                        preferencePublic.setChecked(false);
                    }
                    return true;
                });
            }
            if (preferencePublic != null) {
                if (preferencePublic.isChecked() && (preferencePassword == null
                        || TextUtils.isEmpty(preferencePassword.getText()))) {
                    preferencePublic.setChecked(false);
                }
                preferencePublic.setOnPreferenceChangeListener((preference, value) -> {
                    if (Boolean.TRUE.equals(value) && (preferencePassword == null
                            || TextUtils.isEmpty(preferencePassword.getText()))) {
                        Toast.makeText(requireContext(), R.string.together_public_needs_password,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    return true;
                });
            }

            final EditTextPreference preferenceRelay = findPreference("togetherRelay");
            if (preferenceRelay != null) {
                preferenceRelay.setSummaryProvider(preference -> {
                    final String value = preferenceRelay.getText();
                    return TextUtils.isEmpty(value == null ? null : value.trim())
                            ? getString(R.string.pref_together_relay_default, Relay.DEFAULT_BASE)
                            : value.trim();
                });
                preferenceRelay.setOnPreferenceChangeListener((preference, value) -> {
                    final String address = value == null ? "" : value.toString().trim();
                    if (!validAddress(address, "ws", "wss")) {
                        showAddressError("ws://, wss://");
                        return false;
                    }
                    Relay.setBase(address);
                    return true;
                });
            }

            final EditTextPreference preferenceInvite = findPreference("togetherInvitePage");
            if (preferenceInvite != null) {
                preferenceInvite.setSummaryProvider(preference -> {
                    final String value = preferenceInvite.getText();
                    return TextUtils.isEmpty(value == null ? null : value.trim())
                            ? getString(R.string.pref_together_relay_default,
                                    Room.DEFAULT_INVITE_PAGE)
                            : value.trim();
                });
                preferenceInvite.setOnPreferenceChangeListener((preference, value) -> {
                    final String address = value == null ? "" : value.toString().trim();
                    if (!validAddress(address, "http", "https")) {
                        showAddressError("http://, https://");
                        return false;
                    }
                    Room.setInvitePage(address);
                    return true;
                });
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
            Preference preferenceLanguageSubtitle = findPreference("languageSubtitle");
            if (preferenceLanguageAudio != null || preferenceLanguageSubtitle != null) {
                LinkedHashMap<String, String> languages = getLanguages();
                if (preferenceLanguageAudio != null) {
                    updateLanguageSummary(preferenceLanguageAudio, languages,
                            Prefs.getLanguageAudio(requireContext()),
                            R.string.pref_language_audio_none);
                    preferenceLanguageAudio.setOnPreferenceClickListener(preference -> {
                        AudioLanguagePriorityDialog.show(requireContext(),
                                R.string.pref_language_audio,
                                R.string.pref_language_audio_none,
                                AudioLanguagePriority.parse(
                                        Prefs.getLanguageAudio(requireContext())),
                                languages, pinnedLanguages(), false, picked -> {
                                    String stored = AudioLanguagePriority.serialize(picked);
                                    Prefs.setLanguageAudio(requireContext(), stored);
                                    updateLanguageSummary(preference, languages, stored,
                                            R.string.pref_language_audio_none);
                                });
                        return true;
                    });
                }
                if (preferenceLanguageSubtitle != null) {
                    updateLanguageSummary(preferenceLanguageSubtitle, languages,
                            Prefs.getLanguageSubtitle(requireContext()),
                            R.string.pref_language_subtitle_none);
                    preferenceLanguageSubtitle.setOnPreferenceClickListener(preference -> {
                        AudioLanguagePriorityDialog.show(requireContext(),
                                R.string.pref_language_subtitle,
                                R.string.pref_language_subtitle_none,
                                AudioLanguagePriority.parse(
                                        Prefs.getLanguageSubtitle(requireContext())),
                                languages, pinnedLanguages(), true, picked -> {
                                    String stored = AudioLanguagePriority.serialize(picked);
                                    Prefs.setLanguageSubtitle(requireContext(), stored);
                                    updateLanguageSummary(preference, languages, stored,
                                            R.string.pref_language_subtitle_none);
                                });
                        return true;
                    });
                }
            }

            ListPreference textColor = findPreference("subtitleTextColor");
            ListPreference background = findPreference("subtitleBackground");
            if (textColor != null && background != null) {
                showColorChips(textColor);
                showColorChips(background);
                textColor.setOnPreferenceChangeListener((preference, value) ->
                        allowColor(String.valueOf(value), background.getValue()));
                background.setOnPreferenceChangeListener((preference, value) ->
                        allowColor(textColor.getValue(), String.valueOf(value)));
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
            if (savedInstanceState == null && getArguments() == null) {
                String key = requireActivity().getIntent().getStringExtra(EXTRA_SCROLL_TO);
                if (key != null) openAtPreference(key, 3);
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .registerOnSharedPreferenceChangeListener(this);
            refreshSubtitlePreview();
        }

        @Override
        public void onStop() {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .unregisterOnSharedPreferenceChangeListener(this);
            super.onStop();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if ("subtitleStyleEmbedded".equals(key)
                    || "subtitleScale".equals(key)
                    || "subtitleTextColor".equals(key)
                    || "subtitleBackground".equals(key)
                    || "subtitleEdge".equals(key)
                    || "subtitleStyleBold".equals(key)) {
                refreshSubtitlePreview();
            }
        }

        private void refreshSubtitlePreview() {
            Preference preference = findPreference("subtitleStylePreview");
            if (preference instanceof SubtitlePreviewPreference) {
                ((SubtitlePreviewPreference) preference).refresh();
            }
        }

        private void openAtPreference(String key, int attemptsLeft) {
            RecyclerView list = getListView();
            RecyclerView.Adapter<?> adapter = list == null ? null : list.getAdapter();
            if (attemptsLeft <= 0
                    || !(adapter instanceof PreferenceGroup.PreferencePositionCallback)
                    || !(list.getLayoutManager() instanceof LinearLayoutManager)) {
                return;
            }
            int position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                    .getPreferenceAdapterPosition(key);
            if (position == RecyclerView.NO_POSITION) return;
            LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
            manager.scrollToPositionWithOffset(Math.max(0, position - 1), 0);
            OneShotPreDrawListener.add(list, () -> {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    openAtPreference(key, attemptsLeft - 1);
                    return;
                }
                holder.itemView.requestFocus();
                list.post(() -> manager.scrollToPositionWithOffset(
                        Math.max(0, position - 1), 0));
            });
        }

        private void showColorChips(ListPreference preference) {
            CharSequence[] entries = preference.getEntries();
            CharSequence[] values = preference.getEntryValues();
            if (entries == null || values == null || entries.length != values.length) return;
            CharSequence[] chipped = new CharSequence[entries.length];
            for (int i = 0; i < entries.length; i++) {
                GradientDrawable chip = new GradientDrawable();
                chip.setShape(GradientDrawable.OVAL);
                chip.setColor(Color.parseColor(values[i].toString()));
                chip.setStroke(Math.max(1, Utils.dpToPx(1)), 0x80808080);
                SpannableStringBuilder label =
                        new SpannableStringBuilder("  ").append(entries[i]);
                label.setSpan(new ColorChipSpan(chip), 0, 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                chipped[i] = label;
            }
            preference.setEntries(chipped);
        }

        private boolean allowColor(String textColor, String backgroundColor) {
            if (textColor == null || backgroundColor == null
                    || Color.parseColor(textColor) != Color.parseColor(backgroundColor)) {
                return true;
            }
            Toast.makeText(requireContext(), R.string.pref_subtitle_color_clash,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        private static final class ColorChipSpan extends ImageSpan {
            ColorChipSpan(Drawable drawable) {
                super(drawable);
            }

            private void resize(Paint paint) {
                int size = Math.round(paint.getTextSize() * 0.7f);
                Drawable chip = getDrawable();
                if (chip.getBounds().height() != size) chip.setBounds(0, 0, size, size);
            }

            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                               @Nullable Paint.FontMetricsInt fontMetrics) {
                resize(paint);
                return super.getSize(paint, text, start, end, fontMetrics);
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                             float x, int top, int y, int bottom, @NonNull Paint paint) {
                resize(paint);
                Drawable chip = getDrawable();
                Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
                float middle = y + (metrics.ascent + metrics.descent) / 2f;
                canvas.save();
                canvas.translate(x, middle - chip.getBounds().height() / 2f);
                chip.draw(canvas);
                canvas.restore();
            }
        }

        private void updateLanguageSummary(Preference preference,
                                           LinkedHashMap<String, String> languages,
                                           String stored, int emptyRes) {
            List<String> selected = AudioLanguagePriority.parse(stored);
            if (selected.isEmpty()) {
                preference.setSummary(emptyRes);
                return;
            }
            List<String> labels = new ArrayList<>();
            for (String code : selected) {
                String label = languages.get(code);
                labels.add(label == null ? code : label);
            }
            preference.setSummary(TextUtils.join(", ", labels));
        }

        private boolean validAddress(final String value, final String... schemes) {
            if (value.isEmpty()) {
                return true;
            }
            try {
                final Uri uri = Uri.parse(value);
                if (TextUtils.isEmpty(uri.getHost())) {
                    return false;
                }
                for (String scheme : schemes) {
                    if (scheme.equalsIgnoreCase(uri.getScheme())) {
                        return true;
                    }
                }
            } catch (RuntimeException ignored) {
                // The preference stays unchanged when parsing fails.
            }
            return false;
        }

        private void showAddressError(final String schemes) {
            Toast.makeText(requireContext(),
                    getString(R.string.pref_together_address_invalid, schemes),
                    Toast.LENGTH_SHORT).show();
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
