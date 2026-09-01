package dev.alvar.echoespast.entity.combat;

/**
 * Authoritative states shared by the complete Greek combat controller and
 * future Unknown eras.
 */
public enum UnknownCombatState {
    NEUTRAL,
    STAB,
    CHARGE,
    PHALANX,
    JAVELIN,
    CRASH_STUN,
    RECOVERY,
    BREATHER,
    KHOPESH_COMBO,
    DUAT_GATE,
    SOLAR_JUDGMENT,
    SEKHMET_HUNT,
    SHIELD_BASH,
    SPEAR_ERUPTION,
    MEDIEVAL_COMBO,
    MEDIEVAL_OVERHEAD,
    MEDIEVAL_SHIELD_BASH,
    MEDIEVAL_GUARD,
    MEDIEVAL_RIPOSTE,
    MEDIEVAL_SHOULDER_RUSH,
    MEDIEVAL_RUBBLE_KICK,
    /** Automatic final kneel; appended to preserve every existing network id. */
    EXECUTION;

    public static UnknownCombatState fromNetwork(byte encoded) {
        int index = Byte.toUnsignedInt(encoded);
        return index < values().length ? values()[index] : NEUTRAL;
    }

    public byte networkId() {
        return (byte) ordinal();
    }
}
