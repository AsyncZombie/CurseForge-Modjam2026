package dev.alvar.echoespast.command;

import dev.alvar.echoespast.network.MedusaHeadPoseDebugPayload;
import dev.alvar.echoespast.relic.MedusaHeadAimMath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class MedusaHeadPoseDebugSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    static Session of(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> Session.authored());
    }

    static Session put(ServerPlayer player, Session session) {
        SESSIONS.put(player.getUUID(), session);
        send(player, session);
        return session;
    }

    static void reset(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        send(player, Session.authored());
    }

    static void send(ServerPlayer player, Session session) {
        PacketDistributor.sendToPlayer(
                player,
                new MedusaHeadPoseDebugPayload(
                        session.enabled(),
                        session.rest().x(),
                        session.rest().y(),
                        session.rest().z(),
                        session.active().x(),
                        session.active().y(),
                        session.active().z()));
    }

    record Session(
            boolean enabled,
            MedusaHeadAimMath.PoseEuler rest,
            MedusaHeadAimMath.PoseEuler active) {
        static Session authored() {
            return new Session(false, MedusaHeadAimMath.REST, MedusaHeadAimMath.ACTIVE);
        }

        Session withRest(MedusaHeadAimMath.PoseEuler pose) {
            return new Session(true, pose.canonical(), active);
        }

        Session withActive(MedusaHeadAimMath.PoseEuler pose) {
            return new Session(true, rest, pose.canonical());
        }
    }

    private MedusaHeadPoseDebugSessions() {
    }
}
