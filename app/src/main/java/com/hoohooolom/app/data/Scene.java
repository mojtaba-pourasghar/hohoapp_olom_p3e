package com.hoohooolom.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An animated scene built from the pictures of the book: each picture is cut out of the page
 * (tools/cut_art.py → assets/art/NAME.webp) and becomes an actor that flies in, moves, bobs,
 * spins or shakes on a painted backdrop, with arrows, labels, rain, steam, bubbles and light
 * rays drawn around it — the moving blackboard a teacher would draw for a child.
 *
 * A scene is written as a few short lines, one per actor or motion, on a 1000 × 600 stage
 * (x to the right, y down). Times are fractions of هوهو's line: 0 is when she starts talking,
 * 1 when she stops.
 *
 * <pre>
 *   bg farm                                  backdrop: sky farm kitchen class lab water night
 *                                            park sea desert snow shop warm plain
 *   img NAME x y w [enter] [t] [fx…]         a cut-out picture, centred on (x, y), w wide
 *                                            enter: pop fade left right top bottom grow none
 *                                            fx: bob sway spin shake pulse float flip tilt
 *   text "…" x y [t] [color] [size]          a label chip; "?:…" stays hidden until answered
 *   arrow x1 y1 x2 y2 [t] [color] [curve]    an arrow that draws itself
 *   ring I [t]                               a glowing ring round actor I (1 = first img)
 *   move I x y t1 t2                         actor I travels to (x, y)
 *   scale I s t1 t2 | turn I deg t1 t2       actor I grows/shrinks, turns
 *   hide I t | show I t                      actor I fades out / in at t
 *   rain x y w h t1 t2 | snow …              falling drops / flakes over a patch
 *   steam x y t1 t2 | bubbles x y t1 t2      rising steam puffs / bubbles from a point
 *   rays x y r t                             a sun's glow and rays at (x, y), radius r
 *   beam x1 y1 x2 y2 t [color]               a light beam travelling along a line
 *   sparkle x y t                            a burst of little stars
 *   box x1 y1 x2 y2 [t] [color]              a soft rounded panel behind a group
 * </pre>
 * Colours: orange teal pink blue green red yellow purple white ink.
 */
public final class Scene {

    public enum Kind { IMG, TEXT, ARROW, RING, RAIN, SNOW, STEAM, BUBBLES, RAYS, BEAM, SPARKLE, BOX }

    public static final class Item {
        public Kind kind;
        public String name;         // IMG: asset name; TEXT: the label
        public boolean hidden;      // TEXT: kept back until the child has answered
        public float x, y, w, h;    // centre / size, or x1 y1 x2 y2 for lines and patches
        public float t, t2 = 1f;    // appear time, and end time for particles
        public String enter = "pop";
        public List<String> fx = new ArrayList<>();
        public String color = "orange";
        public float size = 34f;    // TEXT size in stage units
        public boolean curve;
        public int target = -1;     // RING: which actor
    }

    public static final class Motion {
        public String kind;         // move scale turn hide show
        public int actor;           // index into actors()
        public float a, b, t1, t2;
    }

    public final String bg;
    public final List<Item> items;
    public final List<Motion> motions;
    /** The IMG items in order, so `ring 2` or `move 2` means the second picture. */
    public final List<Item> actors;

    private Scene(String bg, List<Item> items, List<Motion> motions, List<Item> actors) {
        this.bg = bg;
        this.items = Collections.unmodifiableList(items);
        this.motions = Collections.unmodifiableList(motions);
        this.actors = Collections.unmodifiableList(actors);
    }

    public static final String[] BACKDROPS = {
        "sky", "farm", "kitchen", "class", "lab", "water", "night", "park", "sea", "desert",
        "snow", "shop", "warm", "plain"
    };
    public static final String[] COLORS = {
        "orange", "teal", "pink", "blue", "green", "red", "yellow", "purple", "white", "ink"
    };

    public static Scene parse(String... lines) {
        String bg = "plain";
        List<Item> items = new ArrayList<>();
        List<Motion> motions = new ArrayList<>();
        List<Item> actors = new ArrayList<>();
        for (String line : lines) {
            List<String> tk = tokens(line);
            if (tk.isEmpty()) continue;
            String op = tk.get(0);
            try {
                switch (op) {
                    case "bg":
                        bg = tk.get(1);
                        if (!contains(BACKDROPS, bg)) throw new IllegalArgumentException("unknown backdrop " + bg);
                        break;
                    case "img": {
                        Item it = new Item();
                        it.kind = Kind.IMG;
                        it.name = tk.get(1);
                        it.x = f(tk, 2); it.y = f(tk, 3); it.w = f(tk, 4);
                        int i = 5;
                        if (i < tk.size() && isWord(tk.get(i)) && isEnter(tk.get(i))) it.enter = tk.get(i++);
                        if (i < tk.size() && isNumber(tk.get(i))) it.t = f(tk, i++);
                        for (; i < tk.size(); i++) {
                            if (!isFx(tk.get(i))) throw new IllegalArgumentException("unknown effect " + tk.get(i));
                            it.fx.add(tk.get(i));
                        }
                        items.add(it);
                        actors.add(it);
                        break;
                    }
                    case "text": {
                        Item it = new Item();
                        it.kind = Kind.TEXT;
                        String s = tk.get(1);
                        if (s.startsWith("?:")) { it.hidden = true; s = s.substring(2); }
                        it.name = s;
                        it.x = f(tk, 2); it.y = f(tk, 3);
                        int i = 4;
                        if (i < tk.size() && isNumber(tk.get(i))) it.t = f(tk, i++);
                        if (i < tk.size() && contains(COLORS, tk.get(i))) it.color = tk.get(i++);
                        if (i < tk.size() && isNumber(tk.get(i))) it.size = f(tk, i++);
                        items.add(it);
                        break;
                    }
                    case "arrow":
                    case "beam": {
                        Item it = new Item();
                        it.kind = op.equals("arrow") ? Kind.ARROW : Kind.BEAM;
                        it.x = f(tk, 1); it.y = f(tk, 2); it.w = f(tk, 3); it.h = f(tk, 4);
                        it.color = op.equals("arrow") ? "red" : "yellow";
                        int i = 5;
                        if (i < tk.size() && isNumber(tk.get(i))) it.t = f(tk, i++);
                        for (; i < tk.size(); i++) {
                            if (tk.get(i).equals("curve")) it.curve = true;
                            else if (contains(COLORS, tk.get(i))) it.color = tk.get(i);
                            else throw new IllegalArgumentException("unknown word " + tk.get(i));
                        }
                        items.add(it);
                        break;
                    }
                    case "ring": {
                        Item it = new Item();
                        it.kind = Kind.RING;
                        it.target = (int) f(tk, 1) - 1;
                        if (tk.size() > 2) it.t = f(tk, 2);
                        items.add(it);
                        break;
                    }
                    case "rain":
                    case "snow": {
                        Item it = new Item();
                        it.kind = op.equals("rain") ? Kind.RAIN : Kind.SNOW;
                        it.x = f(tk, 1); it.y = f(tk, 2); it.w = f(tk, 3); it.h = f(tk, 4);
                        it.t = tk.size() > 5 ? f(tk, 5) : 0f;
                        it.t2 = tk.size() > 6 ? f(tk, 6) : 1f;
                        items.add(it);
                        break;
                    }
                    case "steam":
                    case "bubbles": {
                        Item it = new Item();
                        it.kind = op.equals("steam") ? Kind.STEAM : Kind.BUBBLES;
                        it.x = f(tk, 1); it.y = f(tk, 2);
                        it.t = tk.size() > 3 ? f(tk, 3) : 0f;
                        it.t2 = tk.size() > 4 ? f(tk, 4) : 1f;
                        items.add(it);
                        break;
                    }
                    case "rays": {
                        Item it = new Item();
                        it.kind = Kind.RAYS;
                        it.x = f(tk, 1); it.y = f(tk, 2); it.w = f(tk, 3);
                        it.t = tk.size() > 4 ? f(tk, 4) : 0f;
                        items.add(it);
                        break;
                    }
                    case "sparkle": {
                        Item it = new Item();
                        it.kind = Kind.SPARKLE;
                        it.x = f(tk, 1); it.y = f(tk, 2);
                        it.t = tk.size() > 3 ? f(tk, 3) : 0f;
                        items.add(it);
                        break;
                    }
                    case "box": {
                        Item it = new Item();
                        it.kind = Kind.BOX;
                        it.x = f(tk, 1); it.y = f(tk, 2); it.w = f(tk, 3); it.h = f(tk, 4);
                        it.color = "white";
                        int i = 5;
                        if (i < tk.size() && isNumber(tk.get(i))) it.t = f(tk, i++);
                        if (i < tk.size() && contains(COLORS, tk.get(i))) it.color = tk.get(i);
                        items.add(it);
                        break;
                    }
                    case "move":
                    case "scale":
                    case "turn":
                    case "hide":
                    case "show": {
                        Motion m = new Motion();
                        m.kind = op;
                        m.actor = (int) f(tk, 1) - 1;
                        if (op.equals("move")) { m.a = f(tk, 2); m.b = f(tk, 3); m.t1 = f(tk, 4); m.t2 = f(tk, 5); }
                        else if (op.equals("scale") || op.equals("turn")) { m.a = f(tk, 2); m.t1 = f(tk, 3); m.t2 = f(tk, 4); }
                        else { m.t1 = f(tk, 2); m.t2 = m.t1 + 0.08f; }
                        motions.add(m);
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("unknown command " + op);
                }
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                throw new IllegalArgumentException("bad scene line: " + line, e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " in: " + line, e);
            }
        }
        for (Item it : items) {
            if (it.kind == Kind.RING && (it.target < 0 || it.target >= actors.size())) {
                throw new IllegalArgumentException("ring points at actor " + (it.target + 1) + " of " + actors.size());
            }
        }
        for (Motion m : motions) {
            if (m.actor < 0 || m.actor >= actors.size()) {
                throw new IllegalArgumentException(m.kind + " points at actor " + (m.actor + 1) + " of " + actors.size());
            }
        }
        return new Scene(bg, items, motions, actors);
    }

    /** Every picture this scene needs, for the checker. */
    public List<String> artNames() {
        List<String> out = new ArrayList<>();
        for (Item it : actors) out.add(it.name);
        return out;
    }

    // ── tokenizer: words separated by spaces, "quoted text" kept whole ──

    private static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') { i++; continue; }
            if (c == '"') {
                int j = line.indexOf('"', i + 1);
                if (j < 0) throw new IllegalArgumentException("unclosed quote in: " + line);
                out.add(line.substring(i + 1, j));
                i = j + 1;
            } else {
                int j = i;
                while (j < n && line.charAt(j) != ' ' && line.charAt(j) != '\t') j++;
                out.add(line.substring(i, j));
                i = j;
            }
        }
        return out;
    }

    private static float f(List<String> tk, int i) {
        return Float.parseFloat(tk.get(i));
    }

    private static boolean isNumber(String s) {
        try { Float.parseFloat(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static boolean isWord(String s) {
        return !s.isEmpty() && Character.isLetter(s.charAt(0));
    }

    private static final String[] ENTERS = {"pop", "fade", "left", "right", "top", "bottom", "grow", "none"};
    private static final String[] FX = {"bob", "sway", "spin", "shake", "pulse", "float", "flip", "tilt"};

    private static boolean isEnter(String s) { return contains(ENTERS, s); }
    private static boolean isFx(String s) { return contains(FX, s); }

    private static boolean contains(String[] arr, String s) {
        for (String a : arr) if (a.equals(s)) return true;
        return false;
    }
}
