package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.ColorSetting;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.PixelGridSetting;
import net.peregrine.client.core.settings.PlayerListSetting;
import net.peregrine.client.core.settings.Setting;
import net.peregrine.client.core.settings.SliderSetting;
import net.peregrine.client.core.settings.TextSetting;

/**
 * The in-game menu (Right Shift): a compact window in the middle of the screen
 * with categories down the side, a search box, and a list of features with
 * switches. Also the HUD editor, where HUD items are dragged around.
 *
 * Version adapters just forward render/mouse/key events here.
 */
public final class Menu {

    // GLFW key codes. Adapters for other input systems translate to these.
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_BACKSPACE = 259;

    private static final int TITLE_H = 30;
    private static final int TABBAR_H = 16;
    private static final int SIDE_W = 124;
    private static final int TILE_GAP = 4;
    private static final int SIDE_ROW = 15;

    /** The old tab names, still understood by the self-test ("view:Settings"...). */
    private static final String[] VIEWS = {"Mods", "Settings", "Keybinds"};
    private static final int CARD_H = 50;
    private static final int GAP = 5;
    private static final int TAB_H = 18;
    private static final Module.Category[] TABS = {
        null, Module.Category.HUD, Module.Category.UTILITY, Module.Category.VISUALS, Module.Category.PERFORMANCE
    };

    private final Peregrine pc;
    private String search = "";
    private boolean searchFocused;
    private int windowX = -1, windowY = -1;
    private int windowW, windowH;
    private int dragX, dragY;
    private boolean movingWindow, resizingWindow;
    private int resizeEdges, startX, startY, startW, startH;   // edge bits: 1 left, 2 right, 4 top, 8 bottom
    private String group;         // the tab along the top of the modules list (null: all)
    private int chipScroll, chipMax;
    private final List<Hit> chipHits = new ArrayList<Hit>();
    private int onlyX, onlyW, filterY;
    private float lineX, lineW;
    private int selX, selW;
    private int scroll;
    private float smoothScroll;
    private int maxScroll;
    private boolean editingHud;
    private final HudEditor editor;
    private float k = 1f;        // the menu is drawn at this fraction of the GUI scale, for finer detail
    private int lastMx;  // mouse position from the last frame (screen pixels)
    private long openedAt;
    private long lastFrame;
    private final Map<Module, Float> knob = new IdentityHashMap<Module, Float>();
    private final Map<Module, Float> rowGlow = new IdentityHashMap<Module, Float>();
    private float tabSlide = -1;  // where the category highlight is, sliding to the chosen one
    private Module page;          // the feature whose settings page is open, or null for the list
    private long pageOpenedAt;
    private SliderSetting draggingSlider;
    private int dragClickedAt, dragEventAt, dragSeenAt;   // for the log line when a slider is let go
    private TextSetting typing;        // the text box being typed into
    private KeySetting bindingKey;     // a key setting waiting for a key
    private final Map<Setting, Float> settingKnob = new IdentityHashMap<Setting, Float>();
    private Module binding;       // waiting for a key for this feature's keybind
    private long boundAt;         // when a key was just bound (its typed character is swallowed)
    private int cols = 2;
    private int tileW = 80, tileH = 76;
    private long listShownAt;
    private int view;             // 0 modules, 1 settings, 2 keybinds, 3 a panel (mods, shaders, packs, locker, friends)
    private String section = "cat:all";  // what's chosen in the sidebar
    private String expanded = "modules"; // the sidebar group that's open (one at a time)
    private final Map<String, Float> expand = new java.util.HashMap<String, Float>();
    private final Map<String, Float> sideGlow = new java.util.HashMap<String, Float>();
    private final Map<String, MenuPanel> panels = new java.util.HashMap<String, MenuPanel>();
    private final List<Hit> sideHits = new ArrayList<Hit>();
    private float sideSlide = -1;
    private int sideX, sideY, sideW, sideH;
    private int sideScroll, maxSideScroll;
    private boolean onlyOn;       // the ≡ button: show only what's switched on
    private final ColorPicker picker = new ColorPicker();
    private String listSearch = "";
    private final Map<HudModule, List<Setting>> styleRows = new IdentityHashMap<HudModule, List<Setting>>();
    /** "All HUD items": a page whose look applies to every HUD item at once. */
    private final HudModule allHud = new HudModule("all_hud", "All HUD items",
            "Applies to every HUD item", false, 0f, 0f) {
        @Override
        public int width(Draw d, Platform p) {
            return 0;
        }

        @Override
        public int height(Draw d, Platform p) {
            return 0;
        }

        @Override
        public void render(Draw d, Platform p, int x, int y) {
        }
    };
    private int tabsY, tabX0, tabW, menuBtnX;

    /** Something clickable on a settings page, from the last frame (menu pixels). */
    private static final class Hit {
        final String id;
        final int x, y, w, h;
        final Setting setting;

        Hit(String id, int x, int y, int w, int h, Setting setting) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.setting = setting;
        }
    }

    private final List<Hit> hits = new ArrayList<Hit>();

    // Layout from the last render, used for clicks.
    private int px, py, pw, ph, listX, listY, listW, listH;
    private int editX, editY, editW, editH;
    private int doneX, doneY, doneW, doneH;
    private int closeX, closeY;
    private int searchX, searchY, searchW;

    Menu(Peregrine pc) {
        this.pc = pc;
        this.editor = new HudEditor(pc);
    }

    /**
     * How much smaller than Minecraft's GUI the menu is drawn. At GUI scale 3 or
     * more it uses one step down (e.g. 3 -> 2), which keeps the pixel font sharp
     * while making text and buttons smaller and tidier.
     */
    static float uiScale(Platform p) {
        long g = Math.round(p.guiScale());
        return g >= 3 ? (g - 1f) / g : 1f;
    }

    private int s(int ui) {
        return Math.round(ui * k);
    }

    /** Call when the menu screen opens. */
    public void open() {
        openedAt = System.currentTimeMillis();
        windowX = windowY = -1;   // always opens in the middle (a size you dragged it to is kept)
        listShownAt = openedAt;
        binding = null;
        lastFrame = 0;
        search = "";
        scroll = 0;
        smoothScroll = 0;
        editingHud = false;
        editor.open();
        knob.clear();
        rowGlow.clear();
        tabSlide = -1;
        page = null;
        listSearch = "";
        searchFocused = false;
        typing = null;
        bindingKey = null;
        picker.close();
        dropdown = null;
        draggingSlider = null;
        hits.clear();
        sideSlide = -1;
        sideScroll = 0;
        MenuPanel pn = panel();
        if (pn != null) {
            pn.shown();
            pn.searchChanged("");
        }
    }

    // ------------------------------------------------------------ the sidebar

    /** One line of the sidebar: a group (with children that drop down) or a page. */
    private static final class Node {
        final String id, label, icon;
        final Node[] kids;

        Node(String id, String label, String icon, Node... kids) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.kids = kids;
        }
    }

    private static final Node[] SIDEBAR = {
        new Node("modules", "Modules", "grid"),
        new Node("performance", "Performance", "bolt"),
        new Node("mods", "Mods", "puzzle", new Node("mods:installed", "Installed", null), new Node("mods:browse", "Browse", null)),
        new Node("shaders", "Shaders", "sun", new Node("shaders:installed", "Installed", null), new Node("shaders:browse", "Browse", null)),
        new Node("packs", "Resource packs", "layers", new Node("packs:installed", "Installed", null), new Node("packs:browse", "Browse", null)),
        new Node("schematics", "Schematics", "blueprint"),
        new Node("locker", "Locker", "shirt"),
        new Node("friends", "Friends", "people"),
        new Node("voice", "Voice chat", "mic"),
        new Node("news", "What's new", "layers"),
        new Node("keybinds", "Keybinds", "keyboard"),
        new Node("settings", "Settings", "gear"),
    };

    /** The page shown for a sidebar choice (null for modules, settings and keybinds). */
    private MenuPanel panel() {
        if (view != 3) {
            return null;
        }
        MenuPanel pn = panels.get(section);
        if (pn == null) {
            if (section.equals("performance") || section.equals("news")) {
                pn = new LightPanel(pc, this, section.equals("performance"));
            } else if (section.equals("locker")) {
                pn = new LockerPanel(pc, this);
            } else if (section.equals("friends")) {
                pn = new FriendsPanel(pc, this);
            } else if (section.equals("schematics")) {
                pn = new SchematicsPanel(pc, this);
            } else if (section.equals("voice")) {
                pn = new VoicePanel(pc, this);
            } else if (section.equals("reaction")) {
                pn = new ReactionPanel(pc, this);
            } else {
                String[] parts = section.split(":");
                Library.Kind kind = parts[0].equals("mods") ? Library.Kind.MODS
                        : parts[0].equals("shaders") ? Library.Kind.SHADERS : Library.Kind.PACKS;
                pn = new LibraryPanel(pc, this, kind, parts.length > 1 && parts[1].equals("browse"));
            }
            panels.put(section, pn);
            pn.shown();
        }
        return pn;
    }

    /** Opens the reaction test (from the crosshair's settings). */
    public void showReactionTest() {
        choose("reaction");
    }

    /** Chooses something in the sidebar. */
    void choose(String id) {
        picker.close();
        dropdown = null;
        if (page != null) {
            closePage();
        }
        if (id.equals("modules")) {
            id = "cat:all";
        }
        if (id.startsWith("cat:")) {   // old names for the tabs: cat:all, cat:HUD, cat:PERFORMANCE
            String c = id.substring(4);
            group = c.equals("HUD") ? ModuleGroups.HUD : c.equals("PERFORMANCE") ? ModuleGroups.PERF : null;
            id = "cat:all";
        }
        boolean changed = !id.equals(section);
        section = id;
        if (id.equals("cat:all")) {
            view = 0;
            expanded = "";
        } else if (id.equals("settings")) {
            view = 1;
        } else if (id.equals("keybinds")) {
            view = 2;
        } else {
            view = 3;
            int colon = id.indexOf(':');
            if (colon > 0) {
                expanded = id.substring(0, colon);
            }
        }
        scroll = 0;
        smoothScroll = 0;
        hits.clear();
        if (changed) {
            listShownAt = System.currentTimeMillis();
            if (view == 0) {
                search = listSearch;
            } else {
                search = "";
            }
            searchFocused = false;
            MenuPanel pn = panel();
            if (pn != null) {
                pn.shown();
                pn.searchChanged("");
            }
        }
    }

    /** A group's header was clicked: open it (choosing its first page), or fold it away again. */
    private void toggleGroup(Node n) {
        if (n.id.equals("modules") && section.startsWith("cat:") && !section.equals("cat:all")) {
            choose("cat:all");  // from HUD (or another category) to all of them
            expanded = n.id;
            return;
        }
        boolean inside = n.id.equals("modules") && section.equals("cat:all");
        for (Node k : n.kids) {
            inside |= k.id.equals(section);
        }
        if (inside && n.id.equals(expanded)) {  // clicked again: fold it away
            expanded = "";
            return;
        }
        if (!inside) {
            choose(n.id.equals("modules") ? "cat:all" : n.kids[0].id);
        }
        expanded = n.id;
    }

    // ---- for the menu's pages (MenuPanel)

    void clipArea(Draw d, int x, int y, int w, int h) {
        d.popScale();
        d.clip(s(x), s(y), s(w), s(h));
        d.pushScale(0, 0, k);
    }

    void unclipArea(Draw d) {
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
    }

    void scrollbar(Draw d, int top, int viewH, int scrollNow, int max, float smooth, int a) {
        scrollbar(d, top, viewH, max, smooth, a, null);
    }

    // The scrollbar drawn this frame, so it can be grabbed and dragged (or its track clicked).
    private int sbX = -1, sbTop, sbTrack, sbBarH, sbMax;
    private java.util.function.IntConsumer sbSet;
    private boolean sbDragging;
    private int sbGrab, guiMx, guiMy;
    private float sbGlow;

    /** A scrollbar down the right edge; with set, it can be dragged and its track clicked. */
    void scrollbar(Draw d, int top, int viewH, int max, float smooth, int a, java.util.function.IntConsumer set) {
        if (max <= 0) {
            return;
        }
        int track = viewH - 4;
        int barH = Math.max(18, track * viewH / (viewH + max));
        int barY = top + 2 + Math.round((track - barH) * (Math.max(0f, Math.min(smooth, max)) / max));
        int x = px + pw - 8;
        if (set != null) {
            sbSmooth = smooth;
        }
        boolean hover = set != null && (sbDragging || in(guiMx, guiMy, x - 4, top, 12, viewH));
        sbGlow += ((hover ? 1f : 0f) - sbGlow) * 0.3f;
        int w = 3 + Math.round(2 * sbGlow);
        int bx = x + 2 - w / 2;
        d.roundRect(bx, top + 2, w, track, 1, Theme.mix(0x14FFFFFF, 0x26FFFFFF, sbGlow));
        d.roundRect(bx, barY, w, barH, 1, sbDragging ? a : Theme.withAlpha(a, 0xB0 + Math.round(0x4F * sbGlow)));
        if (set != null) {
            sbX = x;
            sbTop = top + 2;
            sbTrack = track;
            sbBarH = barH;
            sbMax = max;
            sbSet = set;
        }
    }

    /** Where the pointer puts the scroll while dragging the scrollbar. */
    private void sbFollow(int my) {
        if (sbSet == null || sbTrack <= sbBarH) {
            return;
        }
        float t = (my - sbGrab - sbTop) / (float) (sbTrack - sbBarH);
        sbSet.accept(Math.round(Math.max(0f, Math.min(1f, t)) * sbMax));
    }

    /** A click on the scrollbar: grab the thumb, or jump there and grab it. */
    private boolean sbClicked(int mx, int my) {
        if (sbX < 0 || sbSet == null || !in(mx, my, sbX - 4, sbTop - 2, 12, sbTrack + 4)) {
            return false;
        }
        int barY = sbTop + Math.round((sbTrack - sbBarH) * (Math.max(0f, Math.min(smoothNow(), sbMax)) / sbMax));
        sbGrab = my >= barY && my < barY + sbBarH ? my - barY : sbBarH / 2;
        sbDragging = true;
        sbFollow(my);
        return true;
    }

    private float sbSmooth;

    private float smoothNow() {
        return sbSmooth;
    }

    /** Opens a feature's settings page (the gear on its row, or right-click). */
    void openPage(Module m) {
        if (page == null) {
            listSearch = search;   // the modules search comes back when the page closes
        }
        search = "";
        picker.close();
        dropdown = null;
        page = m;
        pageOpenedAt = System.currentTimeMillis();
        scroll = 0;
        smoothScroll = 0;
        hits.clear();
        settingKnob.clear();
    }

    public Module page() {
        return page;
    }

    /** Every feature has a page: its keybind, plus options and (for HUD items) its look. */
    static boolean hasPage(Module m) {
        return true;
    }

    public Module binding() {
        return binding;
    }

    // ---- for the self-test (see SelfTest)

    boolean editingHud() {
        return editingHud;
    }

    /** True while the HUD editor is open (items that are normally hidden show a stand-in to place). */
    public boolean isEditingHud() {
        return editingHud;
    }

    String search() {
        return search;
    }

    String section() {
        return section;
    }

    /** Modrinth results showing on a Browse page (for the self-test). */
    int browseResults() {
        MenuPanel pn = panel();
        return pn instanceof LibraryPanel ? ((LibraryPanel) pn).resultCount() : -1;
    }

    HudEditor editor() {
        return editor;
    }

    /** Centre of something on screen, in GUI pixels, or null if it isn't showing. */
    int[] centerOf(String what) {
        if (editingHud) {
            return editor.centerOf(what);
        }
        layout(pc.platform());
        if (what.equals("edit")) {
            return editW > 0 ? new int[] {s(editX + editW / 2), s(editY + editH / 2)} : null;
        }
        if (what.startsWith("view:") || what.startsWith("side:")) {
            String id = what.startsWith("side:") ? what.substring(5)
                    : what.equals("view:Settings") ? "settings" : what.equals("view:Keybinds") ? "keybinds" : "modules";
            for (Hit h : sideHits) {
                if (h.id.equals(id)) {
                    return new int[] {s(h.x + h.w / 2), s(h.y + h.h / 2)};
                }
            }
            return null;
        }
        if (what.startsWith("panel:") && panel() != null) {
            for (MenuPanel.Spot sp : panel().spots) {
                if (sp.id.equals(what.substring(6))) {
                    return new int[] {s(sp.x + sp.w / 2), s(sp.y + sp.h / 2)};
                }
            }
            return null;
        }
        if (page != null || view != 0) {
            for (Hit h : hits) {
                if (h.id.equals(what)) {
                    return new int[] {s(h.x + h.w / 2), s(h.y + h.h / 2)};
                }
            }
            return null;
        }
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            int[] c = card(i);
            if (what.equals("gear:" + mods.get(i).id)) {  // the tile itself opens its options
                int y = c[1] + tileH / 2;
                return y > listY && y < listY + listH ? new int[] {s(c[0] + c[2] / 2), s(y)} : null;
            }
            if (mods.get(i).id.equals(what)) {  // its switch
                int[] tg = toggleAt(c);
                int y = tg[1] + tg[3] / 2;
                return y > listY && y < listY + listH ? new int[] {s(tg[0] + tg[2] / 2), s(y)} : null;
            }
        }
        return null;
    }

    /** Call when the menu screen closes. */
    public void close() {
        search = "";
        listSearch = "";
        searchFocused = false;
        movingWindow = false;
        resizingWindow = false;
        resizeEdges = 0;
        pc.platform().setCursor(0);
        binding = null;
        editor.mouseReleased();
        editingHud = false;
        pc.markDirty();
    }

    /** The adapter dims the game behind the menu, but not while editing the HUD. */
    public boolean wantsShade() {
        return !editingHud;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<Module>();
        for (Module m : pc.modules()) {
            if ((group == null || ModuleGroups.of(m).equals(group)) && m.matches(search) && pc.platform().supports(m.id)
                    && (!onlyOn || m.enabled() || view != 0)) {
                out.add(m);
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Module>() {  // A to Z, like a shelf
            public int compare(Module x, Module y) {
                if (x.favorite != y.favorite) {
                    return x.favorite ? -1 : 1;  // starred ones first
                }
                return x.displayName().compareToIgnoreCase(y.displayName());
            }
        });
        return out;
    }

    private int count(Module.Category c, boolean onlyOn) {
        int n = 0;
        for (Module m : pc.modules()) {
            if ((c == null || m.category == c) && pc.platform().supports(m.id) && (!onlyOn || m.enabled())) {
                n++;
            }
        }
        return n;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A large window in the middle of the screen, never edge to edge. */
    private void layout(Platform p) {
        int sw = Math.round(p.screenWidth() / k);
        int sh = Math.round(p.screenHeight() / k);
        int fitW = sw - 16, fitH = sh - 16;
        pw = Math.min(fitW, windowW > 0 ? windowW : clamp(Math.round(sw * 0.78f), 330, 600));
        ph = Math.min(fitH, windowH > 0 ? windowH : clamp(Math.round(sh * 0.78f), 210, 340));
        px = windowX < 0 ? (sw - pw) / 2 : clamp(windowX, 8, sw - pw - 8);
        py = windowY < 0 ? (sh - ph) / 2 : clamp(windowY, 8, sh - ph - 8);
        closeX = px + pw - 23;
        closeY = py + 7;
        searchW = clamp(Math.round(pw * 0.27f), 90, 150);
        searchX = closeX - 6 - searchW;
        searchY = py + 7;
        tabsY = searchY;
        menuBtnX = searchX - 4;
        int bodyY = py + TITLE_H + 6;
        boolean list = view == 0 && page == null;
        editW = SIDE_W;
        editH = 17;
        editX = px + 8;
        editY = py + ph - 8 - editH;
        sideX = px + 8;
        sideY = bodyY;
        sideW = SIDE_W;
        sideH = editY - 6 - bodyY;
        listX = px + 8 + SIDE_W + 12;
        listY = bodyY;
        listW = px + pw - 12 - listX;
        listH = py + ph - 8 - bodyY;
        if (list) {
            // the modules list has its own search bar and tabs above it, like a store shelf
            filterY = bodyY;
            onlyW = 44;
            onlyX = listX + listW - onlyW;
            searchX = listX;
            searchY = bodyY;
            searchW = listW - onlyW - 4;
            menuBtnX = closeX - 6 - 70;
            listY = bodyY + 38;
            listH = py + ph - 8 - listY;
        }
        cols = Math.max(1, (listW + TILE_GAP) / (140 + TILE_GAP));
        tileW = (listW - TILE_GAP * (cols - 1)) / cols;
        tileH = pc.compactRows() ? ROW_H_COMPACT : ROW_H;
    }

    /** Tile i of the grid: x, y, width (menu pixels, scrolled). */
    private int[] card(int i) {
        int col = i % cols, row = i / cols;
        return new int[] {listX + col * (tileW + TILE_GAP), listY + row * (tileH + TILE_GAP) - Math.round(smoothScroll), tileW};
    }

    private static final int ROW_H = 27, ROW_H_COMPACT = 20;

    /** With animations off, every fade and slide counts as long finished. */
    private long anim(long started) {
        return pc.animations() ? started : 0L;
    }

    /** A row's switch, on the right: x, y, width, height. */
    private int[] toggleAt(int[] c) {
        return new int[] {c[0] + c[2] - 7 - 24, c[1] + (tileH - 11) / 2, 24, 11};
    }

    private static final int GEAR = 14;
    private static final int GRID_H = 15 * 7 + 26;   // the crosshair pixel grid's row

    /** A tile's options button: x, y (GEAR square), right of the on/off bar. */
    private int[] gearAt(int[] c) {
        return new int[] {c[0] + c[2] - 5 - GEAR, c[1] + tileH - 19};
    }

    /** A row's star (favourite), left of the switch: x, y (11 square). */
    private int[] starAt(int[] c) {
        return new int[] {c[0] + c[2] - 7 - 24 - 15, c[1] + (tileH - 9) / 2};
    }

    private int tabY(int i) {
        return listY + 14 + i * (TAB_H + 3);
    }

    // ------------------------------------------------------------ drawing

    /** How far the opening fade has got, 0-1; adapters use it to fade the background shade too. */
    public float fade() {
        return FadeDraw.progress(anim(openedAt), 180);
    }

    public void render(Draw raw, int screenMx, int screenMy) {
        Platform p = pc.platform();
        k = uiScale(p);
        lastMx = screenMx;
        layout(p);
        if ((draggingSlider != null || movingWindow || resizingWindow || sbDragging) && p.mouseHeld() == 0) {
            mouseReleased();   // let go somewhere the menu didn't hear about
        }
        guiMx = Math.round(screenMx / k);
        guiMy = Math.round(screenMy / k);
        if (sbDragging) {
            sbFollow(guiMy);
        }
        sbX = -1;
        if (draggingSlider != null && !editingHud && screenMx > -500) {
            // A held slider follows the pointer the menu is drawn with (flat or 3D), every frame.
            float before = draggingSlider.value;
            slideTo(draggingSlider, Math.round(screenMx / k));
            dragSeenAt = Math.round(screenMx / k);
            if (draggingSlider.value != before) {
                styleChanged(draggingSlider);
            }
        }
        SelfTest.count(editingHud ? "editor" : "menu");
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (editingHud) {
            editor.render(raw, screenMx, screenMy, k);
            return;
        }
        float t = fade();
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        d.pushScale(0, 0, k);
        renderMenu(d, Math.round(screenMx / k), Math.round(screenMy / k), p, t, dt, now);
        d.popScale();
    }

    private void renderMenu(Draw d, int mx, int my, Platform p, float t, float dt, long now) {
        if (t < 1f) {
            // Rise into place as it fades in.
            float e = 1f - (1f - t) * (1f - t);
            int lift = Math.round((1f - e) * 12);
            py += lift;
            listY += lift;
            searchY += lift;
            filterY += lift;
            closeY += lift;
            editY += lift;
            tabsY += lift;
            sideY += lift;
        }
        int a = pc.accent();

        d.roundRect(px, py, pw, ph, 4, pc.menuBg(MenuStyle.WINDOW));
        d.roundOutline(px, py, pw, ph, 4, MenuStyle.BORDER);
        d.rect(px + 1, py + TITLE_H, pw - 2, 1, MenuStyle.BORDER);

        int ty = py + (TITLE_H - 8) / 2;
        int titleX = px + 12;
        int titleRight = view == 0 && page == null ? menuBtnX : searchX - 8;
        String title = d.trim(crumb(), Math.round((titleRight - titleX) / 1.2f));
        d.textScaled(title, titleX, ty - 1, Theme.TEXT, 1.2f, false);
        boolean closeHover = in(mx, my, closeX, closeY, 16, 16);
        float cg = sideGlowOf("close", closeHover, dt);
        d.roundRect(closeX, closeY, 16, 16, 3, Theme.mix(0x00000000, MenuStyle.ROW_HOVER, cg));
        int xc = Theme.mix(Theme.MUTED, 0xFFFFFFFF, cg);
        if (d.hasTextures()) {
            MenuIcons.draw(d, "close", closeX + 4, closeY + 4, 8, xc);
        } else {
            for (int i = 0; i < 6; i++) {  // a small drawn ×, crisp at any GUI scale
                d.rect(closeX + 5 + i, closeY + 5 + i, 1, 1, xc);
                d.rect(closeX + 10 - i, closeY + 5 + i, 1, 1, xc);
            }
        }

        // Search (it searches whatever page you're on), and ≡ (only what's on) for the modules
        MenuPanel pn = page == null ? panel() : null;
        boolean searchable = canSearch(pn);
        if (view == 0 && page == null) {
            drawFilters(d, mx, my, dt, a);
        }
        if (searchable) {
            boolean searching = !search.isEmpty();
            d.roundRect(searchX, searchY, searchW, TABBAR_H, 3, MenuStyle.FIELD);
            d.roundOutline(searchX, searchY, searchW, TABBAR_H, 3, searchFocused ? a : MenuStyle.BORDER);
            boolean caret = searchFocused && (now / 500) % 2 == 0;
            String shown = searching || searchFocused ? search + (caret ? "|" : "")
                    : pn != null ? pn.placeholder() : page != null ? "Search settings" : "Search modules";
            MenuIcons.draw(d, "search", searchX + 5, searchY + 4, searching ? a : Theme.FAINT);
            if (searchFocused) {
                while (!shown.isEmpty() && d.width(shown) > searchW - 22) {
                    shown = shown.substring(shown.offsetByCodePoints(0, 1));
                }
            }
            d.text(d.trim(shown, searchW - 22), searchX + 17, searchY + 4, searching || searchFocused ? Theme.TEXT : Theme.MUTED, false);
        }

        if (in(mx, my, px + 6, py + 4, Math.max(0, menuBtnX - px - 8), TITLE_H - 9)) {
            d.rect(titleX, py + TITLE_H, Math.round(d.width(title) * 1.2f), 1, Theme.MUTED);
        }
        int hot = resizingWindow ? resizeEdges : movingWindow ? 0 : resizeEdgesAt(mx, my);
        p.setCursor(hot == 0 ? 0 : cursorFor(hot));
        if (hot != 0) {
            int len = (hot & 3) != 0 && (hot & 12) != 0 ? 16 : 0;
            int c = Theme.TEXT;
            if (len > 0) {
                int cx = (hot & 1) != 0 ? px : px + pw - 2, cy = (hot & 4) != 0 ? py : py + ph - 2;
                int hx = (hot & 1) != 0 ? px : px + pw - len, vy = (hot & 4) != 0 ? py : py + ph - len;
                d.rect(hx, cy, len, 2, c);
                d.rect(cx, vy, 2, len, c);
            } else if ((hot & 3) != 0) {
                d.rect((hot & 1) != 0 ? px : px + pw - 2, py + TITLE_H, 2, ph - TITLE_H, c);
            } else {
                d.rect(px, (hot & 4) != 0 ? py : py + ph - 2, pw, 2, c);
            }
        }
        int grip = hot != 0 ? a : Theme.MUTED;
        for (int i = 0; i < 3; i++) {
            d.rect(px + pw - 6 - 3 * i, py + ph - 6, 1, 1, grip);
            if (i > 0) d.rect(px + pw - 6, py + ph - 6 - 3 * i, 1, 1, grip);
        }
        drawSidebar(d, mx, my, dt, a);
        boolean editHover = in(mx, my, editX, editY, editW, editH);
        float eg = sideGlowOf("edit", editHover, dt);
        MenuStyle.button(d, editX, editY, editW, editH, false, eg, a, editHover && p.mouseHeld() == 1);
        MenuIcons.draw(d, "hud", editX + 8, editY + 4, Theme.MUTED);
        String edit = "Edit HUD";
        d.text(edit, editX + (editW - d.width(edit)) / 2 + 5, editY + 5, Theme.TEXT, false);

        if (page != null) {
            renderPage(d, mx, my, p, dt, now, a);
            return;
        }
        if (view == 1) {
            renderSettingsView(d, mx, my, dt, a);
            return;
        }
        if (view == 2) {
            renderKeybindView(d, mx, my, dt, now, a);
            return;
        }
        if (pn != null) {
            // pages slide in a little from the right when chosen
            float st = FadeDraw.progress(anim(listShownAt), 220);
            float se = 1f - (1f - st) * (1f - st);
            int off = Math.round((1f - se) * 10);
            Draw pd = st < 1f ? new FadeDraw(d, 0.2f + 0.8f * st) : d;
            pn.render(pd, mx, my, listX + off, listY, listW, listH, dt, now, a);
            return;
        }

        // Module cards
        List<Module> mods = visible();
        int rows = (mods.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (tileH + TILE_GAP) - TILE_GAP - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }

        // Clipping works in screen pixels on every version, so step out of the scale for it.
        clipArea(d, listX - 2, listY, listW + 4, listH);
        boolean mouseInList = in(mx, my, listX, listY, listW, listH);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int[] c = card(i);
            int cx = c[0], cy = c[1], cw = c[2];
            if (cy + tileH < listY || cy > listY + listH) {
                continue;
            }
            // Cards pop in one after another when the menu (or a new category) opens.
            float raw = FadeDraw.progress(anim(listShownAt + Math.min(i, 18) * 14L), 240);
            float appear = Math.max(0f, Math.min(1f, raw * 1.4f));
            Draw cd = appear < 1f ? new FadeDraw(d, 0.05f + 0.95f * appear) : d;
            if (raw < 1f) {
                cy += Math.round((1f - Glass.backOut(raw)) * 8);
            }
            boolean hover = mouseInList && in(mx, my, cx, cy, cw, tileH);
            float g = ease(rowGlow, m, hover ? 1f : 0f, dt * 10f);
            float on = animate(m, dt);
            cy -= Math.round(g * 1.5f);
            c = new int[] {cx, cy, cw};
            drawTile(cd, m, c, mx, my, hover, g, on, a, p);
        }
        unclipArea(d);

        if (mods.isEmpty()) {
            String none = !search.isEmpty() ? "Nothing matches \"" + d.trim(search, listW - 80) + "\""
                    : onlyOn ? "Nothing on in " + (group == null ? "here" : group) : "Nothing here";
            d.text(none, listX + (listW - d.width(none)) / 2, listY + listH / 2 - 8, Theme.MUTED, false);
            String hint = !search.isEmpty() ? "Backspace clears the search" : onlyOn ? "Click Only on to see everything" : "";
            d.text(hint, listX + (listW - d.width(hint)) / 2, listY + listH / 2 + 4, Theme.FAINT, false);
        }
        scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
    }

    /**
     * The bar above the list: how many are on (in the title), the search box, the Only on
     * button and a tab for each group. Tabs that don't fit scroll sideways with the wheel.
     */
    private void drawFilters(Draw d, int mx, int my, float dt, int a) {
        int on = 0, all = 0;
        for (Module m : pc.modules()) {
            if (pc.platform().supports(m.id)) {
                all++;
                if (m.enabled()) on++;
            }
        }
        String count = on + " of " + all + " on";
        d.text(count, closeX - 8 - d.width(count), py + (TITLE_H - 8) / 2 + 1, Theme.FAINT, false);

        boolean oh = in(mx, my, onlyX, filterY, onlyW, TABBAR_H);
        float og = sideGlowOf("onlyon", oh, dt);
        d.roundRect(onlyX, filterY, onlyW, TABBAR_H, 3, onlyOn ? Theme.withAlpha(a, 0x50) : Theme.mix(0x18FFFFFF, 0x2CFFFFFF, og));
        d.roundOutline(onlyX, filterY, onlyW, TABBAR_H, 3, onlyOn ? Theme.withAlpha(a, 0xC0) : 0x1CFFFFFF);
        String ol = "Only on";
        d.text(ol, onlyX + (onlyW - d.width(ol)) / 2, filterY + 4, onlyOn ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, og), false);

        int cy = filterY + TABBAR_H + 5, ch = 14;
        chipHits.clear();
        // Tabs tighten up before they scroll: most windows then fit every tab without hiding any.
        int pad = 12, gap = 3, total = 0;
        boolean shortNames = false;
        for (int tight = 0; tight < 4; tight++) {
            total = 0;
            for (int i = 0; i <= ModuleGroups.ALL.length; i++) {
                String label = i == 0 ? "All" : tabLabel(ModuleGroups.ALL[i - 1], shortNames);
                total += d.width(label) + pad + (i > 0 ? gap : 0);
            }
            if (total <= listW) break;
            if (tight < 2) {
                pad = tight == 0 ? 8 : 6;
                gap = tight == 0 ? 2 : 1;
            } else {
                shortNames = true;
            }
        }
        chipMax = Math.max(0, total - listW);
        chipScroll = clamp(chipScroll, 0, chipMax);
        clipArea(d, listX, cy - 1, listW, ch + 2);
        int x = listX - chipScroll;
        for (int i = 0; i <= ModuleGroups.ALL.length; i++) {
            String g = i == 0 ? null : ModuleGroups.ALL[i - 1];
            String label = g == null ? "All" : tabLabel(g, shortNames);
            int w = d.width(label) + pad;
            boolean sel = g == null ? group == null : g.equals(group);
            boolean hv = in(mx, my, x, cy, w, ch) && in(mx, my, listX, cy, listW, ch);
            float hg = sideGlowOf("chip:" + label, hv, dt);
            if (sel) {
                selX = x + pad / 2 - 2;   // the underline: the label's width and a little
                selW = w - pad + 4;
            } else if (hg > 0.01f) {
                d.roundRect(x, cy, w, ch, 3, Theme.withAlpha(0xFFFFFF, Math.round(0x14 * hg)));
            }
            d.text(label, x + pad / 2, cy + 3, sel ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, hg), false);
            chipHits.add(new Hit(g == null ? "" : g, x, cy, w, ch, null));
            x += w + gap;
        }
        // the underline glides over to the chosen tab instead of jumping
        if (lineW <= 0) {
            lineX = selX;
            lineW = selW;
        }
        float ease = Math.min(1f, dt * 14f);
        lineX += (selX - lineX) * ease;
        lineW += (selW - lineW) * ease;
        d.rect(Math.round(lineX), cy + ch - 1, Math.max(2, Math.round(lineW)), 1, a);
        unclipArea(d);
        if (chipMax > 0) {   // a fade at the edge says there's more to the side
            int fx = listX + listW - 10;
            for (int i = 0; i < 10; i++) {
                d.rect(fx + i, cy, 1, ch, Theme.withAlpha(MenuStyle.WINDOW, chipScroll >= chipMax ? 0 : i * 22));
            }
        }
    }

    /** A tab's name; the long ones get a short form when the window is narrow. */
    private static String tabLabel(String group, boolean shortNames) {
        if (!shortNames) return group;
        if (group.equals(ModuleGroups.HAND)) return "Items";
        if (group.equals(ModuleGroups.PERF)) return "Perf";
        return group;
    }

    /** True over the row of tabs (the wheel scrolls them sideways there). */
    private boolean overChips(int mx, int my) {
        return view == 0 && page == null && in(mx, my, listX, filterY + TABBAR_H + 4, listW, 16);
    }

    /**
     * One feature's row: its icon, name and a short line on what it does, with the switch on
     * the right. Clicking the row opens its options; the star (on hover) keeps it near the top.
     */
    private void drawTile(Draw cd, Module m, int[] c, int mx, int my, boolean hover, float g, float on, int a, Platform p) {
        int cx = c[0], cy = c[1], cw = c[2];
        cd.roundRect(cx, cy, cw, tileH, 3, Theme.mix(MenuStyle.ROW, MenuStyle.ROW_HOVER, g));
        int ic = 18;
        int ix = cx + 5, iy = cy + (tileH - ic) / 2;
        Object stack = p.icon(Icons.of(m));
        if (stack != null) {
            cd.item(stack, ix + 1, iy + 1);
        } else {
            categoryIcon(cd, m.category, ix + 4, iy + 4, on > 0.5f ? a : Theme.MUTED);
        }
        int tx = ix + ic + 5;
        int[] tg = toggleAt(c);
        int[] st = starAt(c);
        int room = (hover || m.favorite ? st[0] - 3 : tg[0] - 5) - tx;
        boolean warn = hover && m.warning() != null;
        String title = cd.trim(m.displayName(), room);
        boolean compact = pc.compactRows();
        int ty = compact ? cy + (tileH - 8) / 2 : cy + 5;
        cd.text(title, tx, ty, Theme.TEXT, false);
        if (m.key >= 0) {   // its key, after the name
            String key = Keys.name(m.key);
            int kx = tx + cd.width(title) + 4;
            if (kx + cd.width(key) + 4 < tx + room) {
                cd.roundRect(kx, ty - 1, cd.width(key) + 4, 10, 2, 0x40000000);
                cd.text(key, kx + 2, ty, Theme.FAINT, false);
            }
        }
        if (!compact) {
            String sub = cd.trim(warn ? m.warning() : m.description, room);
            cd.text(sub, tx, cy + 15, warn ? Theme.OK : Theme.MUTED, false);
        }
        if (m.favorite || hover) {
            boolean starHover = hover && in(mx, my, st[0] - 2, st[1] - 2, 15, 15);
            MenuIcons.draw(cd, m.favorite ? "star" : "star_o", st[0], st[1],
                    m.favorite ? a : starHover ? Theme.TEXT : Theme.withAlpha(Theme.MUTED, 0xA0));
        }
        boolean swHover = hover && in(mx, my, tg[0] - 2, tg[1] - 3, tg[2] + 4, tg[3] + 6);
        MenuStyle.toggle(cd, tg[0], tg[1], 24, 11, on, a, swHover);
    }

    /** Where you are, for the title: "Modules / HUD", "Mods / Browse"... */
    private String crumb() {
        if (page != null) {
            return page.displayName();
        }
        if (section.equals("cat:PERFORMANCE")) return "Performance / Modules";
        for (Node n : SIDEBAR) {
            if (n == null) {
                continue;
            }
            if (n.id.equals(section) || (n.id.equals("modules") && section.equals("cat:all"))) {
                return n.label;
            }
            for (Node c : n.kids) {
                if (c.id.equals(section)) {
                    return n.label + " / " + c.label;
                }
            }
        }
        return "";
    }

    private float sideGlowOf(String id, boolean on, float dt) {
        Float g = sideGlow.get(id);
        float v = Glass.ease(g == null ? 0f : g, on, dt);
        sideGlow.put(id, v);
        return v;
    }

    /** One drawn line of the sidebar, kept until the selection pill is under it. */
    private static final class Row {
        final Node n;
        final int y;
        final float alpha, open;
        final boolean kid;

        Row(Node n, int y, float alpha, float open, boolean kid) {
            this.n = n;
            this.y = y;
            this.alpha = alpha;
            this.open = open;
            this.kid = kid;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();

    /** The sidebar: groups that drop down (Modules, Mods, Shaders, Resource packs) and single pages. */
    private void drawSidebar(Draw d, int mx, int my, float dt, int a) {
        sideHits.clear();
        rows.clear();
        d.rect(sideX + sideW + 4, sideY, 1, editY + editH - sideY, MenuStyle.BORDER);
        int lines = 0, dividers = 0;
        for (Node n : SIDEBAR) {
            if (n == null) {
                dividers++;
            } else {
                lines += 1 + (n.id.equals(expanded) ? n.kids.length : 0);
            }
        }
        int rowH = Math.max(11, Math.min(SIDE_ROW, (sideH - 8 - dividers * 7) / Math.max(1, lines)));
        int ty = (rowH - 8) / 2 + 1;
        sideScroll = Math.min(sideScroll, Math.max(0, 8 + lines * rowH + dividers * 7 - sideH));
        clipArea(d, sideX, sideY, sideW, sideH);
        int y = sideY + 4 - sideScroll;
        float selY = -1;
        boolean selKid = false;
        for (Node n : SIDEBAR) {
            if (n == null) {
                d.rect(sideX + 8, y + 3, sideW - 16, 1, 0x18FFFFFF);
                y += 7;
                continue;
            }
            float ex = 0f;
            if (n.kids.length > 0) {
                Float e = expand.get(n.id);
                float target = n.id.equals(expanded) ? 1f : 0f;
                ex = e == null ? target : e + (target - e) * Math.min(1f, dt * 14f);
                if (Math.abs(target - ex) < 0.01f) {
                    ex = target;
                }
                expand.put(n.id, ex);
            }
            if (n.id.equals(section) || (n.id.equals("modules") && section.equals("cat:all"))) {
                selY = y;
            }
            sideHits.add(new Hit(n.id, sideX + 3, y, sideW - 6, rowH, null));
            rows.add(new Row(n, y, 1f, ex, false));
            y += rowH;
            if (ex > 0.01f) {
                int kidsTop = y;
                for (Node c : n.kids) {
                    if (c.id.equals(section)) {
                        selY = y;
                        selKid = true;
                    }
                    if (ex > 0.9f) {
                        sideHits.add(new Hit(c.id, sideX + 14, y, sideW - 17, rowH - 2, null));
                    }
                    rows.add(new Row(c, y, Math.max(0f, Math.min(1f, ex * 1.3f - 0.3f)), 0f, true));
                    y += Math.round((rowH - 1) * ex);
                }
                // a thin line of the accent joining the group's pages
                d.rect(sideX + 10, kidsTop, 1, Math.max(0, y - kidsTop - 3), Theme.withAlpha(a, Math.round(0x60 * ex)));
            }
        }
        maxSideScroll = Math.max(0, y + sideScroll + 4 - sideY - sideH);
        // hover washes, under everything else
        for (Row r : rows) {
            boolean selected = isSelected(r.n);
            int rx = r.kid ? sideX + 14 : sideX + 3, rw = r.kid ? sideW - 17 : sideW - 6, rh = r.kid ? rowH - 2 : rowH;
            float g = sideGlowOf("n:" + r.n.id, r.alpha > 0.9f && in(mx, my, rx, r.y, rw, rh), dt);
            if (!selected && g > 0.01f) {
                d.roundRect(rx, r.y, rw, rh, 4, Theme.withAlpha(0xFFFFFF, Math.round(0x16 * g * r.alpha)));
            }
        }
        // The selection slides between sidebar rows.
        if (selY >= 0) {
            sideSlide = sideSlide < 0 ? selY : sideSlide + (selY - sideSlide) * Math.min(1f, dt * 16f);
            int sx = selKid ? sideX + 14 : sideX + 3, sw = selKid ? sideW - 17 : sideW - 6, sh = selKid ? rowH - 2 : rowH;
            int sy = Math.round(sideSlide);
            d.roundRect(sx, sy, sw, sh, 3, Theme.mix(MenuStyle.ROW, a, 0.18f));
            d.rect(sx, sy + 2, 1, sh - 4, a);
        }
        // icons and names on top
        for (Row r : rows) {
            if (r.alpha <= 0.01f) {
                continue;
            }
            Draw rd = r.alpha < 1f ? new FadeDraw(d, r.alpha) : d;
            boolean selected = isSelected(r.n);
            boolean inside = false;
            for (Node c : r.n.kids) {
                inside |= c.id.equals(section);
            }
            Float gl = sideGlow.get("n:" + r.n.id);
            float g = gl == null ? 0f : gl;
            if (r.kid) {
                int kc = selected ? Theme.TEXT : Theme.mix(Theme.FAINT, Theme.TEXT, g);
                if (r.n.icon != null) {
                    MenuIcons.draw(rd, r.n.icon, sideX + 18, r.y + ty - 2, kc);
                } else {
                    rd.roundRect(sideX + 19, r.y + ty + 1, 4, 4, 2, kc);
                }
                rd.text(rd.trim(r.n.label, sideW - 50), sideX + 31, r.y + ty - 1, kc, false);
                if (r.n.id.startsWith("cat:")) {  // how many are on
                    String cnt = String.valueOf(count(Module.Category.valueOf(r.n.id.substring(4)), true));
                    rd.text(cnt, sideX + sideW - 8 - rd.width(cnt), r.y + ty - 1, selected ? Theme.TEXT : Theme.FAINT, false);
                }
            } else {
                int col = selected ? Theme.TEXT : inside ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, g);
                MenuIcons.draw(rd, r.n.icon, sideX + 8, r.y + ty - 1, col);
                rd.text(rd.trim(r.n.label, sideW - 36), sideX + 21, r.y + ty, col, false);
                if (r.n.kids.length > 0) {
                    Glass.chevron(rd, sideX + sideW - 12, r.y + ty + 1, r.open, selected ? col : Theme.withAlpha(col, 0xB0));
                }
            }
        }
        unclipArea(d);
        if (maxSideScroll > 0) {
            int thumb = Math.max(9, sideH * sideH / (sideH + maxSideScroll));
            int offset = (sideH - thumb) * Math.min(sideScroll, maxSideScroll) / maxSideScroll;
            d.rect(sideX + sideW - 2, sideY + offset, 1, thumb, Theme.withAlpha(a, 0x80));
        }
    }

    private boolean isSelected(Node n) {
        return n.id.equals(section) || (n.id.equals("modules") && section.startsWith("cat:"));
    }

    private void scrollbar(Draw d, int top, int viewH, boolean active, int a) {
        scrollbar(d, top, viewH, maxScroll, smoothScroll, a, v -> {
            scroll = clamp(v, 0, maxScroll);
            smoothScroll = scroll;
        });
    }

    /** Small pixel icons for the filter list (9 x 9). */
    private static void categoryIcon(Draw d, Module.Category c, int x, int y, int col) {
        if (c == null) {  // All: three lines
            for (int i = 0; i < 3; i++) {
                d.rect(x, y + i * 3, 9, 2, col);
            }
        } else if (c == Module.Category.HUD) {  // a screen
            d.outline(x, y, 9, 7, col);
            d.rect(x + 2, y + 2, 3, 1, col);
            d.rect(x + 2, y + 4, 5, 1, col);
            d.rect(x + 3, y + 8, 3, 1, col);
        } else if (c == Module.Category.UTILITY) {  // a wrench
            d.rect(x + 1, y, 3, 1, col);
            d.rect(x, y + 1, 1, 3, col);
            d.rect(x + 4, y + 1, 1, 2, col);
            d.rect(x + 1, y + 4, 2, 1, col);
            for (int i = 0; i < 5; i++) {
                d.rect(x + 3 + i, y + 3 + i, 2, 1, col);
            }
        } else if (c == Module.Category.VISUALS) {  // an eye
            d.rect(x + 2, y + 1, 5, 1, col);
            d.rect(x + 1, y + 2, 1, 1, col);
            d.rect(x + 7, y + 2, 1, 1, col);
            d.rect(x, y + 3, 1, 3, col);
            d.rect(x + 8, y + 3, 1, 3, col);
            d.rect(x + 1, y + 6, 1, 1, col);
            d.rect(x + 7, y + 6, 1, 1, col);
            d.rect(x + 2, y + 7, 5, 1, col);
            d.rect(x + 3, y + 3, 3, 3, col);
        } else {  // Performance: a lightning bolt
            d.rect(x + 4, y, 3, 1, col);
            d.rect(x + 3, y + 1, 3, 1, col);
            d.rect(x + 2, y + 2, 3, 1, col);
            d.rect(x + 1, y + 3, 6, 1, col);
            d.rect(x + 4, y + 4, 2, 1, col);
            d.rect(x + 3, y + 5, 2, 1, col);
            d.rect(x + 2, y + 6, 2, 1, col);
            d.rect(x + 2, y + 7, 1, 1, col);
        }
    }

    // ------------------------------------------------------- Settings and Keybinds views

    /** Accent colors to pick from in Settings. */
    static final int[] ACCENTS = {
        Theme.AMBER, 0xFF4FB3FF, 0xFF6BE38B, 0xFFFF6B6B, 0xFFB57BFF, 0xFFFF7AD9, 0xFF5CE1E6, 0xFFFFE066, 0xFFEDEEF2,
    };

    private int settingsH;

    private void renderSettingsView(Draw d, int mx, int my, float dt, int a) {
        hits.clear();
        maxScroll = Math.max(0, settingsH - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }
        d.popScale();
        d.clip(s(listX), s(listY), s(listW), s(listH));
        d.pushScale(0, 0, k);
        int x = listX, w = listW, y = listY - Math.round(smoothScroll);
        int top = y;
        y = settingsRows(d, mx, my, dt, a, x, y, w);
        settingsH = y - top;
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        // only what's in view can be clicked
        hits.removeIf(h -> h.y + h.h / 2 < listY || h.y + h.h / 2 > listY + listH);
        if (maxScroll > 0) {
            scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
        }
        picker.render(d, mx, my, a);
    }

    /** A label on the left and a few pill buttons on the right; the chosen one is filled in. */
    private int choiceRow(Draw d, int mx, int my, int a, String id, String label, String[] options, int chosen,
                          int x, int y, int w) {
        int bx = x + w - 6;
        for (int i = options.length - 1; i >= 0; i--) {
            String o = options[i];
            int ow = d.width(o) + 12;
            bx -= ow;
            boolean on = chosen == i;
            boolean hv = in(mx, my, bx, y + 3, ow, 16);
            MenuStyle.button(d, bx, y + 3, ow, 16, on, hv ? 1f : 0f, a, hv && pc.platform().mouseHeld() == 1);
            d.text(o, bx + 6, y + 7, on ? Theme.onColor(a) : hv ? Theme.TEXT : Theme.MUTED, false);
            hit("choice:" + id + ":" + i, bx, y + 3, ow, 16, null);
            bx -= 3;
        }
        d.text(d.trim(label, bx - x - 9), x + 6, y + 7, Theme.MUTED, false);
        return y + SET_H + 2;
    }

    private int settingsRows(Draw d, int mx, int my, float dt, int a, int x, int y, int w) {
        heading(d, "Look", x, y);
        y += 14;
        d.text("Accent color", x + 6, y + 7, Theme.MUTED, false);
        int drawA = a;
        a = pc.userAccent();   // the swatches show your own colour, even while a holiday theme recolours the menus
        int sx = x + w - 6 - (ACCENTS.length + 1) * 14 + 2;
        {   // the last one: any colour, from the picker (the launcher takes it too)
            int cx = sx + ACCENTS.length * 14;
            boolean custom = true;
            for (int c : ACCENTS) {
                custom &= (c & 0xFFFFFF) != (a & 0xFFFFFF);
            }
            boolean hv = in(mx, my, cx - 1, y + 4, 12, 12);
            if (custom || hv) {
                d.roundOutline(cx - 2, y + 3, 14, 14, custom ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            if (custom) {
                d.roundRect(cx, y + 5, 10, 10, a);
            } else {
                for (int i = 0; i < 10; i++) {
                    d.rect(cx + i, y + 5, 1, 10, 0xFF000000 | java.awt.Color.HSBtoRGB(i / 10f, 0.8f, 1f));
                }
            }
            hit("accent-pick", cx - 1, y + 4, 12, 12, null);
        }
        for (int i = 0; i < ACCENTS.length; i++) {
            int cx = sx + i * 14;
            boolean chosen = (ACCENTS[i] & 0xFFFFFF) == (a & 0xFFFFFF);
            boolean hv = in(mx, my, cx - 1, y + 4, 12, 12);
            if (chosen || hv) {
                d.roundOutline(cx - 2, y + 3, 14, 14, chosen ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            d.roundRect(cx, y + 5, 10, 10, ACCENTS[i]);
            hit("accent:" + i, cx - 1, y + 4, 12, 12, null);
        }
        a = drawA;
        y += SET_H + 4;
        // menu background: solid, see-through or none
        int bx = x + w - 6;
        for (int i = Peregrine.MENU_BACKGROUNDS.length - 1; i >= 0; i--) {
            String o = Peregrine.MENU_BACKGROUNDS[i];
            int ow = d.width(o) + 12;
            bx -= ow;
            boolean on = pc.menuBackground() == i;
            boolean hv = in(mx, my, bx, y + 3, ow, 16);
            MenuStyle.button(d, bx, y + 3, ow, 16, on, hv ? 1f : 0f, a, hv && pc.platform().mouseHeld() == 1);
            d.text(o, bx + 6, y + 7, on ? Theme.onColor(a) : hv ? Theme.TEXT : Theme.MUTED, false);
            hit("menubg:" + i, bx, y + 3, ow, 16, null);
            bx -= 3;
        }
        d.text(d.trim("Menu opacity", bx - x - 9), x + 6, y + 7, Theme.MUTED, false);
        y += SET_H + 2;
        y = choiceRow(d, mx, my, a, "rows", "Module rows", new String[] {"Roomy", "Compact"}, pc.compactRows() ? 1 : 0, x, y, w);
        y = choiceRow(d, mx, my, a, "anim", "Animations", new String[] {"On", "Off"}, pc.animations() ? 0 : 1, x, y, w);
        y = moduleRow(d, mx, my, dt, a, "main_menu", "Light Client main menu", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "pause_menu", "Light Client pause menu", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "clean_menus", "Keep resource packs out of menus", x, y, w);
        y += 8;
        heading(d, "HUD", x, y);
        y += 16;
        y = buttonRow(d, mx, my, a, "all-hud", "One look for every HUD item", "Style all", x, y, w);
        y += 8;
        heading(d, "Profiles", x, y);
        y += 16;
        d.text(d.trim("Save and switch setting profiles", w - 12),
                x + 6, y + 2, Theme.FAINT, false);
        y += 14;
        for (String name : Profiles.list(pc)) {
            boolean cur = name.equals(Profiles.current(pc));
            d.text(d.trim(name, w / 2), x + 6, y + 7, cur ? a : Theme.TEXT, false);
            if (cur) {
                d.text("in use", x + 12 + Math.min(d.width(name), w / 2), y + 7, Theme.FAINT, false);
            }
            int pbx = x + w - 6;
            String[][] btns = {{"del", "Delete"}, {"save", "Update"}, {"load", "Use"}};
            for (String[] b : btns) {
                int bw = d.width(b[1]) + 14;
                pbx -= bw;
                boolean bh = in(mx, my, pbx, y + 3, bw, 16);
                boolean danger = b[0].equals("del");
                d.roundRect(pbx, y + 3, bw, 16, bh ? (danger ? Theme.withAlpha(Theme.BAD, 0x50) : Theme.withAlpha(a, 0x50)) : Theme.CARD);
                d.roundOutline(pbx, y + 3, bw, 16, bh ? (danger ? Theme.BAD : a) : Theme.HAIRLINE);
                d.text(b[1], pbx + 7, y + 7, bh ? Theme.TEXT : Theme.MUTED, false);
                hit("profile-" + b[0] + ":" + name, pbx, y + 3, bw, 16, null);
                pbx -= 4;
            }
            y += SET_H + 2;
        }
        y = buttonRow(d, mx, my, a, "profile-new", "Save what you have now", "New profile", x, y, w);
        y += 8;
        heading(d, "Game", x, y);
        y += 16;
        y = moduleRow(d, mx, my, dt, a, "stretched", "Stretched resolution", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "place_animation", "Blocks grow when placed", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "block_outline", "Rainbow block outline", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "cosmetics", "Capes and badges", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "smooth_inventory", "Smooth inventory", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "motion_blur", "Motion blur", x, y, w);
        y += 8;
        heading(d, "Reset", x, y);
        y += 16;
        y = actionRow(d, mx, my, a, "reset-hud", "HUD layout", "Reset positions & looks", x, y, w);
        y = actionRow(d, mx, my, a, "clear-keys", "Keybinds", "Clear them all", x, y, w);
        return y;
    }

    private void heading(Draw d, String text, int x, int y) {
        d.text(text.toUpperCase(), x + 2, y + 2, Theme.FAINT, false);
        d.rect(x + 4 + d.width(text.toUpperCase()), y + 6, listW - 8 - d.width(text.toUpperCase()), 1, Theme.HAIRLINE);
    }

    private int moduleRow(Draw d, int mx, int my, float dt, int a, String id, String label, int x, int y, int w) {
        Module m = pc.module(id);
        if (m == null || !pc.platform().supports(id)) {
            return y;
        }
        boolean rh = in(mx, my, x, y, w, SET_H);
        if (rh) {
            d.roundRect(x, y, w, SET_H, Theme.ROW_HOVER);
        }
        d.text(label, x + 6, y + 7, rh ? Theme.TEXT : Theme.MUTED, false);
        MenuStyle.toggle(d, x + w - 30, y + 5, 24, 11, animate(m, dt), a, rh);
        hit("module:" + id, x, y, w, SET_H, null);
        return y + SET_H + 2;
    }

    /** A row with a label and an accent button on the right. */
    private int buttonRow(Draw d, int mx, int my, int a, String id, String label, String button, int x, int y, int w) {
        d.text(label, x + 6, y + 7, Theme.MUTED, false);
        int bw = d.width(button) + 16;
        int bx = x + w - 6 - bw;
        boolean bh = in(mx, my, bx, y + 3, bw, 16);
        d.roundRect(bx, y + 3, bw, 16, Theme.withAlpha(a, bh ? 0xFF : 0x2A));
        d.roundOutline(bx, y + 3, bw, 16, Theme.withAlpha(a, 0xA0));
        d.text(button, bx + 8, y + 7, bh ? Theme.onColor(a) : Theme.withAlpha(a, 0xFF), false);
        hit(id, bx, y + 3, bw, 16, null);
        return y + SET_H + 2;
    }

    private int actionRow(Draw d, int mx, int my, int a, String id, String label, String button, int x, int y, int w) {
        d.text(label, x + 6, y + 7, Theme.MUTED, false);
        int bw = d.width(button) + 16;
        int bx = x + w - 6 - bw;
        boolean bh = in(mx, my, bx, y + 3, bw, 16);
        d.roundRect(bx, y + 3, bw, 16, bh ? Theme.withAlpha(Theme.BAD, 0x50) : Theme.CARD);
        d.roundOutline(bx, y + 3, bw, 16, bh ? Theme.BAD : Theme.HAIRLINE);
        d.text(button, bx + 8, y + 7, bh ? Theme.TEXT : Theme.MUTED, false);
        hit(id, bx, y + 3, bw, 16, null);
        return y + SET_H + 2;
    }

    /** Every feature with its keybind, to set them all in one place. */
    private void renderKeybindView(Draw d, int mx, int my, float dt, long now, int a) {
        hits.clear();
        List<Module> mods = visible();
        int rowH = 18;
        maxScroll = Math.max(0, mods.size() * rowH - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }
        d.popScale();
        d.clip(s(listX), s(listY), s(listW), s(listH));
        d.pushScale(0, 0, k);
        int y = listY - Math.round(smoothScroll);
        for (Module m : mods) {
            if (y + rowH >= listY && y <= listY + listH) {
                boolean waiting = binding == m;
                boolean rh = in(mx, my, listX, y, listW, rowH) && in(mx, my, listX, listY, listW, listH);
                if (rh || waiting) {
                    d.roundRect(listX, y, listW, rowH - 1, waiting ? Theme.withAlpha(a, 0x20) : Theme.ROW_HOVER);
                }
                Object stack = pc.platform().icon(Icons.of(m));
                if (stack != null) {
                    d.item(stack, listX + 3, y + 1);
                }
                d.text(d.trim(m.displayName(), listW - 100), listX + 23, y + 5, rh ? Theme.TEXT : Theme.MUTED, false);
                String val = waiting ? "Press a key…" : Keys.name(m.key);
                int bw = Math.max(44, d.width(val) + 12);
                int bx = listX + listW - 6 - bw;
                float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
                d.roundRect(bx, y + 2, bw, rowH - 5, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse)) : Theme.FIELD);
                d.roundOutline(bx, y + 2, bw, rowH - 5, waiting ? a : Theme.HAIRLINE);
                d.text(val, bx + (bw - d.width(val)) / 2, y + 5, waiting ? a : m.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
                if (y + rowH / 2 >= listY && y + rowH / 2 <= listY + listH) {
                    hit("bindrow:" + m.id, bx, y + 2, bw, rowH - 5, null);
                }
            }
            y += rowH;
        }
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
    }

    // ------------------------------------------------------- settings page

    private static final int SET_H = 22;

    private void hit(String id, int x, int y, int w, int h, Setting setting) {
        hits.add(new Hit(id, x, y, w, h, setting));
    }

    private void renderPage(Draw d, int mx, int my, Platform p, float dt, long now, int a) {
        Module m = page;
        hits.clear();
        float t = FadeDraw.progress(anim(pageOpenedAt), 160);
        float e = 1f - (1f - t) * (1f - t);
        int ox = Math.round((1f - e) * 8);  // slides in from the right a little
        int x = listX + ox;
        int w = listW;

        // Header: back, name and description, the feature's own switch
        boolean bh = in(mx, my, x, listY + 3, 16, 16);
        d.roundRect(x, listY + 3, 16, 16, bh ? Theme.withAlpha(a, 0x40) : Theme.FIELD);
        d.roundOutline(x, listY + 3, 16, 16, bh ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
        arrowLeft(d, x + 6, listY + 7, bh ? Theme.TEXT : Theme.MUTED);
        hit("back", x, listY + 3, 16, 16, null);
        d.text(d.trim(m.displayName(), w - 66), x + 22, listY + 2, Theme.TEXT, false);
        d.text(d.trim(m.description, w - 66), x + 22, listY + 13, Theme.FAINT, false);
        if (m != allHud) {
            boolean th = in(mx, my, x + w - 30, listY + 6, 22, 10);
            MenuStyle.toggle(d, x + w - 32, listY + 6, 24, 11, animate(m, dt), a, th);
            hit("toggle", x + w - 32, listY + 3, 26, 16, null);
        }
        d.rect(x, listY + 25, w - 4, 1, Theme.HAIRLINE);

        int top = listY + 28;
        int viewH = listH - 28;
        boolean global = m == allHud;
        List<Setting> list = pageRows(m);
        int nOwn = ownCount(m, list);
        int contentH = global ? 0 : SET_H + 6;  // the keybind row
        if (m instanceof HudModule && !global) {
            contentH += 40;
        }
        contentH += list.size() * (SET_H + 1) + (nOwn < list.size() ? 18 : 0);
        for (Setting st : list) {
            if (st instanceof PixelGridSetting) {
                contentH += GRID_H - SET_H;
            } else if (st instanceof PlayerListSetting) {
                contentH += Math.max(1, ((PlayerListSetting) st).rows.get().size()) * (SET_H + 1);
            }
        }
        if (!list.isEmpty()) {
            contentH += 24;
        }
        maxScroll = Math.max(0, contentH - viewH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }

        d.popScale();
        d.clip(s(listX), s(top), s(listW), s(viewH));
        d.pushScale(0, 0, k);
        boolean inView = in(mx, my, listX, top, listW, viewH) && dropdown == null;   // an open list: nothing behind it lights up
        int y = top + 2 - Math.round(smoothScroll);

        // Keybind: a key that switches this feature while playing.
        if (!global) {
            boolean waiting = binding == m;
            boolean rh = inView && in(mx, my, x, y, w - 4, SET_H);
            if (rh || waiting) {
                d.roundRect(x, y, w - 4, SET_H, Theme.withAlpha(waiting ? a : 0xFFFFFF, waiting ? 0x18 : 0x0E));
            }
            d.text("Keybind", x + 6, y + 7, rh || waiting ? Theme.TEXT : Theme.MUTED, false);
            String hint = waiting ? "Esc to cancel, Backspace for none" : "Toggle key";
            d.text(d.trim(hint, w / 2 - 20), x + 6 + d.width("Keybind  "), y + 7, Theme.FAINT, false);
            String val = waiting ? "Press a key\u2026" : Keys.name(m.key);
            int bw2 = Math.max(44, d.width(val) + 16);
            int right = x + w - 8;
            int clearW = !waiting && m.key >= 0 ? 14 : 0;
            int bx = right - bw2 - clearW;
            boolean bhv = inView && in(mx, my, bx, y + 3, bw2, 16);
            float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
            d.roundRect(bx, y + 3, bw2, 16, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse))
                    : bhv ? Theme.ROW_HOVER : Theme.FIELD);
            d.roundOutline(bx, y + 3, bw2, 16, waiting || bhv ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
            d.text(val, bx + (bw2 - d.width(val)) / 2, y + 7, waiting ? a : m.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
            hit("bind", bx, y + 3, bw2, 16, null);
            if (clearW > 0) {
                int cx = right - 9, cy = y + 8;
                boolean chv = inView && in(mx, my, cx - 3, cy - 3, 12, 12);
                int xc = chv ? Theme.TEXT : Theme.FAINT;
                for (int i = 0; i < 5; i++) {
                    d.rect(cx + i, cy + i, 1, 1, xc);
                    d.rect(cx + 4 - i, cy + i, 1, 1, xc);
                }
                hit("unbind", cx - 3, cy - 3, 12, 12, null);
            }
            y += SET_H + 1;
            d.rect(x + 6, y, w - 16, 1, 0x0CFFFFFF);
            y += 5;
        }

        if (m instanceof HudModule && !global) {
            String label = "Customize look & position";
            int bw = Math.min(w - 8, d.width(label) + 26);
            boolean ch = inView && in(mx, my, x, y, bw, 18);
            d.roundRect(x, y, bw, 18, Theme.withAlpha(a, ch ? 0xFF : 0x2A));
            d.roundOutline(x, y, bw, 18, Theme.withAlpha(a, ch ? 0xFF : 0xA0));
            pencil(d, x + 7, y + 5, ch ? 0xFF15171C : Theme.withAlpha(a, 0xFF));
            d.text(label, x + 19, y + 5, ch ? 0xFF15171C : Theme.withAlpha(a, 0xFF), false);
            hit("customize", x, y, bw, 18, null);
            d.text(d.trim("Size, colors and background", w - 8),
                    x + 1, y + 23, Theme.FAINT, false);
            y += 40;
        }

        if (list.isEmpty() && !search.isEmpty()) {
            d.text("No settings match \"" + d.trim(search, w - 80) + "\"", x + 6, y + 6, Theme.MUTED, false);
        }
        for (int i = 0; i < list.size(); i++) {
            Setting st = list.get(i);
            if (i == nOwn) {   // the HUD item's look, under its own settings
                heading(d, "Style", x, y + 2);
                y += 18;
            }
            boolean rh = inView && in(mx, my, x, y, w - 4, SET_H);
            if (rh) {
                d.roundRect(x, y, w - 4, SET_H, Theme.withAlpha(0xFFFFFF, 0x0E));
            }
            d.text(d.trim(st.label, w / 2), x + 6, y + 7, rh ? Theme.TEXT : Theme.MUTED, false);
            int right = x + w - 8;
            String id = "set:" + st.id;
            if (!st.isDefault()) {   // a small arrow back to how it came, like Lunar's
                int rx = x + 10 + Math.min(d.width(st.label), w / 2);
                boolean rhv = inView && in(mx, my, rx - 2, y + 4, 13, 13);
                MenuIcons.draw(d, "reset", rx, y + 6, rhv ? a : Theme.withAlpha(Theme.FAINT, 0xC0));
                hit("rs:" + i, rx - 2, y + 4, 13, 13, st);
            }
            if (st instanceof BoolSetting) {
                BoolSetting b = (BoolSetting) st;
                float target = b.value ? 1f : 0f;
                Float cur = settingKnob.get(st);
                float v = cur == null ? target : cur;
                v = v < target ? Math.min(target, v + dt * 9f) : Math.max(target, v - dt * 9f);
                settingKnob.put(st, v);
                MenuStyle.toggle(d, right - 24, y + 5, 24, 11, v, a, rh);
                hit(id, x, y, w - 4, SET_H, st);
            } else if (st instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) st;
                String val = sl.text();
                int vw = Math.max(42, Math.max(d.width(val),
                        Math.max(d.width(sl.textAt(sl.min)), d.width(sl.textAt(sl.max)))) + 8);
                d.roundRect(right - vw, y + 4, vw, 15, 3, Theme.FIELD);
                d.text(val, right - (vw + d.width(val)) / 2, y + 7, Theme.TEXT, false);
                int tw = Math.max(40, Math.min(110, w - 16 - w / 2 - vw));
                int tx = right - vw - 8 - tw;
                boolean active = draggingSlider == sl;
                int fill = Math.round(sl.fraction() * tw);
                d.roundRect(tx, y + 10, tw, 3, 1, 0xFF42444C);
                if (fill > 0) d.roundRect(tx, y + 10, fill, 3, 1, a);
                int cx = tx + fill;
                if (active || rh) d.roundRect(cx - 6, y + 5, 13, 13, 6, Theme.withAlpha(a, 0x35));
                d.roundRect(cx - 4, y + 7, 9, 9, 4, 0xFF101116);
                d.roundRect(cx - 3, y + 8, 7, 7, 3, 0xFFF8F9FB);
                hit(id, tx - 3, y + 2, tw + 6, SET_H - 4, st);
                sliderTrack.put(sl, new int[] {tx, tw});
            } else if (st instanceof TextSetting) {
                TextSetting ts = (TextSetting) st;
                boolean focused = typing == ts;
                int fw = Math.max(70, Math.min(150, w / 2 - 8));
                int bx = right - fw;
                boolean fh = inView && in(mx, my, bx, y + 3, fw, 16);
                d.roundRect(bx, y + 3, fw, 16, Theme.FIELD);
                d.roundOutline(bx, y + 3, fw, 16, focused ? Theme.withAlpha(a, 0xE0) : fh ? Theme.withAlpha(a, 0x90) : Theme.HAIRLINE);
                boolean caret = focused && (now / 500) % 2 == 0;
                String shown = ts.value.isEmpty() && !focused ? ts.placeholder : ts.value;
                // Long text shows its end while typing, its start otherwise.
                String fit = focused ? tail(d, shown, fw - 14) : d.trim(shown, fw - 10);
                d.text(fit + (caret ? "_" : ""), bx + 5, y + 7,
                        ts.value.isEmpty() && !focused ? Theme.FAINT : Theme.TEXT, false);
                hit(id, bx, y + 3, fw, 16, st);
            } else if (st instanceof KeySetting) {
                KeySetting ks = (KeySetting) st;
                boolean waiting = bindingKey == ks;
                String val = waiting ? "Press a key\u2026" : ks.text();
                int bw2 = Math.max(44, d.width(val) + 16);
                int bx = right - bw2;
                boolean bhv = inView && in(mx, my, bx, y + 3, bw2, 16);
                float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
                d.roundRect(bx, y + 3, bw2, 16, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse))
                        : bhv ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(bx, y + 3, bw2, 16, waiting || bhv ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
                d.text(val, bx + (bw2 - d.width(val)) / 2, y + 7, waiting ? a : ks.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
                hit(id, bx, y + 3, bw2, 16, st);
            } else if (st instanceof PlayerListSetting) {
                // players near you, each with Hide / Show and Report
                List<String[]> rows = ((PlayerListSetting) st).rows.get();
                int ry = y + SET_H + 1;
                if (rows.isEmpty()) {
                    d.text(((PlayerListSetting) st).empty, x + 14, ry + 7, Theme.FAINT, false);
                    ry += SET_H + 1;
                }
                for (String[] r : rows) {
                    boolean hidden = "hidden".equals(r[2]);
                    d.text(d.trim(r[1], w / 2), x + 14, ry + 7, hidden ? Theme.FAINT : Theme.TEXT, false);
                    int bx2 = x + w - 8;
                    String[][] btns = {{"report", "Report"}, {"hide", hidden ? "Show" : "Hide"}};
                    for (String[] b : btns) {
                        int bw2 = d.width(b[1]) + 14;
                        bx2 -= bw2;
                        boolean pbh = inView && in(mx, my, bx2, ry + 3, bw2, 16);
                        boolean danger = b[0].equals("report");
                        d.roundRect(bx2, ry + 3, bw2, 16, pbh ? Theme.withAlpha(danger ? Theme.BAD : a, 0x50) : Theme.CARD);
                        d.roundOutline(bx2, ry + 3, bw2, 16, pbh ? (danger ? Theme.BAD : a) : Theme.HAIRLINE);
                        d.text(b[1], bx2 + 7, ry + 7, pbh ? Theme.TEXT : Theme.MUTED, false);
                        hit("cape-" + b[0] + ":" + r[0], bx2, ry + 3, bw2, 16, st);
                        bx2 -= 4;
                    }
                    ry += SET_H + 1;
                }
                y = ry - (SET_H + 1);
            } else if (st instanceof PixelGridSetting) {
                // a grid of pixels to click, with the result at real size beside it
                PixelGridSetting pg = (PixelGridSetting) st;
                int n = PixelGridSetting.N, cell = 7;
                int gx = x + 6, gy = y + 18;
                d.roundRect(gx - 3, gy - 3, n * cell + 6, n * cell + 6, 3, 0x60000000);
                for (int yy = 0; yy < n; yy++) {
                    for (int xx = 0; xx < n; xx++) {
                        boolean onPx = pg.get(xx, yy);
                        boolean hv = inView && in(mx, my, gx + xx * cell, gy + yy * cell, cell, cell);
                        int c = onPx ? (hv ? Theme.mix(a, 0xFFFFFFFF, 0.3f) : a)
                                : hv ? 0x50FFFFFF : ((xx + yy) % 2 == 0 ? 0x18FFFFFF : 0x0CFFFFFF);
                        d.rect(gx + xx * cell, gy + yy * cell, cell - 1, cell - 1, c);
                        hit("px:" + (yy * n + xx), gx + xx * cell, gy + yy * cell, cell, cell, st);
                    }
                }
                d.rect(gx + n * cell / 2 - 1, gy - 6, 1, 3, Theme.FAINT);   // marks for the centre
                d.rect(gx - 6, gy + n * cell / 2 - 1, 3, 1, Theme.FAINT);
                int px2 = gx + n * cell + 16;
                d.text("Preview", px2, gy, Theme.FAINT, false);
                d.roundRect(px2, gy + 12, 40, 40, 4, 0xFF6B8F4E);
                for (int yy = 0; yy < n; yy++) {
                    for (int xx = 0; xx < n; xx++) {
                        if (pg.get(xx, yy)) {
                            d.rect(px2 + 20 - n + xx * 2, gy + 32 - n + yy * 2, 2, 2, 0xFFFFFFFF);
                        }
                    }
                }
                String clr = "Clear";
                int cw2 = d.width(clr) + 14;
                boolean ch = inView && in(mx, my, px2, gy + 60, cw2, 14);
                d.roundRect(px2, gy + 60, cw2, 14, ch ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(px2, gy + 60, cw2, 14, ch ? Theme.MUTED : Theme.HAIRLINE);
                d.text(clr, px2 + 7, gy + 63, Theme.TEXT, false);
                hit("pxclear", px2, gy + 60, cw2, 14, st);
                y += GRID_H - SET_H;
            } else if (st instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) st;
                String val = cs.chroma ? "RGB" : cs.hex();
                int pw2 = d.width(val) + 30;
                int bx = right - pw2;
                boolean ph2 = inView && in(mx, my, bx, y + 3, pw2, 16);
                boolean editing = picker.open() && picker.target() instanceof SettingTarget
                        && ((SettingTarget) picker.target()).setting == cs;
                d.roundRect(bx, y + 3, pw2, 16, ph2 ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(bx, y + 3, pw2, 16, ph2 || editing ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
                // the swatch: the colour over a checker (so see-through shows), or a rainbow
                d.rect(bx + 4, y + 6, 10, 10, 0xFF888888);
                d.rect(bx + 4, y + 6, 5, 5, 0xFF555555);
                d.rect(bx + 9, y + 11, 5, 5, 0xFF555555);
                if (cs.chroma) {
                    for (int c = 0; c < 10; c++) {
                        d.rect(bx + 4 + c, y + 6, 1, 10, cs.value(c / 10.0));
                    }
                } else {
                    d.rect(bx + 4, y + 6, 10, 10, cs.color);
                }
                d.text(val, bx + 19, y + 7, Theme.TEXT, false);
                hit(id, bx, y + 3, pw2, 16, st);
            } else if (st instanceof ChoiceSetting || st instanceof ActionSetting) {
                boolean action = st instanceof ActionSetting;
                String val = action ? ((ActionSetting) st).button() : ((ChoiceSetting) st).text();
                int pw2 = d.width(val) + (action ? 14 : 22);
                int bx = right - pw2;
                boolean ph2 = inView && in(mx, my, bx, y + 3, pw2, 16) && dropdown == null;
                boolean open = !action && dropdown == st;
                d.roundRect(bx, y + 3, pw2, 16, action ? Theme.withAlpha(a, ph2 ? 0xFF : 0x2A)
                        : ph2 || open ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(bx, y + 3, pw2, 16, action ? Theme.withAlpha(a, 0xA0)
                        : ph2 || open ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
                int tc = action ? (ph2 ? 0xFF15171C : Theme.withAlpha(a, 0xFF)) : Theme.TEXT;
                d.text(val, bx + 7, y + 7, tc, false);
                if (!action) {  // a small arrow: opens the list of options
                    arrowDown(d, bx + pw2 - 11, y + 9, ph2 || open ? a : Theme.FAINT);
                }
                hit(id, bx, y + 3, pw2, 16, st);
            }
            y += SET_H;
            if (i < list.size() - 1) {
                d.rect(x + 6, y, w - 16, 1, 0x0CFFFFFF);
            }
            y += 1;
        }
        if (!list.isEmpty()) {
            String reset = global ? "Reset every item's look" : "Reset options";
            int rw = d.width(reset) + 14;
            int ry = y + 6;
            boolean rh = inView && in(mx, my, x, ry, rw, 14);
            d.roundRect(x, ry, rw, 14, rh ? Theme.ROW_HOVER : 0x00000000);
            d.roundOutline(x, ry, rw, 14, rh ? Theme.MUTED : Theme.HAIRLINE);
            d.text(reset, x + 7, ry + 3, rh ? Theme.TEXT : Theme.MUTED, false);
            hit("reset", x, ry, rw, 14, null);
        }
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        // Only what's inside the view can be clicked.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.setting != null || h.id.equals("customize") || h.id.equals("reset")
                    || h.id.equals("bind") || h.id.equals("unbind")) {
                if (h.y + h.h / 2 < top || h.y + h.h / 2 > top + viewH) {
                    hits.remove(i);
                }
            }
        }
        scrollbar(d, top, viewH, inView, a);
        picker.render(d, mx, my, a);
        renderDropdown(d, mx, my, a);

    }

    /** The end of a text that fits in maxW (what you're typing stays in view). */
    private static String tail(Draw d, String s, int maxW) {
        if (d.width(s) <= maxW) {
            return s;
        }
        int i = 0;
        while (i < s.length() && d.width("\u2026" + s.substring(i)) > maxW) {
            i++;
        }
        return "\u2026" + s.substring(i);
    }

    /** Where each slider's bar was last drawn (x, width): dragging follows the bar, not other things on its row. */
    private final java.util.Map<SliderSetting, int[]> sliderTrack = new java.util.IdentityHashMap<>();

    private void slideTo(SliderSetting sl, int mx) {
        int[] tr = sliderTrack.get(sl);
        if (tr == null) {
            return;
        }
        float t = (mx - tr[0]) / (float) Math.max(1, tr[1]);
        sl.set(sl.min + Math.max(0f, Math.min(1f, t)) * (sl.max - sl.min));
    }

    private boolean pageClicked(int mx, int my) {
        if (dropdown != null) {
            return dropdownClicked(mx, my);
        }
        if (picker.open()) {
            picker.click(mx, my);
            pc.markDirty();
            return true;
        }
        if (typing != null || bindingKey != null) {
            boolean onIt = false;
            for (Hit h : hits) {
                if ((h.setting == typing || h.setting == bindingKey) && in(mx, my, h.x, h.y, h.w, h.h)) {
                    onIt = true;
                }
            }
            if (!onIt) {
                if (typing != null) {
                    pc.markDirty();
                }
                typing = null;
                bindingKey = null;
            }
        }
        for (Hit h : new ArrayList<Hit>(hits)) {
            if (!in(mx, my, h.x, h.y, h.w, h.h)) {
                continue;
            }
            if (h.id.equals("bind")) {
                binding = binding == page ? null : page;
                return true;
            } else if (h.id.equals("unbind")) {
                page.key = Keys.NONE;
                binding = null;
            } else if (h.id.equals("back")) {
                closePage();
            } else if (h.id.equals("toggle")) {
                page.setEnabled(!page.enabled());
            } else if (h.id.equals("customize")) {
                HudModule hm = (HudModule) page;
                if (!hm.enabled()) {
                    hm.setEnabled(true);
                }
                editingHud = true;
                editor.openFor(hm);
            } else if (h.id.equals("reset")) {
                if (page == allHud) {
                    for (Module mm : pc.modules()) {
                        if (mm instanceof HudModule) {
                            HudModule hm = (HudModule) mm;
                            float fx = hm.fx, fy = hm.fy;
                            hm.resetStyle();   // the look, not where it is
                            hm.fx = fx;
                            hm.fy = fy;
                        }
                    }
                    allHud.resetStyle();
                } else {
                    for (Setting st : page.settings()) {
                        st.reset();
                    }
                    if (page instanceof HudModule) {
                        HudModule hm = (HudModule) page;
                        float fx = hm.fx, fy = hm.fy, sc = hm.scale;
                        hm.resetStyle();
                        hm.fx = fx;
                        hm.fy = fy;
                        hm.scale = sc;
                    }
                }
            } else if (h.id.startsWith("cape-hide:")) {
                net.peregrine.client.core.modules.Cosmetics.toggleHidden(h.id.substring(10));
            } else if (h.id.startsWith("cape-report:")) {
                net.peregrine.client.core.modules.Cosmetics.report(h.id.substring(12));
            } else if (h.id.startsWith("px:")) {
                int i = Integer.parseInt(h.id.substring(3));
                ((PixelGridSetting) h.setting).toggle(i % PixelGridSetting.N, i / PixelGridSetting.N);
            } else if (h.id.equals("pxclear")) {
                ((PixelGridSetting) h.setting).clear();
            } else if (h.id.startsWith("rs:")) {
                h.setting.reset();
                styleChanged(h.setting);
            } else if (h.setting instanceof BoolSetting) {
                ((BoolSetting) h.setting).value = !((BoolSetting) h.setting).value;
                styleChanged(h.setting);
            } else if (h.setting instanceof ChoiceSetting) {
                openDropdown((ChoiceSetting) h.setting, h);
                return true;
            } else if (h.setting instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) h.setting;
                picker.show(new SettingTarget(cs), h.x + h.w - ColorPicker.W, h.y + h.h + 3,
                        Math.round(pc.platform().screenWidth() / k), Math.round(pc.platform().screenHeight() / k));
            } else if (h.setting instanceof TextSetting) {
                typing = typing == h.setting ? null : (TextSetting) h.setting;
                bindingKey = null;
                return true;
            } else if (h.setting instanceof KeySetting) {
                bindingKey = bindingKey == h.setting ? null : (KeySetting) h.setting;
                typing = null;
                return true;
            } else if (h.setting instanceof ActionSetting) {
                ((ActionSetting) h.setting).action.run();
            } else if (h.setting instanceof SliderSetting) {
                draggingSlider = (SliderSetting) h.setting;
                dragClickedAt = mx;
                dragEventAt = dragSeenAt = Integer.MIN_VALUE;
                slideTo(draggingSlider, mx);
                styleChanged(draggingSlider);
            }
            pc.markDirty();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ page rows: own settings, then the look

    /** What a page lists: the feature's settings that match the search, then (HUD items) its look. */
    private List<Setting> pageRows(Module m) {
        List<Setting> out = new ArrayList<Setting>();
        String q = search.trim().toLowerCase();
        if (m != allHud) {
            for (Setting st : m.settings()) {
                if (st.visible() && (q.isEmpty() || st.label.toLowerCase().contains(q))) {
                    out.add(st);
                }
            }
        }
        if (m instanceof HudModule) {
            for (Setting st : styleOf((HudModule) m)) {
                if (q.isEmpty() || st.label.toLowerCase().contains(q)) {
                    out.add(st);
                }
            }
        }
        return out;
    }

    private int ownCount(Module m, List<Setting> rows) {
        if (!(m instanceof HudModule)) {
            return rows.size();
        }
        List<Setting> style = styleOf((HudModule) m);
        int n = 0;
        for (Setting st : rows) {
            if (!style.contains(st)) {
                n++;
            }
        }
        return n;
    }

    /** A HUD item's look as settings (kept in step with the item every frame). */
    private List<Setting> styleOf(HudModule h) {
        List<Setting> rows = styleRows.get(h);
        if (rows == null) {
            rows = new ArrayList<Setting>();
            rows.add(new SliderSetting("st_size", "Size", 40, 300, 5, 50, "%.0f%%"));
            rows.add(new ColorSetting("st_text", "Text color", Theme.TEXT, false));
            rows.add(new ColorSetting("st_label", "Label color", Theme.AMBER, false));
            rows.add(new TextSetting("st_name", "Label text", "", 24, "Its usual label"));
            rows.add(new BoolSetting("st_brackets", "[Brackets]", false));
            rows.add(new BoolSetting("st_shadow", "Text shadow", true));
            rows.add(new BoolSetting("st_outline", "Text outline", false));
            rows.add(new BoolSetting("st_bg", "Background", true));
            rows.add(new ColorSetting("st_bgcolor", "Background color", Theme.HUD_BG, true));
            rows.add(new SliderSetting("st_radius", "Corners", 0, HudModule.MAX_RADIUS, 1, HudModule.DEFAULT_RADIUS, "%.0f px"));
            rows.add(new BoolSetting("st_border", "Border", false));
            rows.add(new ColorSetting("st_bordercolor", "Border color", Theme.AMBER, true));
            rows.add(new SliderSetting("st_borderwidth", "Border thickness", 1, 3, 1, 1, "%.0f px"));
            if (h.hasValueColors()) {
                rows.add(new BoolSetting("st_values", "Color-coded values", false));
            }
            styleRows.put(h, rows);
        }
        pullStyle(h, rows);
        return rows;
    }

    private static Setting find(List<Setting> rows, String id) {
        for (Setting s : rows) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The item's look into its rows. */
    private void pullStyle(HudModule h, List<Setting> rows) {
        int acc = pc.accent();
        ((SliderSetting) find(rows, "st_size")).value = Math.round(h.scale * 100);
        ColorSetting text = (ColorSetting) find(rows, "st_text");
        text.color = h.textColor != 0 ? h.textColor : Theme.TEXT;
        text.chroma = h.chroma;
        ((ColorSetting) find(rows, "st_label")).color = h.labelColor != 0 ? h.labelColor : acc;
        TextSetting name = (TextSetting) find(rows, "st_name");
        if (typing != name) {
            name.value = h.customLabel;
        }
        ((BoolSetting) find(rows, "st_brackets")).value = h.brackets;
        ((BoolSetting) find(rows, "st_shadow")).value = h.shadow;
        ((BoolSetting) find(rows, "st_outline")).value = h.outline;
        ((BoolSetting) find(rows, "st_bg")).value = h.background;
        ((ColorSetting) find(rows, "st_bgcolor")).color = (h.backgroundAlpha << 24)
                | ((h.backgroundColor != 0 ? h.backgroundColor : Theme.HUD_BG) & 0xFFFFFF);
        ((SliderSetting) find(rows, "st_radius")).value = h.radius;
        ((BoolSetting) find(rows, "st_border")).value = h.border;
        ((ColorSetting) find(rows, "st_bordercolor")).color = h.borderColor != 0 ? h.borderColor
                : Theme.withAlpha(h.labelColor != 0 ? h.labelColor : acc, 0xD0);
        ((SliderSetting) find(rows, "st_borderwidth")).value = h.borderWidth;
        Setting v = find(rows, "st_values");
        if (v != null) {
            ((BoolSetting) v).value = h.valueColors;
        }
    }

    /** One row of an item's look back into the item (or, on "All HUD items", into every item). */
    private void pushStyle(HudModule h, Setting st) {
        int acc = pc.accent();
        switch (st.id) {
            case "st_size":
                h.scale = Math.max(HudModule.MIN_SCALE, Math.min(HudModule.MAX_SCALE, ((SliderSetting) st).value / 100f));
                break;
            case "st_text": {
                ColorSetting c = (ColorSetting) st;
                h.textColor = c.color == Theme.TEXT ? 0 : c.color;
                h.chroma = c.chroma;
                break;
            }
            case "st_label": {
                int c = ((ColorSetting) st).color;
                h.labelColor = (c & 0xFFFFFF) == (acc & 0xFFFFFF) || c == Theme.AMBER ? 0 : c;
                break;
            }
            case "st_name":
                h.customLabel = ((TextSetting) st).value.trim();
                h.mainLabel = null;
                break;
            case "st_brackets":
                h.brackets = ((BoolSetting) st).value;
                break;
            case "st_shadow":
                h.shadow = ((BoolSetting) st).value;
                break;
            case "st_outline":
                h.outline = ((BoolSetting) st).value;
                break;
            case "st_bg":
                h.background = ((BoolSetting) st).value;
                break;
            case "st_bgcolor": {
                int c = ((ColorSetting) st).color;
                h.backgroundAlpha = (c >>> 24) & 0xFF;
                h.backgroundColor = (c & 0xFFFFFF) == (Theme.HUD_BG & 0xFFFFFF) ? 0 : (0xFF000000 | c);
                break;
            }
            case "st_radius":
                h.radius = Math.round(((SliderSetting) st).value);
                break;
            case "st_border":
                h.border = ((BoolSetting) st).value;
                break;
            case "st_bordercolor": {
                int c = ((ColorSetting) st).color;
                h.borderColor = c == Theme.AMBER ? 0 : c;
                break;
            }
            case "st_borderwidth":
                h.borderWidth = Math.round(((SliderSetting) st).value);
                break;
            case "st_values":
                h.valueColors = ((BoolSetting) st).value;
                break;
            default:
                break;
        }
    }

    /** A setting on the page changed: if it's part of a HUD item's look, apply it. */
    private void styleChanged(Setting st) {
        if (!(page instanceof HudModule) || st == null || !st.id.startsWith("st_")) {
            return;
        }
        if (page == allHud) {
            pushStyle(allHud, st);
            for (Module mm : pc.modules()) {
                if (mm instanceof HudModule) {
                    pushStyle((HudModule) mm, st);
                }
            }
        } else {
            pushStyle((HudModule) page, st);
        }
        pc.markDirty();
    }

    /** The picker editing a colour setting. */
    private final class SettingTarget implements ColorPicker.Target {
        final ColorSetting setting;

        SettingTarget(ColorSetting s) {
            setting = s;
        }

        public int get() {
            return setting.color;
        }

        public void set(int argb) {
            setting.color = setting.alpha ? argb : (0xFF000000 | argb);
            styleChanged(setting);
            pc.markDirty();
        }

        public boolean chroma() {
            return setting.chroma;
        }

        public void setChroma(boolean on) {
            setting.chroma = on;
            styleChanged(setting);
            pc.markDirty();
        }

        public boolean alpha() {
            return setting.alpha;
        }
    }

    private void closePage() {
        if (typing != null) {
            pc.markDirty();
        }
        typing = null;
        bindingKey = null;
        binding = null;
        dropdown = null;
        page = null;
        search = view == 0 ? listSearch : "";
        searchFocused = false;
        draggingSlider = null;
        hits.clear();
        scroll = 0;
        smoothScroll = 0;
    }

    /** A gear: a ring with eight teeth, drawn pixel by pixel (10x10). */
    private static void gear(Draw d, int x, int y, int c) {
        d.rect(x + 3, y + 1, 4, 1, c);
        d.rect(x + 3, y + 8, 4, 1, c);
        d.rect(x + 1, y + 3, 1, 4, c);
        d.rect(x + 8, y + 3, 1, 4, c);
        d.rect(x + 2, y + 2, 1, 1, c);
        d.rect(x + 7, y + 2, 1, 1, c);
        d.rect(x + 2, y + 7, 1, 1, c);
        d.rect(x + 7, y + 7, 1, 1, c);
        // teeth
        d.rect(x + 4, y, 2, 1, c);
        d.rect(x + 4, y + 9, 2, 1, c);
        d.rect(x, y + 4, 1, 2, c);
        d.rect(x + 9, y + 4, 1, 2, c);
        d.rect(x + 1, y + 1, 1, 1, c);
        d.rect(x + 8, y + 1, 1, 1, c);
        d.rect(x + 1, y + 8, 1, 1, c);
        d.rect(x + 8, y + 8, 1, 1, c);
        // hub
        d.rect(x + 4, y + 4, 2, 2, c);
    }

    private static void arrowLeft(Draw d, int x, int y, int c) {
        for (int i = 0; i < 4; i++) {
            d.rect(x + i, y + 3 - i, 1, 1, c);
            d.rect(x + i, y + 3 + i, 1, 1, c);
        }
        d.rect(x, y + 3, 1, 1, c);
    }

    // ------------------------------------------------------------ dropdowns (settings with a few options)

    /** The setting whose options are listed below it, or null. */
    private ChoiceSetting dropdown;
    private int ddAnchorX, ddAnchorY, ddAnchorW, ddAnchorH;
    private int ddHover = -1, ddScroll;
    private long ddOpenedAt;
    /** Where the open list was drawn: x, y, w, row height, first shown option, rows shown. */
    private final int[] ddBox = new int[6];
    private static final int DD_ROW = 16;

    private void openDropdown(ChoiceSetting cs, Hit h) {
        if (dropdown == cs) {
            dropdown = null;   // clicking it again closes it
            return;
        }
        dropdown = cs;
        ddAnchorX = h.x;
        ddAnchorY = h.y;
        ddAnchorW = h.w;
        ddAnchorH = h.h;
        ddHover = cs.index;
        ddScroll = 0;
        ddOpenedAt = System.currentTimeMillis();
    }

    /** The open list of options: below its button (above when there's no room), the current one ticked. */
    private void renderDropdown(Draw d, int mx, int my, int a) {
        ChoiceSetting cs = dropdown;
        if (cs == null) {
            return;
        }
        Platform p = pc.platform();
        int sw = Math.round(p.screenWidth() / k), sh = Math.round(p.screenHeight() / k);
        int w = ddAnchorW;
        for (String o : cs.options) {
            w = Math.max(w, d.width(o) + 30);
        }
        w = Math.min(w, sw - 8);
        int fit = Math.max(3, (sh - 16) / DD_ROW);
        int shown = Math.min(cs.options.length, Math.min(fit, 10));
        int h = shown * DD_ROW + 4;
        int x = Math.max(4, Math.min(sw - w - 4, ddAnchorX + ddAnchorW - w));
        int below = ddAnchorY + ddAnchorH + 3, above = ddAnchorY - 3 - h;
        int y = below + h <= sh - 4 || above < 4 ? Math.min(below, sh - 4 - h) : above;
        // keep the chosen option in view when the list scrolls
        int maxScroll = cs.options.length - shown;
        ddScroll = Math.max(0, Math.min(maxScroll, ddScroll));
        float t = Math.min(1f, (System.currentTimeMillis() - ddOpenedAt) / 110f);
        int drop = Math.round((1f - t) * 4);
        y += below == y ? -drop : drop;
        ddBox[0] = x;
        ddBox[1] = y;
        ddBox[2] = w;
        ddBox[3] = DD_ROW;
        ddBox[4] = ddScroll;
        ddBox[5] = shown;
        Draw f = t < 1f ? new FadeDraw(d, t) : d;
        f.roundRect(x, y, w, h, 3, MenuStyle.WINDOW);
        f.roundOutline(x, y, w, h, 3, MenuStyle.BORDER);
        int hoverRow = in(mx, my, x, y + 2, w, shown * DD_ROW) ? (my - y - 2) / DD_ROW + ddScroll : -1;
        if (hoverRow >= 0) {
            ddHover = hoverRow;
        }
        for (int r = 0; r < shown; r++) {
            int i = r + ddScroll;
            int ry = y + 2 + r * DD_ROW;
            boolean chosen = i == cs.index, hover = i == ddHover;
            if (hover) {
                f.roundRect(x + 2, ry, w - 4, DD_ROW, 4, chosen ? Theme.withAlpha(a, 0x50) : Theme.ROW_HOVER);
            } else if (chosen) {
                f.roundRect(x + 2, ry, w - 4, DD_ROW, 4, Theme.withAlpha(a, 0x28));
            }
            if (chosen) {   // a tick
                int cx = x + 7, cy = ry + 8;
                for (int j = 0; j < 3; j++) {
                    f.rect(cx + j, cy - 1 + j, 1, 2, a);
                }
                for (int j = 0; j < 5; j++) {
                    f.rect(cx + 3 + j, cy + 1 - j, 1, 2, a);
                }
            }
            f.text(f.trim(cs.options[i], w - 26), x + 20, ry + 4, chosen ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED, false);
        }
        if (maxScroll > 0) {   // a thin scrollbar
            int track = shown * DD_ROW - 4;
            int bar = Math.max(8, track * shown / cs.options.length);
            int by = y + 4 + (track - bar) * ddScroll / maxScroll;
            f.roundRect(x + w - 4, by, 2, bar, 1, 0x60FFFFFF);
        }
    }

    /** A click while the list is open: picks an option, or closes the list. Always used. */
    private boolean dropdownClicked(int mx, int my) {
        ChoiceSetting cs = dropdown;
        dropdown = null;
        if (in(mx, my, ddBox[0], ddBox[1] + 2, ddBox[2], ddBox[5] * ddBox[3])) {
            int i = (my - ddBox[1] - 2) / ddBox[3] + ddBox[4];
            if (i >= 0 && i < cs.options.length) {
                cs.index = i;
                styleChanged(cs);
                pc.markDirty();
            }
        } else if (in(mx, my, ddAnchorX, ddAnchorY, ddAnchorW, ddAnchorH)) {
            return true;   // its own button: just closes it
        }
        return true;
    }

    /** Keys while the list is open: up / down move, Enter picks, Esc closes. */
    private boolean dropdownKey(int key) {
        ChoiceSetting cs = dropdown;
        if (key == KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        if (key == 265 || key == 264) {   // up, down
            ddHover = Math.max(0, Math.min(cs.options.length - 1, (ddHover < 0 ? cs.index : ddHover) + (key == 264 ? 1 : -1)));
            if (ddHover < ddScroll) {
                ddScroll = ddHover;
            } else if (ddHover >= ddScroll + ddBox[5]) {
                ddScroll = ddHover - ddBox[5] + 1;
            }
            return true;
        }
        if (key == KEY_ENTER || key == 32) {
            if (ddHover >= 0 && ddHover < cs.options.length) {
                cs.index = ddHover;
                styleChanged(cs);
                pc.markDirty();
            }
            dropdown = null;
            return true;
        }
        return true;   // nothing else while it's open
    }

    private static void arrowDown(Draw d, int x, int y, int c) {
        for (int i = 0; i < 3; i++) {
            d.rect(x + i, y + i, 1, 1, c);
            d.rect(x + 4 - i, y + i, 1, 1, c);
        }
    }

    private static void arrowRight(Draw d, int x, int y, int c) {
        for (int i = 0; i < 3; i++) {
            d.rect(x + i, y + i, 1, 1, c);
            d.rect(x + i, y + 4 - i, 1, 1, c);
        }
    }

    /** A small pencil, for "customize". */
    private static void pencil(Draw d, int x, int y, int c) {
        for (int i = 0; i < 6; i++) {
            d.rect(x + 1 + i, y + 6 - i, 2, 1, c);
        }
        d.rect(x, y + 7, 1, 1, c);
    }

    /** Eases a per-row value towards a target (for smooth hover highlights). */
    private static float ease(Map<Module, Float> map, Module m, float target, float step) {
        Float cur = map.get(m);
        float v = cur == null ? 0f : cur;
        v = v < target ? Math.min(target, v + step) : Math.max(target, v - step);
        map.put(m, v);
        return v;
    }

    /** A tiny magnifying glass, drawn pixel by pixel so it stays crisp. */
    private static void magnifier(Draw d, int x, int y, int c) {
        d.rect(x + 1, y, 3, 1, c);
        d.rect(x + 1, y + 4, 3, 1, c);
        d.rect(x, y + 1, 1, 3, c);
        d.rect(x + 4, y + 1, 1, 3, c);
        d.rect(x + 4, y + 4, 1, 1, c);
        d.rect(x + 5, y + 5, 1, 1, c);
        d.rect(x + 6, y + 6, 1, 1, c);
    }

    /** Moves a switch's knob towards on or off; returns 0 (off) to 1 (on). */
    private float animate(Module m, float dt) {
        float target = m.enabled() ? 1f : 0f;
        Float cur = knob.get(m);
        float v = cur == null ? target : cur;
        if (v < target) {
            v = Math.min(target, v + dt * 9f);
        } else if (v > target) {
            v = Math.max(target, v - dt * 9f);
        }
        knob.put(m, v);
        return v;
    }

    /** A rounded switch; on is filled with the accent and the knob slides right. */
    private static void toggle(Draw d, int x, int y, float on, int accent, boolean hover) {
        MenuStyle.toggle(d, x, y, 22, 10, on, accent, hover);
    }

    // -------------------------------------------------------------- input

    /** Returns true if the click was used. button: 0 = left, 1 = right. Screen pixels. */
    private boolean canSearch(MenuPanel pn) {
        return page != null || view == 0 || (pn != null && !pn.placeholder().isEmpty());
    }

    private boolean onResizeGrip(int mx, int my) {
        return resizeEdgesAt(mx, my) != 0;
    }

    /** Which edges of the window the pointer is on (a corner gives two), like a desktop window frame. */
    private int resizeEdgesAt(int mx, int my) {
        if (!in(mx, my, px, py, pw, ph)) {
            return 0;
        }
        int e = 0, edge = 5, corner = 13;
        boolean nearX = mx < px + corner || mx >= px + pw - corner;
        boolean nearY = my < py + corner || my >= py + ph - corner;
        if (mx < px + edge || (nearY && mx < px + corner)) e |= 1;
        if (mx >= px + pw - edge || (nearY && mx >= px + pw - corner)) e |= 2;
        if (my < py + edge || (nearX && my < py + corner)) e |= 4;
        if (my >= py + ph - edge || (nearX && my >= py + ph - corner)) e |= 8;
        // the top strip is the title bar; only its very top rows and corners resize
        if ((e & 12) == 4 && (e & 3) == 0 && my >= py + 3) e = 0;
        return e;
    }

    private static int cursorFor(int e) {
        if (e == 1 || e == 2) return 1;
        if (e == 4 || e == 8) return 2;
        return e == 5 || e == 10 ? 3 : 4;
    }

    public boolean mouseClicked(int screenMx, int screenMy, int button) {
        SelfTest.count("click@" + screenMx + "," + screenMy + "/" + button);
        Platform p = pc.platform();
        if (editingHud) {
            editor.mouseClicked(screenMx, screenMy, button);
            if (editor.wantsExit()) {
                editingHud = false;
                pc.markDirty();
            }
            return true;
        }
        int mx = Math.round(screenMx / k);
        int my = Math.round(screenMy / k);
        if (dropdown != null) {   // an open list takes the click, wherever it lands
            return dropdownClicked(mx, my);
        }
        if (binding != null) {
            boolean onBind = false;
            for (Hit h : hits) {
                if ((h.id.equals("bind") || h.id.startsWith("bindrow:")) && in(mx, my, h.x, h.y, h.w, h.h)) {
                    onBind = true;
                }
            }
            if (!onBind) {
                binding = null;  // clicking anywhere else cancels
                return true;
            }
        }
        if (button == 0 && sbClicked(mx, my)) {
            return true;
        }
        if (button == 1 && page == null && view == 0 && in(mx, my, listX, listY, listW, listH)) {
            // Right-click a tile to open its options, same as clicking the tile.
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                int[] c = card(i);
                if (in(mx, my, c[0], c[1], c[2], tileH)) {
                    openPage(mods.get(i));
                    return true;
                }
            }
        }
        if (button != 0) {
            return false;
        }
        if (!in(mx, my, px, py, pw, ph) || in(mx, my, closeX, closeY, 16, 16)) {
            // A click outside the window (or on ×) closes it, like any pop-up.
            p.openScreen(p.inWorld() ? Platform.Screen.NONE : Platform.Screen.TITLE);
            return true;
        }
        searchFocused = canSearch(panel()) && in(mx, my, searchX, searchY, searchW, TABBAR_H);
        if (searchFocused) {
            typing = null;
            return true;
        }
        resizeEdges = resizeEdgesAt(mx, my);
        resizingWindow = resizeEdges != 0;
        movingWindow = in(mx, my, px + 6, py + 4, Math.max(0, menuBtnX - px - 8), TITLE_H - 9);
        if (resizingWindow || movingWindow) {
            windowX = px;
            windowY = py;
            windowW = pw;
            windowH = ph;
            startX = px;
            startY = py;
            startW = pw;
            startH = ph;
            dragX = mx;
            dragY = my;
            if (!resizingWindow) {
                dragX = mx - px;
                dragY = my - py;
            }
            return true;
        }
        for (Hit h : new ArrayList<Hit>(sideHits)) {
            if (in(mx, my, sideX, sideY, sideW, sideH) && in(mx, my, h.x, h.y, h.w, h.h)) {
                Node n = node(h.id);
                if (n != null && n.kids.length > 0) {
                    toggleGroup(n);
                } else {
                    choose(h.id);
                }
                return true;
            }
        }
        if (view == 0 && page == null && in(mx, my, onlyX, filterY, onlyW, TABBAR_H)) {
            onlyOn = !onlyOn;
            listShownAt = System.currentTimeMillis();
            scroll = 0;
            return true;
        }
        if (overChips(mx, my)) {
            for (Hit h : chipHits) {
                if (in(mx, my, h.x, h.y, h.w, h.h)) {
                    group = h.id.isEmpty() ? null : h.id;
                    listShownAt = System.currentTimeMillis();
                    scroll = 0;
                    smoothScroll = 0;
                    return true;
                }
            }
            return true;
        }
        if (in(mx, my, editX, editY, editW, editH)) {
            editingHud = true;
            editor.open();
            return true;
        }
        if (page != null) {
            pageClicked(mx, my);
            return true;
        }
        if (view == 3) {
            MenuPanel pn = panel();
            if (pn != null) {
                pn.click(mx, my, button);
            }
            return true;
        }
        if (view != 0) {
            viewClicked(mx, my);
            return true;
        }
        if (in(mx, my, listX, listY, listW, listH)) {
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                int[] c = card(i);
                if (in(mx, my, c[0], c[1], c[2], tileH)) {
                    Module m = mods.get(i);
                    int[] tg = toggleAt(c);
                    int[] st = starAt(c);
                    if (in(mx, my, tg[0] - 3, c[1], tg[2] + 10, tileH)) {  // the switch
                        m.setEnabled(!m.enabled());
                        pc.markDirty();
                    } else if (in(mx, my, st[0] - 2, st[1] - 2, 15, 15)) {  // the star
                        m.favorite = !m.favorite;
                        pc.markDirty();
                    } else {  // the options button, or anywhere else on the tile: its options
                        openPage(m);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private void viewClicked(int mx, int my) {
        if (picker.open()) {
            picker.click(mx, my);
            pc.markDirty();
            return;
        }
        for (Hit h : new ArrayList<Hit>(hits)) {
            if (!in(mx, my, h.x, h.y, h.w, h.h)) {
                continue;
            }
            if (h.id.equals("accent-pick")) {
                picker.show(new ColorPicker.Target() {
                    public int get() {
                        return pc.accent();
                    }

                    public void set(int argb) {
                        pc.setAccent(argb);
                    }

                    public boolean chroma() {
                        return false;
                    }

                    public void setChroma(boolean on) {
                    }

                    public boolean alpha() {
                        return false;
                    }

                    public boolean chromaAllowed() {
                        return false;
                    }
                }, h.x - ColorPicker.W + 12, h.y + h.h + 3, Math.round(pc.platform().screenWidth() / k),
                        Math.round(pc.platform().screenHeight() / k));
                return;
            }
            if (h.id.startsWith("accent:")) {
                pc.setAccent(ACCENTS[Integer.parseInt(h.id.substring(7))]);
            } else if (h.id.startsWith("menubg:")) {
                pc.setMenuBackground(Integer.parseInt(h.id.substring(7)));
            } else if (h.id.startsWith("choice:rows:")) {
                pc.setCompactRows(h.id.endsWith(":1"));
                listShownAt = System.currentTimeMillis();
            } else if (h.id.startsWith("choice:anim:")) {
                pc.setAnimations(h.id.endsWith(":0"));
            } else if (h.id.startsWith("module:")) {
                Module m = pc.module(h.id.substring(7));
                m.setEnabled(!m.enabled());
            } else if (h.id.equals("reset-hud")) {
                for (Module m : pc.modules()) {
                    if (m instanceof HudModule) {
                        ((HudModule) m).resetStyle();
                    }
                }
            } else if (h.id.equals("all-hud")) {
                openPage(allHud);
                return;
            } else if (h.id.equals("profile-new")) {
                Profiles.saveNew(pc);
            } else if (h.id.startsWith("profile-load:")) {
                Profiles.use(pc, h.id.substring(13));
                styleRows.clear();
            } else if (h.id.startsWith("profile-save:")) {
                Profiles.save(pc, h.id.substring(13));
            } else if (h.id.startsWith("profile-del:")) {
                Profiles.delete(pc, h.id.substring(12));
            } else if (h.id.equals("clear-keys")) {
                for (Module m : pc.modules()) {
                    m.key = Keys.NONE;
                }
            } else if (h.id.startsWith("bindrow:")) {
                Module m = pc.module(h.id.substring(8));
                binding = binding == m ? null : m;
                return;
            }
            pc.markDirty();
            return;
        }
    }

    private static Node node(String id) {
        for (Node n : SIDEBAR) {
            if (n != null && n.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    public boolean mouseDragged(int screenMx, int screenMy) {
        if (movingWindow || resizingWindow) {
            Platform p = pc.platform();
            int sw = Math.round(p.screenWidth() / k), sh = Math.round(p.screenHeight() / k);
            int mxs = Math.round(screenMx / k), mys = Math.round(screenMy / k);
            if (resizingWindow) {
                int dx = mxs - dragX, dy = mys - dragY;
                int minW = Math.min(startW, 340), minH = Math.min(startH, 200);
                int left = startX, top = startY, right = startX + startW, bottom = startY + startH;
                if ((resizeEdges & 1) != 0) left = clamp(left + dx, 8, right - minW);
                if ((resizeEdges & 2) != 0) right = clamp(right + dx, left + minW, sw - 8);
                if ((resizeEdges & 4) != 0) top = clamp(top + dy, 8, bottom - minH);
                if ((resizeEdges & 8) != 0) bottom = clamp(bottom + dy, top + minH, sh - 8);
                windowX = left;
                windowY = top;
                windowW = right - left;
                windowH = bottom - top;
            } else {
                windowX = clamp(mxs - dragX, 8, sw - pw - 8);
                windowY = clamp(mys - dragY, 8, sh - ph - 8);
            }
            return true;
        }
        MenuPanel pn = editingHud ? null : panel();
        if (pn instanceof LockerPanel && ((LockerPanel) pn).dragged(Math.round(screenMx / k))) {
            return true;
        }
        if (!editingHud && picker.drag(Math.round(screenMx / k), Math.round(screenMy / k))) {
            return true;
        }
        if (draggingSlider != null && !editingHud) {
            dragEventAt = Math.round(screenMx / k);   // the slider itself follows the pointer in render()
            return true;
        }
        if (sbDragging) {
            return true;   // followed in render()
        }
        return editingHud && editor.mouseDragged(screenMx, screenMy);
    }

    public boolean mouseReleased() {
        if (sbDragging) {
            sbDragging = false;
            return true;
        }
        if (movingWindow || resizingWindow) {
            movingWindow = false;
            resizingWindow = false;
            resizeEdges = 0;
            return true;
        }
        MenuPanel pn = editingHud ? null : panel();
        if (pn instanceof LockerPanel && ((LockerPanel) pn).released(Math.round(lastMx / k))) {
            return true;
        }
        picker.release();
        if (draggingSlider != null) {
            styleChanged(draggingSlider);
            draggingSlider = null;
            pc.markDirty();
            return true;
        }
        return editingHud && editor.mouseReleased();
    }

    /** amount: positive = scroll up. */
    public boolean mouseScrolled(double amount, int screenMx, int screenMy) {
        int mx = Math.round(screenMx / k), my = Math.round(screenMy / k);
        if (dropdown != null) {
            if (in(mx, my, ddBox[0], ddBox[1], ddBox[2], ddBox[5] * ddBox[3] + 4)) {
                ddScroll -= (int) Math.signum(amount);   // the list itself scrolls
            } else {
                dropdown = null;   // scrolling the page moves the button away: close it
            }
            return true;
        }
        if (editingHud) {
            return editor.mouseScrolled(amount, screenMx, screenMy);
        }
        if (in(mx, my, sideX, sideY, sideW, sideH)) {
            sideScroll = clamp(sideScroll - (int) Math.round(amount * 24), 0, maxSideScroll);
            sideSlide = -1;
            return true;
        }
        if (overChips(mx, my)) {
            chipScroll = clamp(chipScroll - (int) Math.round(amount * 30), 0, chipMax);
            return true;
        }
        if (!in(mx, my, listX, listY, listW + 8, listH)) {
            return false;
        }
        MenuPanel pn = page == null ? panel() : null;
        if (pn != null) {
            pn.scrolled(amount);
            return true;
        }
        scroll -= (int) Math.round(amount * (CARD_H + GAP) * 0.6);
        scroll = clamp(scroll, 0, maxScroll);
        return true;
    }

    /** Returns true if the key was used. Esc is used by the HUD editor and settings pages (to go back). */
    public boolean keyPressed(int key) {
        if (dropdown != null) {
            return dropdownKey(key);
        }
        if (bindingKey != null) {
            if (key == KEY_BACKSPACE || key == Keys.KEY_DELETE) {
                bindingKey.key = Keys.NONE;
            } else if (key != KEY_ESCAPE) {
                bindingKey.key = key;
                boundAt = System.currentTimeMillis();
            }
            bindingKey = null;
            pc.markDirty();
            return true;
        }
        if (typing != null) {
            if (key == KEY_BACKSPACE) {
                typing.backspace();
                styleChanged(typing);
            } else if (key == KEY_ESCAPE || key == KEY_ENTER) {
                typing = null;
                pc.markDirty();
            }
            return true;  // every other key just types (via charTyped)
        }
        if (searchFocused && (key == KEY_ESCAPE || (key == KEY_ENTER && (page != null || view != 0 || search.isEmpty())))) {
            searchFocused = false;   // done searching: keys go back to the game
            return true;
        }
        if (binding != null) {
            if (key == KEY_BACKSPACE || key == Keys.KEY_DELETE) {
                binding.key = Keys.NONE;
            } else if (key != KEY_ESCAPE) {
                binding.key = key;
                boundAt = System.currentTimeMillis();
            }
            binding = null;
            pc.markDirty();
            return true;
        }
        if (editingHud) {
            if (key == KEY_ESCAPE || key == KEY_ENTER) {
                editingHud = false;
                return true;
            }
            return false;
        }
        if (page != null) {
            if (key == KEY_ESCAPE && picker.open()) {
                picker.close();
                return true;
            }
            if (key == KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                return true;
            }
            if (key == KEY_BACKSPACE && searchFocused) return true;
            if (key == KEY_ESCAPE || key == KEY_BACKSPACE) {
                closePage();
                return true;
            }
            return false;
        }
        MenuPanel own = panel();
        if (own != null && own.key(key)) {
            return true;
        }
        if (key == KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
            if (view == 0) listSearch = search;
            scroll = 0;
            smoothScroll = 0;
            MenuPanel pn = panel();
            if (pn != null) {
                pn.searchChanged(search);
            }
            return true;
        }
        if (key == KEY_ENTER && view == 0 && !search.isEmpty()) {
            List<Module> mods = visible();
            if (mods.size() == 1) {  // search narrowed to one: Enter toggles it
                mods.get(0).setEnabled(!mods.get(0).enabled());
                pc.markDirty();
                return true;
            }
        }
        return false;
    }

    /** True while something in the menu takes the keyboard: a search bar you clicked, a text box, a key being set. */
    public boolean wantsTyping() {
        return searchFocused || typing != null || bindingKey != null || binding != null;
    }

    public boolean charTyped(char c) {
        if (System.currentTimeMillis() - boundAt < 250) {
            boundAt = 0;
            return true;  // the character of the key that was just bound
        }
        if (binding != null || bindingKey != null) {
            return true;
        }
        if (typing != null) {
            typing.type(c);
            styleChanged(typing);
            return true;
        }
        if (editingHud || Character.isISOControl(c) || search.length() >= 24) {
            return false;
        }
        boolean free = searchFocused;   // typing only searches once the search bar is clicked
        if (search.isEmpty() && c == ' ') {
            return free;
        }
        if (page != null) {  // on a feature's page, typing searches its settings
            if (!free) {
                return false;
            }
            search += c;
            scroll = 0;
            smoothScroll = 0;
            return true;
        }
        MenuPanel pn = panel();
        if (pn != null && pn.typed(c)) {
            return true;
        }
        if (pn != null) {  // a page with its own search (Browse searches Modrinth)
            if (pn.placeholder().isEmpty() || !free) {
                return false;
            }
            search += c;
            pn.searchChanged(search);
            return true;
        }
        if (view != 0 || !free) {
            return false;
        }
        search += c;
        listSearch = search;
        searchFocused = true;
        scroll = 0;
        smoothScroll = 0;  // results start at the top straight away
        return true;
    }
}
