package com.alphawallet.app.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.alphawallet.app.ui.widget.OnImportSeedListener;

import com.alphawallet.app.R;

import com.alphawallet.app.ui.widget.OnSuggestionClickListener;
import com.alphawallet.app.ui.widget.adapter.SuggestionsAdapter;
import com.alphawallet.app.widget.LayoutCallbackListener;
import com.alphawallet.app.widget.PasswordInputView;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ImportSeedFragment extends Fragment implements View.OnClickListener, TextWatcher, LayoutCallbackListener, OnSuggestionClickListener {
    private static final OnImportSeedListener dummyOnImportSeedListener = (s, c) -> {};
    private static final String validator = "[^a-z^A-Z^ ]";

    private PasswordInputView seedPhrase;
    private Button importButton;
    private Pattern pattern;
    private TextView wordCount;
    private RecyclerView listSuggestions;
    private List<String> suggestions;
    private SuggestionsAdapter suggestionsAdapter;

    @NonNull
    private OnImportSeedListener onImportSeedListener = dummyOnImportSeedListener;

    public static ImportSeedFragment create() {
        return new ImportSeedFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.fragment_import_seed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupView();
        setHintState(true);
    }

    private void setupView()
    {
        seedPhrase = getActivity().findViewById(R.id.input_seed);
        importButton = getActivity().findViewById(R.id.import_action);
        wordCount = getActivity().findViewById(R.id.text_word_count);
        listSuggestions = getActivity().findViewById(R.id.list_suggestions);
        importButton.setOnClickListener(this);
        seedPhrase.getEditText().addTextChangedListener(this);
        updateButtonState(false);
        pattern = Pattern.compile(validator, Pattern.MULTILINE);

        seedPhrase.setLayoutListener(getActivity(), this, getActivity().findViewById(R.id.bottom_marker));

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        listSuggestions.setLayoutManager(linearLayoutManager);

        suggestions = Arrays.asList(getResources().getStringArray(R.array.bip39_english));
        suggestionsAdapter = new SuggestionsAdapter(suggestions, this);
        listSuggestions.setAdapter(suggestionsAdapter);
    }

    private void setHintState(boolean enabled){
        String lang = Locale.getDefault().getDisplayLanguage();
        if (enabled && !lang.equalsIgnoreCase("English")) //remove language hint for English locale
        {
            getActivity().findViewById(R.id.text_non_english_hint).setVisibility(View.VISIBLE);
        }
        else
        {
            getActivity().findViewById(R.id.text_non_english_hint).setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (seedPhrase == null && getActivity() != null) setupView();
    }

    @Override
    public void onClick(View view) {
        processSeed(view);
    }

    private void processSeed(View view)
    {
        this.seedPhrase.setError(null);
        String newMnemonic = seedPhrase.getText().toString();
        if (TextUtils.isEmpty(newMnemonic)) {
            this.seedPhrase.setError(getString(R.string.error_field_required));
        } else {
            onImportSeedListener.onSeed(newMnemonic, getActivity());
        }
    }

    public void setOnImportSeedListener(@Nullable OnImportSeedListener onImportSeedListener) {
        this.onImportSeedListener = onImportSeedListener == null
                ? dummyOnImportSeedListener
                : onImportSeedListener;
    }

    public void onBadSeed()
    {
        seedPhrase.setError(R.string.bad_seed_phrase);
    }

    private void updateButtonState(boolean enabled)
    {
        importButton.setActivated(enabled);
        importButton.setClickable(enabled);
        int colorId = enabled ? R.color.nasty_green : R.color.inactive_green;
        if (getContext() != null) importButton.setBackgroundColor(getContext().getColor(colorId));
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
    {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
    {

    }

    @Override
    public void afterTextChanged(Editable editable)
    {
        if (seedPhrase.isErrorState()) seedPhrase.setError(null);
        String value = seedPhrase.getText().toString();
        final Matcher matcher = pattern.matcher(value);
        if (matcher.find())
        {
            updateButtonState(false);
            seedPhrase.setError("Seed phrase can only contain words");
            wordCount.setVisibility(View.GONE);
        }
        else if (value.length() > 5)
        {
            updateButtonState(true);
            wordCount.setVisibility(View.VISIBLE);
        }
        else
        {
            updateButtonState(false);
            wordCount.setVisibility(View.VISIBLE);
        }

        int words = 0;
        if(value.trim().length() > 0) {
            words = value.trim().replaceAll("\n", "").split("\\s").length;
        }
        wordCount.setText(String.valueOf(words));

        //get last word from the text
        if(value.length() > 1) {
            int lastDelimiterPosition = value.lastIndexOf(" ");
            String lastWord = lastDelimiterPosition == -1 ? value :
                    value.substring(lastDelimiterPosition + " ".length());
            if(lastWord.trim().length() > 0) {
                filterList(lastWord);
            }else{
                suggestionsAdapter.setData(suggestions);
            }
        }else{
            suggestionsAdapter.setData(suggestions);
        }
    }

    private void filterList(String lastWord) {
        List<String> filteredList = Lists.newArrayList(Collections2.filter(suggestions, input -> input.startsWith(lastWord)));
        suggestionsAdapter.setData(filteredList, lastWord);
    }

    @Override
    public void onLayoutShrunk()
    {
        if (importButton != null) importButton.setVisibility(View.GONE);
        listSuggestions.setVisibility(View.VISIBLE);
        setHintState(false);
    }

    @Override
    public void onLayoutExpand()
    {
        if (importButton != null) importButton.setVisibility(View.VISIBLE);
        listSuggestions.setVisibility(View.GONE);
        setHintState(true);
    }

    @Override
    public void onInputDoneClick(View view)
    {
        processSeed(view);
    }

    @Override
    public void onSuggestionClick(String value)
    {
        seedPhrase.getEditText().append(value + " ");
    }
}
