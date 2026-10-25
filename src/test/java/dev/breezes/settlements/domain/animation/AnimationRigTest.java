package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.attachment.AttachmentSlot;
import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationRigTest {

    private static final AnimationRig VILLAGER = VillagerAnimationRig.RIG;

    @Test
    void of_rejectsAChannel_whenListedTwice() {
        // Arrange
        List<AnimationTarget<?>> channels = List.of(
                AnimationTargets.BODY_ROTATION,
                AnimationTargets.HEAD_TRANSLATION,
                AnimationTargets.BODY_ROTATION);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> AnimationRig.of("duplicate", channels));
    }

    @Test
    void resolve_carriesABaseRigMask_ontoARigThatAppendsChannels() {
        // Arrange
        AnimationRig base = baseRig();
        AnimationRig extended = extend(base, AnimationTargets.MOUTH_SCALE);

        // Act
        AnimationMask resolved = extended.resolve(base.getFullBody());

        // Assert
        assertSame(extended, resolved.getRig());
        for (int index = 0; index < extended.getChannelCount(); index++) {
            AnimationTarget<?> channel = extended.channel(index);
            assertEquals(base.contains(channel), resolved.contains(index), channel.getId());
        }
    }

    @Test
    void resolve_matchesChannelsByIdentity_whenTheRigsOrderThemDifferently() {
        // Arrange: copying the source index would select the mouth scale instead of body rotation
        AnimationRig source = baseRig();
        AnimationRig target = AnimationRig.of("reordered", List.of(
                AnimationTargets.HEAD_ROTATION,
                AnimationTargets.MOUTH_SCALE,
                AnimationTargets.BODY_ROTATION,
                AnimationTargets.ROOT_ROTATION));
        AnimationMask mask = source.maskOf(List.of(AnimationTargets.BODY_ROTATION));

        // Act
        AnimationMask resolved = target.resolve(mask);

        // Assert
        assertEquals(1, resolved.size());
        assertTrue(resolved.contains(target.indexOf(AnimationTargets.BODY_ROTATION)));
    }

    @Test
    void resolve_rejectsAMask_holdingAChannelTheRigLacks() {
        // Arrange
        AnimationRig base = baseRig();
        AnimationRig extended = extend(base, AnimationTargets.MOUTH_SCALE);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> base.resolve(extended.getFullBody()));
    }

    @Test
    void resolve_returnsTheMaskItself_whenItIsAlreadyOnTheRig() {
        // Act, Assert
        assertSame(VillagerAnimationRig.HEAD, VILLAGER.resolve(VillagerAnimationRig.HEAD));
    }

    @Test
    void maskOf_holdsExactlyTheNamedChannels() {
        // Act
        AnimationMask mask = VILLAGER.maskOf(List.of(AnimationTargets.NOSE_ROTATION, AnimationTargets.MOUTH_SCALE));

        // Assert
        assertEquals(2, mask.size());
        for (int index = 0; index < VILLAGER.getChannelCount(); index++) {
            AnimationTarget<?> channel = VILLAGER.channel(index);
            boolean named = channel.equals(AnimationTargets.NOSE_ROTATION) || channel.equals(AnimationTargets.MOUTH_SCALE);
            assertEquals(named, mask.contains(index), channel.getId());
        }
    }

    @Test
    void maskOf_rejectsATarget_whenItIsNotAChannelOfTheRig() {
        // Act, Assert
        assertThrows(IllegalArgumentException.class,
                () -> VILLAGER.maskOf(List.of(UmbrellaAnimationTargets.NORTH_ROTATION)));
    }

    @Test
    void villagerRig_holdsATranslationAndRotationChannel_forEveryAttachmentSlot() {
        // Arrange
        List<AttachmentSlot> slots = Stream.<AttachmentSlot>concat(
                Stream.of(UmbrellaSlot.values()),
                Stream.of(EquipmentSlot.values())).toList();

        // Act, Assert
        for (AttachmentSlot slot : slots) {
            assertTrue(VILLAGER.isSlotChannel(VILLAGER.indexOf(SlotTargets.translation(slot))), slot.toString());
            assertTrue(VILLAGER.isSlotChannel(VILLAGER.indexOf(SlotTargets.rotation(slot))), slot.toString());
        }
    }

    @Test
    void fullBody_holdsExactlyTheModelPartChannels() {
        // Act, Assert
        assertHoldsExactly(VillagerAnimationRig.FULL_BODY, channel -> channel.getBone() instanceof AnimationBone.Part);
    }

    @Test
    void regionMasks_placeEveryModelPartChannelInExactlyOneRegion() {
        // Arrange
        List<AnimationMask> regions = List.of(
                VillagerAnimationRig.HEAD,
                VillagerAnimationRig.TORSO,
                VillagerAnimationRig.LEGS,
                VillagerAnimationRig.ARMS,
                VillagerAnimationRig.ROOT,
                VillagerAnimationRig.FACE);

        // Act, Assert: gaps prevent region ownership, while overlaps let unrelated regions control the same channel
        for (int index = 0; index < VILLAGER.getChannelCount(); index++) {
            int containingRegions = 0;
            for (AnimationMask region : regions) {
                containingRegions += region.contains(index) ? 1 : 0;
            }
            boolean modelPart = VILLAGER.channel(index).getBone() instanceof AnimationBone.Part;
            assertEquals(modelPart ? 1 : 0, containingRegions, VILLAGER.channel(index).getId());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("regionMasks")
    void regionMask_holdsExactlyEveryChannelOfItsBones(String maskName, AnimationMask mask, Set<String> bones) {
        // Act, Assert
        assertHoldsExactly(mask, channel -> isPartOf(channel, bones));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotationOnlyMasks")
    void rotationOnlyMask_holdsExactlyTheRotationChannelsOfItsBones(String maskName,
                                                                    AnimationMask mask,
                                                                    Set<String> bones) {
        // Act, Assert: rotation-only ownership must leave translation and scale available to other contributions
        assertHoldsExactly(mask, channel -> isPartOf(channel, bones)
                && channel.getKind() == AnimationChannelKind.ROTATION);
    }

    static Stream<Arguments> regionMasks() {
        // Literal bone names keep a faulty mask declaration from supplying its own expected membership
        return Stream.of(
                Arguments.of("HEAD", VillagerAnimationRig.HEAD, Set.of("head")),
                Arguments.of("TORSO", VillagerAnimationRig.TORSO, Set.of("torso")),
                Arguments.of("LEGS", VillagerAnimationRig.LEGS, Set.of("leg_left", "leg_right")),
                Arguments.of("LEFT_ARM", VillagerAnimationRig.LEFT_ARM, Set.of("arm_crossed_left", "arm_straight_left")),
                Arguments.of("RIGHT_ARM", VillagerAnimationRig.RIGHT_ARM,
                        Set.of("arm_crossed_right", "arm_straight_right")),
                Arguments.of("ARMS", VillagerAnimationRig.ARMS, Set.of(
                        "arms_crossed", "arms_straight",
                        "arm_crossed_left", "arm_straight_left",
                        "arm_crossed_right", "arm_straight_right")),
                Arguments.of("ROOT", VillagerAnimationRig.ROOT, Set.of("root")),
                Arguments.of("EYELIDS", VillagerAnimationRig.EYELIDS, Set.of("eyelid_left", "eyelid_right")),
                Arguments.of("EYES", VillagerAnimationRig.EYES,
                        Set.of("eyeball_left", "eyeball_right", "pupil_left", "pupil_right")),
                Arguments.of("BROW", VillagerAnimationRig.BROW, Set.of("monobrow")),
                Arguments.of("NOSE", VillagerAnimationRig.NOSE, Set.of("nose")),
                Arguments.of("MOUTH", VillagerAnimationRig.MOUTH, Set.of("mouth")),
                Arguments.of("FACE", VillagerAnimationRig.FACE, Set.of(
                        "eyelid_left", "eyelid_right",
                        "eyeball_left", "eyeball_right", "pupil_left", "pupil_right",
                        "monobrow", "nose", "mouth")));
    }

    static Stream<Arguments> rotationOnlyMasks() {
        return Stream.of(
                Arguments.of("HEAD_ROTATION", VillagerAnimationRig.HEAD_ROTATION, Set.of("head")),
                Arguments.of("LEG_ROTATION", VillagerAnimationRig.LEG_ROTATION, Set.of("leg_left", "leg_right")));
    }

    private static AnimationRig baseRig() {
        return AnimationRig.of("base", List.of(
                AnimationTargets.ROOT_ROTATION,
                AnimationTargets.BODY_ROTATION,
                AnimationTargets.HEAD_ROTATION));
    }

    private static AnimationRig extend(AnimationRig base, AnimationTarget<?> appended) {
        List<AnimationTarget<?>> channels = new ArrayList<>();
        for (int index = 0; index < base.getChannelCount(); index++) {
            channels.add(base.channel(index));
        }
        channels.add(appended);
        return AnimationRig.of("extended", channels);
    }

    private static void assertHoldsExactly(AnimationMask mask, Predicate<AnimationTarget<?>> expectedMember) {
        for (int index = 0; index < VILLAGER.getChannelCount(); index++) {
            AnimationTarget<?> channel = VILLAGER.channel(index);
            assertEquals(expectedMember.test(channel), mask.contains(index), channel.getId());
        }
    }

    private static boolean isPartOf(AnimationTarget<?> channel, Set<String> partNames) {
        return channel.getBone() instanceof AnimationBone.Part part && partNames.contains(part.name());
    }

}
