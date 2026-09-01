package dev.alvar.echoespast.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.alvar.echoespast.EchoesShowThePast;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Builds a normal entity texture whose RGB is real stone and whose alpha is
 * copied from the source creature. Using that texture through Minecraft's
 * entity pipelines lets Iris apply its world, hand and shadow programs without
 * discarding the petrified material.
 */
public final class PetrifiedTextureCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier STONE_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/stone.png");
    private static final Map<Identifier, Identifier> GENERATED = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static @Nullable StonePixels stonePixels;

    public static @Nullable Identifier getOrCreate(Identifier sourceTexture) {
        Identifier cached = GENERATED.get(sourceTexture);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(sourceTexture)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        TextureManager textures = minecraft.getTextureManager();
        try {
            NativeImage source = dynamicPixels(textures, sourceTexture);
            boolean closeSource = false;
            if (source == null) {
                TextureContents contents = TextureContents.load(
                        minecraft.getResourceManager(),
                        sourceTexture);
                source = contents.image();
                closeSource = true;
            }

            NativeImage material;
            try {
                material = compose(source, stone(minecraft));
            } finally {
                if (closeSource) {
                    source.close();
                }
            }

            Identifier generatedId = generatedId(sourceTexture);
            try {
                textures.register(
                        generatedId,
                        new DynamicTexture(generatedId::toString, material));
            } catch (RuntimeException | LinkageError exception) {
                material.close();
                throw exception;
            }
            GENERATED.put(sourceTexture, generatedId);
            return generatedId;
        } catch (IOException | RuntimeException | LinkageError exception) {
            FAILED.add(sourceTexture);
            LOGGER.warn(
                    "Could not build shader-compatible stone material for {}",
                    sourceTexture,
                    exception);
            return null;
        }
    }

    public static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        GENERATED.values().forEach(textures::release);
        GENERATED.clear();
        FAILED.clear();
        stonePixels = null;
    }

    public static int composePixel(int sourceArgb, int stoneArgb) {
        int red = ARGB.red(stoneArgb);
        int green = ARGB.green(stoneArgb);
        int blue = ARGB.blue(stoneArgb);
        int mineral = Math.round(red * 0.2126F + green * 0.7152F + blue * 0.0722F);
        int carvedRed = Math.round(red * 0.72F + mineral * 0.28F);
        int carvedGreen = Math.round(green * 0.72F + mineral * 0.28F);
        int carvedBlue = Math.round(blue * 0.72F + mineral * 0.28F);
        return ARGB.color(
                ARGB.alpha(sourceArgb),
                carvedRed,
                carvedGreen,
                carvedBlue);
    }

    private static NativeImage compose(NativeImage source, StonePixels stone) {
        NativeImage material = new NativeImage(
                source.getWidth(),
                source.getHeight(),
                false);
        for (int y = 0; y < source.getHeight(); y++) {
            int stoneY = sampleStoneCoordinate(y, stone.height());
            for (int x = 0; x < source.getWidth(); x++) {
                int stoneX = sampleStoneCoordinate(x, stone.width());
                int stoneArgb = stone.pixels()[stoneX + stoneY * stone.width()];
                material.setPixel(
                        x,
                        y,
                        composePixel(source.getPixel(x, y), stoneArgb));
            }
        }
        return material;
    }

    private static int sampleStoneCoordinate(int sourceCoordinate, int stoneSize) {
        // Match the fragment shader's sampling at texel centres, including
        // high-resolution resource-pack variants of the 16 px stone tile.
        return Math.min(
                stoneSize - 1,
                ((sourceCoordinate % 16) * 2 + 1) * stoneSize / 32);
    }

    private static StonePixels stone(Minecraft minecraft) throws IOException {
        StonePixels cached = stonePixels;
        if (cached != null) {
            return cached;
        }
        try (TextureContents contents = TextureContents.load(
                minecraft.getResourceManager(),
                STONE_TEXTURE)) {
            NativeImage image = contents.image();
            cached = new StonePixels(
                    image.getWidth(),
                    image.getHeight(),
                    image.getPixels());
            stonePixels = cached;
            return cached;
        }
    }

    private static @Nullable NativeImage dynamicPixels(
            TextureManager textures,
            Identifier sourceTexture) {
        AbstractTexture texture = textures.getTexture(sourceTexture);
        if (!(texture instanceof DynamicTexture dynamicTexture)) {
            return null;
        }
        NativeImage pixels = dynamicTexture.getPixels();
        return pixels.isClosed() ? null : pixels;
    }

    private static Identifier generatedId(Identifier sourceTexture) {
        return Identifier.fromNamespaceAndPath(
                EchoesShowThePast.MOD_ID,
                "generated/petrified/"
                        + sourceTexture.toDebugFileName()
                        + "_"
                        + Integer.toUnsignedString(sourceTexture.hashCode(), 16));
    }

    private record StonePixels(int width, int height, int[] pixels) {
    }

    private PetrifiedTextureCache() {
    }
}
