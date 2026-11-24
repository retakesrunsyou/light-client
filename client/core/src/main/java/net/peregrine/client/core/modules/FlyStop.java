package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

/** Flying stops when you let go of the keys, instead of sliding on for a bit. */
public final class FlyStop extends Module {

    public FlyStop() {
        super("fly_stop", "Fly stop", "Stop as soon as you let go of the keys while flying",
                Category.UTILITY, true);
    }

    @Override
    public void tick(Platform p) {
        p.stopFlyDrift();
    }
}
