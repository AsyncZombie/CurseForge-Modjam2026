package dev.alvar.echoespast.client;

import org.slf4j.Logger;

/**
 * Temporary aggregate diagnostics for the second Past Echo performance pass.
 * One log line every five seconds is enough to separate extraction, entity and
 * geometry costs without perturbing every rendered frame with logging.
 */
final class PastEchoRenderProfiler {
    private static final long WINDOW_NANOS = 5_000_000_000L;

    private static long windowStartNanos;
    private static int frames;
    private static long extractNanos;
    private static long maximumExtractNanos;
    private static long selectionNanos;
    private static long occupancyNanos;
    private static long entityExtractionNanos;
    private static long waveBuildNanos;
    private static int maximumRememberedModels;
    private static int maximumPresentModels;
    private static int maximumVisibleEntities;
    private static int maximumWaveFaces;

    private static int mainGeometryCalls;
    private static long mainGeometryNanos;
    private static long maximumMainGeometryNanos;
    private static int shadowGeometryCalls;
    private static long shadowGeometryNanos;
    private static long maximumShadowGeometryNanos;
    private static int maximumGeometryModels;
    private static int maximumGeometryQuads;
    private static int maximumFallbackBoxes;

    private static int mainEntitySubmitCalls;
    private static long mainEntitySubmitNanos;
    private static int shadowEntitySubmitCalls;
    private static long shadowEntitySubmitNanos;

    static void recordExtract(
            long totalNanos,
            long candidateSelectionNanos,
            long ghostOccupancyNanos,
            long entitiesNanos,
            long wavesNanos,
            int rememberedModels,
            int presentModels,
            int visibleEntities,
            int waveFaces) {
        frames++;
        extractNanos += totalNanos;
        maximumExtractNanos = Math.max(maximumExtractNanos, totalNanos);
        selectionNanos += candidateSelectionNanos;
        occupancyNanos += ghostOccupancyNanos;
        entityExtractionNanos += entitiesNanos;
        waveBuildNanos += wavesNanos;
        maximumRememberedModels = Math.max(maximumRememberedModels, rememberedModels);
        maximumPresentModels = Math.max(maximumPresentModels, presentModels);
        maximumVisibleEntities = Math.max(maximumVisibleEntities, visibleEntities);
        maximumWaveFaces = Math.max(maximumWaveFaces, waveFaces);
    }

    static void recordGeometry(
            boolean shadowPass,
            long nanos,
            int models,
            int quads,
            int fallbackBoxes) {
        if (shadowPass) {
            shadowGeometryCalls++;
            shadowGeometryNanos += nanos;
            maximumShadowGeometryNanos = Math.max(maximumShadowGeometryNanos, nanos);
        } else {
            mainGeometryCalls++;
            mainGeometryNanos += nanos;
            maximumMainGeometryNanos = Math.max(maximumMainGeometryNanos, nanos);
        }
        maximumGeometryModels = Math.max(maximumGeometryModels, models);
        maximumGeometryQuads = Math.max(maximumGeometryQuads, quads);
        maximumFallbackBoxes = Math.max(maximumFallbackBoxes, fallbackBoxes);
    }

    static void recordEntitySubmit(boolean shadowPass, long nanos) {
        if (shadowPass) {
            shadowEntitySubmitCalls++;
            shadowEntitySubmitNanos += nanos;
        } else {
            mainEntitySubmitCalls++;
            mainEntitySubmitNanos += nanos;
        }
    }

    static void logIfReady(Logger logger, long now) {
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
            return;
        }
        if (now - windowStartNanos < WINDOW_NANOS || frames == 0) {
            return;
        }
        logger.info(
                "Past Echo render diagnostic: frames={}, extractMs(avg/max)={}/{}, selectMs(avg)={}, occupancyMs(avg)={}, entityExtractMs(avg)={}, waveBuildMs(avg)={}, visible(max remembered/present/entities/waveFaces)={}/{}/{}/{}, geometryMain(calls/avgMs/maxMs)={}/{}/{}, geometryShadow(calls/avgMs/maxMs)={}/{}/{}, geometryInput(max models/quads/boxes)={}/{}/{}, entitySubmitMain(calls/avgMs)={}/{}, entitySubmitShadow(calls/avgMs)={}/{}",
                frames,
                averageMillis(extractNanos, frames),
                millis(maximumExtractNanos),
                averageMillis(selectionNanos, frames),
                averageMillis(occupancyNanos, frames),
                averageMillis(entityExtractionNanos, frames),
                averageMillis(waveBuildNanos, frames),
                maximumRememberedModels,
                maximumPresentModels,
                maximumVisibleEntities,
                maximumWaveFaces,
                mainGeometryCalls,
                averageMillis(mainGeometryNanos, mainGeometryCalls),
                millis(maximumMainGeometryNanos),
                shadowGeometryCalls,
                averageMillis(shadowGeometryNanos, shadowGeometryCalls),
                millis(maximumShadowGeometryNanos),
                maximumGeometryModels,
                maximumGeometryQuads,
                maximumFallbackBoxes,
                mainEntitySubmitCalls,
                averageMillis(mainEntitySubmitNanos, mainEntitySubmitCalls),
                shadowEntitySubmitCalls,
                averageMillis(shadowEntitySubmitNanos, shadowEntitySubmitCalls));
        reset(now);
    }

    private static double averageMillis(long nanos, int samples) {
        return samples == 0 ? 0.0 : nanos / 1_000_000.0 / samples;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void reset(long now) {
        windowStartNanos = now;
        frames = 0;
        extractNanos = 0L;
        maximumExtractNanos = 0L;
        selectionNanos = 0L;
        occupancyNanos = 0L;
        entityExtractionNanos = 0L;
        waveBuildNanos = 0L;
        maximumRememberedModels = 0;
        maximumPresentModels = 0;
        maximumVisibleEntities = 0;
        maximumWaveFaces = 0;
        mainGeometryCalls = 0;
        mainGeometryNanos = 0L;
        maximumMainGeometryNanos = 0L;
        shadowGeometryCalls = 0;
        shadowGeometryNanos = 0L;
        maximumShadowGeometryNanos = 0L;
        maximumGeometryModels = 0;
        maximumGeometryQuads = 0;
        maximumFallbackBoxes = 0;
        mainEntitySubmitCalls = 0;
        mainEntitySubmitNanos = 0L;
        shadowEntitySubmitCalls = 0;
        shadowEntitySubmitNanos = 0L;
    }

    private PastEchoRenderProfiler() {
    }
}
