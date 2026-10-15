package com.sayanlodh.mediabox;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

public final class LibraryDb extends SQLiteOpenHelper {
    public static final class Playlist {
        public final long id;
        public final String name;
        public Playlist(long id, String name) { this.id = id; this.name = name; }
    }

    public LibraryDb(Context context) {
        super(context, "sayan_music_library.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, uri TEXT NOT NULL UNIQUE, kind TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL, position INTEGER NOT NULL, UNIQUE(playlist_id,track_id))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration will be added if the database schema changes in a future release.
    }

    public long addTrack(String title, String uri, String kind) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues row = new ContentValues();
        row.put("title", title);
        row.put("uri", uri);
        row.put("kind", kind);
        long added = db.insertWithOnConflict("tracks", null, row, SQLiteDatabase.CONFLICT_IGNORE);
        if (added != -1) return added;
        try (Cursor cursor = db.rawQuery("SELECT id FROM tracks WHERE uri=?", new String[] { uri })) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private ArrayList<Track> queryTracks(String sql, String[] args) {
        ArrayList<Track> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                out.add(new Track(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1));
            }
        }
        return out;
    }

    public ArrayList<Track> tracks(String filter) {
        String base = "SELECT id,title,uri,kind,favorite FROM tracks ";
        if ("favorites".equals(filter)) return queryTracks(base + "WHERE favorite=1 ORDER BY title COLLATE NOCASE", null);
        if ("audio".equals(filter) || "video".equals(filter) || "stream".equals(filter)) {
            return queryTracks(base + "WHERE kind=? ORDER BY title COLLATE NOCASE", new String[] { filter });
        }
        return queryTracks(base + "ORDER BY title COLLATE NOCASE", null);
    }

    public ArrayList<Playlist> playlists() {
        ArrayList<Playlist> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,name FROM playlists ORDER BY name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) out.add(new Playlist(cursor.getLong(0), cursor.getString(1)));
        }
        return out;
    }

    public long createPlaylist(String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        return getWritableDatabase().insertWithOnConflict("playlists", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public ArrayList<Track> playlistTracks(long playlistId) {
        return queryTracks("SELECT t.id,t.title,t.uri,t.kind,t.favorite FROM tracks t " +
            "INNER JOIN playlist_tracks p ON p.track_id=t.id WHERE p.playlist_id=? ORDER BY p.position ASC",
            new String[] { String.valueOf(playlistId) });
    }

    public void addToPlaylist(long playlistId, long trackId) {
        int nextPosition = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?",
                new String[] { String.valueOf(playlistId) })) {
            if (cursor.moveToFirst()) nextPosition = cursor.getInt(0);
        }
        ContentValues v = new ContentValues();
        v.put("playlist_id", playlistId);
        v.put("track_id", trackId);
        v.put("position", nextPosition);
        getWritableDatabase().insertWithOnConflict("playlist_tracks", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeFromPlaylist(long playlistId, long trackId) {
        getWritableDatabase().delete("playlist_tracks", "playlist_id=? AND track_id=?",
            new String[] { String.valueOf(playlistId), String.valueOf(trackId) });
    }

    public void deletePlaylist(long id) {
        getWritableDatabase().delete("playlist_tracks", "playlist_id=?", new String[] { String.valueOf(id) });
        getWritableDatabase().delete("playlists", "id=?", new String[] { String.valueOf(id) });
    }

    public void deleteTrack(long id) {
        getWritableDatabase().delete("playlist_tracks", "track_id=?", new String[] { String.valueOf(id) });
        getWritableDatabase().delete("tracks", "id=?", new String[] { String.valueOf(id) });
    }

    public void toggleFavorite(long id, boolean favorite) {
        ContentValues v = new ContentValues();
        v.put("favorite", favorite ? 1 : 0);
        getWritableDatabase().update("tracks", v, "id=?", new String[] { String.valueOf(id) });
    }
}
