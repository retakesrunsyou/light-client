package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * The chat as a HUD item: drag it anywhere in Edit HUD and scroll on it to resize it, plus
 * text size, width and height (Minecraft's own chat settings, so servers see nothing
 * different). Clicking links in it still works where it's moved to. Switching it off puts
 * the chat and your previous sizes back.
 */
public final class ChatSize extends HudModule {

    private final SliderSetting textSize = add(new SliderSetting("scale", "Text size", 30f, 100f, 5f, 80f, "%.0f%%"));
    private final SliderSetting width = add(new SliderSetting("width", "Width", 40f, 320f, 10f, 320f, "%.0f px"));
    private final SliderSetting height = add(new SliderSetting("height", "Height when open", 20f, 180f, 10f, 180f, "%.0f px"));
    private final SliderSetting closed = add(new SliderSetting("closed", "Height when closed", 20f, 180f, 10f, 90f, "%.0f px"));
    private double[] saved;
    private String applied = "";

    private static final String[] SAMPLE = {"<Steve> anyone up for a match?", "<Alex> sure, give me a minute",
            "[Server] Welcome back!"};

    public ChatSize() {
        super("chat_size", "Chat position", "Move and resize the chat", false, 0f, 0.93f);
        scale = 1f;
    }

    @Override
    public void resetStyle() {
        super.resetStyle();
        scale = 1f;
    }

    /** The chat's size on screen (its text size included), as Minecraft lays it out. */
    private int chatW(Platform p) {
        double w = p.chatOption("width"), s = p.chatOption("scale");
        return w < 0 ? 320 : (int) Math.ceil((Math.floor(w * 280 + 40) + 12) * Math.max(0.1, s));
    }

    private int chatH(Platform p) {
        double h = p.chatOption("height"), s = p.chatOption("scale");
        return h < 0 ? 180 : (int) Math.ceil(Math.floor(h * 160 + 20) * Math.max(0.1, s));
    }

    @Override
    public int width(Draw d, Platform p) {
        return Math.max(40, chatW(p));
    }

    @Override
    public int height(Draw d, Platform p) {
        return Math.max(20, chatH(p));
    }

    @Override
    public boolean hasContent(Platform p) {
        return Hooks.canMoveChat || !p.inWorld();
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (!Peregrine.get().menu().isEditingHud()) {
            return;   // Minecraft draws the chat itself, moved here
        }
        int w = width(d, p), h = height(d, p);
        d.rect(x, y, w, h, 0x40000000);
        int ty = y + h - 10;
        for (int i = SAMPLE.length - 1; i >= 0 && ty >= y; i--, ty -= 10) {
            d.text(d.trim(SAMPLE[i], w - 6), x + 3, ty, Theme.TEXT, true);
        }
    }

    @Override
    public void tick(Platform p) {
        int[] box = drawnBox();
        Hooks.chatMoved = enabled() && Hooks.canMoveChat && box[0] >= 0;
        if (Hooks.chatMoved) {
            Hooks.chatX = box[0];
            Hooks.chatY = box[1] + box[3];   // its bottom-left corner
            Hooks.chatK = scale;
        }
        String want = textSize.value + "/" + width.value + "/" + height.value + "/" + closed.value;
        if (want.equals(applied) || p.chatOption("scale") < 0) {
            return;
        }
        if (saved == null) {
            saved = new double[] {p.chatOption("scale"), p.chatOption("width"), p.chatOption("height"), p.chatOption("unfocused")};
        }
        // Minecraft keeps these as 0-1: width is 40-320 px, heights 20-180 px.
        p.setChatOption("scale", textSize.value / 100.0);
        p.setChatOption("width", (width.value - 40) / 280.0);
        p.setChatOption("height", (height.value - 20) / 160.0);
        p.setChatOption("unfocused", (closed.value - 20) / 160.0);
        applied = want;
    }

    @Override
    protected void onDisable() {
        Hooks.chatMoved = false;
        if (saved != null) {
            Platform p = platform();
            p.setChatOption("scale", saved[0]);
            p.setChatOption("width", saved[1]);
            p.setChatOption("height", saved[2]);
            p.setChatOption("unfocused", saved[3]);
            saved = null;
        }
        applied = "";
    }
}
