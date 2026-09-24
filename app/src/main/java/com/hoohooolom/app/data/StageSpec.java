package com.hoohooolom.app.data;

/**
 * The moving picture that plays while هوهو explains a step. In the science app every scene is a
 * real page of the printed book: the camera starts on the whole page, flies into the picture
 * being talked about, frames it with a glowing ring, and moves on to the next picture in turn —
 * the way a teacher's finger moves across the book on the desk.
 *
 * Rectangles are in thousandths of the page: {x1, y1, x2, y2}, origin at the top-left corner.
 * LessonStageView draws and animates them from app/src/main/assets/book/pNNN.webp.
 */
public class StageSpec {

    public enum Kind {
        NONE,
        /** A page of the book, with the camera touring its pictures one after another. */
        BOOK,
        /** An animated scene of pictures cut out of the book (see {@link Scene}). */
        SCENE
    }

    public enum Effect {
        /** The camera zooms right into each picture and rings it. */
        ZOOM,
        /** The camera stays wide; everything but the picture is dimmed, like a torch on the page. */
        SPOT
    }

    public final Kind kind;
    /** The printed page number (۷ to ۱۲۸). */
    public final int page;
    /** 4 values per stop: x1, y1, x2, y2 in ‰ of the page. Empty = the whole page. */
    public final int[] stops;
    public final Effect effect;
    /** A patch covered with a «؟» note until the child has answered — where the book shows the answer. */
    public final int[] cover;

    /** SCENE only. */
    public final Scene scene;

    private StageSpec(Kind kind, int page, int[] stops, Effect effect, int[] cover) {
        this(kind, page, stops, effect, cover, null);
    }

    private StageSpec(Kind kind, int page, int[] stops, Effect effect, int[] cover, Scene scene) {
        this.scene = scene;
        this.kind = kind;
        this.page = page;
        this.stops = stops == null ? new int[0] : stops;
        this.effect = effect;
        this.cover = cover;
    }

    public static final StageSpec NONE = new StageSpec(Kind.NONE, 0, null, Effect.ZOOM, null);

    /**
     * Page `page`, touring the given rectangles (4 numbers each) in order. With no rectangles
     * the whole page stays in view.
     */
    public static StageSpec book(int page, int... stops) {
        if (stops.length % 4 != 0) throw new IllegalArgumentException("page " + page + ": stops come in fours");
        return new StageSpec(Kind.BOOK, page, stops, Effect.ZOOM, null);
    }

    /** An animated scene of the book's own pictures; one line per actor or motion. */
    public static StageSpec scene(String... lines) {
        return new StageSpec(Kind.SCENE, 0, null, Effect.ZOOM, null, Scene.parse(lines));
    }

    /** The same tour, but with the torch-light look instead of zooming right in. */
    public StageSpec spot() {
        return new StageSpec(kind, page, stops, Effect.SPOT, cover);
    }

    /** Hides this patch of the page (x1, y1, x2, y2 in ‰) until the child has answered. */
    public StageSpec cover(int x1, int y1, int x2, int y2) {
        return new StageSpec(kind, page, stops, effect, new int[]{x1, y1, x2, y2});
    }

    public int stopCount() {
        return stops.length / 4;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }
}
