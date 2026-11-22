package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Reconnect;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Auto reconnect: kicked for being idle, or the server restarts? You're put back in by yourself. */
public final class AutoReconnect extends Module {

    public final SliderSetting wait = add(new SliderSetting("wait", "First try after", 1f, 30f, 1f, 5f, "%.0f s"));
    public final BoolSetting keepTrying = add(new BoolSetting("keep_trying", "Keep retrying", true));
    public final BoolSetting skipBans = add(new BoolSetting("skip_bans", "Skip after bans", true));

    public AutoReconnect() {
        super("auto_reconnect", "Auto reconnect", "Rejoins after a kick or restart",
                Category.UTILITY, false);
    }

    @Override
    public void tick(Platform p) {
        apply();
        Reconnect.playing(p);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        Reconnect.enabled = false;
        Reconnect.cancel();
    }

    private void apply() {
        Reconnect.enabled = enabled();
        Reconnect.firstWait = Math.round(wait.value);
        Reconnect.keepTrying = keepTrying.value;
        Reconnect.skipBans = skipBans.value;
    }
}
