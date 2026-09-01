package dev.alvar.echoespast.resonance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Immutable so every mutation goes through Entity#setData, which both marks the
 * player data dirty and synchronizes the new value to that player.
 */
public record ResonanceKnowledge(
        Set<Identifier> discovered,
        Map<Identifier, ResonanceColor> colors,
        Set<Identifier> ignored,
        Set<Identifier> claimedRelics) {

    public static final Codec<ResonanceKnowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("discovered", List.of())
                    .xmap(Set::copyOf, List::copyOf)
                    .forGetter(ResonanceKnowledge::discovered),
            Codec.unboundedMap(Identifier.CODEC, ResonanceColor.CODEC)
                    .optionalFieldOf("colors", Map.of())
                    .forGetter(ResonanceKnowledge::colors),
            Identifier.CODEC.listOf().optionalFieldOf("ignored", List.of())
                    .xmap(Set::copyOf, List::copyOf)
                    .forGetter(ResonanceKnowledge::ignored),
            Identifier.CODEC.listOf().optionalFieldOf("claimed_relics", List.of())
                    .xmap(Set::copyOf, List::copyOf)
                    .forGetter(ResonanceKnowledge::claimedRelics)
    ).apply(instance, ResonanceKnowledge::new));

    public static final ResonanceKnowledge EMPTY =
            new ResonanceKnowledge(Set.of(), Map.of(), Set.of(), Set.of());

    public ResonanceKnowledge {
        discovered = Set.copyOf(discovered);
        colors = Map.copyOf(colors);
        ignored = Set.copyOf(ignored);
        claimedRelics = Set.copyOf(claimedRelics);
    }

    public ResonanceKnowledge discover(EchoSiteType site) {
        if (discovered.contains(site.id())) {
            return this;
        }
        Set<Identifier> next = new HashSet<>(discovered);
        next.add(site.id());
        Map<Identifier, ResonanceColor> nextColors = new HashMap<>(colors);
        nextColors.putIfAbsent(site.id(), site.defaultColor());
        return new ResonanceKnowledge(next, nextColors, ignored, claimedRelics);
    }

    public ResonanceKnowledge setColor(Identifier site, ResonanceColor color) {
        if (!discovered.contains(site)) {
            return this;
        }
        Map<Identifier, ResonanceColor> next = new HashMap<>(colors);
        next.put(site, color);
        return new ResonanceKnowledge(discovered, next, ignored, claimedRelics);
    }

    public ResonanceKnowledge toggleIgnored(Identifier site) {
        if (!discovered.contains(site)) {
            return this;
        }
        Set<Identifier> next = new HashSet<>(ignored);
        if (!next.remove(site)) {
            next.add(site);
        }
        return new ResonanceKnowledge(discovered, colors, next, claimedRelics);
    }

    /**
     * Mute or unmute every discovered site in {@code sites}.
     * Returns a new instance whenever the ignored set would change.
     */
    public ResonanceKnowledge setIgnored(Iterable<Identifier> sites, boolean mute) {
        Set<Identifier> next = new HashSet<>(ignored);
        for (Identifier site : sites) {
            if (!discovered.contains(site)) {
                continue;
            }
            if (mute) {
                next.add(site);
            } else {
                next.remove(site);
            }
        }
        if (next.equals(ignored)) {
            return this;
        }
        return new ResonanceKnowledge(discovered, colors, next, claimedRelics);
    }

    /** True when at least one of {@code sites} is still being listened for. */
    public boolean anyListening(Iterable<Identifier> sites) {
        for (Identifier site : sites) {
            if (discovered.contains(site) && !ignored.contains(site)) {
                return true;
            }
        }
        return false;
    }

    public ResonanceKnowledge claimRelic(Identifier site) {
        Set<Identifier> next = new HashSet<>(claimedRelics);
        next.add(site);
        return new ResonanceKnowledge(discovered, colors, ignored, next);
    }

    public ResonanceColor colorFor(EchoSiteType site) {
        return colors.getOrDefault(site.id(), site.defaultColor());
    }
}
