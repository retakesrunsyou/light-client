package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/**
 * The server's sidebar scoreboard as a HUD item: drag it anywhere in Edit HUD and scroll on it
 * to resize it. Minecraft still draws it (so it's exactly the server's scoreboard); this only
 * moves and sizes it.
 */
public final class ScoreboardSize extends HudModule {

    private static final String[] SAMPLE = {"Scoreboard", "Kills: 4", "Coins: 1,250", "Team: Red", "play.example.net"};

    public ScoreboardSize() {
        super("scoreboard", "Scoreboard", "Move and resize the server's sidebar scoreboard (Edit HUD)", false, 1f, 0.5f);
        scale = 1f;
    }

    @Override
    public void resetStyle() {
        super.resetStyle();
        scale = 1f;
    }

    private boolean real() {
        return Hooks.scoreboardBox[2] > 0 && System.currentTimeMillis() - Hooks.scoreboardSeenAt < 1500;
    }

    @Override
    public int width(Draw d, Platform p) {
        if (real()) {
            return Hooks.scoreboardBox[2];
        }
        int w = 0;
        for (String s : SAMPLE) {
            w = Math.max(w, d.width(s));
        }
        return w + 6;
    }

    @Override
    public int height(Draw d, Platform p) {
        return real() ? Hooks.scoreboardBox[3] : SAMPLE.length * 9 + 2;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        Hooks.scoreboardScale = scale;
        if (real() || !Peregrine.get().menu().isEditingHud()) {
            return;   // Minecraft draws the real one, moved here
        }
        // a stand-in to place while there's no scoreboard on screen
        d.rect(x, y, width(d, p), 10, 0x66000000);
        d.rect(x, y + 10, width(d, p), height(d, p) - 10, 0x4D000000);
        d.text(SAMPLE[0], x + (width(d, p) - d.width(SAMPLE[0])) / 2, y + 1, Theme.TEXT, false);
        for (int i = 1; i < SAMPLE.length; i++) {
            d.text(SAMPLE[i], x + 3, y + 2 + i * 9, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void tick(Platform p) {
        int[] box = drawnBox();
        Hooks.scoreboardMoved = enabled() && Hooks.canMoveScoreboard && box[0] >= 0;
        if (Hooks.scoreboardMoved) {
            Hooks.scoreboardX = box[0];
            Hooks.scoreboardY = box[1];
            Hooks.scoreboardK = scale;
        }
        Hooks.scoreboardScale = enabled() ? scale : 1f;   // versions that can only resize it
    }

    @Override
    protected void onDisable() {
        Hooks.scoreboardMoved = false;
        Hooks.scoreboardScale = 1f;
    }
}
