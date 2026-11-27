package net.peregrine.client.core;

/**
 * Peregrine's main menu, shown instead of Minecraft's title screen: the logo, your
 * name and head, a column of buttons with icons (the first in the accent color),
 * small Realms / Language / Accessibility buttons at the bottom, and a "Jump back
 * in" card for the last server you played. Version adapters draw the panorama (unless
 * drawsBackground() says a picture replaces it), then call render().
 *
 * On the right, your player (the adapter draws it at playerBox()) and the Locker button. The
 * Locker slides in over the menu: pick your Peregrine cape and the main menu's background,
 * and your player glides across to model them.
 */
public final class TitleMenu {

    public static final String CLIENT_VERSION = "5.0.1";

    static final String BRAND = "Light Client";
    static final int H = 20, GAP = 4, SMALL_H = 15;

    /** A menu button: label, icon, what it opens. Shared with the pause menu. */
    static final class Button {
        String label;
        final String icon;
        final Platform.Screen action;
        final boolean primary;
        String id;           // what the layout editor remembers it by
        int x, y, w, h;
        float glow;  // hover highlight, eased in and out

        Button(String label, String icon, Platform.Screen action, boolean primary) {
            this.label = label;
            this.icon = icon;
            this.action = action;
            this.primary = primary;
        }

        Button named(String id) {
            this.id = id;
            return this;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void place(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Just the icon in a small square; its name shows above it while hovered. */
        void drawIcon(Draw d, int mx, int my, float dt, int accent) {
            boolean hover = contains(mx, my);
            glow = hover ? Math.min(1f, glow + dt * 9f) : Math.max(0f, glow - dt * 6f);
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 3, Theme.mix(0xD81C1C1C, 0xE82B2B2B, glow));
            MenuIcons.draw(d, icon, x + (w - 9) / 2, y + (h - 9) / 2, Theme.mix(0xFFD3D6DE, Theme.TEXT, glow));
            if (glow > 0.5f) {
                d.text(label, x, y - 11, Theme.withAlpha(Theme.TEXT, Math.round(0xFF * glow)), true);
            }
        }

        /** Eases the hover glow, then draws the button. */
        void draw(Draw d, int mx, int my, float dt, int accent, boolean small) {
            boolean hover = contains(mx, my);
            glow = hover ? Math.min(1f, glow + dt * 9f) : Math.max(0f, glow - dt * 6f);
            float g = glow;
            int bg = primary ? Theme.mix(accent, Theme.mix(accent, 0xFFFFFFFF, 0.18f), g)
                    : Theme.mix(0xD81C1C1C, 0xE82B2B2B, g);
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 3, bg);
            // a darker lip along the bottom, like a key you can press
            d.rect(x + 2, y + h - 2, w - 4, 1, primary ? Theme.mix(accent, 0xFF000000, 0.35f) : 0x50000000);
            if (!primary && g > 0) {  // accent bar on the left while hovered
                int bh = Math.round((h - 8) * g);
                d.rect(x + 1, y + (h - bh) / 2, 2, bh, Theme.withAlpha(accent, Math.round(0xFF * g)));
            }
            int fg = primary ? Theme.onColor(accent) : Theme.mix(0xFFD3D6DE, Theme.TEXT, g);
            int tx = x + 8;
            if (icon != null) {
                int iy = y + (h - 9) / 2;
                MenuIcons.draw(d, icon, x + 8, iy, fg);
                tx = x + 23;
            }
            String text = d.trim(label, x + w - 6 - tx);
            if (small) {
                d.text(text, tx, y + (h - d.lineHeight()) / 2 + 1, fg, false);
            } else {
                d.text(text, tx, y + (h - d.lineHeight()) / 2 + 1, fg, !primary);
            }
        }
    }

    private final Peregrine pc;
    private long shownAt = -1;
    private long lastFrame;
    private final Button[] buttons = {
        new Button("Singleplayer", "controller", Platform.Screen.SINGLEPLAYER, true).named("singleplayer"),
        new Button("Multiplayer", "globe", Platform.Screen.MULTIPLAYER, false).named("multiplayer"),
        new Button("Light Client menu", "grid", Platform.Screen.PEREGRINE_MENU, false).named("menu"),
        new Button("Settings", "sliders", Platform.Screen.OPTIONS, false).named("settings"),
        new Button("Quit game", "power", Platform.Screen.QUIT, false).named("quit"),
    };
    private final Button[] small = {
        new Button("Realms", "castle", Platform.Screen.REALMS, false).named("realms"),
        new Button("Language", "language", Platform.Screen.LANGUAGE, false).named("language"),
        new Button("Accessibility", "person", Platform.Screen.ACCESSIBILITY, false).named("accessibility"),
    };
    private final int[] card = new int[4];  // the "Jump back in" card: x, y, w, h (w 0 = none)
    private float cardGlow;
    private final Button newsButton = new Button("What's new", "layers", null, false).named("news");
    private int left, logoY, homeWidth;
    static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

    // ---- the right-hand panel and the Locker
    private final Button lockerButton = new Button("Locker", "shirt", null, false).named("locker");
    private final Button backButton = new Button("Back", "back", null, false);
    private final int[] panel = new int[4];      // the player panel on the right: x, y, w, h (w 0 = none)
    private boolean lockerOpen;
    private float lockerT;                       // 0 = menu, 1 = Locker, eased
    private int lockerTab;                       // 0 = capes, 1 = skins, 2 = backgrounds
    static final String[] TABS = {"Capes", "Skins", "Backgrounds"};
    private final LockerSkins skins = new LockerSkins();
    private int trying = -1;                     // the skin card being tried on
    private final Button wearButton = new Button("Wear it", "check", null, true);
    private final float[] skinGlow = new float[64];
    private float tabT = 1f;
    private final float[] capeGlow = new float[net.peregrine.client.core.modules.Cosmetics.CAPES.length];
    private final float[] bgGlow = new float[8];
    private final float[] tabGlow = new float[3];
    /** Locker cards laid out this frame: {kind (0 cape, 1 background, 2 tab), index, x, y, w, h}. */
    private final java.util.List<int[]> cards = new java.util.ArrayList<int[]>();
    private int[] lockerPlayer = new int[4];
    private final PlayerPreview preview = new PlayerPreview();
    private boolean pressedPlayer;
    private int pressX;

    // ---- the layout editor: hide buttons, drag them anywhere
    private final TitleLayout layout = new TitleLayout();
    private boolean editing;
    private String dragId;
    private int dragDX, dragDY;
    private boolean dragMoved;
    private final Button editChip = new Button("Edit layout", "wrench", null, false);
    private final Button doneButton = new Button("Done", "check", null, true);
    private final Button resetButton = new Button("Reset", "reset", null, false);
    private final java.util.List<Object[]> trayHits = new java.util.ArrayList<Object[]>();   // {id, x, y, w, h}

    // ---- backgrounds
    final BackgroundEditor editor = new BackgroundEditor(this);
    private final Holidays.Scene holidayScene = new Holidays.Scene(46);
    private float adjustGlow;
    private String customId;
    private long customStamp = -1;
    private int customW = 16, customH = 9;
    private long customCheckedAt;

    static String versionLine(Platform p) {
        return BRAND + " " + CLIENT_VERSION + "  \u00B7  Minecraft " + p.minecraftVersion();
    }

    TitleMenu(Peregrine pc) {
        this.pc = pc;
    }

    private void layout(Platform p) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        var mm = mainMenu();
        int pw = Math.min(122, sw / 5), ph = Math.min(178, sh - 71);
        boolean player = (mm == null || mm.player.value) && ph >= 96 && sw >= 380;
        left = Math.max(12, sw / 15);
        homeWidth = Math.min(182, Math.max(106, sw - 2 * left - (player ? pw + 30 : 0)));
        layout.load(p.configFile());
        boolean recent = pc.lastServerName() != null && p.supportsJoin() && sh >= 180 && !layout.hidden("recent");
        boolean recentFlows = recent && !layout.moved("recent");
        boolean shortScreen = sh < 205;
        int rowHeight = shortScreen ? 15 : 20;
        int rowGap = shortScreen ? 3 : 5;
        int headerHeight = shortScreen ? 28 : 40;
        int recentHeight = shortScreen ? 28 : 36;
        int flowing = 0;
        for (Button b : buttons) {
            if (flows(b)) flowing++;
        }
        int stackHeight = headerHeight + (recentFlows ? recentHeight + 7 : 0) + flowing * (rowHeight + rowGap);
        logoY = Math.max(7, (sh - 34 - stackHeight) / 2);
        int y = logoY + headerHeight;
        card[0] = left;
        card[1] = y;
        card[2] = recent ? homeWidth : 0;
        card[3] = recentHeight;
        if (recentFlows) y += recentHeight + 7;
        for (Button button : buttons) {
            button.place(left, y, homeWidth, rowHeight);
            if (flows(button)) {
                y += rowHeight + rowGap;
            }
        }

        // the bottom row: separate chips, each its own size
        int footerY = Math.max(y + 4, sh - 26);
        int fx = left;
        Button[] row = {lockerButton, newsButton, small[0], small[1], small[2]};
        for (Button b : row) {
            int bw = chipWidth(b.label);
            if (fx + bw > sw - left - 200 && b != lockerButton) {   // narrow windows: icons only
                bw = 18;
            }
            b.place(fx, footerY, bw, 18);
            if (flows(b)) {
                fx += bw + 5;
            }
        }
        if (player) {
            panel[0] = sw - left - pw;
            panel[1] = Math.max(12, (sh - ph) / 2 - 10);
            panel[2] = pw;
            panel[3] = ph;
        } else {
            panel[2] = 0;
        }
        applyLayout(sw, sh);
    }

    // ------------------------------------------------------------------ the player's own layout

    /** Whether a button takes its place in the default stack or row (not hidden, not moved). */
    private boolean flows(Button b) {
        return !layout.hidden(b.id) && !layout.moved(b.id);
    }

    /** Puts moved buttons where the player left them and takes hidden ones out of play. */
    private void applyLayout(int sw, int sh) {
        for (Button b : allButtons()) {
            if (layout.hidden(b.id)) {
                b.place(0, 0, 0, 0);
            } else if (layout.moved(b.id)) {
                TitleLayout.Entry e = layout.get(b.id);
                b.place(clamp(Math.round(e.fx * sw), 0, sw - b.w), clamp(Math.round(e.fy * sh), 0, sh - b.h), b.w, b.h);
            }
        }
        if (layout.moved("recent") && card[2] > 0) {
            TitleLayout.Entry e = layout.get("recent");
            card[0] = clamp(Math.round(e.fx * sw), 0, sw - card[2]);
            card[1] = clamp(Math.round(e.fy * sh), 0, sh - card[3]);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    private java.util.List<Button> allButtons() {
        java.util.List<Button> all = new java.util.ArrayList<Button>();
        java.util.Collections.addAll(all, buttons);
        all.add(lockerButton);
        all.add(newsButton);
        java.util.Collections.addAll(all, small);
        return all;
    }

    private static String labelOf(String id) {
        switch (id) {
            case "singleplayer": return "Singleplayer";
            case "multiplayer": return "Multiplayer";
            case "menu": return "Light Client menu";
            case "settings": return "Settings";
            case "quit": return "Quit game";
            case "realms": return "Realms";
            case "language": return "Language";
            case "accessibility": return "Accessibility";
            case "locker": return "Locker";
            case "news": return "What's new";
            default: return "Jump back in";
        }
    }

    /** Everything the player may rearrange, by id, in drawing order. */
    private java.util.List<String> editableIds() {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (Button b : allButtons()) {
            ids.add(b.id);
        }
        ids.add("recent");
        return ids;
    }

    /** {x, y, w, h} of an editable thing right now (w 0 = not on screen). */
    private int[] rectOf(String id) {
        if (id.equals("recent")) {
            return new int[] {card[0], card[1], card[2], card[3]};
        }
        for (Button b : allButtons()) {
            if (b.id.equals(id)) {
                return new int[] {b.x, b.y, b.w, b.h};
            }
        }
        return new int[] {0, 0, 0, 0};
    }

    private static final int TRAY_H = 14;

    private static int trayWidth(String label) {
        return 20 + Math.round(label.length() * 5.6f);
    }

    /** The Edit button's place: the top right corner. */
    private void placeEditChip(int sw) {
        editChip.place(sw - 6 - 18, 6, 18, 18);
    }

    private void layoutEditBar(int sw) {
        int total = 74 + 64 + 6;
        int x = (sw - total) / 2;
        resetButton.place(x, 6, 64, 18);
        doneButton.place(x + 70, 6, 74, 18);
    }

    /** The edit chip when not editing; the Done / Reset bar, outlines, hide badges and the tray of hidden buttons when editing. */
    private void drawEditLayer(Draw d, Platform p, int mx, int my, float dt, int a) {
        int sw = p.screenWidth();
        trayHits.clear();
        if (!editing) {
            if (lockerOpen) return;
            placeEditChip(sw);
            drawAction(d, editChip, mx, my, dt, a);
            if (editChip.contains(mx, my)) {
                d.text(editChip.label, editChip.x - 6 - d.width(editChip.label), editChip.y + 5, Theme.MUTED, true);
            }
            return;
        }
        layoutEditBar(sw);
        drawAction(d, resetButton, mx, my, dt, a);
        drawAction(d, doneButton, mx, my, dt, a);
        String hint = "Drag a button to move it  \u00B7  \u00D7 hides it";
        d.text(hint, (sw - d.width(hint)) / 2, 30, Theme.MUTED, true);
        for (String id : editableIds()) {
            if (layout.hidden(id)) continue;
            int[] r = rectOf(id);
            if (r[2] <= 0) continue;
            boolean dragging = id.equals(dragId) && dragMoved;
            d.roundOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 4,
                    dragging ? Theme.withAlpha(a, 0xFF) : Theme.withAlpha(a, 0x90));
            int bx = r[0] + r[2] - 6, by = r[1] - 6;
            boolean over = mx >= bx && mx < bx + 11 && my >= by && my < by + 11;
            d.roundRect(bx, by, 11, 11, 5, over ? 0xFFE5484D : 0xF0282828);
            d.roundOutline(bx, by, 11, 11, 5, over ? 0xFFFF8A8E : 0x80FFFFFF);
            MenuIcons.draw(d, "close", bx + 1, by + 1, 0xFFFFFFFF);
        }
        // hidden buttons wait in a tray; click one to bring it back
        java.util.List<String> hidden = new java.util.ArrayList<String>();
        for (String id : editableIds()) {
            if (layout.hidden(id)) hidden.add(id);
        }
        if (!hidden.isEmpty()) {
            int total = -4;
            for (String id : hidden) total += trayWidth(labelOf(id)) + 4;
            int x = (sw - total) / 2, y = 42;
            for (String id : hidden) {
                String label = labelOf(id);
                int w = trayWidth(label);
                boolean over = mx >= x && mx < x + w && my >= y && my < y + TRAY_H;
                d.roundRect(x, y, w, TRAY_H, 3, over ? 0xF02B2B2B : 0xD81C1C1C);
                d.roundOutline(x, y, w, TRAY_H, 3, Theme.withAlpha(a, over ? 0xE0 : 0x60));
                d.text("+ " + label, x + 6, y + 3, over ? Theme.TEXT : Theme.MUTED, false);
                trayHits.add(new Object[] {id, x, y, w, TRAY_H});
                x += w + 4;
            }
        }
    }

    private boolean editClick(int mx, int my) {
        Platform p = pc.platform();
        layoutEditBar(p.screenWidth());
        if (doneButton.contains(mx, my)) {
            stopEditing();
            return true;
        }
        if (resetButton.contains(mx, my)) {
            layout.reset();
            return true;
        }
        for (Object[] t : trayHits) {
            int x = (Integer) t[1], y = (Integer) t[2], w = (Integer) t[3], h = (Integer) t[4];
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                layout.get((String) t[0]).hidden = false;
                layout.save();
                return true;
            }
        }
        java.util.List<String> ids = editableIds();
        for (int i = ids.size() - 1; i >= 0; i--) {   // the hide badges first (they sit on the corners)
            String id = ids.get(i);
            int[] r = rectOf(id);
            if (!layout.hidden(id) && r[2] > 0 && mx >= r[0] + r[2] - 6 && mx < r[0] + r[2] + 5 && my >= r[1] - 6 && my < r[1] + 5) {
                layout.get(id).hidden = true;
                layout.save();
                return true;
            }
        }
        for (int i = ids.size() - 1; i >= 0; i--) {
            String id = ids.get(i);
            int[] r = rectOf(id);
            if (!layout.hidden(id) && r[2] > 0 && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                dragId = id;
                dragDX = mx - r[0];
                dragDY = my - r[1];
                dragMoved = false;
                return true;
            }
        }
        return true;   // nothing else works while arranging
    }

    private void stopEditing() {
        editing = false;
        dragId = null;
        layout.save();
    }

    private boolean editDrag(int mx, int my) {
        if (dragId == null) {
            return editing;
        }
        Platform p = pc.platform();
        int sw = p.screenWidth(), sh = p.screenHeight();
        int[] r = rectOf(dragId);
        int nx = clamp(mx - dragDX, 0, sw - r[2]), ny = clamp(my - dragDY, 0, sh - r[3]);
        if (!dragMoved && Math.abs(nx - r[0]) < 2 && Math.abs(ny - r[1]) < 2) {
            return true;
        }
        dragMoved = true;
        TitleLayout.Entry e = layout.get(dragId);
        e.fx = nx / (float) sw;
        e.fy = ny / (float) sh;
        return true;
    }

    /** A footer chip's width: icon, label, padding (about 5.5 GUI px a letter). */
    private static int chipWidth(String label) {
        Integer measured = CHIP_W.get(label);
        return measured != null ? measured : 26 + Math.round(label.length() * 5.6f);
    }

    /** Footer chips' widths as last drawn (the font decides): the layout uses them from then on. */
    private static final java.util.Map<String, Integer> CHIP_W = new java.util.HashMap<>();

    private static int lockerLeft(int sw) {
        return Math.max(14, Math.round(sw * 0.06f));
    }

    /** The game window's title: "Peregrine Client 1.2.0 - Minecraft 1.21.1 - Singleplayer". */
    public static String windowTitle(String vanilla) {
        String rest = vanilla == null ? "Minecraft" : vanilla.replaceFirst("^Minecraft\\*", "Minecraft");
        return BRAND + " " + CLIENT_VERSION + " - " + rest;
    }

    /** Call when the main menu screen is created, so it fades in. */
    public void shown() {
        shownAt = System.currentTimeMillis();
        UpdateNotice.atTitle();
    }

    public void render(Draw raw, int mx, int my) {
        SelfTest.count("title");
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 400);
        Draw base = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int a = pc.accent();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float target = lockerOpen ? 1f : 0f;
        lockerT += (target - lockerT) * Math.min(1f, dt * 9f);
        if (Math.abs(target - lockerT) < 0.002f) {
            lockerT = target;
        }
        tabT = Math.min(1f, tabT + dt * 2.2f);
        float e = lockerT * lockerT * (3 - 2 * lockerT);  // smoothstep
        net.peregrine.client.core.modules.MainMenu mm = mainMenu();
        editor.update(mm, dt);
        float ed = editor.openness();  // the picture editor hides the menu while it's open

        drawBackground(base, sw, sh, now);
        int holiday = mm == null ? Holidays.NONE : mm.holidayTheme();

        // A vignette: the world darkens towards the edges and the bottom, so the middle glows
        float shade = 1f - ed;
        if (shade > 0.001f) {
            base.rect(0, 0, sw, sh, Theme.withAlpha(0, Math.round((0x28 + 0x40 * e) * shade)));
            for (int y = 0; y < sh; y += 2) {
                float v = y / (float) sh;
                float k = v > 0.55f ? (v - 0.55f) / 0.45f : v < 0.18f ? (0.18f - v) / 0.18f * 0.6f : 0f;
                int alpha = Math.round(0x80 * k * k * (1f - e) * shade);
                if (alpha > 0) {
                    base.rect(0, y, sw, 2, Theme.withAlpha(0, alpha));
                }
            }
            Glass.vignette(base, sw, sh, Math.round(0x86 * (1f - e) * shade));
        }
        if (holiday != Holidays.NONE && ed < 0.999f) {
            holidayScene.render(ed > 0.001f ? new FadeDraw(base, 1f - ed) : base, holiday, 0, 0, sw, sh, dt);
        }
        Draw editorLayer = base;
        if (ed > 0.999f) {
            editor.render(editorLayer, p, mm, mx, my, dt, a);
            UpdateNotice.render(raw, p, mx, my, a);
            return;
        }
        if (ed > 0.001f) {
            base = new FadeDraw(base, 1f - ed);
        }

        layout(p);
        layoutLocker(p, base, sw, sh);

        // the menu slides left and fades as the Locker comes in
        if (e < 0.999f) {
            int off = Math.round(-e * sw * 0.25f);
            Draw d = e > 0.001f ? new FadeDraw(base, 1f - e) : base;
            d.pushScale(off, 0, 1f);
            drawHome(d, p, mx - off, my, dt, a);
            d.popScale();
        }
        if (e > 0.001f) {
            int off = Math.round((1f - e) * sw * 0.2f);
            Draw d = e < 0.999f ? new FadeDraw(base, e) : base;
            d.pushScale(off, 0, 1f);
            drawLocker(d, p, mx - off, my, dt, a, sw, sh);
            drawLockerExtras(d, p, mx - off, my, dt, a);
            d.popScale();
        }

        int[] box = playerBox();
        if (box != null) {
            preview.draw(base, p, box[0], box[1], box[2], box[3], net.peregrine.client.core.modules.Cosmetics.capeIndex());
        }

        if (e < 0.98f) {
            Draw vd = e > 0.01f ? new FadeDraw(base, 1f - e) : base;
            if (holiday != Holidays.NONE && !editing) {
                Holidays.greetingChip(vd, holiday, sw / 2, 6);
            }
            String ver = versionLine(p);
            vd.text(ver, sw - 6 - vd.width(ver), sh - 20, Theme.FAINT, true);
            vd.text(COPYRIGHT, sw - 6 - vd.width(COPYRIGHT), sh - 10, Theme.FAINT, true);
            String up = UpdateNotice.available();
            if (up != null) {  // a quiet reminder under the card once it has slid away
                String note = "Update " + up + " ready \u00B7 restart the launcher";
                vd.text(note, sw - 6 - vd.width(note), sh - 30, Theme.withAlpha(a, 0xD0), true);
            }
        }
        editor.render(editorLayer, p, mm, mx, my, dt, a);
        UpdateNotice.render(raw, p, mx, my, a);
    }

    private void drawHome(Draw d, Platform p, int mx, int my, float dt, int a) {
        float entrance = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 270);
        Draw home = entrance < 1f ? new FadeDraw(d, entrance) : d;
        int offset = -Math.round((1f - entrance) * 10);
        home.pushScale(offset, 0, 1f);
        int mouseX = mx - offset;
        // the logo and name, on their own (a soft light behind the prism)
        float pulse = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2);
        home.glow(left - 4, logoY - 2, 26, 26, Theme.withAlpha(a, Math.round(0x30 + 0x20 * pulse)));
        lightMark(home, left + 1, logoY + 3, a);
        home.textScaled(BRAND, left + 26, logoY, Theme.TEXT, 1.6f, true);
        home.text("Minecraft " + p.minecraftVersion(), left + 27, logoY + 17, Theme.MUTED, true);

        if (card[2] > 0) drawCard(home, p, mouseX, my, dt, a);
        int i = 0;
        for (Button button : buttons) {   // the tiles come in one after another
            if (button.w <= 0) {
                i++;
                continue;
            }
            float in = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 60L * i++, 260);
            if (in < 1f) {
                Draw f = new FadeDraw(home, in);
                f.pushScale(-Math.round((1f - in) * 8), 0, 1f);
                drawAction(f, button, mouseX + Math.round((1f - in) * 8), my, dt, a);
                f.popScale();
            } else {
                drawAction(home, button, mouseX, my, dt, a);
            }
        }
        boolean measured = false;
        for (Button b : new Button[] {lockerButton, newsButton, small[0], small[1], small[2]}) {
            int real = home.width(b.label) + 30;
            if (!Integer.valueOf(real).equals(CHIP_W.get(b.label))) {
                CHIP_W.put(b.label, real);
                measured = true;
            }
        }
        if (measured) {
            layout(p);
        }
        if (lockerButton.w > 0) drawAction(home, lockerButton, mouseX, my, dt, a);
        if (newsButton.w > 0) drawAction(home, newsButton, mouseX, my, dt, a);
        for (Button button : small) if (button.w > 0) drawAction(home, button, mouseX, my, dt, a);
        drawEditLayer(home, p, mouseX, my, dt, a);
        home.popScale();

        if (panel[2] > 0) {
            int x = panel[0], y = panel[1], w = panel[2], h = panel[3];
            d.roundRect(x, y, w, h, 7, 0x720A101A);
            d.rect(x + 9, y + h - 15, w - 18, 1, Theme.withAlpha(a, 0x60));
            String name = d.trim(p.playerName(), w - 18);
            d.text(name, x + (w - d.width(name)) / 2, y + h - 11, Theme.MUTED, false);
        }
    }

    static void lightMark(Draw d, int x, int y, int accent) {
        d.texture("peregrine-client:icon.png", x, y, 18, 18, 0, 0, 1, 1, 0xFFFFFFFF);
    }

    /** A textured button that lifts on hover and sinks on press. */
    private void drawAction(Draw d, Button button, int mx, int my, float dt, int accent) {
        boolean hover = button.contains(mx, my);
        button.glow = Glass.ease(button.glow, hover, dt);
        float g = button.glow;
        int x = button.x, y = button.y - Math.round(g), w = button.w, h = button.h;
        boolean pressed = hover && pc.platform().mouseHeld() == 1;
        MenuStyle.button(d, x, y, w, h, button.primary, g, accent, pressed);
        y += pressed ? 1 : 0;
        if (g > 0.01f && !button.primary) {   // the accent bar grows in from the middle
            int bh = Math.round((h - 8) * g);
            d.rect(x + 1, y + (h - bh) / 2, 2, bh, Theme.withAlpha(accent, Math.round(0xFF * g)));
        }
        int fg = button.primary ? Theme.onColor(accent) : Theme.mix(0xFFDDE0E8, 0xFFFFFFFF, g);
        boolean iconOnly = w < 24;
        int iy = y + (h - 9) / 2;
        if (iconOnly) {
            MenuIcons.draw(d, button.icon, x + (w - 9) / 2, iy, fg);
            return;
        }
        MenuIcons.draw(d, button.icon, x + 7, iy, fg);
        boolean chevron = h >= 19 && w > 90;
        d.text(d.trim(button.label, w - (chevron ? 34 : 24)), x + 21 + Math.round(g * 2), y + (h - d.lineHeight()) / 2 + 1, fg, Theme.lightText(fg));
        if (chevron) {
            MenuIcons.draw(d, "chevron", x + w - 13 + Math.round(g * 2), iy,
                    Theme.withAlpha(button.primary ? 0xFFFFFFFF : accent, Math.round(0x60 + 0x9F * g)));
        }
    }

    // ------------------------------------------------------------------ the Locker

    private void layoutLocker(Platform p, Draw d, int sw, int sh) {
        cards.clear();
        int lx = lockerLeft(sw);
        backButton.place(lx, 12, 60, 18);
        int pw = Math.max(90, Math.min(130, sw / 5));
        int ph = Math.min(sh - 96, pw * 2);
        lockerPlayer = new int[] {lx, 58, pw, ph};
        int gx = lx + pw + 24;
        int gw = sw - gx - 14;
        // tabs
        int tx = gx;
        String[] tabs = TABS;
        for (int i = 0; i < tabs.length; i++) {
            int tw = 16 + (d == null ? tabs[i].length() * 6 : d.width(tabs[i]));
            cards.add(new int[] {2, i, tx, 36, tw, 16});
            tx += tw + 6;
        }
        int y0 = 62;
        int gh = sh - y0 - 8;
        if (lockerTab == 0) {
            grid(0, net.peregrine.client.core.modules.Cosmetics.shown(), 58, 82, 0, gx, y0, gw, gh);
            for (int[] c : cards) {   // places in the list -> the capes there
                if (c[0] == 0) {
                    c[1] = net.peregrine.client.core.modules.Cosmetics.atPosition(c[1]);
                }
            }
        } else if (lockerTab == 1) {
            grid(3, Math.min(skins.list(p).size(), skinGlow.length), 58, 92, 0, gx, y0, gw, gh);
        } else {
            grid(1, net.peregrine.client.core.modules.MainMenu.BACKGROUNDS.length, 150, 0, 14, gx, y0, gw, gh);
            if (customBackground() != null) {  // an "Adjust" button on your picture's card
                for (int[] c : new java.util.ArrayList<int[]>(cards)) {
                    if (c[0] == 1 && c[1] == net.peregrine.client.core.modules.MainMenu.CUSTOM) {
                        int bw = (d == null ? 36 : d.width("Adjust")) + 22;
                        cards.add(new int[] {4, c[1], c[2] + c[4] - bw - 7, c[3] + 7, bw, 14});
                    }
                }
            }
        }
    }

    /**
     * Lays n cards out in the space given, as big as fits (up to w0 x h0; h0 0 = a 16:9 picture
     * plus extra for the label).
     */
    private void grid(int kind, int n, int w0, int h0, int extra, int gx, int gy, int gw, int gh) {
        int gap = 8;
        if (n == 0) {
            return;
        }
        float best = 0;
        int bestCols = 1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            float fw = (gw - gap * (cols - 1)) / (float) cols / w0;
            int hh = h0 > 0 ? h0 : w0 * 9 / 16 + extra;
            float fh = (gh - gap * (rows - 1)) / (float) rows / hh;
            float f = Math.min(1f, Math.min(fw, fh));
            if (f > best + 0.001f) {
                best = f;
                bestCols = cols;
            }
        }
        int cw = Math.max(30, Math.round(w0 * best));
        int ch = h0 > 0 ? Math.max(40, Math.round(h0 * best)) : cw * 9 / 16 + extra;
        for (int i = 0; i < n; i++) {
            cards.add(new int[] {kind, i, gx + (i % bestCols) * (cw + gap), gy + (i / bestCols) * (ch + gap), cw, ch});
        }
    }

    private void drawLocker(Draw d, Platform p, int mx, int my, float dt, int a, int sw, int sh) {
        int[] lpw = lockerPlayer;
        wearButton.place(lpw[0], lpw[1] + lpw[3] + 18, lpw[2], H - 2);
        backButton.draw(d, mx, my, dt, a, true);
        d.textScaled("Locker", lockerLeft(p.screenWidth()) + 70, 13, Theme.TEXT, 1.6f, true);
        int[] lp = lockerPlayer;
        d.shadow(lp[0], lp[1], lp[2], lp[3]);
        d.roundRect(lp[0], lp[1], lp[2], lp[3], 4, 0x9C15171D);
        d.roundOutline(lp[0], lp[1], lp[2], lp[3], 4, 0x22FFFFFF);
        String hint = "Drag to turn";
        d.text(hint, lp[0] + (lp[2] - d.width(hint)) / 2, lp[1] + lp[3] + 6, Theme.FAINT, false);

        net.peregrine.client.core.modules.MainMenu menuMod = mainMenu();
        int worn = net.peregrine.client.core.modules.Cosmetics.capeIndex();
        int bg = menuMod == null ? 0 : menuMod.background.index;
        int order = 0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);
        for (int[] c : cards) {
            int kind = c[0], i = c[1], x = c[2], y = c[3], w = c[4], h = c[5];
            // cards arrive one after another, each rising into place
            float fade = kind == 2 ? 1f : Math.max(0f, Math.min(1f, (tabT * 1.8f - order++ * 0.09f) / 0.55f));
            fade = 1f - (1f - fade) * (1f - fade) * (1f - fade);
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            if (kind == 2) {
                tabGlow[i] = ease(tabGlow[i], hover || lockerTab == i, dt);
                boolean on = lockerTab == i;
                d.roundRect(x, y, w, h, 3, on ? Theme.mix(a, 0xFFFFFFFF, 0.1f * tabGlow[i]) : Theme.mix(0xC01C1C1C, 0xE02B2B2B, tabGlow[i]));
                String label = TABS[i];
                d.text(label, x + 8, y + 4, on ? Theme.onColor(a) : Theme.mix(Theme.MUTED, Theme.TEXT, tabGlow[i]), false);
                continue;
            }
            if (fade <= 0.01f) {
                continue;
            }
            Draw cd = fade < 1f ? new FadeDraw(d, fade) : d;
            int dy = Math.round((1f - fade) * 14);
            if (kind == 4) {
                adjustGlow = ease(adjustGlow, hover, dt);
                int by = y + dy - Math.round(bgGlow[i] * 2);
                cd.roundRect(x, by, w, h, 3, Theme.mix(0xD0101217, a, adjustGlow));
                cd.roundOutline(x, by, w, h, 3, Theme.withAlpha(a, 0x90 + Math.round(0x6F * adjustGlow)));
                int fg = Theme.mix(Theme.TEXT, Theme.onColor(a), adjustGlow);
                MenuIcons.draw(cd, "resize", x + 4, by + 3, fg);
                cd.text("Adjust", x + 16, by + 3, fg, false);
                continue;
            }
            if (kind == 3) {
                java.util.List<LockerSkins.Skin> list = skins.list(p);
                if (i >= list.size()) {
                    continue;
                }
                LockerSkins.Skin sk = list.get(i);
                skinGlow[i] = ease(skinGlow[i], hover, dt);
                float g = skinGlow[i];
                int cy = y + dy - Math.round(g * 2);
                cd.shadow(x, cy, w, h);
                cd.roundRect(x, cy, w, h, 4, Theme.mix(0xC01C1C1C, 0xE02B2B2B, g));
                if (trying == i) {
                    cd.roundOutline(x, cy, w, h, 4, a);
                } else if (g > 0) {
                    cd.roundOutline(x, cy, w, h, 4, Theme.withAlpha(a, Math.round(0x90 * g)));
                }
                String tex = skins.texture(p, sk);
                if (tex != null) {
                    PlayerPreview.drawFront(cd, tex, sk.slim, sk.legacy, x + 4, cy + 5, w - 8, h - 32);
                }
                String n = cd.trim(sk.name, w - 6);
                cd.text(n, x + (w - cd.width(n)) / 2, cy + h - 22, Theme.TEXT, false);
                String sub = sk.slim ? "Slim" : "Classic";
                cd.text(sub, x + (w - cd.width(sub)) / 2, cy + h - 11, Theme.FAINT, false);
                continue;
            }
            float[] glow = kind == 0 ? capeGlow : bgGlow;
            boolean picked = kind == 0 ? i == worn : i == bg;
            glow[i] = ease(glow[i], hover, dt);
            float g = glow[i];
            int lift = Math.round(g * 2);
            int cy = y + dy - lift;
            cd.shadow(x, cy, w, h);
            cd.roundRect(x, cy, w, h, 4, Theme.mix(0xC01C1C1C, 0xE02B2B2B, g));
            if (picked) {  // what you wear: a ring that breathes
                cd.roundOutline(x - 1, cy - 1, w + 2, h + 2, 5, Theme.withAlpha(a, Math.round(0x30 + 0x50 * pulse)));
                cd.roundOutline(x, cy, w, h, 4, a);
                cd.roundOutline(x + 1, cy + 1, w - 2, h - 2, 3, Theme.withAlpha(a, 0x70));
            } else if (g > 0) {
                cd.roundOutline(x, cy, w, h, 4, Theme.withAlpha(a, Math.round(0x90 * g)));
            }
            if (kind == 0) {
                String file = net.peregrine.client.core.modules.Cosmetics.FILES[i];
                int ph = Math.max(16, h - 44), pw = ph * 10 / 16;
                int px = x + (w - pw) / 2, py = cy + 8;
                String capeTex = net.peregrine.client.core.modules.Cosmetics.textureId(file);
                if (capeTex != null) {
                    // the outside of the cape: 10x16 pixels at (1, 1) of its 64x32 layout
                    cd.texture(capeTex, px, py, pw, ph,
                            1f / 64, 1f / 32, 11f / 64, 17f / 32, 0xFFFFFFFF);
                } else {
                    cd.roundOutline(px, py, pw, ph, 2, 0x40FFFFFF);
                    cd.text("-", px + pw / 2 - 2, py + ph / 2 - 4, Theme.FAINT, false);
                }
                String name = net.peregrine.client.core.modules.Cosmetics.CAPES[i];
                String n = cd.trim(name, w - 6);
                cd.text(n, x + (w - cd.width(n)) / 2, cy + h - 22, Theme.TEXT, false);
                String sub = picked ? "Wearing" : net.peregrine.client.core.modules.Cosmetics.locked(i) ? "Locked" : i == net.peregrine.client.core.modules.Cosmetics.CUSTOM ? "Only you" : "Owned";
                cd.text(sub, x + (w - cd.width(sub)) / 2, cy + h - 11, picked ? a : Theme.FAINT, false);
            } else {
                int iw = w - 8, ih = iw * 9 / 16;
                int ix = x + 4, iy = cy + 4;
                String id = backgroundTexture(i);
                if (id != null) {
                    float[] uv = cover(i == net.peregrine.client.core.modules.MainMenu.CUSTOM ? customW : 16,
                            i == net.peregrine.client.core.modules.MainMenu.CUSTOM ? customH : 9, iw, ih);
                    cd.texture(id, ix, iy, iw, ih, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF);
                } else if (i == 0) {
                    cd.rect(ix, iy, iw, ih, 0xFF2B3A55);
                    cd.rect(ix, iy + ih * 2 / 3, iw, ih / 3, 0xFF3D5A3A);
                    MenuIcons.draw(cd, "globe", ix + iw / 2 - 4, iy + ih / 2 - 6, Theme.TEXT);
                } else {
                    cd.roundOutline(ix, iy, iw, ih, 2, 0x40FFFFFF);
                    MenuIcons.draw(cd, "image", ix + iw / 2 - 4, iy + ih / 2 - 8, Theme.MUTED);
                    String tip = "Pick one in the launcher";
                    cd.text(cd.trim(tip, iw - 4), ix + (iw - Math.min(iw - 4, cd.width(tip))) / 2, iy + ih / 2 + 4, Theme.FAINT, false);
                }
                String name = net.peregrine.client.core.modules.MainMenu.BACKGROUNDS[i];
                cd.text(cd.trim(name, w - 20), x + 5, cy + h - 11, Theme.TEXT, false);
                if (picked) {
                    MenuIcons.draw(cd, "check", x + w - 13, cy + h - 12, a);
                }
            }
        }
    }

    private void drawLockerExtras(Draw d, Platform p, int mx, int my, float dt, int a) {
        if (lockerTab == 1 && skins.list(p).isEmpty()) {
            int gx = lockerPlayer[0] + lockerPlayer[2] + 24;
            d.text("No skins yet.", gx, 66, Theme.TEXT, true);
            d.text("Add some in the launcher's Locker (Skins tab),", gx, 80, Theme.MUTED, false);
            d.text("then wear them right here.", gx, 91, Theme.MUTED, false);
        }
        if (trying >= 0) {
            wearButton.draw(d, mx, my, dt, a, false);
        }
        String st = skins.status;
        if (st != null && !st.isEmpty()) {
            int[] lp = lockerPlayer;
            String t = d.trim(st, Math.max(lp[2] + 120, 200));
            d.text(t, lp[0], lp[1] + lp[3] + 42, skins.busy ? Theme.MUTED : Theme.TEXT, false);
        }
    }

    private static float ease(float v, boolean on, float dt) {
        return on ? Math.min(1f, v + dt * 9f) : Math.max(0f, v - dt * 6f);
    }

    private net.peregrine.client.core.modules.MainMenu mainMenu() {
        Module m = pc.module("main_menu");
        return m instanceof net.peregrine.client.core.modules.MainMenu ? (net.peregrine.client.core.modules.MainMenu) m : null;
    }

    /** Where the adapter should draw your player this frame (screen GUI pixels), or null. It glides
     *  from the menu's panel to the Locker as that opens. */
    public int[] playerBox() {
        float e = lockerT * lockerT * (3 - 2 * lockerT);
        int[] from = panel[2] > 0 ? new int[] {panel[0] + 8, panel[1] + 8, panel[2] - 16, panel[3] - 52} : null;
        int[] to = new int[] {lockerPlayer[0] + 6, lockerPlayer[1] + 6, lockerPlayer[2] - 12, lockerPlayer[3] - 12};
        if (from == null) {
            return e > 0.5f ? to : null;
        }
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) {
            r[i] = Math.round(from[i] + (to[i] - from[i]) * e);
        }
        return r;
    }

    /** For the self-test: which skin card is being tried on (-1 none). */
    public int tryingSkin() {
        return trying;
    }

    public boolean lockerOpen() {
        return lockerOpen;
    }

    /** Turning your player by dragging it. */
    public boolean mouseDragged(int mx, int my) {
        if (editor.isOpen()) {
            return editor.mouseDragged(pc, mainMenu(), mx, my);
        }
        if (editing) {
            return editDrag(mx, my);
        }
        if (pressedPlayer) {
            preview.drag(mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mx, int my) {
        if (editor.isOpen()) {
            return editor.mouseReleased(pc);
        }
        if (editing) {
            if (dragId != null) {
                dragId = null;
                layout.save();
            }
            return true;
        }
        if (!pressedPlayer) {
            return false;
        }
        pressedPlayer = false;
        preview.release();
        if (Math.abs(mx - pressX) < 3) {
            preview.spin();  // a click: spin round
        }
        return true;
    }

    /** Esc in the Locker goes back to the menu. */
    public boolean mouseScrolled(int mx, int my, double amount) {
        return editor.isOpen() && editor.mouseScrolled(pc, mainMenu(), mx, my, amount);
    }

    /** For the self-test: the picture editor. */
    public boolean editingBackground() {
        return editor.isOpen();
    }

    public int[] editorButton(int i) {
        return editor.barButton(i);
    }

    public int[] editorCorner(int i) {
        return editor.corner(i);
    }

    public boolean keyPressed(int key) {
        if (key == Menu.KEY_ESCAPE && editing) {
            stopEditing();
            return true;
        }
        if (key == Menu.KEY_ESCAPE && editor.isOpen()) {
            editor.close(pc);
            return true;
        }
        if (key == Menu.KEY_ESCAPE && lockerOpen) {
            lockerOpen = false;
            trying = -1;
            preview.stopTrying();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ backgrounds

    /** True when a picture replaces Minecraft's panorama (so the adapter doesn't draw it). */
    public boolean drawsBackground() {
        net.peregrine.client.core.modules.MainMenu m = mainMenu();
        return m != null && m.background.index != 0 && backgroundTexture(m.background.index) != null;
    }

    private String backgroundTexture(int index) {
        if (index > 0 && index < net.peregrine.client.core.modules.MainMenu.FILES.length
                && net.peregrine.client.core.modules.MainMenu.FILES[index] != null) {
            return "peregrine-client:textures/background/" + net.peregrine.client.core.modules.MainMenu.FILES[index] + ".png";
        }
        if (index == net.peregrine.client.core.modules.MainMenu.CUSTOM) {
            return customBackground();
        }
        return null;
    }

    /** Your own picture: client/backgrounds/custom.png in Peregrine's data folder (the launcher puts it there). */
    public static java.nio.file.Path customBackgroundFile() {
        String data = System.getProperty("peregrine.data");
        java.nio.file.Path base = data != null && !data.isEmpty() ? java.nio.file.Paths.get(data, "client")
                : Peregrine.get().platform().configFile().getParent();
        return base.resolve("backgrounds").resolve("custom.png");
    }

    private String customBackground() {
        long now = System.currentTimeMillis();
        if (now - customCheckedAt < 2000 && customStamp != -1) {
            return customId;
        }
        customCheckedAt = now;
        java.nio.file.Path f = customBackgroundFile();
        long stamp;
        try {
            stamp = java.nio.file.Files.isRegularFile(f) ? java.nio.file.Files.getLastModifiedTime(f).toMillis() : 0;
        } catch (java.io.IOException e) {
            stamp = 0;
        }
        if (stamp != customStamp) {
            customStamp = stamp;
            customId = null;
            if (stamp != 0) {
                int[] size = pngSize(f);
                if (size != null) {
                    customW = size[0];
                    customH = size[1];
                    customId = pc.platform().loadImage(f);
                }
            }
        }
        return customId;
    }

    static int[] pngSize(java.nio.file.Path f) {
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(f)) {
            byte[] b = new byte[24];
            int n = 0;
            while (n < 24) {
                int r = in.read(b, n, 24 - n);
                if (r < 0) {
                    return null;
                }
                n += r;
            }
            if ((b[0] & 0xFF) != 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
                return null;
            }
            int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
            int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
            return w > 0 && h > 0 ? new int[] {w, h} : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** The part of a tw x th picture that fills a w x h box without stretching: {u0, v0, u1, v1}. */
    static float[] cover(int tw, int th, int w, int h) {
        float ta = tw / (float) th, ba = w / (float) Math.max(1, h);
        if (ba > ta) {
            float vh = ta / ba;
            return new float[] {0f, (1f - vh) / 2, 1f, (1f + vh) / 2};
        }
        float uw = ba / ta;
        return new float[] {(1f - uw) / 2, 0f, (1f + uw) / 2, 1f};
    }

    /**
     * Where a tw x th picture goes: zoom 1 just covers the screen; cx, cy is where its middle
     * sits, as a fraction of the screen. Returns {x, y, w, h}.
     */
    public static float[] placement(int tw, int th, int sw, int sh, float zoom, float cx, float cy) {
        float s = Math.max(sw / (float) Math.max(1, tw), sh / (float) Math.max(1, th)) * zoom;
        float w = tw * s, h = th * s;
        return new float[] {cx * sw - w / 2, cy * sh - h / 2, w, h};
    }

    /** Draws the part of a picture placed at r that's on screen (nothing outside it). */
    static void drawPlaced(Draw d, String id, float[] r, int sw, int sh, int tint) {
        float x0 = Math.max(0, r[0]), y0 = Math.max(0, r[1]);
        float x1 = Math.min(sw, r[0] + r[2]), y1 = Math.min(sh, r[1] + r[3]);
        if (x1 - x0 < 0.5f || y1 - y0 < 0.5f) {
            return;
        }
        int ix0 = Math.round(x0), iy0 = Math.round(y0), ix1 = Math.round(x1), iy1 = Math.round(y1);
        d.texture(id, ix0, iy0, ix1 - ix0, iy1 - iy0, (ix0 - r[0]) / r[2], (iy0 - r[1]) / r[3],
                (ix1 - r[0]) / r[2], (iy1 - r[1]) / r[3], tint);
    }

    /** Your picture's size in pixels (16 x 9 until it's loaded). */
    int[] customSize() {
        return new int[] {customW, customH};
    }

    String customTexture() {
        return customBackground();
    }

    private void drawBackground(Draw d, int sw, int sh, long now) {
        net.peregrine.client.core.modules.MainMenu m = mainMenu();
        if (m == null || m.background.index == 0) {
            return;
        }
        String id = backgroundTexture(m.background.index);
        if (id == null) {
            return;
        }
        boolean custom = m.background.index == net.peregrine.client.core.modules.MainMenu.CUSTOM;
        int tw = custom ? customW : 16, th = custom ? customH : 9;
        float zoom = custom ? editor.zoom(m) : 1f, cx = custom ? editor.cx(m) : 0.5f, cy = custom ? editor.cy(m) : 0.5f;
        float[] r = placement(tw, th, sw, sh, zoom, cx, cy);
        boolean editing = editor.active();
        if (m.drift.value && !editing) {
            // a slow drift so the picture feels alive (like the panorama turning)
            float t = (now % 120000L) / 120000f * (float) (Math.PI * 2);
            float grow = 1.04f;
            float gw = r[2] * (grow - 1f), gh = r[3] * (grow - 1f);
            r = new float[] {r[0] - gw / 2 + (float) Math.sin(t) * gw / 2, r[1] - gh / 2 + (float) Math.cos(t * 0.7f) * gh / 2,
                    r[2] + gw, r[3] + gh};
        }
        if (r[0] > 0.5f || r[1] > 0.5f || r[0] + r[2] < sw - 0.5f || r[1] + r[3] < sh - 0.5f) {
            // the picture doesn't reach the edges: a dark, softened copy fills in behind it
            d.rect(0, 0, sw, sh, 0xFF0B0D11);
            float[] behind = placement(tw, th, sw, sh, 1.15f, 0.5f, 0.5f);
            drawPlaced(d, id, behind, sw, sh, 0xFF3A3A3A);
            d.rect(0, 0, sw, sh, 0x50000000);
            d.shadow(Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]));
        }
        drawPlaced(d, id, r, sw, sh, 0xFFFFFFFF);
        if (m.dim.value > 0) {
            d.rect(0, 0, sw, sh, Theme.withAlpha(0, Math.round(m.dim.value / 100f * 255)));
        }
        editor.lastPlacement = r;
    }

    private void drawCard(Draw d, Platform p, int mx, int my, float dt, int a) {
        int x = card[0], y = card[1], w = card[2], h = card[3];
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        cardGlow = hover ? Math.min(1f, cardGlow + dt * 9f) : Math.max(0f, cardGlow - dt * 6f);
        d.shadow(x, y, w, h);
        d.roundRect(x, y, w, h, 5, Theme.mix(0xB8181818, 0xD8262626, cardGlow));
        d.roundOutline(x, y, w, h, 5, Theme.mix(Theme.withAlpha(a, 0x70), Theme.withAlpha(a, 0xE0), cardGlow));
        int[] icon = ServerIcons.get(pc.lastServerAddress(), null);
        int is = Math.min(26, h - 8), ix = x + 5, iy = y + (h - is) / 2;
        if (icon != null) {
            d.pushScale(ix, iy, is / (float) ServerIcons.CELLS);
            for (int i = 0; i < icon.length; i++) {
                if ((icon[i] >>> 24) != 0) {
                    d.rect(i % ServerIcons.CELLS, i / ServerIcons.CELLS, 1, 1, icon[i]);
                }
            }
            d.popScale();
        } else {
            d.roundRect(ix, iy, is, is, 3, Theme.withAlpha(a, 0x60));
            MenuIcons.draw(d, "globe", ix + 7, iy + 7, Theme.TEXT);
        }
        int tx = ix + is + 7;
        boolean roomy = h >= 34;
        int textTop = y + (h - (roomy ? 30 : 21)) / 2;
        d.text("Jump back in", tx, textTop, Theme.withAlpha(a, 0xE0), false);
        d.text(d.trim(pc.lastServerName(), x + w - 22 - tx), tx, textTop + 10, Theme.TEXT, true);
        String addr = pc.lastServerAddress();
        if (roomy && addr != null && !addr.equals(pc.lastServerName())) {
            d.text(d.trim(addr, x + w - 22 - tx), tx, textTop + 20, Theme.MUTED, false);
        }
        // a play mark on the right
        int px = x + w - 15 + Math.round(cardGlow * 2), py = y + h / 2;
        for (int r = 0; r < 7; r++) {
            int half = r < 4 ? r : 6 - r;
            d.rect(px, py - 3 + r, half + 1, 1, Theme.mix(a, 0xFFFFFFFF, cardGlow * 0.4f));
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        SelfTest.count("titleclick@" + mx + "," + my + "/" + button);
        if (button != 0) {
            return false;
        }
        if (UpdateNotice.click(mx, my)) {
            return true;
        }
        if (editor.isOpen()) {
            return editor.mouseClicked(pc, mainMenu(), mx, my);
        }
        Platform p = pc.platform();
        layout(p);
        if (editing) {
            return editClick(mx, my);
        }
        if (!lockerOpen) {
            placeEditChip(p.screenWidth());
            if (editChip.contains(mx, my)) {
                editing = true;
                return true;
            }
        }
        int[] box = playerBox();
        if (box != null && mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
            preview.press(mx);
            pressedPlayer = true;
            pressX = mx;
            return true;
        }
        if (lockerOpen) {
            layoutLocker(p, null, p.screenWidth(), p.screenHeight());
            if (backButton.contains(mx, my)) {
                lockerOpen = false;
                trying = -1;
                preview.stopTrying();
                return true;
            }
            if (trying >= 0 && wearButton.contains(mx, my)) {
                java.util.List<LockerSkins.Skin> list = skins.list(p);
                if (trying < list.size()) {
                    skins.wear(p, list.get(trying), new Runnable() {
                        public void run() {
                            trying = -1;
                            preview.stopTrying();
                            preview.reload();
                        }
                    });
                }
                return true;
            }
            for (int[] c : cards) {
                if (c[0] == 4 && mx >= c[2] && mx < c[2] + c[4] && my >= c[3] && my < c[3] + c[5]) {
                    net.peregrine.client.core.modules.MainMenu m = mainMenu();
                    if (m != null) {
                        m.background.index = net.peregrine.client.core.modules.MainMenu.CUSTOM;
                        pc.markDirty();
                        editor.open();
                    }
                    return true;
                }
            }
            for (int[] c : cards) {
                if (mx >= c[2] && mx < c[2] + c[4] && my >= c[3] && my < c[3] + c[5]) {
                    if (c[0] == 2) {
                        if (lockerTab != c[1]) {
                            lockerTab = c[1];
                            tabT = 0f;
                        }
                    } else if (c[0] == 3) {
                        java.util.List<LockerSkins.Skin> list = skins.list(p);
                        if (trying == c[1] || c[1] >= list.size()) {
                            trying = -1;
                            preview.stopTrying();
                        } else {
                            LockerSkins.Skin sk = list.get(c[1]);
                            String tex = skins.texture(p, sk);
                            if (tex != null) {
                                trying = c[1];
                                preview.tryOn(tex, sk.slim, sk.legacy);
                                skins.status = "Trying on " + sk.name;
                            }
                        }
                    } else if (c[0] == 0) {
                        net.peregrine.client.core.modules.Cosmetics.setCape(c[1]);
                        if (c[1] > 0) {
                            preview.showBack();
                        }
                    } else {
                        net.peregrine.client.core.modules.MainMenu m = mainMenu();
                        if (m != null) {
                            m.background.index = c[1];
                            pc.markDirty();
                        }
                    }
                    return true;
                }
            }
            return false;
        }
        if (lockerButton.contains(mx, my)) {
            lockerOpen = true;
            tabT = 0f;
            return true;
        }
        if (newsButton.contains(mx, my)) {
            pc.menu().choose("news");
            p.openScreen(Platform.Screen.PEREGRINE_MENU);
            return true;
        }
        for (Button b : buttons) {
            if (b.contains(mx, my)) {
                p.openScreen(b.action);
                return true;
            }
        }
        for (Button b : small) {
            if (b.contains(mx, my)) {
                p.openScreen(b.action);
                return true;
            }
        }
        if (card[2] > 0 && mx >= card[0] && mx < card[0] + card[2] && my >= card[1] && my < card[1] + card[3]) {
            p.joinServer(pc.lastServerAddress(), pc.lastServerName());
            return true;
        }
        return false;
    }

    /** For the self-test: centre of the Locker button (or the back button while the Locker is open). */
    public int[] lockerButtonCenter() {
        layout(pc.platform());
        Button b = lockerOpen ? backButton : lockerButton;
        return new int[] {b.x + b.w / 2, b.y + b.h / 2};
    }

    /** For the self-test: centre of a Locker card (kind 0 cape, 1 background, 2 tab). */
    public int[] lockerCardCenter(int kind, int index) {
        Platform p = pc.platform();
        layout(p);
        layoutLocker(p, null, p.screenWidth(), p.screenHeight());
        for (int[] c : cards) {
            if (c[0] == kind && c[1] == index) {
                return new int[] {c[2] + c[4] / 2, c[3] + c[5] / 2};
            }
        }
        return null;
    }

    /** For the self-test: centre of the button that opens that screen, in GUI pixels. */
    public int[] centerOf(Platform.Screen action) {
        layout(pc.platform());
        for (Button[] row : new Button[][] {buttons, small}) {
            for (Button b : row) {
                if (b.action == action) {
                    return new int[] {b.x + b.w / 2, b.y + b.h / 2};
                }
            }
        }
        return null;
    }

    /** Whether to replace Minecraft's title screen (the "Peregrine main menu" toggle). */
    public boolean enabled() {
        Module m = pc.module("main_menu");
        return m == null || m.enabled();
    }
}
