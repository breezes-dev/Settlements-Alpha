package dev.breezes.settlements.infrastructure.minecraft.attachments;

import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import dev.breezes.settlements.domain.inventory.IVillagerEquipment;
import dev.breezes.settlements.domain.inventory.VillagerInventory;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class EquipmentLookup {

    public static Optional<IVillagerEquipment> find(@Nonnull BaseVillager villager) {
        return Optional.of(new VanillaHandBridgedEquipment(villager, settlementsInventoryOrNull(villager)));
    }

    /**
     * The stack equipped in the slot, or an empty stack when the slot holds nothing.
     * Allocates nothing while the villager holds no Settlements inventory.
     */
    public static ItemStack findEquippedOrEmpty(@Nonnull BaseVillager villager, @Nonnull EquipmentSlot slot) {
        return equippedOrEmpty(villager, settlementsInventoryOrNull(villager), slot);
    }

    @Nullable
    private static VillagerInventory settlementsInventoryOrNull(@Nonnull BaseVillager villager) {
        // Client entities are constructed from spawn packets and never go through load() or finalizeSpawn(),
        // so the client holds no Settlements inventory, and lookups fall through to the vanilla-synced hand slots,
        // which are always available on both sides.
        return villager.hasSettlementsInventory() ? villager.getSettlementsInventory() : null;
    }

    private static ItemStack equippedOrEmpty(@Nonnull BaseVillager villager,
                                             @Nullable VillagerInventory inventory,
                                             @Nonnull EquipmentSlot slot) {
        if (inventory != null) {
            Optional<ItemStack> equipped = inventory.getEquipped(slot);
            if (equipped.isPresent()) {
                return equipped.get();
            }
        }
        // Server-side inventory writes don't reach the client entity's in-memory equipped map;
        // vanilla hand slots are always synced and serve as the authoritative fallback.
        return switch (slot) {
            case MAIN_HAND -> villager.getItemInHand(InteractionHand.MAIN_HAND);
            case OFF_HAND -> villager.getItemInHand(InteractionHand.OFF_HAND);
        };
    }

    private static final class VanillaHandBridgedEquipment implements IVillagerEquipment {

        private final BaseVillager villager;
        @Nullable
        private final VillagerInventory inventory;

        VanillaHandBridgedEquipment(@Nonnull BaseVillager villager, @Nullable VillagerInventory inventory) {
            this.villager = villager;
            this.inventory = inventory;
        }

        @Override
        public Optional<ItemStack> getEquipped(@Nonnull EquipmentSlot slot) {
            ItemStack equipped = equippedOrEmpty(this.villager, this.inventory, slot);
            return equipped.isEmpty() ? Optional.empty() : Optional.of(equipped);
        }

        @Override
        public Set<EquipmentSlot> occupiedSlots() {
            EnumSet<EquipmentSlot> occupied = EnumSet.noneOf(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (this.getEquipped(slot).isPresent()) {
                    occupied.add(slot);
                }
            }
            return Collections.unmodifiableSet(occupied);
        }
    }

}
