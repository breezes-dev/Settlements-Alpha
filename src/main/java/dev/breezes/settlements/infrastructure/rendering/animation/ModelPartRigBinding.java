package dev.breezes.settlements.infrastructure.rendering.animation;

import dev.breezes.settlements.domain.animation.AnimationBone;
import dev.breezes.settlements.domain.animation.AnimationChannelKind;
import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.AnimationRig;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.client.model.geom.ModelPart;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Binds the model-part channels of one rig to the parts of a baked model. Applying a pose adds its rotation and
 * translation onto each bound part as it stands and multiplies the part's scale, so after a reset the pose lands
 * relative to rest. Neither resetting nor applying allocates.
 */
@ClientSide
public final class ModelPartRigBinding {

    private final AnimationRig rig;
    private final ModelPart[] allParts;
    private final int[] boundChannels;
    private final ModelPart[] boundParts;
    private final AnimationChannelKind[] boundKinds;

    /**
     * Resolves every model-part channel of the rig to its part. Attachment slot channels bind to no part.
     *
     * @param root       the model's root, whose whole hierarchy a reset returns to rest
     * @param partByName finds a part by the name its channels' bone gives it
     * @throws IllegalArgumentException if a model-part channel names a part the lookup does not find
     */
    public ModelPartRigBinding(@Nonnull AnimationRig rig,
                               @Nonnull ModelPart root,
                               @Nonnull Function<String, Optional<ModelPart>> partByName) {
        List<Integer> channels = new ArrayList<>();
        List<ModelPart> parts = new ArrayList<>();
        for (int channel = 0; channel < rig.getChannelCount(); channel++) {
            if (!(rig.channel(channel).getBone() instanceof AnimationBone.Part(String name))) {
                continue;
            }
            int boundChannel = channel;
            parts.add(partByName.apply(name).orElseThrow(() -> new IllegalArgumentException(
                    "Rig %s binds channel %s to part %s, which the model lacks".formatted(
                            rig.getName(), rig.channel(boundChannel).getId(), name))));
            channels.add(channel);
        }

        this.rig = rig;
        this.allParts = root.getAllParts().toArray(ModelPart[]::new);
        this.boundChannels = channels.stream().mapToInt(Integer::intValue).toArray();
        this.boundParts = parts.toArray(ModelPart[]::new);
        this.boundKinds = new AnimationChannelKind[this.boundChannels.length];
        for (int binding = 0; binding < this.boundChannels.length; binding++) {
            this.boundKinds[binding] = rig.kind(this.boundChannels[binding]);
        }
    }

    /**
     * Returns every part of the model to its rest pose. Visibility is left as it is.
     */
    public void resetToRest() {
        for (ModelPart part : this.allParts) {
            part.resetPose();
        }
    }

    /**
     * Applies the pose onto the bound parts.
     *
     * @throws IllegalArgumentException if the pose is on another rig
     */
    public void apply(@Nonnull AnimationPose pose) {
        // Keep the successful rig check free of varargs allocation
        if (pose.getRig() != this.rig) {
            throw new IllegalArgumentException("A pose on rig " + pose.getRig().getName()
                    + " does not apply to a binding of rig " + this.rig.getName());
        }
        for (int binding = 0; binding < this.boundChannels.length; binding++) {
            int channel = this.boundChannels[binding];
            ModelPart part = this.boundParts[binding];
            float x = pose.get(channel, 0);
            float y = pose.get(channel, 1);
            float z = pose.get(channel, 2);
            switch (this.boundKinds[binding]) {
                case ROTATION -> {
                    part.xRot += x;
                    part.yRot += y;
                    part.zRot += z;
                }
                case TRANSLATION -> {
                    part.x += x;
                    part.y += y;
                    part.z += z;
                }
                case SCALE -> {
                    part.xScale *= x;
                    part.yScale *= y;
                    part.zScale *= z;
                }
            }
        }
    }

}
