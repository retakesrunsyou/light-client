package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** Block light where you stand; red at 0, where mobs can spawn. */
public final class LightHud extends LineHud {

    public LightHud() {
        super("light", "Light level", "Light level where you stand", "Light", 0f, 0.99f);
    }

    @Override
    String value(Platform p) {
        int l = p.lightLevel();
        return l < 0 ? null : String.valueOf(l);
    }

    @Override
    int labelColor(Platform p) {
        return p.lightLevel() == 0 ? Theme.BAD : 0;
    }
}
