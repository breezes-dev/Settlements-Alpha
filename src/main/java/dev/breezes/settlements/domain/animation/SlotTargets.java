package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SlotTargets {

    private static final Map<AttachmentSlot, AnimationTarget<Vec3>> TRANSLATIONS = new ConcurrentHashMap<>();
    private static final Map<AttachmentSlot, AnimationTarget<Vector3f>> ROTATIONS = new ConcurrentHashMap<>();

    public static AnimationTarget<Vec3> translation(@Nonnull AttachmentSlot slot) {
        return TRANSLATIONS.computeIfAbsent(slot, ignored ->
                AnimationTarget.translation(slotTargetId(slot, "translation"), AnimationBone.slot(slot)));
    }

    public static AnimationTarget<Vector3f> rotation(@Nonnull AttachmentSlot slot) {
        return ROTATIONS.computeIfAbsent(slot, ignored ->
                AnimationTarget.rotation(slotTargetId(slot, "rotation"), AnimationBone.slot(slot)));
    }

    private static String slotTargetId(@Nonnull AttachmentSlot slot, @Nonnull String property) {
        return "slot:" + slot.getClass().getName() + ":" + slot + "." + property;
    }

}
