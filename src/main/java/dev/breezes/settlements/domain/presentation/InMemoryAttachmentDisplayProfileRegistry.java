package dev.breezes.settlements.domain.presentation;

import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import dev.breezes.settlements.domain.attachment.LanternSlot;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import lombok.Builder;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class InMemoryAttachmentDisplayProfileRegistry implements AttachmentDisplayProfileRegistry {

    private static final AttachmentDisplayProfile IDENTITY = AttachmentDisplayProfile.generic();
    private static final float LANTERN_SCALE = 0.7F;

    private final Map<AttachmentSlot, Map<ItemCategory, AttachmentDisplayProfile>> profilesBySlot;

    @Builder
    private InMemoryAttachmentDisplayProfileRegistry(@Nonnull Map<AttachmentDisplayProfileKey, AttachmentDisplayProfile> profilesByKey) {
        Map<AttachmentSlot, Map<ItemCategory, AttachmentDisplayProfile>> profiles = new HashMap<>();
        profilesByKey.forEach((key, profile) -> profiles
                .computeIfAbsent(key.slot(), slot -> new EnumMap<>(ItemCategory.class))
                .put(key.category(), profile));
        profiles.replaceAll((slot, categories) -> Map.copyOf(categories));
        this.profilesBySlot = Map.copyOf(profiles);
    }

    public static InMemoryAttachmentDisplayProfileRegistry defaults() {
        Map<AttachmentDisplayProfileKey, AttachmentDisplayProfile> profiles = new HashMap<>();

        AttachmentDisplayProfile hangingLantern = AttachmentDisplayProfile.builder()
                .translation(Vec3.ZERO)
                .rotation(new Vector3f())
                .scale(LANTERN_SCALE)
                .hanging(true)
                .build();
        profiles.put(AttachmentDisplayProfileKey.of(LanternSlot.LANTERN, ItemCategory.GENERIC), hangingLantern);
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.LANTERN), hangingLantern);

        // Hand-held tools and weapons are rendered with the item's thirdperson_righthand JSON display
        // transform so they look gripped rather than floating flat. Food, carried items, and
        // categories without an explicit profile fall back to the anchor's GROUND default.
        AttachmentDisplayProfile handTool = AttachmentDisplayProfile.builder()
                .translation(Vec3.ZERO)
                .rotation(new Vector3f())
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build();
        for (ItemCategory category : new ItemCategory[]{ItemCategory.SWORD, ItemCategory.MACE}) {
            profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, category), handTool);
        }

        // Declare custom model display profile overrides
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.AXE), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.15, -0.1))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-15), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.PICKAXE), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.15, -0.1))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-15), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.HAMMER), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.05, -0.15))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-50), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.SHOVEL), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.05, -0.15))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-50), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());
        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.HOE), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.05, -0.15))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-50), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());

        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.FISHING_ROD), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.0, -0.25, -0.0))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(-50), 0.0F, 0.0F)))
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());

        profiles.put(AttachmentDisplayProfileKey.of(EquipmentSlot.MAIN_HAND, ItemCategory.SPYGLASS), AttachmentDisplayProfile.builder()
                .translation(new Vec3(0.1, -0.15, -0.1))
                .rotation(new Vector3f(new Vector3f((float) Math.toRadians(30), 0.0F, 0.0F)))
                .scale(1.2F)
                .displayContextOverride(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .build());

        return InMemoryAttachmentDisplayProfileRegistry.builder()
                .profilesByKey(profiles)
                .build();
    }

    @Override
    public AttachmentDisplayProfile get(@Nonnull AttachmentSlot slot, @Nullable ItemCategory category) {
        Map<ItemCategory, AttachmentDisplayProfile> categories = this.profilesBySlot.get(slot);
        if (categories == null) {
            return IDENTITY;
        }
        if (category != null) {
            AttachmentDisplayProfile exact = categories.get(category);
            if (exact != null) {
                return exact;
            }
        }

        AttachmentDisplayProfile slotDefault = categories.get(ItemCategory.GENERIC);
        return slotDefault == null ? IDENTITY : slotDefault;
    }

}
