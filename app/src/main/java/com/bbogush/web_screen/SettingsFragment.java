package com.bbogush.web_screen;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final int PORT_MIN = 1024;
    private static final int PORT_MAX = 65535;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.root_preferences);

        EditTextPreference editTextPreference = getPreferenceManager().
                findPreference("port");
        editTextPreference.setOnBindEditTextListener(new EditTextPreference.
                OnBindEditTextListener() {
            @Override
            public void onBindEditText(final EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(5) });
                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        int number;
                        try {
                            number = Integer.parseInt(editable.toString());
                        } catch (Exception e) {
                            number = 0;
                        }
                        Button okButton = editText.getRootView().findViewById(android.R.id.button1);
                        okButton.setEnabled(number >= PORT_MIN && number <= PORT_MAX);
                    }
                });
            }
        });
    }
}
