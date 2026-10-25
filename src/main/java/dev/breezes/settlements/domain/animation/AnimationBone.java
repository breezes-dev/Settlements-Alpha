package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;

/**
 * The identity of the bone an animation channel targets.
 */
public sealed interface AnimationBone {

    static AnimationBone part(@Nonnull String name) {
        return new Part(name);
    }

    static AnimationBone slot(@Nonnull AttachmentSlot slot) {
        return new Slot(slot);
    }

    /**
     * A model part identified by its name in the model hierarchy.
     */
    record Part(String name) implements AnimationBone {

        public Part {
            Validate.notBlank(name, "Model part name must not be blank");
        }

    }

    /**
     * An attachment slot. Its channels place the attached item relative to the slot's anchor rather than moving any
     * part of the model.
     */
    record Slot(AttachmentSlot slot) implements AnimationBone {

        public Slot {
            Validate.notNull(slot, "Attachment slot must not be null");
        }

    }

}
