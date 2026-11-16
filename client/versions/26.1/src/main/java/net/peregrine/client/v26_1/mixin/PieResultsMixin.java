package net.peregrine.client.v26_1.mixin;

import net.minecraft.util.profiling.ProfileResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the results the game hands its own pie chart, for the pie chart HUD item. */
@Mixin(targets = "net.minecraft.client.gui.components.debugchart.ProfilerPieChart")
public abstract class PieResultsMixin {

    @Inject(method = "setPieChartResults", at = @At("HEAD"), require = 0)
    private void peregrine$keepResults(ProfileResults results, CallbackInfo ci) {
        net.peregrine.client.v26_1.PieData.results = results;
    }
}
