package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class XpHud extends LineHud {

    private final net.peregrine.client.core.settings.ChoiceSetting show = add(new net.peregrine.client.core.settings.ChoiceSetting("show", "Show", 0,
            "Level and percent", "Level only", "Percent only"));

    public XpHud() {
        super("xp", "Experience", "Level and progress", "Level", 1f, 0.80f);
    }

    @Override
    String value(Platform p) {
        int pct = Math.round(p.xpProgress() * 100);
        if (show.index == 1) return String.valueOf(p.xpLevel());
        if (show.index == 2) return pct + "%";
        return p.xpLevel() + "  (" + Math.round(p.xpProgress() * 100) + "%)";
    }
}
