package net.peregrine.client.core;

/**
 * Light Client's pause menu, laid out like the main menu: the mark and name at the left, a card
 * with you and where you're playing, then a stack of full-width buttons (Resume on top, Leave at
 * the bottom). Under the stack, when music is playing on the computer: the song with its cover
 * art, and back, play / pause and skip buttons. The buttons can be dragged to swap places.
 */
public final class PauseMenu {

    static final int W = 236, PAD = 12, TILE_H = 38, GAP = 6;

    private final Peregrine pc;
    private final TitleMenu.Button resume = new TitleMenu.Button("Resume game", "play", Platform.Screen.NONE, true);
    private final TitleMenu.Button modules = new TitleMenu.Button("Modules", "grid", Platform.Screen.PEREGRINE_MENU, false);
    private final TitleMenu.Button options = new TitleMenu.Button("Options", "sliders", Platform.Screen.OPTIONS, false);
    private final TitleMenu.Button locker = new TitleMenu.Button("Locker", "shirt", Platform.Screen.PEREGRINE_MENU, false);
    private final TitleMenu.Button more = new TitleMenu.Button("Advancements, stats & more", "more", Platform.Screen.VANILLA_PAUSE, false);
    private TitleMenu.Button leave = new TitleMenu.Button("Leave server", "leave", null, false);
    private final int[] box = new int[4];
    static final int NP_H = 58, NP_GAP = 6;
    private final int[] np = new int[4];             // the now-playing card (w = 0 when hidden)
    private final int[][] ctl = new int[3][4];       // back, play / pause, skip
    private final float[] ctlGlow = new float[3];
    private static final String[] CTL = {"previous", "toggle", "next"};
    private final Holidays.Scene holidayScene = new Holidays.Scene(16);
    private String coverId;
    private int coverVer = -1;
    private final java.util.Map<String, String> serverTex = new java.util.HashMap<String, String>();
    private long shownAt = -1, lastFrame;
    private int musicOffset;
    private final int[] buttonOffsets = new int[IDS.length];

    PauseMenu(Peregrine pc) {
        this.pc = pc;
    }

    private static final String[] IDS = {"resume", "modules", "options", "locker", "more", "leave"};
    /** Dragging a button to a new place: which one, where it was grabbed, and where the mouse is. */
    private TitleMenu.Button grabbed;
    private int grabX, grabY, dragX, dragY;
    private long grabAt;
    private boolean dragging;

    private TitleMenu.Button byId(String id) {
        switch (id) {
            case "resume": return resume;
            case "modules": return modules;
            case "options": return options;
            case "locker": return locker;
            case "more": return more;
            case "leave": return leave;
            default: return null;
        }
    }

    /** The buttons in their places (your order, any missing ones after). */
    private TitleMenu.Button[] buttons() {
        TitleMenu.Button[] out = new TitleMenu.Button[6];
        int n = 0;
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String id : order().split(",")) {
            TitleMenu.Button b = byId(id.trim());
            if (b != null && seen.add(id.trim()) && n < 6) {
                out[n++] = b;
            }
        }
        for (String id : IDS) {
            if (seen.add(id)) {
                out[n++] = byId(id);
            }
        }
        return out;
    }

    private String order() {
        net.peregrine.client.core.modules.PauseMenuToggle m = (net.peregrine.client.core.modules.PauseMenuToggle) pc.module("pause_menu");
        return m == null ? net.peregrine.client.core.modules.PauseMenuToggle.DEFAULT_ORDER : m.order.value;
    }

    private String idOf(TitleMenu.Button b) {
        for (String id : IDS) {
            if (byId(id) == b) {
                return id;
            }
        }
        return "";
    }

    /** Swaps two buttons' places and saves it. */
    private void swap(TitleMenu.Button a, TitleMenu.Button b) {
        TitleMenu.Button[] bs = buttons();
        StringBuilder sb = new StringBuilder();
        for (TitleMenu.Button x : bs) {
            TitleMenu.Button put = x == a ? b : x == b ? a : x;
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(idOf(put));
        }
        net.peregrine.client.core.modules.PauseMenuToggle m = (net.peregrine.client.core.modules.PauseMenuToggle) pc.module("pause_menu");
        if (m != null) {
            m.order.value = sb.toString();
            pc.markDirty();
        }
    }

    public void shown() {
        grabbed = null;
        dragging = false;
        shownAt = System.currentTimeMillis();
        lastFrame = 0;
    }

    private int left, logoY, rowGap = 5;
    private final int[] who = new int[4];   // the player card: x, y, w, h

    private void layout(Platform p) {
        String l = p.localWorld() ? "Save and quit to title" : "Leave server";
        if (!leave.label.equals(l)) {
            leave = new TitleMenu.Button(l, "leave", null, false);
        }
        int sw = p.screenWidth(), sh = p.screenHeight();
        boolean music = showMusic() && NowPlaying.get() != null;
        boolean tight = sh < 260;
        int rowH = tight ? 15 : 20;
        rowGap = tight ? 3 : 5;
        int head = tight ? 28 : 40;
        int whoH = tight ? 0 : 30;
        left = Math.max(12, sw / 15);
        int w = Math.min(182, Math.max(106, sw - 2 * left));
        int stack = head + (whoH > 0 ? whoH + 7 : 0) + 6 * (rowH + rowGap) - rowGap;
        int total = stack + (music && !tight ? NP_GAP + NP_H : 0);
        logoY = Math.max(7, (sh - total) / 2);
        int y = logoY + head;
        if (whoH > 0) {
            who[0] = left;
            who[1] = y;
            who[2] = w;
            who[3] = whoH;
            y += whoH + 7;
        } else {
            who[2] = 0;
        }
        box[0] = left;
        box[1] = logoY;
        box[2] = w;
        box[3] = stack;
        for (TitleMenu.Button b : buttons()) {
            b.place(left, y, w, rowH);
            y += rowH + rowGap;
        }
        if (music && !tight) {
            np[0] = left;
            np[1] = y - rowGap + NP_GAP;
            np[2] = w;
            np[3] = NP_H;
            int cx = np[0] + np[2] - 10 - 58;   // three buttons at the top right
            int[] widths = {16, 20, 16};
            for (int i = 0; i < 3; i++) {
                ctl[i][0] = cx;
                ctl[i][1] = np[1] + 8 + (i == 1 ? 0 : 2);
                ctl[i][2] = widths[i];
                ctl[i][3] = i == 1 ? 20 : 16;
                cx += widths[i] + 3;
            }
        } else {
            np[2] = 0;
        }
    }

    private boolean showMusic() {
        net.peregrine.client.core.modules.PauseMenuToggle m = (net.peregrine.client.core.modules.PauseMenuToggle) pc.module("pause_menu");
        return m == null || m.music.value;
    }

    /** The cover art as a texture (reloaded when the song changes), or null. */
    private String coverTexture(Platform p) {
        int v = NowPlaying.coverVersion();
        if (v != coverVer) {
            coverVer = v;
            java.nio.file.Path f = NowPlaying.coverFile();
            coverId = f == null ? null : p.loadImage(f);
        }
        return coverId;
    }

    /** The server's own icon as a texture, or null until it's known. */
    private String serverTexture(Platform p, String address) {
        if (address == null || p.localWorld()) {
            return null;
        }
        int px = (int) Math.round(20 * p.guiScale());
        String key = address + "@" + px;
        String id = serverTex.get(key);
        if (id == null) {
            java.nio.file.Path f = ServerIcons.sized(address, p.serverIcon(), px);
            if (f != null) {
                id = p.loadImage(f);
                serverTex.put(key, id == null ? "" : id);
            }
        }
        return id == null || id.isEmpty() ? null : id;
    }

    private static int holiday(Peregrine pc) {
        Module m = pc.module("main_menu");
        return m instanceof net.peregrine.client.core.modules.MainMenu ? ((net.peregrine.client.core.modules.MainMenu) m).holidayTheme() : Holidays.NONE;
    }

    public void render(Draw raw, int mx, int my) {
        SelfTest.count("pause");
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 260);
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        layout(p);
        int a = pc.accent();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = p.screenWidth(), sh = p.screenHeight();
        // the game dims, a little more at the edges
        if (net.peregrine.client.core.modules.Menu3D.get() == null || !p.supports("menu_3d")) {   // a 3D panel doesn't dim the world
            d.rect(0, 0, sw, sh, 0x48000000);
            Glass.vignette(d, sw, sh, 0x90);
        }

        int holiday = holiday(pc);
        if (holiday != Holidays.NONE) {
            holidayScene.render(d, holiday, 0, 0, sw, sh, dt);
        }

        // the whole column slides in from the left, like the main menu
        int rise = 0;
        int slide = -Math.round((1f - Glass.backOut(t)) * 10);

        // the mark and the name
        float pulse = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2);
        d.glow(left - 4 + slide, logoY - 2, 26, 26, Theme.withAlpha(a, Math.round(0x30 + 0x20 * pulse)));
        TitleMenu.lightMark(d, left + 1 + slide, logoY + 3, a);
        d.textScaled(TitleMenu.BRAND, left + 26 + slide, logoY, Theme.TEXT, 1.6f, true);
        d.text("Game menu  \u00B7  Minecraft " + p.minecraftVersion(), left + 27 + slide, logoY + 17, Theme.MUTED, true);

        // you, and where you are
        if (who[2] > 0) {
            int x = who[0] + slide, y = who[1], w = who[2], h = who[3];
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 3, 0xD81C1C1C);
            d.rect(x + 1, y + 6, 2, h - 12, Theme.withAlpha(a, 0xC0));
            int[] face = SkinFaces.get(p.playerUuid());
            int hx = x + 9;
            String serverId = d.hasTextures() ? serverTexture(p, p.serverAddress()) : null;
            int[] serverGrid = serverId == null && !p.localWorld() ? ServerIcons.get(p.serverAddress(), p.serverIcon()) : null;
            int icon = 20, iy = y + (h - icon) / 2;
            if (serverId != null || serverGrid != null) {
                if (serverId != null) {
                    d.texture(serverId, hx, iy, icon, icon, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
                } else {
                    d.pushScale(hx, iy, icon / (float) ServerIcons.CELLS);
                    for (int i = 0; i < serverGrid.length; i++) {
                        if ((serverGrid[i] >>> 24) != 0) {
                            d.rect(i % ServerIcons.CELLS, i / ServerIcons.CELLS, 1, 1, serverGrid[i]);
                        }
                    }
                    d.popScale();
                }
                if (face != null) {
                    d.rect(hx + 12, iy + 12, 10, 10, 0xFF181818);
                    SkinFaces.draw(d, face, hx + 13, iy + 13, 8);
                }
            } else if (face != null) {
                SkinFaces.draw(d, face, hx, iy, icon);
            } else {
                Glass.logo(d, hx, iy, 2.2f, 0xFFFFFFFF);
            }
            hx += icon + 8;
            String where = p.localWorld() ? "Singleplayer" : p.serverName() != null && !p.serverName().isEmpty()
                    ? p.serverName() : p.serverAddress() == null ? "Playing" : p.serverAddress();
            int right = x + w - 8;
            if (!p.localWorld() && p.ping() > 0) {   // a ping chip
                String ping = p.ping() + " ms";
                int pw = d.width(ping) + 14, px = right - pw;
                int col = p.ping() < 80 ? Theme.GOOD : p.ping() < 160 ? Theme.OK : Theme.BAD;
                d.roundRect(px, y + 9, pw, 12, 6, Theme.withAlpha(col, 0x30));
                d.roundRect(px + 4, y + 13, 4, 4, 2, col);
                d.text(ping, px + 10, y + 11, Theme.TEXT, false);
                right = px - 4;
            }
            d.text(d.trim(p.playerName(), right - hx), hx, y + 5, Theme.TEXT, true);
            d.text(d.trim(where, right - hx), hx, y + 16, Theme.MUTED, false);
        }

        int i = 0;
        for (TitleMenu.Button b : buttons()) {
            float ti = FadeDraw.progress(shownAt + 60 + i * 40L, 260);
            Draw bd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, Math.min(1f, ti * 1.4f))) : d;
            int sx = -Math.round((1f - Glass.backOut(ti)) * 8);
            buttonOffsets[i++] = 0;
            int bx0 = b.x, by0 = b.y;
            b.x += sx;
            if (dragging && b == grabbed) {   // the button you're moving follows the mouse
                b.x += dragX - grabX;
                b.y += dragY - grabY;
            }
            boolean hover = b.contains(mx - sx, my) || (dragging && b == grabbed);
            b.glow = Glass.ease(b.glow, hover, dt);
            boolean pressed = b == grabbed && hover && !dragging;
            drawRow(bd, b, a, pressed);
            b.x = bx0;
            b.y = by0;
        }
        if (np[2] > 0) {
            float ti = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 300, 300);
            Draw nd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, ti)) : d;
            musicOffset = rise + Math.round((1f - Glass.backOut(ti)) * 8);
            drawMusic(nd, p, mx, my - musicOffset, musicOffset, dt, a);
        }
        if (holiday != Holidays.NONE) {
            Holidays.greetingChip(d, holiday, sw / 2, Math.max(2, logoY - 20));
        }
    }

    /** A stacked button, drawn like the main menu's: lifts on hover, accent bar, chevron. */
    private void drawRow(Draw d, TitleMenu.Button b, int accent, boolean pressed) {
        float g = b.glow;
        boolean danger = b == leave;
        boolean primary = b == resume;
        int x = b.x, y = b.y - (pressed ? 0 : Math.round(g)), w = b.w, h = b.h;
        if (danger) {
            MenuStyle.block(d, x, y, w, h, 0xFF4A2A2E, g, pressed);
        } else {
            MenuStyle.button(d, x, y, w, h, primary, g, accent, pressed);
        }
        y += pressed ? 1 : 0;
        if (g > 0.01f && !primary && !danger) {   // the accent bar grows in from the middle
            int bh = Math.round((h - 8) * g);
            d.rect(x + 1, y + (h - bh) / 2, 2, bh, Theme.withAlpha(accent, Math.round(0xFF * g)));
        }
        int fg = primary ? Theme.onColor(accent) : Theme.mix(0xFFDDDDDD, 0xFFFFFFFF, g);
        int iy = y + (h - 9) / 2;
        MenuIcons.draw(d, b.icon, x + 7, iy, fg);
        String label = b == more ? "Advancements, stats & more" : b.label;
        boolean chevron = !danger && h >= 19 && w > 90;
        d.text(d.trim(label, w - (chevron ? 34 : 24)), x + 21 + Math.round(g * 2), y + (h - d.lineHeight()) / 2 + 1, fg,
                Theme.lightText(fg));
        if (chevron) {
            MenuIcons.draw(d, "chevron", x + w - 13 + Math.round(g * 2), iy,
                    Theme.withAlpha(primary ? 0xFFFFFFFF : accent, Math.round(0x60 + 0x9F * g)));
        }
    }

    private static final String[] PREV = {"a....a", "a...aa", "a..aaa", "a.aaaa", "a..aaa", "a...aa", "a....a"};
    private static final String[] NEXT = {"a....a", "aa...a", "aaa..a", "aaaa.a", "aaa..a", "aa...a", "a....a"};
    private static final String[] PLAY = {"a....", "aa...", "aaa..", "aaaa.", "aaa..", "aa...", "a...."};
    private static final String[] PAUSE = {"aa.aa", "aa.aa", "aa.aa", "aa.aa", "aa.aa", "aa.aa", "aa.aa"};

    /** The song playing: cover art, title and artist, how far through it is, and the three buttons. */
    private void drawMusic(Draw d, Platform p, int mx, int my, int lift, float dt, int a) {
        NowPlaying.Track t = NowPlaying.get();
        if (t == null) {
            return;
        }
        int x = np[0], y = np[1] + lift, w = np[2], h = np[3];
        d.shadow(x, y, w, h);
        d.roundRect(x, y, w, h, 3, 0xD81C1C1C);
        // the cover: sharp if we have it, the small colour grid otherwise
        int cs = 42, cx = x + 8, cy = y + 8;
        String cover = d.hasTextures() ? coverTexture(p) : null;
        int[] grid = NowPlaying.cover();
        d.roundRect(cx - 1, cy - 1, cs + 2, cs + 2, 4, 0x40FFFFFF);
        if (cover != null) {
            d.texture(cover, cx, cy, cs, cs, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
        } else if (grid != null) {
            int n = NowPlaying.ART;
            d.pushScale(cx, cy, cs / (float) n);
            for (int i = 0; i < grid.length; i++) {
                d.rect(i % n, i / n, 1, 1, grid[i]);
            }
            d.popScale();
        } else {
            d.roundRect(cx, cy, cs, cs, 3, Theme.withAlpha(a, 0x50));
            MenuIcons.draw(d, "play", cx + cs / 2 - 4, cy + cs / 2 - 4, Theme.TEXT);
        }
        int tx = cx + cs + 8, tw = ctl[0][0] - 6 - tx;
        d.text(d.trim(t.title, tw), tx, y + 9, Theme.TEXT, true);
        String sub = t.artist.isEmpty() ? t.app : t.app.isEmpty() ? t.artist : t.artist + " \u00B7 " + t.app;
        d.text(d.trim(sub, tw), tx, y + 20, Theme.MUTED, false);
        // progress
        int bx = tx, bw = x + w - 10 - tx, by = y + 36;
        d.roundRect(bx, by, bw, 3, 1, 0x30FFFFFF);
        if (t.length > 0) {
            long pos = t.positionNow();
            int fill = Math.round(bw * Math.min(1f, pos / (float) t.length));
            if (fill > 0) {
                d.roundRect(bx, by, fill, 3, 1, a);
            }
            d.textScaled(NowPlaying.time(pos), bx, by + 6, Theme.FAINT, 0.75f, false);
            String len = NowPlaying.time(t.length);
            d.textScaled(len, bx + bw - Math.round(d.width(len) * 0.75f), by + 6, Theme.FAINT, 0.75f, false);
        } else {
            d.textScaled("Live", bx, by + 6, Theme.FAINT, 0.75f, false);
        }
        // back, play / pause, skip
        for (int i = 0; i < 3; i++) {
            int[] c = ctl[i];
            int ccy = c[1] + lift;
            boolean hover = mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3];
            ctlGlow[i] = Glass.ease(ctlGlow[i], hover, dt);
            float g = ctlGlow[i];
            if (i == 1) {
                if (g > 0.01f) {
                    d.glow(c[0] - 2, ccy - 1, c[2] + 4, c[3] + 4, Theme.withAlpha(a, Math.round(0x50 * g)));
                }
                d.roundRect(c[0], ccy, c[2], c[3], c[3] / 2, Theme.mix(a, Theme.mix(a, 0xFFFFFFFF, 0.25f), g));
                int fg = Theme.onColor(a);
                Holidays.sprite(d, t.playing ? PAUSE : PLAY, c[0] + (t.playing ? 8 : 8), ccy + 6, 1, fg);
            } else {
                if (g > 0.01f) {
                    d.roundRect(c[0], ccy, c[2], c[3], c[3] / 2, Theme.withAlpha(0xFFFFFF, Math.round(0x22 * g)));
                }
                int fg = Theme.mix(0xFFC9CDD6, 0xFFFFFFFF, g);
                Holidays.sprite(d, i == 0 ? PREV : NEXT, c[0] + 5, ccy + 4, 1, fg);
            }
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0 || lastFrame == 0) {
            return false;
        }
        grabbed = null;
        dragging = false;
        if (np[2] > 0) {
            int musicY = my - musicOffset;
            for (int i = 0; i < 3; i++) {
                int[] c = ctl[i];
                if (mx >= c[0] && mx < c[0] + c[2] && musicY >= c[1] && musicY < c[1] + c[3]) {
                    NowPlaying.control(CTL[i]);
                    return true;
                }
            }
            if (mx >= np[0] && mx < np[0] + np[2] && musicY >= np[1] && musicY < np[1] + np[3]) {
                return true;   // a click on the card itself does nothing
            }
        }
        TitleMenu.Button b = buttonAt(mx, my);
        if (b != null) {
            grabbed = b;
            grabX = dragX = mx;
            grabY = dragY = my;
            grabAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** Dragging a button: once it's moved a few pixels it follows the mouse. */
    public boolean mouseDragged(int mx, int my) {
        if (grabbed == null) {
            return false;
        }
        dragX = mx;
        dragY = my;
        // only a hold-and-move picks a button up; a click with a bit of wobble is still a click
        if (!dragging && System.currentTimeMillis() - grabAt > 220
                && Math.abs(mx - grabX) + Math.abs(my - grabY) > 12) {
            dragging = true;
        }
        return true;
    }

    /** Letting go: dropped on another button swaps them; a plain click does what the button does. */
    public boolean mouseReleased(int mx, int my) {
        TitleMenu.Button b = grabbed;
        grabbed = null;
        if (b == null) {
            return false;
        }
        TitleMenu.Button target = buttonAt(mx, my);
        if (dragging) {
            dragging = false;
            if (target != null && target != b) {
                swap(b, target);
            }
            return true;
        }
        if (target == b || Math.abs(mx - grabX) + Math.abs(my - grabY) <= 12) {
            return press(b);
        }
        return true;
    }

    private TitleMenu.Button buttonAt(int x, int y) {
        TitleMenu.Button[] buttons = buttons();
        for (int i = buttons.length - 1; i >= 0; i--) {
            if (buttons[i].contains(x, y - buttonOffsets[i])) {
                return buttons[i];
            }
        }
        return null;
    }

    private boolean press(TitleMenu.Button b) {
        Platform p = pc.platform();
        if (b.action == null) {
            p.leaveWorld();
        } else {
            if (b == locker) {
                pc.menu().choose("locker");
            } else if (b == modules) {
                pc.menu().choose("cat:all");
            }
            p.openScreen(b.action);
        }
        return true;
    }

    /** For the self-test: centre of a button, by what it opens (null: Leave). */
    int[] centerOf(Platform.Screen action) {
        layout(pc.platform());
        for (TitleMenu.Button b : buttons()) {
            if (b.action == action && b != locker) {
                return new int[] {b.x + b.w / 2, b.y + b.h / 2};
            }
        }
        return null;
    }

    /** Whether to replace Minecraft's pause menu (the "Peregrine pause menu" toggle). */
    public boolean enabled() {
        Module m = pc.module("pause_menu");
        return m == null || m.enabled();
    }
}
