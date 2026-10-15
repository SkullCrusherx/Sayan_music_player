package com.sayanlodh.mediabox;

public final class Track {
    public final long id;
    public final String title;
    public final String uri;
    public final String kind;
    public final boolean favorite;

    public Track(long id, String title, String uri, String kind, boolean favorite) {
        this.id = id;
        this.title = title;
        this.uri = uri;
        this.kind = kind;
        this.favorite = favorite;
    }
}
