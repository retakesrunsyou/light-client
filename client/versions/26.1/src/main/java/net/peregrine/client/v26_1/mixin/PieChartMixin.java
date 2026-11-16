package net.peregrine.client.v26_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the pie chart HUD item is on screen, the game measures each frame as it does for F3's chart. */
@Mixin(Minecraft.class)
public abstract class PieChartMixin {

    @ModifyExpressionValue(method = "runTick", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showProfilerChart()Z"))
    private boolean peregrine$measureForPie(boolean shown) {
        return shown || net.peregrine.client.core.modules.PieChart.collecting();
    }
}
