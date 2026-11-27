package net.peregrine.client.core.modules;

import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;

public final class CpsHud extends LineHud {

    private final net.peregrine.client.core.settings.ChoiceSetting show = add(new net.peregrine.client.core.settings.ChoiceSetting(
            "show", "Show", 0, "Left | Right", "Total", "Left only", "Right only"));

    public CpsHud() {
        super("cps", "CPS", "Clicks per second", "CPS", 0f, 0.175f);
    }

    @Override
    String value(Platform p) {
        Peregrine pc = Peregrine.get();
        switch (show.index) {
            case 1: return String.valueOf(pc.cps(0) + pc.cps(1));
            case 2: return String.valueOf(pc.cps(0));
            case 3: return String.valueOf(pc.cps(1));
            default: return pc.cps(0) + " | " + pc.cps(1);
        }
    }
}
