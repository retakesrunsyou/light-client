package net.peregrine.client.core;

/**
 * Light Client's pause menu: a glass card in the middle of the screen. The server's own icon,
 * your name and where you're playing at the top, a big Resume, four tiles (modules, Options,
 * Locker, and the rest of Minecraft's menu), and Leave at the bottom. Under it, when music is
 * playing on the computer: the song with its cover art, and back, play / pause and skip buttons.
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

    private void layout(Platform p) {
        String l = p.localWorld() ? "Save and quit to title" : "Leave server";
        if (!leave.label.equals(l)) {
            leave = new TitleMenu.Button(l, "leave", null, false);
        }
        int w = Math.min(W + 2 * PAD, p.screenWidth() - 16);
        int inner = w - 2 * PAD;
        int h = PAD + 30 + 24 + GAP + 2 * (TILE_H + GAP) + 18 + PAD;
        boolean music = showMusic() && NowPlaying.get() != null;
        int total = h + (music ? NP_GAP + NP_H : 0);
        box[0] = (p.screenWidth() - w) / 2;
        box[1] = Math.max(4, (p.screenHeight() - total) / 2);
        box[2] = w;
        box[3] = h;
        int x = box[0] + PAD, y = box[1] + PAD + 30;
        TitleMenu.Button[] bs = buttons();   // places: a wide one, four tiles, a bar at the bottom
        bs[0].place(x, y, inner, 24);
        y += 24 + GAP;
        int half = (inner - GAP) / 2;
        bs[1].place(x, y, half, TILE_H);
        bs[2].place(x + half + GAP, y, inner - half - GAP, TILE_H);
        y += TILE_H + GAP;
        bs[3].place(x, y, half, TILE_H);
        bs[4].place(x + half + GAP, y, inner - half - GAP, TILE_H);
        y += TILE_H + GAP;
        bs[5].place(x, y, inner, 18);
        if (music) {
            np[0] = box[0];
            np[1] = box[1] + h + NP_GAP;
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

        // the card rises into place with a little overshoot
        int rise = Math.round((1f - Glass.backOut(t)) * 14);
        int x = box[0], y = box[1] + rise;
        Glass.window(d, x, y, box[2], box[3], 0xD80F1218, a);
        Glass.falcon(d, x + box[2] - 58, y + box[3] - 62, 6f, 0x0AFFFFFF);

        // you, and where you are
        int[] face = SkinFaces.get(p.playerUuid());
        int hx = x + PAD;
        String serverId = d.hasTextures() ? serverTexture(p, p.serverAddress()) : null;
        int[] serverGrid = serverId == null && !p.localWorld() ? ServerIcons.get(p.serverAddress(), p.serverIcon()) : null;
        if (serverId != null || serverGrid != null) {   // the server's own icon, with your face on its corner
            d.roundRect(hx - 1, y + PAD - 1, 22, 22, 4, Theme.withAlpha(a, 0x60));
            if (serverId != null) {
                d.texture(serverId, hx, y + PAD, 20, 20, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
            } else {
                d.pushScale(hx, y + PAD, 20f / ServerIcons.CELLS);
                for (int i = 0; i < serverGrid.length; i++) {
                    if ((serverGrid[i] >>> 24) != 0) {
                        d.rect(i % ServerIcons.CELLS, i / ServerIcons.CELLS, 1, 1, serverGrid[i]);
                    }
                }
                d.popScale();
            }
            if (face != null) {
                d.rect(hx + 12, y + PAD + 12, 10, 10, 0xFF0F1218);
                SkinFaces.draw(d, face, hx + 13, y + PAD + 13, 8);
            }
            hx += 26;
        } else if (face != null) {
            d.roundRect(hx - 1, y + PAD - 1, 22, 22, 4, Theme.withAlpha(a, 0x60));
            SkinFaces.draw(d, face, hx + 1, y + PAD + 1, 18);
            hx += 26;
        } else {
            Glass.logo(d, hx, y + PAD, 2.2f, 0xFFFFFFFF);
            hx += 24;
        }
        d.text(p.playerName(), hx, y + PAD + 2, Theme.TEXT, true);
        String where = p.localWorld() ? "Singleplayer" : p.serverName() != null && !p.serverName().isEmpty()
                ? p.serverName() : p.serverAddress() == null ? "Playing" : p.serverAddress();
        d.text(d.trim(where, box[2] - (hx - x) - 64), hx, y + PAD + 12, Theme.MUTED, false);
        if (!p.localWorld() && p.ping() > 0) {  // a ping chip
            String ping = p.ping() + " ms";
            int pw = d.width(ping) + 14, px = x + box[2] - PAD - pw;
            int col = p.ping() < 80 ? Theme.GOOD : p.ping() < 160 ? Theme.OK : Theme.BAD;
            d.roundRect(px, y + PAD + 4, pw, 12, 6, Theme.withAlpha(col, 0x30));
            d.roundRect(px + 4, y + PAD + 8, 4, 4, 2, col);
            d.text(ping, px + 10, y + PAD + 6, Theme.TEXT, false);
        }

        int i = 0;
        for (TitleMenu.Button b : buttons()) {
            float ti = FadeDraw.progress(shownAt + 60 + i * 40L, 260);
            Draw bd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, Math.min(1f, ti * 1.4f))) : d;
            int offset = rise + Math.round((1f - Glass.backOut(ti)) * 8);
            buttonOffsets[i++] = offset;
            int by = b.y + offset;
            int bx0 = b.x;
            if (dragging && b == grabbed) {   // the button you're moving follows the mouse
                b.x += dragX - grabX;
                by += dragY - grabY;
            }
            boolean hover = b.contains(mx, my - offset) || (dragging && b == grabbed);
            b.glow = Glass.ease(b.glow, hover, dt);
            boolean pressed = b == grabbed && hover && !dragging;
            int contentY = by + (pressed ? 1 : 0);
            if (b == resume) {
                MenuStyle.button(bd, b.x, by, b.w, b.h, true, b.glow, a, pressed);
                MenuIcons.draw(bd, "play", b.x + b.w / 2 - bd.width(b.label) / 2 - 8, contentY + 8, Theme.onColor(a));
                bd.text(b.label, b.x + (b.w - bd.width(b.label)) / 2 + 6, contentY + 8, Theme.onColor(a), true);
            } else if (b == leave) {
                MenuStyle.block(bd, b.x, by, b.w, b.h, 0xFF563239, b.glow, pressed);
                MenuIcons.draw(bd, "leave", b.x + b.w / 2 - bd.width(b.label) / 2 - 8, contentY + 5, Theme.TEXT);
                bd.text(b.label, b.x + (b.w - bd.width(b.label)) / 2 + 6, contentY + 5, Theme.TEXT, true);
            } else {
                MenuStyle.button(bd, b.x, by, b.w, b.h, false, b.glow, a, pressed);
                bd.roundRect(b.x + 7, contentY + (b.h - 22) / 2, 22, 22, 11, Theme.mix(0x24FFFFFF, Theme.withAlpha(a, 0x70), b.glow));
                MenuIcons.draw(bd, b.icon, b.x + 13, contentY + (b.h - 11) / 2, 11, Theme.mix(0xFFD3D6DE, 0xFFFFFFFF, b.glow));
                String label = b == more ? "More" : b.label;
                bd.text(bd.trim(label, b.w - 40), b.x + 35, contentY + b.h / 2 - 7, Theme.TEXT, false);
                String sub = b == modules ? "Right Shift" : b == options ? "Video, sound..." : b == locker ? "Capes, skins" : "Stats, LAN...";
                bd.text(bd.trim(sub, b.w - 40), b.x + 35, contentY + b.h / 2 + 3, Theme.FAINT, false);
            }
            b.x = bx0;
        }
        if (np[2] > 0) {
            float ti = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 300, 300);
            Draw nd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, ti)) : d;
            musicOffset = rise + Math.round((1f - Glass.backOut(ti)) * 8);
            drawMusic(nd, p, mx, my - musicOffset, musicOffset, dt, a);
        }
        if (holiday != Holidays.NONE) {
            Holidays.greetingChip(d, holiday, sw / 2, Math.max(2, y - 20));
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
        Glass.window(d, x, y, w, h, 0xD80F1218, a);
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
