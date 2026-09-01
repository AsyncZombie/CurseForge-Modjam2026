package dev.alvar.echoespast.boss;

import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.entity.UnknownEntity;
import dev.alvar.echoespast.network.UnknownBossBarPayload;
import dev.alvar.echoespast.resonance.ResonanceColor;
import dev.alvar.echoespast.world.TimelessDimensions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

/**
 * Canonical chronology and presentation contract for the Unknown encounter.
 *
 * <p>Declaration order is gameplay order. Server stages, arena templates,
 * altar sockets and HUD seals must obtain their indices from this enum instead
 * of maintaining parallel switches.</p>
 */
public enum UnknownEraSequence {
    MEDIEVAL(
            UnknownEntity.ERA_MEDIEVAL,
            UnknownBossBarPayload.ERA_MEDIEVAL,
            ResonanceColor.CORAL,
            BossEvent.BossBarColor.RED,
            id("boss/medieval_past"),
            id("boss/medieval_ruins"),
            TimelessDimensions.PEDESTAL_MEDIEVAL,
            4),
    GREEK(
            UnknownEntity.ERA_GREEK,
            UnknownBossBarPayload.ERA_GREEK,
            ResonanceColor.PALE_BLUE,
            BossEvent.BossBarColor.BLUE,
            id("boss/greek_past"),
            id("boss/greek_ruins"),
            TimelessDimensions.PEDESTAL_GREEK,
            0),
    EGYPTIAN(
            UnknownEntity.ERA_EGYPTIAN,
            UnknownBossBarPayload.ERA_EGYPTIAN,
            ResonanceColor.GOLD,
            BossEvent.BossBarColor.YELLOW,
            id("boss/egyptian_past"),
            id("boss/egyptian_ruins"),
            TimelessDimensions.PEDESTAL_EGYPTIAN,
            2);

    public static final int FRAGMENTS_PER_ERA = 2;
    public static final int ERA_COUNT = values().length;
    public static final int STAGE_COUNT = ERA_COUNT * FRAGMENTS_PER_ERA;
    private static final List<UnknownEraSequence> ORDER = List.of(values());

    private final byte entityEra;
    private final byte bossBarEra;
    private final ResonanceColor fragmentColor;
    private final BossEvent.BossBarColor bossBarColor;
    private final Identifier pastTemplate;
    private final Identifier ruinsTemplate;
    private final BlockPos pedestal;
    /** Existing atlas row; chronology can change without repainting the atlas. */
    private final int atlasStageBase;

    UnknownEraSequence(
            byte entityEra,
            byte bossBarEra,
            ResonanceColor fragmentColor,
            BossEvent.BossBarColor bossBarColor,
            Identifier pastTemplate,
            Identifier ruinsTemplate,
            BlockPos pedestal,
            int atlasStageBase) {
        this.entityEra = entityEra;
        this.bossBarEra = bossBarEra;
        this.fragmentColor = fragmentColor;
        this.bossBarColor = bossBarColor;
        this.pastTemplate = pastTemplate;
        this.ruinsTemplate = ruinsTemplate;
        this.pedestal = pedestal.immutable();
        this.atlasStageBase = atlasStageBase;
    }

    public int eraIndex() {
        return ordinal();
    }

    public int pastThreshold() {
        return eraIndex() * FRAGMENTS_PER_ERA;
    }

    public int ruinsThreshold() {
        return pastThreshold() + 1;
    }

    public int threshold(boolean ruins) {
        return ruins ? ruinsThreshold() : pastThreshold();
    }

    public int fightSlot(boolean ruins) {
        return threshold(ruins);
    }

    public Identifier template(boolean ruins) {
        return ruins ? ruinsTemplate : pastTemplate;
    }

    public int atlasStageRow(boolean ruins) {
        return atlasStageBase + (ruins ? 1 : 0);
    }

    public byte entityEra() {
        return entityEra;
    }

    public byte bossBarEra() {
        return bossBarEra;
    }

    public ResonanceColor fragmentColor() {
        return fragmentColor;
    }

    public BossEvent.BossBarColor bossBarColor() {
        return bossBarColor;
    }

    public Identifier pastTemplate() {
        return pastTemplate;
    }

    public Identifier ruinsTemplate() {
        return ruinsTemplate;
    }

    public BlockPos pedestal() {
        return pedestal;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<UnknownEraSequence> ordered() {
        return ORDER;
    }

    public static UnknownEraSequence forIndex(int eraIndex) {
        if (eraIndex < 0 || eraIndex >= ERA_COUNT) {
            throw new IllegalArgumentException("Unknown era index out of range: " + eraIndex);
        }
        return ORDER.get(eraIndex);
    }

    public static UnknownEraSequence clamped(int eraIndex) {
        return ORDER.get(Math.clamp(eraIndex, 0, ERA_COUNT - 1));
    }

    public static UnknownEraSequence forFightSlot(int slot) {
        int safeSlot = Math.clamp(slot, 0, STAGE_COUNT - 1);
        return forIndex(safeSlot / FRAGMENTS_PER_ERA);
    }

    public static boolean isRuinsSlot(int slot) {
        return Math.clamp(slot, 0, STAGE_COUNT - 1) % FRAGMENTS_PER_ERA == 1;
    }

    public static UnknownEraSequence forKey(String key) {
        return valueOf(key.trim().toUpperCase(Locale.ROOT));
    }

    public static Optional<UnknownEraSequence> forBossBarEra(byte era) {
        for (UnknownEraSequence definition : ORDER) {
            if (definition.bossBarEra == era) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, path);
    }
}
