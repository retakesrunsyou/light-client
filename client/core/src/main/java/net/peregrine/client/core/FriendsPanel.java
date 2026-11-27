package net.peregrine.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Friends in the Right Shift menu, the same ones as in the launcher: who's online, text chat and
 * calls. The launcher holds the locked chats and does the calling (and its microphone), so the
 * game only reads what the launcher saved in <data>/client/ and leaves its requests in
 * <data>/client/outbox/ (see peregrine/ui/game_bridge.py in the launcher).
 */
final class FriendsPanel extends MenuPanel {

    static final class Friend {
        String uuid = "", tag = "", status = "", picture = "";
        boolean online, minecraft;
        int unread;
    }

    static final class Line {
        boolean mine;
        String kind = "text", text = "";
        long at;
    }

    private List<Friend> friends = new ArrayList<Friend>();
    private long updated, readAt, stamp = -1;
    private String filter = "";

    private String open;                      // the friend whose chat is showing, or null for the list
    private String draft = "";
    private boolean stick = true;
    private final Map<String, List<Line>> chats = new HashMap<String, List<Line>>();
    private final Map<String, Long> chatStamp = new HashMap<String, Long>();
    private long chatReadAt;

    private String callState = "none", callUuid = "", callName = "";
    private boolean callIncoming, callMuted;
    private int callSeconds;
    private long callReadAt, callStampMs = -1;
    private long lastRequestAt;

    FriendsPanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    String placeholder() {
        return open == null ? "Find a friend..." : "";
    }

    @Override
    void searchChanged(String text) {
        filter = text.toLowerCase(Locale.ROOT);
        scroll = 0;
    }

    @Override
    void shown() {
        super.shown();
        open = null;
    }

    static Path file() {
        String data = System.getProperty("peregrine.data");
        return data != null && !data.isEmpty() ? Paths.get(data, "client", "friends.json")
                : Library.gameDir().resolve("peregrine-friends.json");
    }

    private static boolean linked() {
        String data = System.getProperty("peregrine.data");
        return data != null && !data.isEmpty();
    }

    private static Path base() {
        return file().getParent();
    }

    // ------------------------------------------------------------ what the launcher saved

    private void read() {
        long now = System.currentTimeMillis();
        if (now - readAt < 1500) {
            return;
        }
        readAt = now;
        Path f = file();
        try {
            long m = Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (m == stamp) {
                return;
            }
            stamp = m;
            List<Friend> out = new ArrayList<Friend>();
            if (m != 0) {
                JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                updated = o.has("updated") ? o.get("updated").getAsLong() * 1000 : m;
                for (JsonElement e : o.getAsJsonArray("friends")) {
                    JsonObject fo = e.getAsJsonObject();
                    Friend fr = new Friend();
                    fr.uuid = Library.str(fo, "uuid");
                    fr.tag = Library.str(fo, "gamertag");
                    fr.status = Library.str(fo, "status");
                    fr.picture = Library.str(fo, "picture");
                    fr.online = fo.has("online") && fo.get("online").getAsBoolean();
                    fr.minecraft = fo.has("minecraft") && fo.get("minecraft").getAsBoolean();
                    fr.unread = fo.has("unread") ? fo.get("unread").getAsInt() : 0;
                    out.add(fr);
                }
            }
            friends = out;
        } catch (Exception e) {
            // a half-written file: try again in a moment
            stamp = -1;
        }
    }

    private void readCall() {
        long now = System.currentTimeMillis();
        if (now - callReadAt < 400) {
            return;
        }
        callReadAt = now;
        Path f = base().resolve("call.json");
        try {
            if (!Files.isRegularFile(f)) {
                callState = "none";
                return;
            }
            long m = Files.getLastModifiedTime(f).toMillis();
            if (m == callStampMs && !callState.equals("live")) {
                return;
            }
            callStampMs = m;
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)).getAsJsonObject();
            callState = Library.str(o, "state");
            callUuid = Library.str(o, "uuid");
            callName = Library.str(o, "name");
            callIncoming = o.has("incoming") && o.get("incoming").getAsBoolean();
            callMuted = o.has("muted") && o.get("muted").getAsBoolean();
            callSeconds = o.has("seconds") ? o.get("seconds").getAsInt() : 0;
            if (callState.isEmpty()) {
                callState = "none";
            }
        } catch (Exception e) {
            // half-written: next time
            callStampMs = -1;
        }
    }

    private List<Line> chat(String uuid) {
        long now = System.currentTimeMillis();
        if (now - chatReadAt >= 500) {
            chatReadAt = now;
            Path f = base().resolve("chat").resolve(uuid + ".json");
            try {
                if (Files.isRegularFile(f)) {
                    long m = Files.getLastModifiedTime(f).toMillis();
                    Long seen = chatStamp.get(uuid);
                    if (seen == null || seen != m) {
                        chatStamp.put(uuid, m);
                        JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        List<Line> out = new ArrayList<Line>();
                        for (JsonElement e : o.getAsJsonArray("messages")) {
                            JsonObject mo = e.getAsJsonObject();
                            Line l = new Line();
                            l.mine = mo.has("mine") && mo.get("mine").getAsBoolean();
                            l.kind = Library.str(mo, "kind");
                            l.text = Library.str(mo, "text");
                            l.at = mo.has("at") ? mo.get("at").getAsLong() : 0;
                            out.add(l);
                        }
                        chats.put(uuid, out);
                    }
                }
            } catch (Exception e) {
                chatStamp.remove(uuid);
            }
        }
        return chats.get(uuid);
    }

    /** Asks the launcher for something (it picks the file up within half a second). */
    private void ask(String op, String uuid, String text) {
        if (!linked()) {
            return;
        }
        try {
            Path dir = base().resolve("outbox");
            Files.createDirectories(dir);
            long id = Math.max(System.nanoTime(), lastRequestAt + 1);
            lastRequestAt = id;
            JsonObject o = new JsonObject();
            o.addProperty("op", op);
            o.addProperty("uuid", uuid == null ? "" : uuid);
            if (text != null) {
                o.addProperty("text", text);
            }
            Path tmp = dir.resolve(id + ".tmp");
            Files.write(tmp, o.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, dir.resolve(id + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // no launcher folder to leave it in
        }
    }

    private Friend friend(String uuid) {
        for (Friend f : friends) {
            if (f.uuid.equals(uuid)) {
                return f;
            }
        }
        return null;
    }

    private void openChat(Friend f) {
        open = f.uuid;
        draft = "";
        stick = true;
        scroll = 0;
        smooth = 0;
        f.unread = 0;
        ask("open", f.uuid, null);
    }

    // ------------------------------------------------------------ typing

    @Override
    boolean typed(char c) {
        if (open == null || Character.isISOControl(c)) {
            return false;
        }
        if (draft.length() < 300) {
            draft += c;
        }
        return true;
    }

    @Override
    boolean key(int key) {
        if (open == null) {
            return false;
        }
        if (key == Menu.KEY_BACKSPACE) {
            draft = draft.isEmpty() ? draft : draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
            return true;
        }
        if (key == Menu.KEY_ENTER || key == 335) {   // Enter, keypad Enter
            send();
            return true;
        }
        if (key == Menu.KEY_ESCAPE) {
            open = null;
            return true;
        }
        // Shift keys must not close the menu while you type a capital letter
        return key == 340 || key == 344;
    }

    @Override
    boolean wantsTyping() {
        return open != null;
    }

    private void send() {
        String t = draft.trim();
        if (t.isEmpty() || open == null) {
            return;
        }
        draft = "";
        stick = true;
        ask("text", open, t);
        // show it straight away; the launcher's copy replaces this in a moment
        List<Line> l = chats.get(open);
        if (l == null) {
            l = new ArrayList<Line>();
            chats.put(open, l);
        }
        Line mine = new Line();
        mine.mine = true;
        mine.text = t;
        mine.at = System.currentTimeMillis();
        l.add(mine);
    }

    // ------------------------------------------------------------ drawing

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        read();
        readCall();
        if (!linked()) {
            d.textScaled("Friends", x, y + 1, Theme.TEXT, 1.25f, true);
            banner(d, x, y + 22, w, "Start the game from the launcher to use friends", Theme.BAD);
            return;
        }
        if (open != null && friend(open) == null) {
            open = null;
        }
        if (open == null) {
            drawList(d, mx, my, dt, now, a);
        } else {
            drawChat(d, mx, my, dt, now, a);
        }
    }

    private int smallButton(Draw d, String id, String label, int rx, int ry, boolean primary, boolean danger,
                            int mx, int my, float dt, int a) {
        int bw = d.width(label) + 14;
        int bx = rx - bw;
        float g = glow(id, in(mx, my, bx, ry, bw, 16), dt);
        if (danger) {
            MenuStyle.block(d, bx, ry, bw, 16, 0xFF4A2A2E, g, false);
        } else {
            MenuStyle.button(d, bx, ry, bw, 16, primary, g, a);
        }
        d.text(label, bx + 7, ry + 4, primary && !danger ? Theme.onColor(a) : Theme.TEXT, false);
        spot(id, bx, ry, bw, 16);
        return bw;
    }

    /** The bar for a call that's ringing or going on. Returns its height (0 when there isn't one). */
    private int drawCall(Draw d, int top, int mx, int my, float dt, long now, int a) {
        if (callState.equals("none")) {
            return 0;
        }
        int ch = 24;
        d.roundRect(x, top, w, ch, 3, Theme.withAlpha(callState.equals("live") ? Theme.GOOD : a, 0x26));
        d.roundOutline(x, top, w, ch, 3, Theme.withAlpha(callState.equals("live") ? Theme.GOOD : a, 0x90));
        MenuIcons.draw(d, "mic", x + 7, top + 7, callState.equals("live") ? Theme.GOOD : a);
        String line;
        if (callState.equals("ringing")) {
            line = callName + " is calling";
        } else if (callState.equals("calling")) {
            line = callIncoming ? "Joining " + callName + "..." : "Calling " + callName + "...";
        } else {
            line = callName + "  " + (callSeconds / 60) + ":" + String.format("%02d", callSeconds % 60);
        }
        d.text(d.trim(line, w / 2), x + 22, top + 8, Theme.TEXT, false);
        int rx = x + w - 6;
        if (callState.equals("ringing")) {
            rx -= smallButton(d, "answer", "Answer", rx, top + 4, true, false, mx, my, dt, a) + 4;
            smallButton(d, "decline", "Decline", rx, top + 4, false, true, mx, my, dt, a);
        } else {
            rx -= smallButton(d, "hangup", callState.equals("live") ? "End" : "Cancel", rx, top + 4, false, true, mx, my, dt, a) + 4;
            if (callState.equals("live")) {
                smallButton(d, "mute", callMuted ? "Unmute" : "Mute", rx, top + 4, callMuted, false, mx, my, dt, a);
            }
        }
        return ch + 6;
    }

    private void drawList(Draw d, int mx, int my, float dt, long now, int a) {
        List<Friend> online = new ArrayList<Friend>(), offline = new ArrayList<Friend>();
        for (Friend f : friends) {
            if (!filter.isEmpty() && !f.tag.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            (f.online ? online : offline).add(f);
        }
        d.textScaled("Friends", x, y + 1, Theme.TEXT, 1.25f, true);
        String count = online.size() + " online";
        d.text(count, x + Math.round(d.width("Friends") * 1.25f) + 6, y + 3, a, false);
        // the voice party (play together on a server with a code) lives behind this chip
        smallButton(d, "party", "Voice party", x + w, y - 1, false, false, mx, my, dt, a);
        int top = y + 20;
        top += drawCall(d, top, mx, my, dt, now, a);
        if (friends.isEmpty()) {
            String t = stamp == 0 ? "Open Friends in the launcher once to load them" : "No friends yet";
            d.text(d.trim(t, w), x + (w - Math.min(w, d.width(t))) / 2, y + h / 2 - 10, Theme.MUTED, false);
            String t2 = "Add friends on the Friends page in the launcher";
            d.text(d.trim(t2, w), x + (w - Math.min(w, d.width(t2))) / 2, y + h / 2 + 2, Theme.FAINT, false);
            return;
        }
        if (updated != 0 && now - updated > 10 * 60_000L) {
            top += banner(d, x, top, w, "Chat and calls need the launcher open", Theme.BAD);
        }
        int listTop = top, listH = y + h - top;
        int rowH = 26, gap = 4;
        int total = (online.isEmpty() ? 0 : 14 + online.size() * (rowH + gap)) + (offline.isEmpty() ? 0 : 14 + offline.size() * (rowH + gap));
        maxScroll = Math.max(0, total - listH);
        menu.clipArea(d, x, listTop, w + 6, listH);
        int cy = listTop - Math.round(smooth);
        int i = 0;
        for (int part = 0; part < 2; part++) {
            List<Friend> l = part == 0 ? online : offline;
            if (l.isEmpty()) {
                continue;
            }
            d.text(part == 0 ? "Online" : "Offline", x + 2, cy + 2, Theme.MUTED, false);
            cy += 14;
            for (Friend f : l) {
                float ap = appear(i++);
                Draw rd = ap < 1f ? new FadeDraw(d, 0.1f + 0.9f * ap) : d;
                int ry = cy + Math.round((1f - ap) * 6);
                int right = x + w - 6;
                boolean canCall = f.online && callState.equals("none");
                int callW = d.width("Call") + 14;
                boolean hover = in(mx, my, x, ry, w, rowH) && my >= listTop && my < listTop + listH;
                float g = glow("f:" + f.uuid, hover, dt);
                MenuStyle.panel(rd, x, ry, w, rowH, Theme.mix(MenuStyle.ROW, MenuStyle.ROW_HOVER, g));
                icon(rd, Library.webIcon(f.picture), f.tag, x + 5, ry + 4, 18, a);
                int dot = f.online ? (f.minecraft ? a : Theme.GOOD) : Theme.FAINT;
                rd.roundRect(x + 18, ry + 17, 7, 7, 3, 0xFF0C0E13);
                rd.roundRect(x + 19, ry + 18, 5, 5, 2, dot);
                if (canCall) {
                    int bx = right - callW;
                    boolean bh = in(mx, my, bx, ry + 5, callW, 16);
                    float bg = glow("c:" + f.uuid, bh, dt);
                    MenuStyle.button(rd, bx, ry + 5, callW, 16, false, bg, a);
                    rd.text("Call", bx + 7, ry + 9, Theme.TEXT, false);
                    if (my >= listTop && my < listTop + listH) {
                        spot("c:" + f.uuid, bx, ry + 5, callW, 16);
                    }
                    right = bx - 6;
                }
                if (f.unread > 0) {
                    String n = f.unread > 9 ? "9+" : String.valueOf(f.unread);
                    int bw = Math.max(12, d.width(n) + 8);
                    rd.roundRect(right - bw, ry + 7, bw, 12, 6, a);
                    rd.text(n, right - bw + (bw - d.width(n)) / 2, ry + 9, Theme.onColor(a), false);
                    right -= bw + 6;
                }
                rd.text(rd.trim(f.tag, Math.max(20, right - x - 34)), x + 30, ry + 4, f.online ? Theme.TEXT : Theme.MUTED, false);
                String s = f.online ? (f.status.isEmpty() ? "Online" : f.status) : "Offline";
                rd.text(rd.trim(s, Math.max(20, right - x - 34)), x + 30, ry + 15, f.minecraft ? a : Theme.FAINT, false);
                if (my >= listTop && my < listTop + listH) {
                    spot("f:" + f.uuid, x, ry, w, rowH);
                }
                cy += rowH + gap;
            }
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, maxScroll, smooth, a, v -> { scroll = v; smooth = v; });
    }

    /** Breaks a message into lines that fit. */
    private static List<String> wrap(Draw d, String text, int max) {
        List<String> out = new ArrayList<String>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (d.width(tryLine) <= max) {
                    line.setLength(0);
                    line.append(tryLine);
                    continue;
                }
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (d.width(rest) > max && rest.length() > 1) {   // a word wider than the bubble
                    int cut = rest.length() - 1;
                    while (cut > 1 && d.width(rest.substring(0, cut)) > max) {
                        cut--;
                    }
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    private void drawChat(Draw d, int mx, int my, float dt, long now, int a) {
        Friend f = friend(open);
        // header: back, picture, name, call
        boolean bh = in(mx, my, x, y, 18, 18);
        float bg = glow("back", bh, dt);
        MenuStyle.button(d, x, y, 18, 18, false, bg, a);
        MenuIcons.draw(d, "back", x + 4, y + 4, Theme.TEXT);
        spot("back", x, y, 18, 18);
        icon(d, Library.webIcon(f.picture), f.tag, x + 24, y, 18, a);
        d.text(d.trim(f.tag, w / 2), x + 47, y + 1, Theme.TEXT, true);
        d.text(f.online ? (f.status.isEmpty() ? "Online" : f.status) : "Offline", x + 47, y + 11, f.online ? a : Theme.FAINT, false);
        if (callState.equals("none") && f.online) {
            smallButton(d, "call", "Call", x + w, y + 1, true, false, mx, my, dt, a);
        }
        int top = y + 24;
        top += drawCall(d, top, mx, my, dt, now, a);
        if (updated != 0 && now - updated > 10 * 60_000L) {
            top += banner(d, x, top, w, "Chat and calls need the launcher open", Theme.BAD);
        }
        int inputH = 18;
        int areaTop = top, areaH = y + h - top - inputH - 6;

        List<Line> lines = chat(open);
        boolean atBottom = stick || scroll >= maxScroll - 1;
        int maxBubble = Math.max(60, Math.round(w * 0.72f) - 14);
        // lay the messages out
        int total = 0;
        List<List<String>> wrapped = new ArrayList<List<String>>();
        if (lines != null) {
            for (Line l : lines) {
                List<String> parts = l.kind.equals("text") ? wrap(d, l.text, maxBubble) : null;
                wrapped.add(parts);
                total += (parts == null ? 14 : parts.size() * 10 + 8) + 4;
            }
        }
        maxScroll = Math.max(0, total - areaH);
        if (atBottom) {
            scroll = maxScroll;
            smooth = maxScroll;
        }
        stick = scroll >= maxScroll - 1;
        menu.clipArea(d, x, areaTop, w + 6, areaH);
        if (lines == null) {
            String t = "Loading messages";
            d.text(t, x + (w - d.width(t)) / 2, areaTop + areaH / 2 - 8, Theme.MUTED, false);
            dots(d, x + w / 2 - 5, areaTop + areaH / 2 + 4, now, a);
        } else if (lines.isEmpty()) {
            String t = "Say hi to " + f.tag;
            d.text(t, x + (w - d.width(t)) / 2, areaTop + areaH / 2 - 4, Theme.FAINT, false);
        } else {
            int cy = areaTop + Math.max(0, areaH - total) - Math.round(smooth);
            if (total < areaH) {
                cy = areaTop + areaH - total;   // a short chat sits at the bottom, like a phone's
            }
            for (int i = 0; i < lines.size(); i++) {
                Line l = lines.get(i);
                List<String> parts = wrapped.get(i);
                if (parts == null) {
                    d.text(l.text + "  " + clock(l.at), x + (w - d.width(l.text + "  " + clock(l.at))) / 2, cy + 3, Theme.FAINT, false);
                    cy += 18;
                    continue;
                }
                int widest = 0;
                for (String p : parts) {
                    widest = Math.max(widest, d.width(p));
                }
                int bw = widest + 14, bh2 = parts.size() * 10 + 8;
                int bx = l.mine ? x + w - 8 - bw : x + 2;
                int fill = l.mine ? Theme.mix(0xFF2B2B2B, a, 0.35f) : MenuStyle.ROW_HOVER;
                d.roundRect(bx, cy, bw, bh2, 5, fill);
                int fg = Theme.TEXT;
                for (int k = 0; k < parts.size(); k++) {
                    d.text(parts.get(k), bx + 7, cy + 4 + k * 10, fg, false);
                }
                cy += bh2 + 4;
            }
        }
        menu.unclipArea(d);
        menu.scrollbar(d, areaTop, areaH, maxScroll, smooth, a, v -> { scroll = v; smooth = v; stick = false; });

        // the box you type in
        int iy = y + h - inputH;
        d.roundRect(x, iy, w, inputH, 4, MenuStyle.FIELD);
        d.roundOutline(x, iy, w, inputH, 4, Theme.withAlpha(a, 0xC0));
        boolean caret = (now / 500) % 2 == 0;
        String shown = draft + (caret ? "|" : " ");
        int room = w - 70;
        while (!shown.isEmpty() && d.width(shown) > room) {
            shown = shown.substring(shown.offsetByCodePoints(0, 1));
        }
        if (draft.isEmpty()) {
            d.text("Message " + f.tag, x + 8, iy + 5, Theme.FAINT, false);
            d.text("|", x + 7, iy + 5, caret ? a : 0, false);
        } else {
            d.text(shown, x + 8, iy + 5, Theme.TEXT, false);
        }
        String send = "Enter to send";
        d.text(send, x + w - 8 - d.width(send), iy + 5, Theme.FAINT, false);
    }

    private static String clock(long ms) {
        if (ms <= 0) {
            return "";
        }
        java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault());
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    static String ago(long ms) {
        long m = ms / 60_000L;
        return m < 1 ? "just now" : m < 60 ? m + " min ago" : m / 60 < 24 ? (m / 60) + " h ago" : (m / 1440) + " days ago";
    }

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        if (button != 0) {
            return false;
        }
        if (id.startsWith("f:")) {
            Friend f = friend(id.substring(2));
            if (f != null) {
                openChat(f);
            }
        } else if (id.startsWith("c:")) {
            Friend f = friend(id.substring(2));
            if (f != null) {
                ask("call", f.uuid, null);
                openChat(f);
            }
        } else if (id.equals("call") && open != null) {
            ask("call", open, null);
        } else if (id.equals("back")) {
            open = null;
        } else if (id.equals("party")) {
            menu.choose("voice");
        } else if (id.equals("answer")) {
            ask("answer", "", null);
        } else if (id.equals("decline")) {
            ask("decline", "", null);
        } else if (id.equals("hangup")) {
            ask("hangup", "", null);
        } else if (id.equals("mute")) {
            ask("mute", "", null);
        } else {
            return false;
        }
        return true;
    }
}
