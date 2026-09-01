package dev.alvar.echoespast.visual;

/**
 * Distance interval that can contain a radial surface crest. Keeping this
 * calculation independent from rendering lets a sorted face cache discard
 * almost the entire scan without testing it every frame.
 */
public record EchoRadialWindow(
        double minimumDistance,
        double maximumDistance) {

    public static EchoRadialWindow forPulse(
            double front,
            boolean returning,
            double widthScale,
            double supportAhead,
            double supportBehind,
            double margin) {
        double width = Math.max(1.0, widthScale);
        double lowerSupport = returning
                ? supportAhead
                : supportBehind;
        double upperSupport = returning
                ? supportBehind
                : supportAhead;
        return new EchoRadialWindow(
                Math.max(
                        0.0,
                        front - (lowerSupport + margin) * width),
                front + (upperSupport + margin) * width);
    }
}
