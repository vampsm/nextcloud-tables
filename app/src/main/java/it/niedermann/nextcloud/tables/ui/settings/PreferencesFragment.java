package it.niedermann.nextcloud.tables.ui.settings;


import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Objects;

import it.niedermann.nextcloud.tables.R;
import it.niedermann.nextcloud.tables.database.entity.Account;
import it.niedermann.nextcloud.tables.remote.SyncWorker;
import it.niedermann.nextcloud.tables.repository.PreferencesRepository;

public class PreferencesFragment extends PreferenceFragmentCompat {

    private static final String TAG = PreferencesFragment.class.getSimpleName();
    private static final String KEY_ACCOUNT = "account";
    private PreferencesRepository preferencesRepository;
    private Account account;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        final var args = getArguments();
        if (args == null || !args.containsKey(KEY_ACCOUNT)) {
            throw new IllegalArgumentException(KEY_ACCOUNT + " must be provided");
        }

        account = (Account) args.getSerializable(KEY_ACCOUNT);
        preferencesRepository = new PreferencesRepository(requireContext());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        final Preference prefSyncBackground = Objects.requireNonNull(findPreference(getString(R.string.pref_key_sync_background)));
        prefSyncBackground.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
            SyncWorker.update(requireContext().getApplicationContext());
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Preference prefTheme = Objects.requireNonNull(findPreference(getString(R.string.pref_key_theme)));

        final var entries = getResources().getStringArray(R.array.theme_entries);
        final var values = getResources().getStringArray(R.array.pref_values_theme);

        preferencesRepository.getTheme$().observe(getViewLifecycleOwner(), theme -> {
            for (int i = 0; i < values.length; i++) {
                if (Objects.equals(theme, Integer.parseInt(values[i]))) {
                    prefTheme.setSummary(entries[i]);
                    return;
                }
            }
            prefTheme.setSummary("");
        });
    }

    @NonNull
    public static Fragment newInstance(@NonNull Account account) {
        final var fragment = new PreferencesFragment();

        final var args = new Bundle();
        args.putSerializable(KEY_ACCOUNT, account);
        fragment.setArguments(args);

        return fragment;
    }
}
