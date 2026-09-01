package dev.alvar.echoespast.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alvar.echoespast.EchoesShowThePast;
import dev.alvar.echoespast.boss.UnknownEraSequence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Durable, server-authoritative state for the single Unknown encounter.
 *
 * <p>The boss bar is deliberately transient: its source of truth is this saved
 * state, so it can always be rebuilt without serializing client-facing data.
 */
public final class UnknownEncounterSavedData extends SavedData {
    private static final Codec<UnknownFightManager.Era> ERA_CODEC = Codec.STRING.xmap(
            value -> parseEnum(UnknownFightManager.Era.class, value, UnknownFightManager.Era.VOID),
            Enum::name);
    private static final Codec<UnknownFightManager.Phase> PHASE_CODEC = Codec.STRING.xmap(
            value -> parseEnum(UnknownFightManager.Phase.class, value, UnknownFightManager.Phase.IDLE),
            Enum::name);
    private static final Codec<UnknownFightManager.Action> ACTION_CODEC = Codec.STRING.xmap(
            value -> parseEnum(UnknownFightManager.Action.class, value, UnknownFightManager.Action.WAITING),
            Enum::name);

    public static final Codec<UnknownEncounterSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.optionalFieldOf("boss_id").forGetter(data -> Optional.ofNullable(data.bossId)),
                    UUIDUtil.CODEC.optionalFieldOf("owner_id").forGetter(data -> Optional.ofNullable(data.ownerId)),
                    ERA_CODEC.optionalFieldOf("era", UnknownFightManager.Era.VOID).forGetter(data -> data.era),
                    PHASE_CODEC.optionalFieldOf("phase", UnknownFightManager.Phase.IDLE).forGetter(data -> data.phase),
                    ACTION_CODEC.optionalFieldOf("action", UnknownFightManager.Action.WAITING).forGetter(data -> data.action),
                    Codec.intRange(0, UnknownEraSequence.STAGE_COUNT)
                            .optionalFieldOf("threshold", 0).forGetter(data -> data.thresholdIndex),
                    Codec.intRange(0, UnknownEraSequence.ERA_COUNT)
                            .optionalFieldOf("next_era", 0).forGetter(data -> data.nextEraIndex),
                    Codec.INT.optionalFieldOf("cinematic_ticks", 0).forGetter(data -> data.cinematicTicks),
                    Codec.intRange(1, UnknownEraSequence.ERA_COUNT)
                            .optionalFieldOf("review_eras", UnknownEraSequence.ERA_COUNT)
                            .forGetter(data -> data.reviewEraCount),
                    Codec.intRange(-1, 7).optionalFieldOf("deposit_step", -1).forGetter(data -> data.depositStep),
                    Codec.INT.optionalFieldOf("era_stun_ticks", 0).forGetter(data -> data.eraStunTicks),
                    Codec.BOOL.optionalFieldOf("medieval_rooftop_started", false)
                            .forGetter(data -> data.medievalRooftopStarted),
                    Codec.BOOL.optionalFieldOf("medieval_inner_active", false)
                            .forGetter(data -> data.medievalInnerActive),
                    UUIDUtil.CODEC.listOf().optionalFieldOf("medieval_vanguard_ids", List.of())
                            .forGetter(data -> data.medievalVanguardIds),
                    Codec.INT.optionalFieldOf("execution_ticks", 0)
                            .forGetter(data -> data.executionTicks),
                    Codec.BOOL.optionalFieldOf("execution_resolved", false)
                            .forGetter(data -> data.executionResolved))
            .apply(instance, UnknownEncounterSavedData::new));

    public static final SavedDataType<UnknownEncounterSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_encounter"),
            UnknownEncounterSavedData::new,
            CODEC);

    private UUID bossId;
    private UUID ownerId;
    private UnknownFightManager.Era era = UnknownFightManager.Era.VOID;
    private UnknownFightManager.Phase phase = UnknownFightManager.Phase.IDLE;
    private UnknownFightManager.Action action = UnknownFightManager.Action.WAITING;
    private int thresholdIndex;
    private int nextEraIndex;
    private int cinematicTicks;
    private int reviewEraCount = UnknownEraSequence.ERA_COUNT;
    /** -1 idle; 0-5 next fragment; 6 stone; 7 finished depositing. */
    private int depositStep = -1;
    private int eraStunTicks;
    private boolean medievalRooftopStarted;
    private boolean medievalInnerActive;
    private List<UUID> medievalVanguardIds = List.of();
    private int executionTicks;
    private boolean executionResolved;
    private transient ServerBossEvent bossBar;

    public UnknownEncounterSavedData() {
    }

    private UnknownEncounterSavedData(
            Optional<UUID> bossId,
            Optional<UUID> ownerId,
            UnknownFightManager.Era era,
            UnknownFightManager.Phase phase,
            UnknownFightManager.Action action,
            int thresholdIndex,
            int nextEraIndex,
            int cinematicTicks,
            int reviewEraCount,
            int depositStep,
            int eraStunTicks,
            boolean medievalRooftopStarted,
            boolean medievalInnerActive,
            List<UUID> medievalVanguardIds,
            int executionTicks,
            boolean executionResolved) {
        this.bossId = bossId.orElse(null);
        this.ownerId = ownerId.orElse(null);
        this.era = era;
        this.phase = phase;
        this.action = action;
        this.thresholdIndex = thresholdIndex;
        this.nextEraIndex = nextEraIndex;
        this.cinematicTicks = Math.max(0, cinematicTicks);
        this.reviewEraCount = reviewEraCount;
        this.depositStep = depositStep;
        this.eraStunTicks = Math.max(0, eraStunTicks);
        this.medievalRooftopStarted = medievalRooftopStarted;
        this.medievalInnerActive = medievalInnerActive;
        this.medievalVanguardIds = List.copyOf(medievalVanguardIds);
        this.executionTicks = Math.max(0, executionTicks);
        this.executionResolved = executionResolved;
    }

    public void begin(UUID newBossId, UUID newOwnerId, int enabledEraCount) {
        clearRuntimeBar();
        bossId = newBossId;
        ownerId = newOwnerId;
        era = UnknownFightManager.Era.VOID;
        phase = UnknownFightManager.Phase.CINEMATIC_WALK;
        action = UnknownFightManager.Action.APPROACHING_PEDESTAL;
        thresholdIndex = 0;
        nextEraIndex = 0;
        cinematicTicks = 0;
        reviewEraCount = Math.clamp(enabledEraCount, 1, UnknownEraSequence.ERA_COUNT);
        depositStep = -1;
        eraStunTicks = 0;
        medievalRooftopStarted = false;
        medievalInnerActive = false;
        medievalVanguardIds = List.of();
        executionTicks = 0;
        executionResolved = false;
        setDirty();
    }

    public void reset() {
        clearRuntimeBar();
        bossId = null;
        ownerId = null;
        era = UnknownFightManager.Era.VOID;
        phase = UnknownFightManager.Phase.IDLE;
        action = UnknownFightManager.Action.WAITING;
        thresholdIndex = 0;
        nextEraIndex = 0;
        cinematicTicks = 0;
        reviewEraCount = UnknownEraSequence.ERA_COUNT;
        depositStep = -1;
        eraStunTicks = 0;
        medievalRooftopStarted = false;
        medievalInnerActive = false;
        medievalVanguardIds = List.of();
        executionTicks = 0;
        executionResolved = false;
        setDirty();
    }

    public boolean isActive() {
        return bossId != null && ownerId != null && phase != UnknownFightManager.Phase.IDLE;
    }

    public boolean owns(UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    public boolean controls(UUID entityId) {
        return bossId != null && bossId.equals(entityId);
    }

    public UUID bossId() {
        return bossId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UnknownFightManager.Era era() {
        return era;
    }

    public UnknownFightManager.Phase phase() {
        return phase;
    }

    public UnknownFightManager.Action action() {
        return action;
    }

    public int thresholdIndex() {
        return thresholdIndex;
    }

    public int nextEraIndex() {
        return nextEraIndex;
    }

    public int cinematicTicks() {
        return cinematicTicks;
    }

    public int reviewEraCount() {
        return reviewEraCount;
    }

    public void setReviewEraCount(int value) {
        int clamped = Math.clamp(value, 1, UnknownEraSequence.ERA_COUNT);
        if (reviewEraCount != clamped) {
            reviewEraCount = clamped;
            setDirty();
        }
    }

    public int depositStep() {
        return depositStep;
    }

    public int eraStunTicks() {
        return eraStunTicks;
    }

    public boolean medievalRooftopStarted() {
        return medievalRooftopStarted;
    }

    public boolean medievalInnerActive() {
        return medievalInnerActive;
    }

    public List<UUID> medievalVanguardIds() {
        return medievalVanguardIds;
    }

    public int executionTicks() {
        return executionTicks;
    }

    public boolean executionResolved() {
        return executionResolved;
    }

    public void setEra(UnknownFightManager.Era value) {
        if (era != value) {
            era = value;
            setDirty();
        }
    }

    public void setState(UnknownFightManager.Phase newPhase, UnknownFightManager.Action newAction) {
        if (phase != newPhase || action != newAction) {
            phase = newPhase;
            action = newAction;
            setDirty();
        }
    }

    public void setThresholdIndex(int value) {
        int clamped = Math.clamp(value, 0, UnknownEraSequence.STAGE_COUNT);
        if (thresholdIndex != clamped) {
            thresholdIndex = clamped;
            setDirty();
        }
    }

    public void setNextEraIndex(int value) {
        int clamped = Math.clamp(value, 0, UnknownEraSequence.ERA_COUNT);
        if (nextEraIndex != clamped) {
            nextEraIndex = clamped;
            setDirty();
        }
    }

    public void setDepositStep(int value) {
        int clamped = Math.clamp(value, -1, 7);
        if (depositStep != clamped) {
            depositStep = clamped;
            setDirty();
        }
    }

    public void setEraStunTicks(int value) {
        int clamped = Math.max(0, value);
        if (eraStunTicks != clamped) {
            eraStunTicks = clamped;
            setDirty();
        }
    }

    public void setMedievalRooftopStarted(boolean value) {
        if (medievalRooftopStarted != value) {
            medievalRooftopStarted = value;
            setDirty();
        }
    }

    public void setMedievalInnerActive(boolean value) {
        if (medievalInnerActive != value) {
            medievalInnerActive = value;
            setDirty();
        }
    }

    public void setMedievalVanguardIds(List<UUID> value) {
        List<UUID> copy = List.copyOf(value);
        if (!medievalVanguardIds.equals(copy)) {
            medievalVanguardIds = copy;
            setDirty();
        }
    }

    /** Starts the final sequence once; repeated threshold callbacks are ignored. */
    public boolean beginExecution() {
        if (executionResolved
                || (phase == UnknownFightManager.Phase.EXECUTION
                        && action == UnknownFightManager.Action.EXECUTION)) {
            return false;
        }
        phase = UnknownFightManager.Phase.EXECUTION;
        action = UnknownFightManager.Action.EXECUTION;
        executionTicks = 0;
        setDirty();
        return true;
    }

    public int advanceExecutionTick() {
        executionTicks++;
        setDirty();
        return executionTicks;
    }

    /** Returns true only to the first server tick allowed to apply fatal damage. */
    public boolean tryResolveExecution() {
        if (executionResolved) {
            return false;
        }
        executionResolved = true;
        setDirty();
        return true;
    }

    public void retryExecutionResolution() {
        if (executionResolved) {
            executionResolved = false;
            setDirty();
        }
    }

    public void resetExecution() {
        if (executionTicks != 0 || executionResolved) {
            executionTicks = 0;
            executionResolved = false;
            setDirty();
        }
    }

    public int advanceCinematicTick() {
        cinematicTicks++;
        setDirty();
        return cinematicTicks;
    }

    public void resetCinematicTicks() {
        if (cinematicTicks != 0) {
            cinematicTicks = 0;
            setDirty();
        }
    }

    public int tickEraStun() {
        if (eraStunTicks > 0) {
            eraStunTicks--;
            setDirty();
        }
        return eraStunTicks;
    }

    public ServerBossEvent bossBar() {
        if (bossBar == null && bossId != null) {
            bossBar = new ServerBossEvent(
                    bossId,
                    Component.translatable("bossbar.echoes_show_the_past.unknown.void"),
                    BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_6);
            bossBar.setDarkenScreen(true);
            bossBar.setCreateWorldFog(true);
        }
        return bossBar;
    }

    public void clearRuntimeBar() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
