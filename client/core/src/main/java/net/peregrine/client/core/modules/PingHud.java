package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class PingHud extends LineHud {

    private final net.peregrine.client.core.settings.BoolSetting unit = add(
            new net.peregrine.client.core.settings.BoolSetting("unit", "Show the ms unit", true));

    public PingHud() {
        super("ping", "Ping", "Ping to the server", "Ping", 0f, 0.245f);
    }

    @Override
    String value(Platform p) {
        int ms = p.ping();
        return ms < 0 ? null : unit.value ? ms + " ms" : String.valueOf(ms);
    }

    @Override
    boolean rated() {
        return true;
    }

    @Override
    int rate(Platform p) {
        int ms = p.ping();
        return ms < 0 ? -1 : ms <= 80 ? 0 : ms <= 160 ? 1 : 2;
    }
}
