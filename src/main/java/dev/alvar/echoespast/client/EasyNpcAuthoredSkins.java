package dev.alvar.echoespast.client;

import com.mojang.logging.LogUtils;
import de.markusbordihn.easynpc.client.texture.CustomTextureManager;
import de.markusbordihn.easynpc.data.skin.SkinModel;
import de.markusbordihn.easynpc.io.CustomSkinDataFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

/**
 * EasyNPC 7.4.2 only loads CUSTOM skins from {@code config/easy_npc/skin/<model>}.
 * Authored site statues ship their PNGs in this mod and are copied there so
 * worldgen NPCs resolve {@code athenea.png} / {@code cariatide.png} by UUID.
 */
final class EasyNpcAuthoredSkins {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier ATHENA = Identifier.fromNamespaceAndPath(
            "echoes_show_the_past",
            "textures/entity/easy_npc/humanoid/athenea.png");
    private static final Identifier CARYATID = Identifier.fromNamespaceAndPath(
            "echoes_show_the_past",
            "textures/entity/easy_npc/humanoid_slim/cariatide.png");

    private EasyNpcAuthoredSkins() {
    }

    static void install(ResourceManager resources) {
        install(resources, SkinModel.HUMANOID, ATHENA, "athenea.png");
        install(resources, SkinModel.HUMANOID_SLIM, CARYATID, "cariatide.png");
    }

    private static void install(
            ResourceManager resources,
            SkinModel model,
            Identifier resource,
            String fileName) {
        Path folder = CustomSkinDataFiles.getCustomSkinDataFolder(model);
        if (folder == null) {
            LOGGER.warn("EasyNPC skin folder for {} is unavailable", model);
            return;
        }
        Resource packed = resources.getResource(resource).orElse(null);
        if (packed == null) {
            LOGGER.warn("Missing authored EasyNPC skin {}", resource);
            return;
        }
        Path target = folder.resolve(fileName);
        try (InputStream input = packed.open()) {
            Files.createDirectories(folder);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("Could not install EasyNPC skin {} at {}", fileName, target, exception);
            return;
        }
        CustomTextureManager.clearTextureCache(model);
        CustomSkinDataFiles.refreshRegisterTextureFiles(model);
    }
}
