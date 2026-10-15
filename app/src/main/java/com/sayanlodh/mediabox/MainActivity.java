package com.sayanlodh.mediabox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Locale;

/** Self-contained offline-first music/movie player; no app account or server required. */
public final class MainActivity extends Activity {
    private static final int PICK_AUDIO = 101;
    private static final int PICK_VIDEO = 102;
    private static final int BG = 0xFF0B1019;
    private static final int SURFACE = 0xFF171F31;
    private static final int PURPLE = 0xFF8976FF;
    private static final int LIGHT = 0xFFF3F1FF;
    private static final int MUTED = 0xFFA8ACC3;

    private LibraryDb database;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private LinearLayout root, pageContent, playerPanel, tabRow;
    private FrameLayout pageFrame;
    private TextView playerTitle, playerStatus, elapsedText, durationText;
    private TextView playPause, repeatButton, shuffleButton;
    private SeekBar seekBar;
    private PlayerView videoView;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateProgress = new Runnable() {
        @Override public void run() {
            updatePlayerUi();
            uiHandler.postDelayed(this, 800L);
        }
    };
    private int currentSection = 0;
    private String libraryFilter = "all";
    private String searchTerm = "";
    private String onlineSearchTerm = "";
    private LinearLayout trackRows;
    private boolean dragging;
    private String displayedMediaId = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new LibraryDb(this);
        onlineSearchTerm = getSharedPreferences("sayan_search", MODE_PRIVATE).getString("last_online_search", "");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        showSection(0);
        connectPlayer();
        uiHandler.post(updateProgress);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 50);
        }
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(updateProgress);
        if (videoView != null) videoView.setPlayer(null);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        database.close();
        super.onDestroy();
    }

    private void connectPlayer() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(new Player.Listener() {
                    @Override public void onMediaItemTransition(MediaItem item, int reason) { updatePlayerUi(); }
                    @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlayerUi(); }
                    @Override public void onPlaybackStateChanged(int state) { updatePlayerUi(); }
                    @Override public void onShuffleModeEnabledChanged(boolean enabled) { updatePlayerUi(); }
                    @Override public void onRepeatModeChanged(int repeatMode) { updatePlayerUi(); }
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        toast("Cannot play this file or URL: " + error.getErrorCodeName());
                    }
                });
                updatePlayerUi();
            } catch (Exception e) {
                toast("Unable to connect to player: " + e.getMessage());
            }
        }, command -> uiHandler.post(command));
    }

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        return panel;
    }
    private LinearLayout horizontal() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        return panel;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    private LinearLayout.LayoutParams spaced(int height, int marginBottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height == -2 ? -2 : dp(height));
        params.bottomMargin = dp(marginBottom);
        return params;
    }
    private LinearLayout card() {
        LinearLayout panel = vertical();
        panel.setPadding(dp(15), dp(15), dp(15), dp(15));
        panel.setBackground(shape(SURFACE, 16));
        return panel;
    }
    private TextView action(String label, boolean primary, View.OnClickListener listener) {
        TextView v = text(label, 13, primary ? Color.WHITE : LIGHT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setBackground(shape(primary ? PURPLE : 0xFF2B3550, 12));
        v.setOnClickListener(listener);
        return v;
    }
    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setHintTextColor(0xFF9196B0);
        e.setTextColor(LIGHT);
        e.setTextSize(14);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(shape(0xFF26314B, 12));
        return e;
    }
    private void title(LinearLayout parent, String value, String subtitle) {
        TextView header = text(value, 24, LIGHT, true);
        parent.addView(header, spaced(-2, 5));
        if (subtitle != null) {
            parent.addView(text(subtitle, 13, MUTED, false), spaced(-2, 17));
        }
    }
    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void buildShell() {
        root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout brand = horizontal();
        brand.setPadding(dp(18), dp(12), dp(18), dp(10));
        TextView logo = text("♫", 26, PURPLE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(SURFACE, 13));
        brand.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brandCopy = vertical();
        brandCopy.setPadding(dp(11), 0, 0, 0);
        brandCopy.addView(text("SAYAN MUSIC & MOVIES", 17, LIGHT, true));
        brandCopy.addView(text("Your music. Your movies. Your rules.", 11, MUTED, false));
        brand.addView(brandCopy, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(brand, matchWrap());

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabRow = horizontal();
        tabRow.setPadding(dp(14), dp(2), dp(14), dp(10));
        renderTabs();
        tabScroll.addView(tabRow);
        root.addView(tabScroll, matchWrap());

        pageFrame = new FrameLayout(this);
        root.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        playerPanel = vertical();
        playerPanel.setPadding(dp(12), dp(10), dp(12), dp(8));
        playerPanel.setBackground(shape(0xFF151D30, 18));
        LinearLayout.LayoutParams playerParams = matchWrap();
        playerParams.setMargins(dp(8), dp(4), dp(8), dp(7));
        root.addView(playerPanel, playerParams);
        buildPlayerPanel();
    }

    private void renderTabs() {
        if (tabRow == null) return;
        tabRow.removeAllViews();
        String[] labels = { "♪ Library", "▤ Playlists", "⌕ Online Search", "? Guide" };
        for (int i = 0; i < labels.length; i++) {
            final int destination = i;
            TextView tab = action(labels[i], i == currentSection, v -> showSection(destination));
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(44));
            tabParams.rightMargin = dp(7);
            tabRow.addView(tab, tabParams);
        }
    }

    private void buildPlayerPanel() {
        videoView = new PlayerView(this);
        videoView.setUseController(true);
        videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        videoView.setVisibility(View.GONE);
        playerPanel.addView(videoView, new LinearLayout.LayoutParams(-1, dp(210)));

        LinearLayout top = horizontal();
        LinearLayout info = vertical();
        playerTitle = text("No track playing", 15, LIGHT, true);
        playerTitle.setSingleLine(true);
        playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playerStatus = text("Import music or choose an online audio link", 11, MUTED, false);
        info.addView(playerTitle, matchWrap());
        info.addView(playerStatus, matchWrap());
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        playerPanel.addView(top, spaced(-2, 5));

        LinearLayout timeline = horizontal();
        elapsedText = text("0:00", 11, MUTED, false);
        durationText = text("0:00", 11, MUTED, false);
        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgressTintList(ColorStateList.valueOf(PURPLE));
        seekBar.setThumbTintList(ColorStateList.valueOf(PURPLE));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seek) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar seek) {
                dragging = false;
                if (controller != null) controller.seekTo(seek.getProgress() * 1000L);
            }
        });
        timeline.addView(elapsedText);
        timeline.addView(seekBar, new LinearLayout.LayoutParams(0, dp(37), 1));
        timeline.addView(durationText);
        playerPanel.addView(timeline, matchWrap());

        LinearLayout buttons = horizontal();
        buttons.setGravity(Gravity.CENTER);
        shuffleButton = playerControl("⇄", v -> {
            if (controller != null) controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
        });
        TextView previous = playerControl("◀◀", v -> {
            if (controller != null) controller.seekToPreviousMediaItem();
        });
        playPause = playerControl("▶", v -> {
            if (controller == null) { toast("Player starting, try again."); return; }
            if (controller.isPlaying()) controller.pause();
            else if (controller.getMediaItemCount() > 0) controller.play();
            else toast("Import a song or choose an online audio URL first.");
        });
        TextView next = playerControl("▶▶", v -> {
            if (controller != null) controller.seekToNextMediaItem();
        });
        repeatButton = playerControl("↻", v -> {
            if (controller == null) return;
            int mode = controller.getRepeatMode();
            controller.setRepeatMode(mode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE :
                (mode == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ALL));
        });
        for (TextView control : new TextView[] { shuffleButton, previous, playPause, next, repeatButton }) {
            buttons.addView(control, new LinearLayout.LayoutParams(0, dp(45), 1));
        }
        playerPanel.addView(buttons, matchWrap());
        TextView sleepShortcut = text("☾ Set sleep timer", 12, MUTED, true);
        sleepShortcut.setGravity(Gravity.CENTER);
        sleepShortcut.setPadding(dp(8), dp(5), dp(8), dp(3));
        sleepShortcut.setOnClickListener(v -> chooseSleepTimer());
        playerPanel.addView(sleepShortcut, matchWrap());
    }

    private TextView playerControl(String label, View.OnClickListener listener) {
        TextView t = text(label, 21, LIGHT, true);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(listener);
        return t;
    }

    private String prettyTime(long millis) {
        if (millis < 0) return "0:00";
        long seconds = millis / 1000L;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void updatePlayerUi() {
        if (playerTitle == null || controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null) {
            displayedMediaId = "";
            playerTitle.setText("No track playing");
            playerStatus.setText("Tap a track to begin");
            if (videoView.getVisibility() != View.GONE) { videoView.setPlayer(null); videoView.setVisibility(View.GONE); }
            return;
        }
        String title = item.mediaMetadata.title == null ? "Untitled" : item.mediaMetadata.title.toString();
        playerTitle.setText(title);
        playerStatus.setText(controller.isPlaying() ? "▶ Playing in background" : "❚❚ Paused");
        playPause.setText(controller.isPlaying() ? "❚❚" : "▶");
        repeatButton.setText(controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? "↻¹" :
            controller.getRepeatMode() == Player.REPEAT_MODE_ALL ? "↻" : "→");
        repeatButton.setTextColor(controller.getRepeatMode() == Player.REPEAT_MODE_OFF ? MUTED : PURPLE);
        shuffleButton.setTextColor(controller.getShuffleModeEnabled() ? PURPLE : MUTED);
        long length = controller.getDuration();
        long position = Math.max(0, controller.getCurrentPosition());
        durationText.setText(prettyTime(length));
        elapsedText.setText(prettyTime(position));
        if (!dragging) {
            seekBar.setMax(length > 0 ? (int)Math.min(length / 1000L, Integer.MAX_VALUE) : 100);
            seekBar.setProgress((int)Math.min(position / 1000L, Integer.MAX_VALUE));
        }
        String id = item.mediaId == null ? "" : item.mediaId;
        if (!id.equals(displayedMediaId)) {
            displayedMediaId = id;
            boolean video = id.startsWith("video:");
            if (video) {
                videoView.setVisibility(View.VISIBLE);
                videoView.setPlayer(controller);
            } else {
                videoView.setPlayer(null);
                videoView.setVisibility(View.GONE);
            }
        }
    }

    private void showSection(int section) {
        currentSection = section;
        renderTabs();
        pageFrame.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pageContent = vertical();
        pageContent.setPadding(dp(16), dp(10), dp(16), dp(28));
        scroll.addView(pageContent);
        pageFrame.addView(scroll);
        if (section == 0) showLibrary();
        else if (section == 1) showPlaylists();
        else if (section == 2) showOnline();
        else showGuide();
    }

    private void showLibrary() {
        title(pageContent, "Your music library", "Audio, movies and playlists without creating an account.");
        LinearLayout actions = horizontal();
        TextView audio = action("+ Import audio", true, v -> pickFile(false));
        TextView video = action("+ Import video", false, v -> pickFile(true));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1);
        a.rightMargin = dp(7);
        actions.addView(audio, a);
        actions.addView(video, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageContent.addView(actions, spaced(-2, 17));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = horizontal();
        String[] modes = { "all", "audio", "video", "stream", "favorites" };
        String[] titles = { "All", "Songs", "Movies", "Online", "♥ Favorites" };
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            TextView chip = action(titles[i], mode.equals(libraryFilter), v -> {
                libraryFilter = mode;
                showSection(0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
            params.rightMargin = dp(7);
            filters.addView(chip, params);
        }
        filterScroll.addView(filters);
        pageContent.addView(filterScroll, spaced(-2, 12));
        EditText search = input("Search your saved tracks...");
        search.setText(searchTerm);
        search.setSelection(searchTerm.length());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchTerm = s.toString();
                populateTrackRows();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        pageContent.addView(search, spaced(-2, 16));
        trackRows = vertical();
        pageContent.addView(trackRows, matchWrap());
        populateTrackRows();
    }

    private void populateTrackRows() {
        if (trackRows == null) return;
        trackRows.removeAllViews();
        ArrayList<Track> list = database.tracks(libraryFilter);
        int shown = 0;
        for (Track track : list) {
            if (!track.title.toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT))) continue;
            trackRows.addView(trackCard(track, list, -1), spaced(-2, 9));
            shown++;
        }
        if (shown == 0) {
            LinearLayout empty = card();
            empty.addView(text("No matching tracks yet", 17, LIGHT, true), spaced(-2, 6));
            empty.addView(text("Import audio/video files or save an authorized online audio URL.", 13, MUTED, false));
            trackRows.addView(empty, matchWrap());
        }
    }

    private LinearLayout trackCard(Track track, ArrayList<Track> queue, long playlistId) {
        LinearLayout panel = card();
        LinearLayout top = horizontal();
        TextView symbol = text("video".equals(track.kind) ? "▣" : "♫", 26, PURPLE, true);
        symbol.setGravity(Gravity.CENTER);
        top.addView(symbol, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout info = vertical();
        info.addView(text(track.title, 15, LIGHT, true), matchWrap());
        info.addView(text("video".equals(track.kind) ? "VIDEO" :
            "stream".equals(track.kind) ? "ONLINE LINK" : "LOCAL AUDIO", 11, MUTED, false), matchWrap());
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(top, spaced(-2, 10));
        LinearLayout controls = horizontal();
        TextView play = action("▶ Play", true, v -> playQueue(queue, track));
        TextView add = action("+ Playlist", false, v -> choosePlaylist(track));
        TextView fave = action(track.favorite ? "♥" : "♡", false, v -> {
            database.toggleFavorite(track.id, !track.favorite);
            showSection(currentSection);
        });
        controls.addView(play, new LinearLayout.LayoutParams(0, dp(44), 2));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(44), 2);
        second.setMargins(dp(7), 0, dp(7), 0);
        controls.addView(add, second);
        controls.addView(fave, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(controls);
        TextView remove = text(playlistId >= 0 ? "Remove from this playlist" : "Remove from library", 11, MUTED, false);
        remove.setPadding(dp(2), dp(12), 0, 0);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Remove track?")
            .setMessage(track.title)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (dialog, which) -> {
                if (playlistId >= 0) database.removeFromPlaylist(playlistId, track.id);
                else database.deleteTrack(track.id);
                showSection(currentSection);
            }).show());
        panel.addView(remove);
        return panel;
    }

    private void playQueue(ArrayList<Track> queue, Track selected) {
        if (controller == null) { toast("Player is starting. Please try again."); return; }
        if (queue.isEmpty()) { toast("This playlist is empty."); return; }
        ArrayList<MediaItem> items = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            MediaMetadata metadata = new MediaMetadata.Builder().setTitle(track.title)
                .setArtist("Sayan Music & Movies").build();
            items.add(new MediaItem.Builder()
                .setUri(Uri.parse(track.uri))
                .setMediaId(track.kind + ":" + track.id)
                .setMediaMetadata(metadata)
                .build());
            if (track.id == selected.id) start = i;
        }
        controller.setMediaItems(items, start, 0L);
        controller.prepare();
        controller.play();
        toast("Playing: " + selected.title);
    }

    private void pickFile(boolean video) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(video ? "video/*" : "audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, video ? PICK_VIDEO : PICK_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || (requestCode != PICK_AUDIO && requestCode != PICK_VIDEO)) return;
        boolean video = requestCode == PICK_VIDEO;
        int imported = 0;
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                if (addImported(data.getClipData().getItemAt(i).getUri(), video)) imported++;
            }
        } else if (data.getData() != null && addImported(data.getData(), video)) {
            imported++;
        }
        toast("Imported " + imported + " file(s).");
        libraryFilter = video ? "video" : "audio";
        showSection(0);
    }

    private boolean addImported(Uri uri, boolean video) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers don't offer persistable grants. Playback may stop
            // after a restart for those providers; the user can import again.
        }
        String name = uri.getLastPathSegment();
        try (Cursor c = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) name = video ? "Imported movie" : "Imported song";
        return database.addTrack(name, uri.toString(), video ? "video" : "audio") > 0;
    }

    private void showPlaylists() {
        title(pageContent, "Your playlists", "Create custom playlists and play every song automatically.");
        TextView create = action("+ Create named playlist", true, v -> newPlaylist());
        pageContent.addView(create, spaced(50, 18));
        ArrayList<LibraryDb.Playlist> playlists = database.playlists();
        if (playlists.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No playlists yet", 17, LIGHT, true));
            empty.addView(text("Create a playlist, then add music from your Library.", 13, MUTED, false));
            pageContent.addView(empty);
        }
        for (LibraryDb.Playlist playlist : playlists) {
            LinearLayout c = card();
            ArrayList<Track> tracks = database.playlistTracks(playlist.id);
            c.addView(text("▤ " + playlist.name, 18, LIGHT, true), spaced(-2, 6));
            c.addView(text(tracks.size() + " track(s)", 12, MUTED, false), spaced(-2, 12));
            LinearLayout controls = horizontal();
            TextView view = action("Open", true, v -> openPlaylist(playlist));
            TextView play = action("▶ Play all", false, v -> {
                if (tracks.size() > 0) playQueue(tracks, tracks.get(0));
                else toast("Add some tracks to this playlist first.");
            });
            controls.addView(view, new LinearLayout.LayoutParams(0, dp(43), 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(43), 1);
            p.leftMargin = dp(8);
            controls.addView(play, p);
            c.addView(controls);
            pageContent.addView(c, spaced(-2, 10));
        }
    }

    private void newPlaylist() {
        EditText name = input("e.g. Gym Motivation");
        LinearLayout container = vertical();
        container.setPadding(dp(18), dp(10), dp(18), dp(6));
        container.addView(name);
        new AlertDialog.Builder(this)
            .setTitle("Create a playlist")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", (dialog, which) -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty()) { toast("Enter a playlist name."); return; }
                if (database.createPlaylist(value) < 0) toast("That playlist already exists.");
                showSection(1);
            }).show();
    }

    private void choosePlaylist(Track track) {
        ArrayList<LibraryDb.Playlist> playlists = database.playlists();
        if (playlists.isEmpty()) { toast("Create a playlist first in the Playlists tab."); return; }
        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
        new AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setItems(names, (dialog, which) -> {
                database.addToPlaylist(playlists.get(which).id, track.id);
                toast("Added to " + names[which]);
            }).show();
    }

    private void openPlaylist(LibraryDb.Playlist playlist) {
        currentSection = 1;
        pageFrame.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        pageContent = vertical();
        pageContent.setPadding(dp(16), dp(10), dp(16), dp(30));
        scroll.addView(pageContent);
        pageFrame.addView(scroll);
        pageContent.addView(action("← All playlists", false, v -> showSection(1)), spaced(45, 12));
        title(pageContent, playlist.name, "Tap any song to play the entire playlist in sequence.");
        ArrayList<Track> tracks = database.playlistTracks(playlist.id);
        pageContent.addView(action("▶ Play playlist", true, v -> {
            if (tracks.isEmpty()) toast("No songs in this playlist.");
            else playQueue(tracks, tracks.get(0));
        }), spaced(48, 12));
        for (Track track : tracks) pageContent.addView(trackCard(track, tracks, playlist.id), spaced(-2, 10));
        pageContent.addView(action("Delete playlist", false, v -> new AlertDialog.Builder(this)
            .setTitle("Delete playlist?")
            .setMessage("Songs remain in your library.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (dialog, which) -> {
                database.deletePlaylist(playlist.id);
                showSection(1);
            }).show()), spaced(45, 8));
    }

    private void showOnline() {
        title(pageContent, "Search songs & videos", "One search. Choose YouTube Music, JioSaavn Songs or YouTube Videos.");

        LinearLayout searchCard = card();
        searchCard.addView(text("⌕ SEARCH ALL SERVICES", 16, LIGHT, true), spaced(-2, 8));
        searchCard.addView(text("Enter a song, artist, album or music video name.", 12, MUTED, false), spaced(-2, 10));
        EditText onlineQuery = input("Search songs or YouTube videos...");
        onlineQuery.setSingleLine(true);
        onlineQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        onlineQuery.setText(onlineSearchTerm);
        searchCard.addView(onlineQuery, spaced(-2, 10));
        LinearLayout resultContainer = vertical();
        Runnable searchAction = () -> {
            String query = onlineQuery.getText().toString().trim();
            if (query.length() == 0) { onlineQuery.setError("Enter a song or video name"); return; }
            onlineSearchTerm = query;
            getSharedPreferences("sayan_search", MODE_PRIVATE).edit()
                .putString("last_online_search", query).apply();
            renderOnlineSearchResults(resultContainer, query);
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(onlineQuery.getWindowToken(), 0);
            onlineQuery.clearFocus();
        };
        searchCard.addView(action("⌕ Search YouTube + JioSaavn", true, v -> searchAction.run()), spaced(49, 7));
        onlineQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                 event.getAction() == KeyEvent.ACTION_DOWN)) {
                searchAction.run();
                return true;
            }
            return false;
        });
        searchCard.addView(text("No API key or app account required. Searches open the official services to choose and play a result.",
            11, MUTED, false), matchWrap());
        pageContent.addView(searchCard, spaced(-2, 13));
        pageContent.addView(resultContainer, spaced(-2, 13));
        if (!onlineSearchTerm.isEmpty()) renderOnlineSearchResults(resultContainer, onlineSearchTerm);

        LinearLayout provider = card();
        provider.addView(text("Official streaming apps", 17, LIGHT, true), spaced(-2, 8));
        provider.addView(text("Play music and videos using their official apps or websites. Your phone may ask which app should open the link.",
            12, MUTED, false), spaced(-2, 12));
        provider.addView(action("▶ YouTube", true, v -> openWebsite("https://www.youtube.com/")), spaced(45, 8));
        provider.addView(action("♫ YouTube Music", false, v -> openWebsite("https://music.youtube.com/")), spaced(45, 8));
        provider.addView(action("♫ JioSaavn", false, v -> openWebsite("https://www.jiosaavn.com/")), spaced(45, 0));
        pageContent.addView(provider, spaced(-2, 14));

        LinearLayout direct = card();
        direct.addView(text("Play your own direct media URL", 17, LIGHT, true), spaced(-2, 7));
        direct.addView(text("For public-domain, Creative Commons, or your own authorized audio/video links. Not YouTube or JioSaavn page URLs.", 12, MUTED, false), spaced(-2, 12));
        EditText name = input("Track name (e.g. My Podcast)");
        direct.addView(name, spaced(-2, 9));
        EditText url = input("https://example.com/song.mp3");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        direct.addView(url, spaced(-2, 10));
        LinearLayout buttons = horizontal();
        TextView addAudio = action("+ Save audio URL", true, v -> saveStream(name, url, "stream", false));
        TextView addVideo = action("+ Save video URL", false, v -> saveStream(name, url, "video", false));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(46), 1);
        a.rightMargin = dp(6);
        buttons.addView(addAudio, a);
        buttons.addView(addVideo, new LinearLayout.LayoutParams(0, dp(46), 1));
        direct.addView(buttons, spaced(-2, 10));
        direct.addView(action("▶ Save and play now", false, v -> saveStream(name, url, guessKind(url.getText().toString()), true)), spaced(46, 0));
        pageContent.addView(direct, spaced(-2, 14));

        LinearLayout download = card();
        download.addView(text("Download permitted MP3s", 17, LIGHT, true), spaced(-2, 8));
        download.addView(text("Only direct HTTPS MP3 files you own or are licensed to save. Downloads appear in your phone's Downloads folder; import them from the Library afterward.", 12, MUTED, false), spaced(-2, 12));
        EditText downloadUrl = input("https://example.com/allowed-song.mp3");
        downloadUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        download.addView(downloadUrl, spaced(-2, 8));
        EditText downloadName = input("Save as (e.g. Workout song)");
        download.addView(downloadName, spaced(-2, 11));
        android.widget.CheckBox rights = new android.widget.CheckBox(this);
        rights.setText("I own this audio or have permission to download it.");
        rights.setTextColor(LIGHT);
        rights.setTextSize(12);
        download.addView(rights, spaced(-2, 10));
        download.addView(action("↓ Download MP3", true, v -> {
            if (!rights.isChecked()) { toast("Confirm your download rights first."); return; }
            downloadMp3(downloadUrl.getText().toString().trim(), downloadName.getText().toString().trim());
        }), spaced(48, 0));
        pageContent.addView(download, spaced(-2, 15));
    }

    /** An API-key-free search chooser. Does not scrape copyrighted streams. */
    private void renderOnlineSearchResults(LinearLayout container, String query) {
        container.removeAllViews();
        String encoded = Uri.encode(query);
        String[] names = { "YouTube Music", "JioSaavn Songs", "YouTube Videos" };
        String[] icons = { "♫", "♪", "▶" };
        String[] descriptions = {
            "Search official music, artists and albums",
            "Find songs in the official JioSaavn catalog",
            "Find music videos and other YouTube videos"
        };
        String[] urls = {
            "https://music.youtube.com/search?q=" + encoded,
            "https://www.jiosaavn.com/search/song/" + encoded,
            "https://www.youtube.com/results?search_query=" + encoded
        };
        TextView header = text("Search \"" + query + "\" on:", 16, LIGHT, true);
        container.addView(header, spaced(-2, 9));
        for (int i = 0; i < names.length; i++) {
            final String url = urls[i];
            LinearLayout result = card();
            LinearLayout label = horizontal();
            TextView serviceIcon = text(icons[i], 27, PURPLE, true);
            serviceIcon.setGravity(Gravity.CENTER);
            label.addView(serviceIcon, new LinearLayout.LayoutParams(dp(39), dp(42)));
            LinearLayout content = vertical();
            content.addView(text(names[i], 16, LIGHT, true));
            content.addView(text(descriptions[i], 11, MUTED, false));
            label.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
            result.addView(label, spaced(-2, 10));
            result.addView(action("⌕ Show results & play", true, v -> openWebsite(url)), spaced(44, 0));
            container.addView(result, spaced(-2, 9));
        }
        container.addView(text("Choose a provider, then tap a result to play there. Online results are not downloaded or played inside this app. YouTube/JioSaavn may have ads, login or background playback limits.",
            11, MUTED, false), spaced(-2, 8));
    }

    private void openWebsite(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("No browser or streaming app found."); }
    }
    private boolean https(String value) {
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    }
    private String guessKind(String url) {
        String lower = Uri.parse(url).getPath();
        if (lower != null && (lower.toLowerCase(Locale.ROOT).endsWith(".mp4") ||
            lower.toLowerCase(Locale.ROOT).endsWith(".m3u8") ||
            lower.toLowerCase(Locale.ROOT).endsWith(".webm"))) return "video";
        return "stream";
    }
    private void saveStream(EditText titleField, EditText linkField, String kind, boolean autoplay) {
        String link = linkField.getText().toString().trim();
        String titleValue = titleField.getText().toString().trim();
        if (!https(link)) { toast("Use a direct HTTPS media URL."); return; }
        String host = Uri.parse(link).getHost().toLowerCase(Locale.ROOT);
        if (host.contains("youtube.com") || host.contains("youtu.be") || host.contains("jiosaavn.com") || host.contains("saavn.com")) {
            toast("Open YouTube or JioSaavn through their official service above."); return;
        }
        if (titleValue.isEmpty()) titleValue = "Online media " + (database.tracks("all").size() + 1);
        long id = database.addTrack(titleValue, link, kind);
        if (id < 0) { toast("Unable to save link."); return; }
        toast("Saved to your library.");
        if (autoplay) {
            ArrayList<Track> saved = database.tracks(kind);
            for (Track t : saved) if (t.id == id) { playQueue(saved, t); break; }
        }
    }

    private void downloadMp3(String url, String label) {
        if (!https(url)) { toast("Only HTTPS download links are accepted."); return; }
        Uri source = Uri.parse(url);
        String path = source.getPath() == null ? "" : source.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".mp3")) {
            toast("Enter a direct MP3 link ending in .mp3 (not a streaming page)."); return;
        }
        String host = source.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("youtube") || host.contains("youtu.be") || host.contains("jiosaavn") || host.contains("saavn")) {
            toast("Third-party music-service downloads are not supported."); return;
        }
        if (label.isEmpty()) label = "Sayan Music " + System.currentTimeMillis();
        String clean = label.replaceAll("[^a-zA-Z0-9 _-]", "_").trim();
        if (clean.length() > 65) clean = clean.substring(0, 65);
        if (clean.isEmpty()) clean = "Music";
        String filename = clean + "-" + System.currentTimeMillis() + ".mp3";
        try {
            DownloadManager.Request request = new DownloadManager.Request(source)
                .setTitle(filename)
                .setDescription("Authorized MP3 download - Sayan Music & Movies")
                .setMimeType("audio/mpeg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager downloads = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloads == null) throw new IllegalStateException("Download manager unavailable");
            downloads.enqueue(request);
            toast("MP3 downloading. Find it in Downloads, then import it into Library.");
        } catch (Exception error) {
            toast("Download failed: " + error.getMessage());
        }
    }

    private void showGuide() {
        title(pageContent, "Guide & About", "Everything you need to use your personal music and movie player.");
        infoCard("1 · Import local music", "Open Library → + Import audio. Select one or many MP3, AAC, WAV, FLAC, OGG or other device-supported audio files. Access is granted through Android's file picker.");
        infoCard("2 · Watch your local videos", "Open Library → + Import video. Select a supported MP4, MKV, WebM or other format. Tap Play to show the video player.");
        infoCard("3 · Automatic next song", "Play any song from your library or playlist. The app automatically advances to the next track, and Repeat All starts again at the end. Use ⇄ to shuffle.");
        infoCard("4 · Playlists with custom names", "Open Playlists → Create named playlist. In Library choose + Playlist beside a song. Open the playlist and tap Play all.");
        infoCard("5 · Background music & controls", "When local music plays, you can lock your screen or switch apps. Media playback continues through the Android media notification, with Play/Pause and Next/Previous controls.");
        infoCard("6 · Seek bar, Repeat, Shuffle", "Drag the music bar to skip to any part. Use the repeat icon to choose All, One, or Off. Use shuffle to randomize your queue.");
        infoCard("7 · Sleep timer", "Tap Sleep timer below to select 5, 10, 15, 30, 45, 60 or 90 minutes. Audio pauses automatically. You can cancel it any time.");
        infoCard("8 · Search YouTube & JioSaavn", "Open Online Search. Type a song, artist or video name once and tap Search YouTube + JioSaavn. Choose YouTube Music, JioSaavn Songs or YouTube Videos, then tap a result in that official app/site to play. Service playback rules apply.");
        infoCard("9 · Online files and videos", "Use the Online Search tab to save or play direct HTTPS audio/video URLs you have permission to use. YouTube and JioSaavn page links are opened officially instead.");
        infoCard("10 · Download MP3", "In Online, paste a direct HTTPS MP3 download URL and confirm you have permission. It downloads into the phone's Downloads folder. Then import it from Library.");
        infoCard("11 · No login", "This player itself does not need an account. Songs, playlists and favorites are stored locally on your device. External YouTube/JioSaavn services have their own rules and may require a login.");
        LinearLayout timeCard = card();
        timeCard.addView(text("☾ Sleep timer", 18, LIGHT, true), spaced(-2, 7));
        long until = getSharedPreferences(PlaybackService.PREFS, MODE_PRIVATE).getLong(PlaybackService.SLEEP_END, 0L);
        timeCard.addView(text(until > System.currentTimeMillis()
            ? "Scheduled to pause in approximately " + ((until - System.currentTimeMillis()) / 60000L + 1) + " minute(s)"
            : "No sleep timer active", 13, MUTED, false), spaced(-2, 12));
        timeCard.addView(action("Set sleep timer", true, v -> chooseSleepTimer()), spaced(47, 7));
        timeCard.addView(action("Cancel sleep timer", false, v -> {
            Intent actionIntent = new Intent(this, PlaybackService.class);
            actionIntent.setAction(PlaybackService.ACTION_CANCEL_SLEEP);
            startService(actionIntent);
            toast("Sleep timer cancelled.");
            showSection(3);
        }), spaced(47, 0));
        pageContent.addView(timeCard, spaced(-2, 17));

        LinearLayout developer = card();
        developer.addView(text("DEVELOPED BY", 11, PURPLE, true), spaced(-2, 8));
        developer.addView(text("Eng. Sayan Lodh", 22, LIGHT, true), spaced(-2, 4));
        developer.addView(text("CSE (Computer Science Engineer) & AI Developer", 13, MUTED, false), spaced(-2, 10));
        developer.addView(text("Sayan Music & Movies • v1.1.0", 12, MUTED, false));
        pageContent.addView(developer, matchWrap());
    }

    private void infoCard(String heading, String details) {
        LinearLayout c = card();
        c.addView(text(heading, 16, LIGHT, true), spaced(-2, 7));
        c.addView(text(details, 13, MUTED, false));
        pageContent.addView(c, spaced(-2, 10));
    }

    private void chooseSleepTimer() {
        String[] labels = { "5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes" };
        int[] mins = { 5, 10, 15, 30, 45, 60, 90 };
        new AlertDialog.Builder(this).setTitle("Pause playback after")
            .setItems(labels, (dialog, index) -> {
                Intent intent = new Intent(this, PlaybackService.class);
                intent.setAction(PlaybackService.ACTION_SLEEP);
                intent.putExtra(PlaybackService.EXTRA_MINUTES, mins[index]);
                startService(intent);
                toast("Sleep timer set for " + labels[index]);
                showSection(3);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
