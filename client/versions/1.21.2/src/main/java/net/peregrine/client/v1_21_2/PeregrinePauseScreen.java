package net.peregrine.client.v1_21_2;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;
import net.peregrine.client.core.PauseMenu;
import net.peregrine.client.core.Peregrine;

/** Peregrine's pause menu over the game. Layout lives in the core's PauseMenu. */
public final class PeregrinePauseScreen extends Screen {

    /** Set to let Minecraft's own pause menu through once ("Advancements, stats & more"). */
    public static boolean vanillaNext;

    private final PauseMenu menu = Peregrine.get().pauseMenu();

    public PeregrinePauseScreen() {
        super(Component.translatable("menu.game"));
        menu.shown();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        menu.render(new GuiDraw(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the game stays sharp behind the menu (the menu dims it a little)
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return menu.mouseClicked((int) mouseX, (int) mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return menu.mouseDragged((int) mouseX, (int) mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // the buttons act on release, so this has to reach the menu
        return menu.mouseReleased((int) mouseX, (int) mouseY) || super.mouseReleased(mouseX, mouseY, button);
    }
}
