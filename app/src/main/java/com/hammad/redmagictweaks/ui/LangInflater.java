package com.hammad.redmagictweaks.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.theme.MaterialComponentsViewInflater;
import com.hammad.redmagictweaks.L;

/** The theme's view inflater (viewInflaterClass): Material's, plus the app language (L) on every inflated text. */
public class LangInflater extends MaterialComponentsViewInflater {
    private static <T extends TextView> T lang(T v) {
        // Watcher is in place before the inflater sets the XML text, so the first draw is already Russian.
        L.attach(v);
        return v;
    }
    @NonNull @Override protected AppCompatTextView createTextView(Context c, AttributeSet a) { return lang(super.createTextView(c, a)); }
    @NonNull @Override protected AppCompatButton createButton(@NonNull Context c, AttributeSet a) { return lang(super.createButton(c, a)); }
    @NonNull @Override protected AppCompatCheckBox createCheckBox(Context c, AttributeSet a) { return lang(super.createCheckBox(c, a)); }
    @NonNull @Override protected AppCompatRadioButton createRadioButton(Context c, AttributeSet a) { return lang(super.createRadioButton(c, a)); }
}
