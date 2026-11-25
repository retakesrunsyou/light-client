package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The selected hotbar slot: a public field up to 1.21.4, private with a setter from 1.21.5, so reach it the same way on all. */
@Mixin(Inventory.class)
public interface InventorySlotAccessor {

    @Accessor("selected")
    int peregrine$selected();

    @Accessor("selected")
    void peregrine$setSelected(int slot);
}
