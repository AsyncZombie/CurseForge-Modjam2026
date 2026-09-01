package dev.alvar.echoespast.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.boss.UnknownEraSequence;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.server.UnknownFightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Compact premium Unknown boss HUD: slim metal rail, soft memory fill, and
 * circular epoch marks for the six Past/Ruins stages.
 */
public final class UnknownBossBarOverlay {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
            EchoesShowThePast.MOD_ID, "textures/gui/unknown_boss_bar.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    private static final int ATLAS_SIZE = 256;
    private static final int FRAME_W = 182;
    private static final int FRAME_H = 16;
    private static final int FILL_X = 5;
    private static final int FILL_Y = 5;
    private static final int FILL_W = 172;
    private static final int FILL_H = 5;
    private static final int MARK_SIZE = 11;
    private static final int MARK_ATLAS_U = 190;
    private static final int MARK_ATLAS_V = 170;
    private static final int MARK_STRIDE = 13;
    private static final int FILL_ATLAS_V = 128;
    private static final int TOTAL_HEIGHT = 26;

    private static final int[] TITLE_COLOR = {
            0xFFEAF4FF, 0xFFD0D8E0, 0xFFFFE8B0, 0xFFE0D0A8,
            0xFFFFD4D4, 0xFFE8B8A8, 0xFFF4DEFF, 0xFFF8F8FF
    };
    private static final int[] ACCENT = {
            0xFF6EA8DC, 0xFF7892A8, 0xFF38BAA8, 0xFF6E9484,
            0xFFC6363A, 0xFFA84638, 0xFFE48CFF, 0xFFFFFFFF
    };

    private UnknownBossBarOverlay() {
    }

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        if (!isUnknownBar(bossEvent)) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(TOTAL_HEIGHT);
        render(
                event.getGuiGraphics(),
                event.getX(),
                event.getY(),
                bossEvent,
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static boolean isUnknownBar(LerpingBossEvent bossEvent) {
        if (ClientUnknownBossBar.matches(bossEvent.getId())) {
            return true;
        }
        if (bossEvent.getName().getContents() instanceof TranslatableContents contents) {
            return contents.getKey().startsWith("bossbar.echoes_show_the_past.unknown");
        }
        return false;
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            LerpingBossEvent bossEvent,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        byte theme = ClientUnknownBossBar.isActive()
                ? ClientUnknownBossBar.themeIndex()
                : themeFromVanilla(bossEvent);
        float progress = Mth.clamp(bossEvent.getProgress(), 0.0F, 1.0F);
        float pulse = 0.5F + 0.5F * Mth.sin((System.currentTimeMillis() % 100000L) / 260.0F);
        float flash = ClientUnknownBossBar.themeFlash(partialTick);
        boolean reconstructing = ClientUnknownBossBar.phase()
                == UnknownBossBarPayload.PHASE_RECONSTRUCTING;
        boolean dimMarks = theme >= 6;
        int accent = ACCENT[theme];

        Component title = bossEvent.getName();
        int titleWidth = minecraft.font.width(title);
        int titleX = x + (FRAME_W - titleWidth) / 2;
        int titleColor = TITLE_COLOR[theme];
        if (flash > 0.01F) {
            titleColor = ARGB.srgbLerp(flash, titleColor, 0xFFFFFFFF);
        }
        // Soft title plate — thin, not a heavy box.
        graphics.fill(titleX - 5, y - 10, titleX + titleWidth + 5, y - 1, 0x88080A10);
        graphics.fill(titleX - 5, y - 1, titleX + titleWidth + 5, y, ARGB.color(70, ARGB.red(accent), ARGB.green(accent), ARGB.blue(accent)));
        graphics.text(minecraft.font, title, titleX, y - 9, titleColor, true);

        int frameTint = 0xFFFFFFFF;
        if (reconstructing) {
            float shimmer = 0.82F + 0.18F * pulse;
            frameTint = ARGB.color(255, (int) (255 * shimmer), (int) (255 * shimmer), (int) (255 * shimmer));
        } else if (flash > 0.01F) {
            frameTint = ARGB.srgbLerp(flash * 0.4F, 0xFFFFFFFF, accent);
        }
        graphics.blit(
                PIPELINE,
                ATLAS,
                x,
                y,
                0.0F,
                theme * FRAME_H,
                FRAME_W,
                FRAME_H,
                ATLAS_SIZE,
                ATLAS_SIZE,
                frameTint);

        int wellX = x + FILL_X;
        int wellY = y + FILL_Y;
        int fillWidth = Mth.lerpDiscrete(progress, 0, FILL_W);
        if (fillWidth > 0) {
            // Sample the themed fill strip; width clips naturally.
            int fillTint = reconstructing
                    ? ARGB.color(200 + (int) (40 * pulse), 255, 255, 255)
                    : 0xFFFFFFFF;
            graphics.blit(
                    PIPELINE,
                    ATLAS,
                    wellX,
                    wellY,
                    0.0F,
                    FILL_ATLAS_V + theme * 5,
                    fillWidth,
                    FILL_H,
                    ATLAS_SIZE,
                    ATLAS_SIZE,
                    fillTint);
            // Soft leading tip.
            if (fillWidth < FILL_W) {
                graphics.fill(
                        wellX + fillWidth - 1,
                        wellY,
                        wellX + fillWidth + 1,
                        wellY + FILL_H,
                        ARGB.color(160 + (int) (60 * pulse), ARGB.red(accent), ARGB.green(accent), ARGB.blue(accent)));
            }
            // Gentle specular sweep over the fill.
            if (fillWidth > 24) {
                int sweep = wellX + (int) ((fillWidth - 16) * ((pulse + 1.0F) * 0.5F));
                graphics.fill(
                        Math.max(wellX, sweep),
                        wellY,
                        Math.min(wellX + fillWidth, sweep + 12),
                        wellY + 2,
                        0x44FFFFFF);
            }
        }

        // Threshold notches at the real HP floors (510…60), not equal sixths.
        // Fill is left-aligned by progress, so these sit where each phase gate lands.
        for (int threshold = 0; threshold < 6; threshold++) {
            float gate = UnknownFightManager.healthFloorForThreshold(threshold)
                    / UnknownFightManager.BOSS_MAX_HEALTH;
            int nx = wellX + Math.round(gate * FILL_W);
            graphics.fill(nx, wellY + 1, nx + 1, wellY + FILL_H - 1, 0x88000000);
            graphics.fill(nx - 1, wellY + 1, nx, wellY + FILL_H - 1, 0x33FFFFFF);
        }

        // Hairline that ties marks to the rail.
        int markY = y + FRAME_H + 2;
        graphics.fill(x + 8, markY - 1, x + FRAME_W - 8, markY, 0x55FFFFFF);
        graphics.fill(x + 8, markY, x + FRAME_W - 8, markY + 1, 0x88000000);
        drawMarks(graphics, x, markY + 1, pulse, dimMarks);

        if (flash > 0.02F) {
            graphics.fill(
                    x + 4,
                    y + 3,
                    x + FRAME_W - 4,
                    y + FRAME_H - 3,
                    ARGB.color((int) (55 * flash), 255, 255, 255));
        }
    }

    private static void drawMarks(
            GuiGraphicsExtractor graphics,
            int barX,
            int markY,
            float pulse,
            boolean dimAll) {
        int active = ClientUnknownBossBar.activeSealIndex();
        int completed = ClientUnknownBossBar.thresholdIndex();
        float maxHp = UnknownFightManager.BOSS_MAX_HEALTH;
        for (int kind = 0; kind < 6; kind++) {
            int state;
            if (dimAll) {
                state = kind < completed ? 2 : 0;
            } else if (kind < completed) {
                state = 2;
            } else if (kind == active) {
                state = 1;
            } else {
                state = 0;
            }
            // Center each mark under its 90 HP combat segment (rightmost = Medieval Past).
            float high = kind == 0
                    ? 1.0F
                    : UnknownFightManager.healthFloorForThreshold(kind - 1) / maxHp;
            float low = UnknownFightManager.healthFloorForThreshold(kind) / maxHp;
            float center = (high + low) * 0.5F;
            int sx = barX + FILL_X + Math.round(center * FILL_W) - MARK_SIZE / 2;
            sx = Mth.clamp(sx, barX, barX + FRAME_W - MARK_SIZE);

            graphics.fill(sx + MARK_SIZE / 2, markY - 1, sx + MARK_SIZE / 2 + 1, markY + 1, 0x66FFFFFF);

            // Active feedback is a soft tint on the badge itself — no square behind it.
            int tint = state == 1
                    ? ARGB.color(
                            255,
                            255,
                            230 + (int) (20 * pulse),
                            190 + (int) (40 * pulse))
                    : 0xFFFFFFFF;
            graphics.blit(
                    PIPELINE,
                    ATLAS,
                    sx,
                    markY,
                    MARK_ATLAS_U + state * MARK_STRIDE,
                    MARK_ATLAS_V + sealAtlasRow(kind) * MARK_STRIDE,
                    MARK_SIZE,
                    MARK_SIZE,
                    ATLAS_SIZE,
                    ATLAS_SIZE,
                    tint);
        }
    }

    private static int sealAtlasRow(int fightSlot) {
        UnknownEraSequence era = UnknownEraSequence.forFightSlot(fightSlot);
        return era.atlasStageRow(UnknownEraSequence.isRuinsSlot(fightSlot));
    }

    private static byte themeFromVanilla(LerpingBossEvent bossEvent) {
        return switch (bossEvent.getColor()) {
            case BLUE -> (byte) 0;
            case YELLOW -> (byte) 2;
            case RED -> (byte) 4;
            case WHITE -> (byte) 7;
            default -> (byte) 6;
        };
    }
}
