package net.peregrine.client.v1_21_9;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return menu.mouseClicked((int) e.x(), (int) e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        return menu.mouseDragged((int) e.x(), (int) e.y()) || super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        // the buttons act on release, so this has to reach the menu
        return menu.mouseReleased((int) e.x(), (int) e.y()) || super.mouseReleased(e);
    }
}
