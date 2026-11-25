package net.peregrine.client.v1_21_6;

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
        double[] p = pointer(mouseX, mouseY);
        menu.render(new GuiDraw(g), (int) p[0], (int) p[1]);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the game stays sharp behind the menu (the menu dims it a little)
    }

    private double[] pointer(double x, double y) {
        return Panel3D.pointer(x, y, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double[] p = pointer(mouseX, mouseY);
        if (menu.mouseClicked((int) p[0], (int) p[1], button)) {
            setDragging(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double[] p = pointer(mouseX, mouseY);
        return (button == 0 && menu.mouseDragged((int) p[0], (int) p[1])) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // the buttons act on release, so this has to reach the menu
        double[] p = pointer(mouseX, mouseY);
        if (button == 0) {
            setDragging(false);
            if (menu.mouseReleased((int) p[0], (int) p[1])) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
