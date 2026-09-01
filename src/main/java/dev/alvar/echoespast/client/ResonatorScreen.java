package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.menu.ResonatorMenu;
import dev.alvar.echoespast.resonance.EchoSiteType;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.resonance.ResonanceKnowledge;
import dev.alvar.echoespast.resonance.ResonatorLoadout;
import dev.alvar.echoespast.resonance.ResonatorModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Resonator console in the Past Echo visual family. Header text is clipped to
 * the plaque; signature rows scroll; Mute all appears only with a decoder and
 * known sites.
 */
public final class ResonatorScreen extends AbstractContainerScreen<ResonatorMenu> {
    private static final int SCREEN_WIDTH = 256;
    private static final int SCREEN_HEIGHT = 270;

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID, "textures/gui/resonator_console.png");
    private static final Identifier BUTTON = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID, "textures/gui/past_echo_button.png");

    private static final int TEXT = 0xFFF2E6D4;
    private static final int TEXT_MUTED = 0xFFA89478;
    private static final int TEXT_DIM = 0xFF6E5C48;
    private static final int PANEL = 0xFF100C09;
    private static final int PANEL_HOVER = 0xFF2A1F14;
    private static final int GOLD = 0xFFD1AE6C;
    private static final int GOLD_DIM = 0xFF886332;
    private static final int DANGER = 0xFFC47A5A;

    private static final int TITLE_Y = 9;
    private static final int STATUS_Y = 19;
    private static final int HEADER_TEXT_MAX = 220;
    private static final int INVENTORY_LABEL_Y = 171;

    private static final int WELL_X = 18;
    private static final int WELL_Y = 94;
    private static final int WELL_WIDTH = 220;
    private static final int WELL_BOTTOM = 160;

    private static final int SIGNATURE_LIST_X = 24;
    private static final int SIGNATURE_LIST_Y = 114;
    private static final int SIGNATURE_LIST_WIDTH = 196;
    private static final int SIGNATURE_ROW_HEIGHT = 14;
    private static final int VISIBLE_SIGNATURE_ROWS = 3;
    private static final int SIGNATURE_NAME_WIDTH = 112;
    private static final int MUTE_BUTTON_WIDTH = 46;

    private final List<SiteControls> siteControls = new ArrayList<>();
    private BrassButton modeButton;
    private BrassButton muteAllButton;
    private boolean matrixAtInit;
    private boolean decoderAtInit;
    private int siteCountAtInit;
    private int signatureScroll;

    public ResonatorScreen(ResonatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        inventoryLabelX = ResonatorMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
        titleLabelY = TITLE_Y;
    }

    @Override
    protected void init() {
        super.init();
        siteControls.clear();
        signatureScroll = 0;

        modeButton = addRenderableWidget(new BrassButton(
                leftPos + ResonatorMenu.MODE_BUTTON_X,
                topPos + ResonatorMenu.MODE_BUTTON_Y,
                ResonatorMenu.MODE_BUTTON_WIDTH,
                ResonatorMenu.MODE_BUTTON_HEIGHT,
                modeLabel(),
                () -> {
                    click(ResonatorMenu.TOGGLE_MODE_BUTTON);
                    modeButton.setMessage(modeLabel());
                },
                true));
        refreshModeControl();

        muteAllButton = addRenderableWidget(new BrassButton(
                leftPos + ResonatorMenu.MUTE_ALL_BUTTON_X,
                topPos + ResonatorMenu.MUTE_ALL_BUTTON_Y,
                ResonatorMenu.MUTE_ALL_BUTTON_WIDTH,
                ResonatorMenu.MUTE_ALL_BUTTON_HEIGHT,
                muteAllLabel(),
                this::pressMuteAll,
                false));
        refreshMuteAllControl();

        ResonatorLoadout loadout = menu.loadout();
        matrixAtInit = loadout.has(ResonatorModule.DIRECTIONAL_MATRIX);
        decoderAtInit = loadout.has(ResonatorModule.HARMONIC_DECODER);
        siteCountAtInit = menu.discoveredSites().size();
        rebuildSignatureControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ResonatorLoadout loadout = menu.loadout();
        boolean hasMatrix = loadout.has(ResonatorModule.DIRECTIONAL_MATRIX);
        boolean hasDecoder = loadout.has(ResonatorModule.HARMONIC_DECODER);
        int siteCount = menu.discoveredSites().size();
        if (hasMatrix != matrixAtInit
                || hasDecoder != decoderAtInit
                || siteCount != siteCountAtInit) {
            rebuildWidgets();
            return;
        }
        clampSignatureScroll();
        refreshModeControl();
        refreshMuteAllControl();
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        if (muteAllButton != null) {
            muteAllButton.setMessage(muteAllLabel());
        }
        siteControls.forEach(this::refreshSiteControl);
    }

    private void refreshModeControl() {
        if (modeButton == null) {
            return;
        }
        boolean canToggle = menu.canToggleMode();
        modeButton.visible = canToggle;
        modeButton.active = canToggle;
        modeButton.setTooltip(canToggle
                ? Tooltip.create(Component.translatable("gui.echoes_show_the_past.mode_hint"))
                : null);
    }

    private void refreshMuteAllControl() {
        if (muteAllButton == null) {
            return;
        }
        boolean show = menu.canManageSignatures() && !menu.discoveredSites().isEmpty();
        muteAllButton.visible = show;
        muteAllButton.active = show;
        if (show) {
            boolean mute = anyListening();
            muteAllButton.setTooltip(Tooltip.create(Component.translatable(mute
                    ? "gui.echoes_show_the_past.mute_all_hint"
                    : "gui.echoes_show_the_past.listen_all_hint")));
        } else {
            muteAllButton.setTooltip(null);
        }
    }

    private void rebuildSignatureControls() {
        for (SiteControls controls : siteControls) {
            removeWidget(controls.colorButton());
            removeWidget(controls.ignoredButton());
        }
        siteControls.clear();

        if (!menu.canManageSignatures()) {
            return;
        }

        List<Identifier> sites = menu.discoveredSites();
        clampSignatureScroll();
        int end = Math.min(sites.size(), signatureScroll + VISIBLE_SIGNATURE_ROWS);
        for (int index = signatureScroll; index < end; index++) {
            Identifier siteId = sites.get(index);
            EchoSiteType site = EchoSiteType.byId(siteId);
            if (site == null) {
                continue;
            }
            int row = index - signatureScroll;
            int y = topPos + SIGNATURE_LIST_Y + row * SIGNATURE_ROW_HEIGHT;
            int siteIndex = index;

            SwatchButton colorButton = addRenderableWidget(new SwatchButton(
                    leftPos + SIGNATURE_LIST_X,
                    y + 1,
                    11,
                    11,
                    () -> cycleColor(siteIndex, site),
                    () -> 0xFF000000 | knowledge().colorFor(site).rgb()));
            BrassButton ignoredButton = addRenderableWidget(new BrassButton(
                    leftPos + SIGNATURE_LIST_X + SIGNATURE_LIST_WIDTH - MUTE_BUTTON_WIDTH,
                    y + 1,
                    MUTE_BUTTON_WIDTH,
                    12,
                    ignoreActionLabel(site),
                    () -> pressIgnore(siteIndex),
                    false));
            siteControls.add(new SiteControls(site, colorButton, ignoredButton));
            refreshSiteControl(siteControls.getLast());
        }
    }

    private void refreshSiteControl(SiteControls controls) {
        boolean ignored = knowledge().ignored().contains(controls.site().id());
        controls.ignoredButton().setMessage(ignoreActionLabel(controls.site()));
        controls.ignoredButton().setTooltip(Tooltip.create(Component.translatable(
                ignored
                        ? "gui.echoes_show_the_past.include_signature"
                        : "gui.echoes_show_the_past.ignore_signature",
                Component.translatable(controls.site().translationKey()))));
        controls.colorButton().setTooltip(Tooltip.create(Component.translatable(
                "gui.echoes_show_the_past.change_color",
                Component.translatable(controls.site().translationKey()))));
    }

    private void pressMuteAll() {
        // Predict locally first — MultiPlayerGameMode only sends a packet and
        // never calls clickMenuButton on the client.
        menu.clickMenuButton(minecraft.player, ResonatorMenu.MUTE_ALL_BUTTON);
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, ResonatorMenu.MUTE_ALL_BUTTON);
        refreshMuteAllControl();
        if (muteAllButton != null) {
            muteAllButton.setMessage(muteAllLabel());
        }
        siteControls.forEach(this::refreshSiteControl);
    }

    private void pressIgnore(int siteIndex) {
        menu.clickMenuButton(minecraft.player, ResonatorMenu.IGNORE_BUTTON_BASE + siteIndex);
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, ResonatorMenu.IGNORE_BUTTON_BASE + siteIndex);
        refreshMuteAllControl();
        if (muteAllButton != null) {
            muteAllButton.setMessage(muteAllLabel());
        }
        siteControls.forEach(this::refreshSiteControl);
    }

    private void cycleColor(int siteIndex, EchoSiteType site) {
        ResonanceColor current = knowledge().colorFor(site);
        int next = (ResonanceColor.PALETTE.indexOf(current) + 1)
                % ResonanceColor.PALETTE.size();
        int button = ResonatorMenu.COLOR_BUTTON_BASE
                + siteIndex * ResonanceColor.PALETTE.size()
                + next;
        menu.clickMenuButton(minecraft.player, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private ResonanceKnowledge knowledge() {
        return minecraft.player.getData(EchoesShowThePast.RESONANCE_KNOWLEDGE.get());
    }

    private boolean anyListening() {
        return knowledge().anyListening(menu.discoveredSites());
    }

    private void click(int button) {
        if (menu.clickMenuButton(minecraft.player, button)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    private Component modeLabel() {
        return Component.translatable(menu.loadout().effectiveDirectionalMode()
                ? "gui.echoes_show_the_past.mode_directional"
                : "gui.echoes_show_the_past.mode_wide");
    }

    private Component muteAllLabel() {
        return Component.translatable(anyListening()
                ? "gui.echoes_show_the_past.mute_all"
                : "gui.echoes_show_the_past.listen_all");
    }

    private Component ignoreActionLabel(EchoSiteType site) {
        return Component.translatable(knowledge().ignored().contains(site.id())
                ? "gui.echoes_show_the_past.listen"
                : "gui.echoes_show_the_past.mute");
    }

    private void clampSignatureScroll() {
        int max = Math.max(0, menu.discoveredSites().size() - VISIBLE_SIGNATURE_ROWS);
        int previous = signatureScroll;
        signatureScroll = Mth.clamp(signatureScroll, 0, max);
        if (signatureScroll != previous && menu.canManageSignatures()) {
            rebuildSignatureControls();
        }
    }

    private boolean isOverSignatureWell(double mouseX, double mouseY) {
        return mouseX >= leftPos + WELL_X
                && mouseX <= leftPos + WELL_X + WELL_WIDTH
                && mouseY >= topPos + WELL_Y
                && mouseY <= topPos + WELL_BOTTOM;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.canManageSignatures()
                && menu.discoveredSites().size() > VISIBLE_SIGNATURE_ROWS
                && isOverSignatureWell(mouseX, mouseY)) {
            int previous = signatureScroll;
            signatureScroll -= (int) Math.signum(scrollY);
            clampSignatureScroll();
            if (signatureScroll != previous) {
                rebuildSignatureControls();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        // Cover the mode trough when no matrix is installed — empty controls
        // should not look like a broken progress bar.
        if (!menu.canToggleMode()) {
            graphics.fill(
                    leftPos + ResonatorMenu.MODE_BUTTON_X,
                    topPos + ResonatorMenu.MODE_BUTTON_Y,
                    leftPos + ResonatorMenu.MODE_BUTTON_X + ResonatorMenu.MODE_BUTTON_WIDTH,
                    topPos + ResonatorMenu.MODE_BUTTON_Y + ResonatorMenu.MODE_BUTTON_HEIGHT,
                    0xFF100C09);
        }
        drawSignatureRows(graphics);
        drawScrollCue(graphics);
    }

    private void drawSignatureRows(GuiGraphicsExtractor graphics) {
        if (!menu.canManageSignatures() || menu.discoveredSites().isEmpty()) {
            return;
        }
        List<Identifier> sites = menu.discoveredSites();
        int end = Math.min(sites.size(), signatureScroll + VISIBLE_SIGNATURE_ROWS);
        for (int index = signatureScroll; index < end; index++) {
            int row = index - signatureScroll;
            int x = leftPos + SIGNATURE_LIST_X - 2;
            int y = topPos + SIGNATURE_LIST_Y + row * SIGNATURE_ROW_HEIGHT;
            boolean ignored = knowledge().ignored().contains(sites.get(index));
            graphics.fill(x, y, x + SIGNATURE_LIST_WIDTH + 2, y + SIGNATURE_ROW_HEIGHT - 1, PANEL);
            graphics.fill(
                    x,
                    y,
                    x + 1,
                    y + SIGNATURE_ROW_HEIGHT - 1,
                    ignored ? DANGER : GOLD_DIM);
        }
    }

    private void drawScrollCue(GuiGraphicsExtractor graphics) {
        int total = menu.discoveredSites().size();
        if (!menu.canManageSignatures() || total <= VISIBLE_SIGNATURE_ROWS) {
            return;
        }
        int trackX = leftPos + SIGNATURE_LIST_X + SIGNATURE_LIST_WIDTH + 1;
        int trackY = topPos + SIGNATURE_LIST_Y;
        int trackH = VISIBLE_SIGNATURE_ROWS * SIGNATURE_ROW_HEIGHT - 2;
        graphics.fill(trackX, trackY, trackX + 2, trackY + trackH, GOLD_DIM);
        int max = total - VISIBLE_SIGNATURE_ROWS;
        int thumbH = Math.max(6, trackH * VISIBLE_SIGNATURE_ROWS / total);
        int thumbY = trackY + (trackH - thumbH) * signatureScroll / max;
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, GOLD);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String titleText = ellipsize(title, HEADER_TEXT_MAX);
        int titleWidth = font.width(titleText);
        graphics.text(font, titleText, (imageWidth - titleWidth) / 2, TITLE_Y, TEXT, false);

        String statusText = ellipsize(statusLine(), HEADER_TEXT_MAX);
        int statusWidth = font.width(statusText);
        graphics.text(
                font,
                statusText,
                (imageWidth - statusWidth) / 2,
                STATUS_Y,
                TEXT_MUTED,
                false);

        if (menu.canManageSignatures()) {
            if (menu.discoveredSites().isEmpty()) {
                drawCenteredMuted(
                        graphics,
                        Component.translatable("gui.echoes_show_the_past.no_signatures"),
                        128);
            } else {
                graphics.text(
                        font,
                        Component.translatable("gui.echoes_show_the_past.signatures"),
                        SIGNATURE_LIST_X,
                        100,
                        TEXT_MUTED,
                        false);
                if (menu.discoveredSites().size() > VISIBLE_SIGNATURE_ROWS) {
                    int first = signatureScroll + 1;
                    int last = Math.min(
                            menu.discoveredSites().size(),
                            signatureScroll + VISIBLE_SIGNATURE_ROWS);
                    String scroll = first + "-" + last + "/" + menu.discoveredSites().size();
                    int scrollX = SIGNATURE_LIST_X
                            + font.width(Component.translatable("gui.echoes_show_the_past.signatures"))
                            + 6;
                    int muteAllLeft = ResonatorMenu.MUTE_ALL_BUTTON_X;
                    if (scrollX + font.width(scroll) < muteAllLeft - 4) {
                        graphics.text(font, scroll, scrollX, 100, TEXT_DIM, false);
                    }
                }
                drawSiteNames(graphics);
            }
        }

        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_MUTED, false);
    }

    private Component statusLine() {
        ResonatorLoadout loadout = menu.loadout();
        if (loadout.modules().isEmpty()) {
            return Component.translatable("gui.echoes_show_the_past.status_empty");
        }
        String seconds = formatSeconds(loadout.cooldownTicks());
        if (loadout.has(ResonatorModule.DIRECTIONAL_MATRIX)) {
            if (loadout.effectiveDirectionalMode()) {
                return Component.translatable(
                        "gui.echoes_show_the_past.status_directional",
                        loadout.effectiveRange(),
                        seconds,
                        (int) loadout.coneDegrees());
            }
            return Component.translatable(
                    "gui.echoes_show_the_past.status_wide",
                    loadout.effectiveRange(),
                    seconds);
        }
        return Component.translatable(
                "gui.echoes_show_the_past.status_ready",
                loadout.effectiveRange(),
                seconds);
    }

    private void drawSiteNames(GuiGraphicsExtractor graphics) {
        List<Identifier> sites = menu.discoveredSites();
        int end = Math.min(sites.size(), signatureScroll + VISIBLE_SIGNATURE_ROWS);
        for (int index = signatureScroll; index < end; index++) {
            EchoSiteType site = EchoSiteType.byId(sites.get(index));
            if (site == null) {
                continue;
            }
            int row = index - signatureScroll;
            int y = SIGNATURE_LIST_Y + row * SIGNATURE_ROW_HEIGHT + 3;
            boolean ignored = knowledge().ignored().contains(site.id());
            graphics.text(
                    font,
                    ellipsize(Component.translatable(site.translationKey()), SIGNATURE_NAME_WIDTH),
                    SIGNATURE_LIST_X + 16,
                    y,
                    ignored ? TEXT_DIM : TEXT,
                    false);
        }
    }

    private void drawCenteredMuted(GuiGraphicsExtractor graphics, Component text, int y) {
        List<FormattedCharSequence> lines = font.split(text, SIGNATURE_LIST_WIDTH);
        int lineY = y;
        for (FormattedCharSequence line : lines) {
            int width = font.width(line);
            graphics.text(font, line, (imageWidth - width) / 2, lineY, TEXT_MUTED, false);
            lineY += 10;
        }
    }

    private static String formatSeconds(int ticks) {
        return Integer.toString(Math.max(1, (ticks + 19) / 20));
    }

    private String ellipsize(Component component, int maxWidth) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null
                && hoveredSlot.index < ResonatorLoadout.SLOT_COUNT
                && !hoveredSlot.hasItem()) {
            graphics.setTooltipForNextFrame(
                    font,
                    Component.translatable("gui.echoes_show_the_past.module_slot_hint"),
                    mouseX,
                    mouseY);
        }
    }

    private record SiteControls(
            EchoSiteType site,
            SwatchButton colorButton,
            BrassButton ignoredButton) {
    }

    private static final class BrassButton extends AbstractButton {
        private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;
        private final Runnable action;
        private final boolean textured;

        private BrassButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                Runnable action,
                boolean textured) {
            super(x, y, width, height, message);
            this.action = action;
            this.textured = textured;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (textured) {
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
            } else {
                int background = isHoveredOrFocused() && active ? PANEL_HOVER : PANEL;
                graphics.fill(getX(), getY(), getRight(), getBottom(), background);
                graphics.outline(
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        active ? GOLD_DIM : TEXT_DIM);
            }

            var font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            int max = Math.max(8, getWidth() - 4);
            if (font.width(label) > max) {
                label = font.plainSubstrByWidth(label, Math.max(0, max - font.width("…"))) + "…";
            }
            graphics.centeredText(
                    font,
                    label,
                    getX() + getWidth() / 2,
                    getY() + Math.max(1, (getHeight() - 8) / 2),
                    active ? TEXT : TEXT_MUTED);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class SwatchButton extends AbstractButton {
        private final Runnable action;
        private final IntSupplier color;

        private SwatchButton(int x, int y, int width, int height, Runnable action, IntSupplier color) {
            super(x, y, width, height, Component.empty());
            this.action = action;
            this.color = color;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), PANEL);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), GOLD_DIM);
            graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, color.getAsInt());
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
