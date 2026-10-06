package com.snes9x.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Start screen: the last game played, the library and a button to add games. */
public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_ROM = 1;
    private static final int MAX_RECENT = 30;
    private static final String PREFS = "recent";
    private static final String KEY_RECENT = "games";

    /** A game in the library, stored as "uri\tname\ttime" lines. */
    private static final class Game {
        final Uri uri;
        final String name;
        long lastPlayed;

        Game(Uri uri, String name, long lastPlayed) {
            this.uri = uri;
            this.name = name;
            this.lastPlayed = lastPlayed;
        }
    }

    private final List<Game> games = new ArrayList<>();
    private LinearLayout content;
    private View addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        root.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        content.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 110));
        scroll.addView(content);
        root.addView(scroll);

        addButton = floatingAddButton();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 58),
                Gravity.BOTTOM | Gravity.END);
        params.setMargins(pad, pad, pad, pad);
        root.addView(addButton, params);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGames();
        render();
    }

    // --- Layout ---------------------------------------------------------------

    private void render() {
        content.removeAllViews();

        TextView title = Ui.text(this, getString(R.string.library_title), 32, Ui.TEXT, true);
        content.addView(title);
        TextView subtitle = Ui.text(this, getString(R.string.app_name), 14, Ui.ACCENT, true);
        subtitle.setAllCaps(true);
        subtitle.setLetterSpacing(0.1f);
        content.addView(subtitle, 0);

        if (games.isEmpty()) {
            addButton.setVisibility(View.GONE);
            content.addView(emptyState(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            return;
        }
        addButton.setVisibility(View.VISIBLE);

        content.addView(sectionLabel(getString(R.string.continue_playing)));
        View hero = heroCard(games.get(0));
        content.addView(hero);

        content.addView(sectionLabel(getResources().getQuantityString(
                R.plurals.library_count, games.size(), games.size())));
        addGrid();

        TextView hint = Ui.text(this, getString(R.string.long_press_hint), 13, Ui.TEXT_DIM, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setPadding(0, Ui.dp(this, 20), 0, 0);
        content.addView(hint);

        hero.requestFocus();
    }

    private TextView sectionLabel(String label) {
        TextView view = Ui.text(this, label, 16, Ui.TEXT, true);
        view.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 12));
        return view;
    }

    private View heroCard(Game game) {
        float radius = Ui.dp(this, 24);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.selectable(this, Ui.gradient(Ui.coverColors(game.name), radius), radius));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> play(game));
        card.setOnLongClickListener(v -> {
            showOptions(game);
            return true;
        });

        // Big faded initials as a backdrop.
        TextView watermark = Ui.text(this, Ui.initials(game.name), 110, 0x33FFFFFF, true);
        watermark.setIncludeFontPadding(false);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        markParams.rightMargin = Ui.dp(this, 16);
        card.addView(watermark, markParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        info.setPadding(pad, pad, pad, pad);

        TextView name = Ui.text(this, game.name, 22, 0xFFFFFFFF, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);
        info.addView(Ui.text(this, lastPlayed(game), 13, 0xCCFFFFFF, false));

        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.rounded(0xFFFFFFFF, Ui.dp(this, 22)));
        pill.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));
        ImageView play = new ImageView(this);
        play.setImageDrawable(Ui.icon(this, R.drawable.ic_play, 0xFF111111));
        pill.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView playText = Ui.text(this, getString(R.string.play), 15, 0xFF111111, true);
        playText.setPadding(Ui.dp(this, 6), 0, 0, 0);
        pill.addView(playText);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = Ui.dp(this, 18);
        info.addView(pill, pillParams);

        card.addView(info);
        return card;
    }

    private void addGrid() {
        float density = getResources().getDisplayMetrics().density;
        int available = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40);
        int columns = Math.max(2, (int) (available / density / 165));
        int gap = Ui.dp(this, 12);

        LinearLayout row = null;
        for (int i = 0; i < games.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = gap;
                }
                content.addView(row, rowParams);
            }
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % columns > 0) {
                cellParams.leftMargin = gap;
            }
            row.addView(gameCard(games.get(i)), cellParams);
        }
        // Pad the last row so its cards keep the same width.
        for (int i = games.size() % columns; i > 0 && i < columns; i++) {
            View spacer = new View(this);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1);
            spacerParams.leftMargin = gap;
            row.addView(spacer, spacerParams);
        }
    }

    private View gameCard(Game game) {
        float radius = Ui.dp(this, 20);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 8);
        card.setPadding(pad, pad, pad, Ui.dp(this, 12));
        card.setBackground(Ui.selectable(this, Ui.rounded(Ui.SURFACE, radius), radius));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> play(game));
        card.setOnLongClickListener(v -> {
            showOptions(game);
            return true;
        });

        TextView cover = Ui.text(this, Ui.initials(game.name), 34, 0xFFFFFFFF, true);
        cover.setGravity(Gravity.CENTER);
        cover.setBackground(Ui.gradient(Ui.coverColors(game.name), Ui.dp(this, 14)));
        card.addView(cover, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 110)));

        TextView name = Ui.text(this, game.name, 15, Ui.TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), 0);
        card.addView(name);

        TextView time = Ui.text(this, lastPlayed(game), 12, Ui.TEXT_DIM, false);
        time.setSingleLine(true);
        time.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), 0);
        card.addView(time);
        return card;
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(Ui.icon(this, R.drawable.ic_gamepad, Ui.ACCENT));
        int iconPad = Ui.dp(this, 26);
        icon.setPadding(iconPad, iconPad, iconPad, iconPad);
        icon.setBackground(Ui.rounded(0x268B5CF6, Ui.dp(this, 60)));
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120)));

        TextView title = Ui.text(this, getString(R.string.empty_title), 22, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        box.addView(title);

        TextView body = Ui.text(this, getString(R.string.empty_body), 15, Ui.TEXT_DIM, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), Ui.dp(this, 28));
        box.addView(body);

        View button = primaryButton();
        box.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 56)));
        button.requestFocus();
        return box;
    }

    private View floatingAddButton() {
        View button = primaryButton();
        button.setElevation(Ui.dp(this, 8));
        return button;
    }

    private View primaryButton() {
        float radius = Ui.dp(this, 29);
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 24), 0);
        button.setBackground(Ui.selectable(this,
                Ui.gradient(new int[] {Ui.ACCENT, Ui.ACCENT_DARK}, radius), radius));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(v -> openRom());

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(Ui.icon(this, R.drawable.ic_add, 0xFFFFFFFF));
        button.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView label = Ui.text(this, getString(R.string.add_game), 16, 0xFFFFFFFF, true);
        label.setPadding(Ui.dp(this, 8), 0, 0, 0);
        button.addView(label);
        return button;
    }

    private String lastPlayed(Game game) {
        if (game.lastPlayed <= 0) {
            return getString(R.string.never_played);
        }
        return DateUtils.getRelativeTimeSpanString(game.lastPlayed, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString();
    }

    private void showOptions(Game game) {
        new ActionSheet(this)
                .title(game.name)
                .subtitle(lastPlayed(game))
                .action(R.drawable.ic_play, getString(R.string.play), () -> play(game))
                .danger(R.drawable.ic_delete, getString(R.string.remove_from_library), () -> {
                    games.remove(game);
                    saveGames();
                    render();
                    Toast.makeText(this, R.string.removed_from_library, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // --- Controller ------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The controller's A button presses whatever is selected.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                View focused = getCurrentFocus();
                if (focused != null) {
                    focused.performClick();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // --- Games -----------------------------------------------------------------

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
        // Keep access to the file so it can be reopened from the library.
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Not persistable; it will still open this time.
        }
        play(new Game(uri, RomLoader.displayName(getContentResolver(), uri), 0));
    }

    private void play(Game game) {
        game.lastPlayed = System.currentTimeMillis();
        for (int i = games.size() - 1; i >= 0; i--) {
            if (games.get(i).uri.equals(game.uri)) {
                games.remove(i);
            }
        }
        games.add(0, game);
        while (games.size() > MAX_RECENT) {
            games.remove(games.size() - 1);
        }
        saveGames();

        Intent intent = new Intent(this, GameActivity.class);
        intent.setData(game.uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void loadGames() {
        games.clear();
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RECENT, "");
        for (String line : stored.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length < 2 || parts[0].isEmpty()) {
                continue;
            }
            long time = 0;
            if (parts.length > 2) {
                try {
                    time = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    // Keep 0.
                }
            }
            games.add(new Game(Uri.parse(parts[0]), parts[1], time));
        }
    }

    private void saveGames() {
        StringBuilder out = new StringBuilder();
        for (Game game : games) {
            out.append(game.uri).append('\t')
                    .append(game.name.replace('\n', ' ').replace('\t', ' ')).append('\t')
                    .append(game.lastPlayed).append('\n');
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_RECENT, out.toString()).apply();
    }
}
