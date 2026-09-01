package dev.alvar.echoespast.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.DefaultedGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;
import dev.alvar.echoespast.EchoesShowThePast;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.common.Tags;

/**
 * Unknown medieval panoply, equipped on the boss only. Geometry still uses
 * the GeckoLib humanoid armor rig if a piece is present on an entity.
 */
public final class UnknownMedievalArmorItem extends Item implements GeoItem {
    public static final ResourceKey<EquipmentAsset> EQUIPMENT =
            ResourceKey.create(
                    EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_medieval"));

    public static final ArmorMaterial MATERIAL = new ArmorMaterial(
            15,
            defense(),
            9,
            SoundEvents.ARMOR_EQUIP_IRON,
            0.0F,
            0.0F,
            Tags.Items.INGOTS_IRON,
            EQUIPMENT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public UnknownMedievalArmorItem(Properties properties) {
        super(properties);
    }

    public static boolean hidesJacketAndSleeves(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof UnknownMedievalArmorItem;
    }

    public static boolean hidesPants(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof UnknownMedievalArmorItem
                || entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof UnknownMedievalArmorItem;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?, ?> renderer;

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(
                    ItemStack itemStack, EquipmentSlot equipmentSlot) {
                if (this.renderer == null) {
                    this.renderer = new Renderer(UnknownMedievalArmorItem.this);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private static EnumMap<ArmorType, Integer> defense() {
        EnumMap<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.HELMET, 2);
        map.put(ArmorType.CHESTPLATE, 6);
        map.put(ArmorType.LEGGINGS, 5);
        map.put(ArmorType.BOOTS, 2);
        map.put(ArmorType.BODY, 5);
        return map;
    }

    private static final class Renderer extends GeoArmorRenderer<UnknownMedievalArmorItem, HumanoidRenderState> {
        private Renderer(UnknownMedievalArmorItem item) {
            super(new DefaultedGeoModel<>(BuiltInRegistries.ITEM.getKey(item)) {
                @Override
                protected String subtype() {
                    return "armor";
                }
            });
        }

        @Override
        public List<ArmorSegment> getSegmentsForSlot(
                HumanoidRenderState renderState, EquipmentSlot slot) {
            if (slot == EquipmentSlot.LEGS) {
                return List.of(ArmorSegment.CHEST, ArmorSegment.LEFT_LEG, ArmorSegment.RIGHT_LEG);
            }
            return super.getSegmentsForSlot(renderState, slot);
        }
    }
}
