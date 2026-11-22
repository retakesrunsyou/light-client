package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The chat HUD item: moves and sizes the chat to where it was placed in Edit HUD. The same
 * step lays out the chat for drawing and for finding what's under the mouse, so links and
 * hover text keep working where it's moved to.
 */
@Mixin(ChatComponent.class)
public abstract class ChatMoveMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD"), require = 0)
    private void peregrine$move(ChatComponent.ChatGraphicsAccess access, int screenHeight, int ticks,
                                ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (!Hooks.chatMoved) {
            return;
        }
        float x = Hooks.chatX, y = Hooks.chatY, k = Hooks.chatK;
        float bottom = screenHeight - 40;   // where Minecraft puts the chat's bottom
        access.updatePose(m -> {
            m.translate(x, y);
            m.scale(k, k);
            m.translate(0, -bottom);
        });
    }
}
