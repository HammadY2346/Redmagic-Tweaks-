package com.hammad.redmagictweaks.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.hammad.redmagictweaks.LogStore;
import com.hammad.redmagictweaks.R;

import java.util.ArrayList;
import java.util.List;

public class LogActivity extends AppCompatActivity {
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private int filter = 0; // 0 all, 1 errors, 2 hook installs
    private RecyclerView list;
    private TextView empty;

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
            if (id == R.id.action_share) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, String.join("\n", shown)), "Share log"));
                return true;
            }
            return false;
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

    private void reload() {
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
