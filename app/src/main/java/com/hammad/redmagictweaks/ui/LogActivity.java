package com.hammad.redmagictweaks.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hammad.redmagictweaks.LogStore;
import com.hammad.redmagictweaks.R;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LogActivity extends AppCompatActivity {
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private int filter = 0; // 0 all, 1 errors, 2 hook installs
    private RecyclerView list;
    private TextView empty, state;
    private MaterialSwitch logSwitch;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        tb.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear) { LogStore.clear(this); reload(); return true; }
            if (id == R.id.action_copy) {
                ClipboardManager cm = getSystemService(ClipboardManager.class);
                cm.setPrimaryClip(ClipData.newPlainText((com.hammad.redmagictweaks.BuildConfig.APP_NAME + " log"), String.join("\n", shown)));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == R.id.action_share) { shareTxt(); return true; }
            if (id == R.id.action_save) { saveTxt(); return true; }
            return false;
        });
        state = findViewById(R.id.log_state);
        logSwitch = findViewById(R.id.log_switch);
        logSwitch.setChecked(LogStore.enabled(this));
        logSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on == LogStore.enabled(this)) return;
            LogStore.setEnabled(this, on);
            Toast.makeText(this, on ? "Logging on" : "Logging off", Toast.LENGTH_SHORT).show();
            reload();
        });
        ChipGroup filters = findViewById(R.id.filters);
        filters.setOnCheckedStateChangeListener((g, ids) -> {
            int id = ids.isEmpty() ? R.id.f_all : ids.get(0);
            filter = id == R.id.f_err ? 1 : id == R.id.f_hooks ? 2 : 0;
            apply();
        });
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new LogAdapter());
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    private String filterName() { return filter == 1 ? "errors and warnings" : filter == 2 ? "hook installs" : "all lines"; }

    /** The lines shown (current filter) as a .txt file through the share sheet. */
    private void shareTxt() {
        if (shown.isEmpty()) { Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show(); return; }
        try {
            File f = LogStore.exportTxt(this, shown, filterName());
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, u)
                    .putExtra(Intent.EXTRA_SUBJECT, f.getName())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            s.setClipData(ClipData.newRawUri(f.getName(), u));
            startActivity(Intent.createChooser(s, "Share " + f.getName()));
        } catch (Throwable t) {
            Toast.makeText(this, "Can't share the log: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** The lines shown as Download/<app>-log-<time>.txt (no storage permission needed). */
    private void saveTxt() {
        if (shown.isEmpty()) { Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show(); return; }
        String name = LogStore.txtName();
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new IllegalStateException("Download is not available");
            try (OutputStream out = getContentResolver().openOutputStream(u)) {
                out.write(LogStore.txt(this, shown, filterName()).getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Saved Download/" + name, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Can't save the log: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void reload() {
        boolean on = LogStore.enabled(this);
        if (logSwitch != null && logSwitch.isChecked() != on) logSwitch.setChecked(on);
        if (state != null) state.setText(on ? R.string.log_on : R.string.log_off);
        empty.setText(on ? R.string.log_empty : R.string.log_off_empty);
        all.clear();
        all.addAll(LogStore.read(this));
        apply();
    }

    private void apply() {
        shown.clear();
        for (String l : all) {
            boolean err = l.contains("] E ") || l.contains("] W ");
            boolean hook = l.contains("hooks ready") || l.contains("hooks installed") || l.contains("hooked");
            if (filter == 0 || (filter == 1 && err) || (filter == 2 && hook)) shown.add(l);
        }
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
        if (!shown.isEmpty()) list.scrollToPosition(shown.size() - 1);
    }

    private final class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return shown.size(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false)) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            String l = shown.get(pos);
            TextView t = h.itemView.findViewById(R.id.line);
            t.setText(l);
            int color = l.contains("] E ") ? R.color.err : l.contains("] W ") ? R.color.warn : (l.contains("hooks ready") || l.contains("hooks installed")) ? R.color.ok : R.color.text;
            t.setTextColor(getColor(color));
        }
    }
}
