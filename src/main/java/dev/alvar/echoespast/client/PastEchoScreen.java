package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.item.PastEchoMemory;
import dev.alvar.echoespast.menu.PastEchoMenu;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.snapshot.EchoSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Ceremonial Past Echo reliquary — textured stone-and-brass shell with a
 * single fragment socket. Zones never overlap: header → chamber → control
 * trough → inventory vault.
 */
public final class PastEchoScreen extends AbstractContainerScreen<PastEchoMenu> {
    private static final int SCREEN_WIDTH = 256;
    private static final int SCREEN_HEIGHT = 240;

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID, "textures/gui/past_echo_reliquary.png");
    private static final Identifier BUTTON = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID, "textures/gui/past_echo_button.png");

    private static final int TEXT = 0xFFF2E6D4;
    private static final int TEXT_MUTED = 0xFFA89478;
    private static final int GOLD_SEAL = 0xFFE8C86A;

    /** Lives inside the header plaque under the title — never crosses the chamber rings. */
    private static final int STATUS_Y = 18;
    private static final int TITLE_Y = 8;
    private static final int INVENTORY_LABEL_Y = 124;

    private ReliquaryButton colorButton;

    public PastEchoScreen(PastEchoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        inventoryLabelX = PastEchoMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
        titleLabelY = TITLE_Y;
    }

    @Override
    protected void init() {
        super.init();
        colorButton = addRenderableWidget(new ReliquaryButton(
                leftPos + PastEchoMenu.COLOR_BUTTON_X,
                topPos + PastEchoMenu.COLOR_BUTTON_Y,
                PastEchoMenu.COLOR_BUTTON_WIDTH,
                PastEchoMenu.COLOR_BUTTON_HEIGHT,
                colorLabel(),
                () -> {
                    minecraft.gameMode.handleInventoryButtonClick(
                            menu.containerId, PastEchoMenu.CYCLE_COLOR_BUTTON);
                    colorButton.setMessage(colorLabel());
                }));
        refreshColorControl();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshColorControl();
        if (colorButton != null) {
            colorButton.setMessage(colorLabel());
        }
    }

    private void refreshColorControl() {
        if (colorButton == null) {
            return;
        }
        boolean editable = menu.canEditColor();
        colorButton.visible = editable;
        colorButton.active = editable;
        if (editable) {
            colorButton.setTooltip(Tooltip.create(Component.translatable(
                    "gui.echoes_show_the_past.past_echo.cycle_color")));
        } else {
            colorButton.setTooltip(null);
        }
    }

    private Component colorLabel() {
        ResonanceColor color = PastEchoMemory.resolveColor(menu.fragment());
        return Component.translatable(
                "gui.echoes_show_the_past.past_echo.color",
                Component.translatable(
                        "resonance.echoes_show_the_past.color." + color.getSerializedName()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                leftPos,
                topPos,
                0f,
                0f,
                imageWidth,
                imageHeight,
                imageWidth,
                imageHeight);

        ItemStack fragment = menu.fragment();
        int socketX = leftPos + PastEchoMenu.FRAGMENT_SLOT_X;
        int socketY = topPos + PastEchoMenu.FRAGMENT_SLOT_Y;

        EchoSnapshot snapshot = fragment.isEmpty()
                ? null
                : fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        if (snapshot != null && snapshot.sealed()) {
            drawSealCorners(graphics, socketX, socketY);
        }
    }

    private void drawSealCorners(GuiGraphicsExtractor graphics, int socketX, int socketY) {
        int x0 = socketX - 3;
        int y0 = socketY - 3;
        int x1 = socketX + 18;
        int y1 = socketY + 18;
        graphics.fill(x0, y0, x0 + 7, y0 + 2, GOLD_SEAL);
        graphics.fill(x0, y0, x0 + 2, y0 + 7, GOLD_SEAL);
        graphics.fill(x1 - 7, y0, x1, y0 + 2, GOLD_SEAL);
        graphics.fill(x1 - 2, y0, x1, y0 + 7, GOLD_SEAL);
        graphics.fill(x0, y1 - 2, x0 + 7, y1, GOLD_SEAL);
        graphics.fill(x0, y1 - 7, x0 + 2, y1, GOLD_SEAL);
        graphics.fill(x1 - 7, y1 - 2, x1, y1, GOLD_SEAL);
        graphics.fill(x1 - 2, y1 - 7, x1, y1, GOLD_SEAL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int titleWidth = font.width(title);
        graphics.text(font, title, (imageWidth - titleWidth) / 2, TITLE_Y, TEXT, false);

        ItemStack fragment = menu.fragment();
        EchoSnapshot snapshot = fragment.isEmpty()
                ? null
                : fragment.get(EchoesShowThePast.ECHO_SNAPSHOT.get());
        Component status = statusLine(fragment, snapshot);
        int statusWidth = font.width(status);
        graphics.text(font, status, (imageWidth - statusWidth) / 2, STATUS_Y, TEXT_MUTED, false);

        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_MUTED, false);
    }

    private Component statusLine(ItemStack fragment, EchoSnapshot snapshot) {
        if (fragment.isEmpty()) {
            return Component.translatable("gui.echoes_show_the_past.past_echo.empty_socket");
        }
        if (snapshot == null) {
            return Component.translatable("gui.echoes_show_the_past.past_echo.blank_fragment");
        }
        if (snapshot.sealed()) {
            return Component.translatable("gui.echoes_show_the_past.past_echo.sealed_socket");
        }
        return Component.translatable("gui.echoes_show_the_past.past_echo.living_socket");
    }

    private static final class ReliquaryButton extends AbstractButton {
        private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;
        private final Runnable action;

        private ReliquaryButton(int x, int y, int width, int height, Component message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int v = !active ? 28 : (isHoveredOrFocused() ? 14 : 0);
            graphics.blit(
                    PIPELINE,
                    BUTTON,
                    getX(),
                    getY(),
                    0f,
                    (float) v,
                    getWidth(),
                    getHeight(),
                    120,
                    42);

            int color = active ? TEXT : TEXT_MUTED;
            graphics.centeredText(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + getWidth() / 2,
                    getY() + Math.max(1, (getHeight() - 8) / 2),
                    color);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
