package com.snes9x.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Start screen: pick a ROM or reopen a recent one. */
public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_ROM = 1;
    private static final int MAX_RECENT = 20;
    private static final String PREFS = "recent";
    private static final String KEY_RECENT = "games";

    /** A recently played game, stored as "uri\tname" lines. */
    private static final class Recent {
        final Uri uri;
        final String name;

        Recent(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final List<Recent> recent = new ArrayList<>();
    private ArrayAdapter<Recent> adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFB39DDB);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.subtitle);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        Button open = new Button(this);
        open.setText(R.string.open_rom);
        open.setOnClickListener(v -> openRom());
        root.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView header = new TextView(this);
        header.setText(R.string.recent_games);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        emptyText = new TextView(this);
        emptyText.setText(R.string.no_recent_games);
        emptyText.setTextColor(Color.GRAY);
        emptyText.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyText.setPadding(0, dp(24), 0, 0);
        root.addView(emptyText);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, recent);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                play(recent.get(position).uri));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmRemove(position);
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        loadRecent();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_OPEN_ROM);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_ROM || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        // Keep access to the file so it can be reopened from the recent list.
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Not persistable; it will still open this time.
        }
        addRecent(uri, RomLoader.displayName(getContentResolver(), uri));
        play(uri);
    }

    private void play(Uri uri) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void confirmRemove(int position) {
        Recent game = recent.get(position);
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_recent, game.name))
                .setPositiveButton(R.string.remove, (dialog, which) -> {
                    recent.remove(game);
                    saveRecent();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addRecent(Uri uri, String name) {
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).uri.equals(uri)) {
                recent.remove(i);
            }
        }
        recent.add(0, new Recent(uri, name));
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        saveRecent();
    }

    private void loadRecent() {
        recent.clear();
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RECENT, "");
        for (String line : stored.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                recent.add(new Recent(Uri.parse(line.substring(0, tab)), line.substring(tab + 1)));
            }
        }
        refresh();
    }

    private void saveRecent() {
        StringBuilder out = new StringBuilder();
        for (Recent game : recent) {
            out.append(game.uri).append('\t').append(game.name.replace('\n', ' ')).append('\n');
        }
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString(KEY_RECENT, out.toString()).apply();
        refresh();
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        emptyText.setVisibility(recent.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }
}
