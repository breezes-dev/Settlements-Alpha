package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.LanternSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composition declarations every production clip must carry, over the clips {@link ProductionAnimationClips}
 * discovers.
 */
class ProductionClipCompositionTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;

    // The rotation-only masks, the only ownership a clip outside the exclusive profile declares
    private static final List<AnimationMask> ROTATION_MASKS = List.of(
            VillagerAnimationRig.HEAD_ROTATION,
            VillagerAnimationRig.LEG_ROTATION);

    @Test
    void everyClipKeyingHeadRotation_ownsHeadRotation_exceptTheGaits() throws Exception {
        // Arrange: a gait's head keys add onto the look, so the gaits alone may key the head without owning it
        Set<ResourceLocation> gaits = gaitIds();
        List<KeyframeAnimation> clips = villagerClips();

        for (KeyframeAnimation clip : clips) {
            if (gaits.contains(clip.getId()) || !keys(clip, VillagerAnimationRig.HEAD_ROTATION)) {
                continue;
            }

            // Act
            AnimationContribution contribution = contributionAtStart(clip);

            // Assert
            assertOwnsFully(clip, contribution, VillagerAnimationRig.HEAD_ROTATION);
        }
    }

    @Test
    void everyClipKeyingLegRotation_ownsLegRotation() throws Exception {
        for (KeyframeAnimation clip : villagerClips()) {
            if (!keys(clip, VillagerAnimationRig.LEG_ROTATION)) {
                continue;
            }

            // Act
            AnimationContribution contribution = contributionAtStart(clip);

            // Assert
            assertOwnsFully(clip, contribution, VillagerAnimationRig.LEG_ROTATION);
        }
    }

    @Test
    void everyClipOutsideTheExclusiveProfile_ownsOnlyRotationMasksItKeys() throws Exception {
        // Arrange: broadening a clip to a region mask would own, and pull to rest, channels it never keys
        for (KeyframeAnimation clip : villagerClips()) {
            AnimationContribution contribution = contributionAtStart(clip);
            if (ownsFully(contribution, RIG.getFullBody())) {
                continue;
            }

            // Act, Assert
            for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
                if (contribution.coverage(channel) > 0.0F) {
                    assertTrue(inARotationMask(channel), clip.getId() + " owns " + RIG.channel(channel).getId()
                            + ", outside the rotation-only masks");
                }
            }
            for (AnimationMask mask : ROTATION_MASKS) {
                if (ownsAny(contribution, mask)) {
                    assertTrue(keys(clip, mask), clip.getId() + " owns a rotation mask it keys no channel of");
                }
            }
        }
    }

    @Test
    void everyClipKeyingTheEyelids_suppressesTheBlink_exceptTheGaitsAndTheBlinkItself() throws Exception {
        // Arrange: an action holding the lids, which an owning blink would close; a gait's raised lids are meant to
        // blink shut
        Set<ResourceLocation> gaits = gaitIds();
        ResourceLocation blink = AmbientAnimations.blink().getId();
        int eyelidClips = 0;

        for (KeyframeAnimation clip : villagerClips()) {
            if (gaits.contains(clip.getId()) || clip.getId().equals(blink)
                    || !keys(clip, VillagerAnimationRig.EYELIDS)) {
                continue;
            }
            eyelidClips++;

            // Act
            AnimationContribution contribution = contributionAtStart(clip);

            // Assert
            assertEquals(1.0F, contribution.suppression(SuppressibleComponent.BLINK), 0.0F, clip.getId().toString());
        }
        assertTrue(eyelidClips > 0, "Discovery found no action keying the eyelids");
    }

    @Test
    void ponder_suppressesGazeAndBrow() {
        // Act
        AnimationContribution contribution = contributionAtStart(IdleAnimations.ponder());

        // Assert: its upward glance and raised brow are authored
        assertEquals(1.0F, contribution.suppression(SuppressibleComponent.GAZE), 0.0F);
        assertEquals(1.0F, contribution.suppression(SuppressibleComponent.BROW), 0.0F);
    }

    @Test
    void everyGait_keysTheLanternRotation_andTheStandingPoseDoesNot() throws Exception {
        // Arrange: a gait without a swing freezes the lantern while walking, and a standing pose that keys it
        // leaves the lantern swinging at a standstill
        Set<ResourceLocation> gaits = gaitIds();
        AnimationTarget<?> lanternRotation = SlotTargets.rotation(LanternSlot.LANTERN);
        int gaitsChecked = 0;

        for (KeyframeAnimation clip : villagerClips()) {
            if (!gaits.contains(clip.getId())) {
                continue;
            }
            gaitsChecked++;

            // Act, Assert
            assertTrue(keysTarget(clip, lanternRotation), clip.getId() + " must swing the lantern");
        }
        assertEquals(gaits.size(), gaitsChecked, "Discovery found fewer villager clips than gaits");
        assertFalse(keysTarget(LocomotionAnimations.standingPose(), lanternRotation),
                "The standing pose must leave the lantern unkeyed so the swing fades with the stride");
    }

    private static boolean keysTarget(KeyframeAnimation clip, AnimationTarget<?> target) {
        for (AnimationTrack<?> track : clip.getTracks()) {
            if (track.getTarget().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static List<KeyframeAnimation> villagerClips() throws Exception {
        List<KeyframeAnimation> clips = ProductionAnimationClips.all().stream()
                .filter(clip -> ProductionAnimationClips.rigFor(clip) == RIG)
                .toList();
        assertFalse(clips.isEmpty(), "Discovery found no villager clips");
        return clips;
    }

    /**
     * The ids of the gaits LocomotionAnimations declares, discovered so a new gait joins the exception without an edit
     * here.
     */
    private static Set<ResourceLocation> gaitIds() throws Exception {
        Set<ResourceLocation> ids = new HashSet<>();
        for (Method method : LocomotionAnimations.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && method.getParameterCount() == 0
                    && method.getReturnType() == KeyframeAnimation.class) {
                ids.add(((KeyframeAnimation) method.invoke(null)).getId());
            }
        }
        // The standing pose is the Locomotion slot's other alternative, not a gait, so it earns no exception
        ids.remove(LocomotionAnimations.standingPose().getId());
        assertFalse(ids.isEmpty(), "Discovery found no gaits");
        return ids;
    }

    private static boolean keys(KeyframeAnimation clip, AnimationMask mask) {
        for (AnimationTrack<?> track : clip.getTracks()) {
            AnimationTarget<?> target = track.getTarget();
            if (RIG.contains(target) && mask.contains(RIG.indexOf(target))) {
                return true;
            }
        }
        return false;
    }

    private static boolean inARotationMask(int channel) {
        for (AnimationMask mask : ROTATION_MASKS) {
            if (mask.contains(channel)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsAny(AnimationContribution contribution, AnimationMask mask) {
        for (int member = 0; member < mask.size(); member++) {
            if (contribution.coverage(mask.channelAt(member)) > 0.0F) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsFully(AnimationContribution contribution, AnimationMask mask) {
        for (int member = 0; member < mask.size(); member++) {
            if (contribution.coverage(mask.channelAt(member)) < 1.0F) {
                return false;
            }
        }
        return true;
    }

    private static AnimationContribution contributionAtStart(KeyframeAnimation clip) {
        AnimationContribution contribution = new AnimationContribution(RIG);
        BakedAnimation.bake(clip, RIG).contribute(contribution, 0.0F);
        return contribution;
    }

    private static void assertOwnsFully(KeyframeAnimation clip,
                                        AnimationContribution contribution,
                                        AnimationMask mask) {
        for (int member = 0; member < mask.size(); member++) {
            int channel = mask.channelAt(member);
            assertEquals(1.0F, contribution.coverage(channel), 0.0F,
                    clip.getId() + " must own " + RIG.channel(channel).getId());
        }
    }

}
