package dev.breezes.settlements.infrastructure.minecraft.behavior.ambient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Walk behaviors that select destinations relative to remembered POIs in the entity's current dimension.
 * POI proximity is measured between block positions in three dimensions.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VillagePoiBoundBehaviors {

    // Preserve vanilla stroll sizing when callers supply only the POI bound.
    private static final int DEFAULT_MAX_HORIZONTAL_DISTANCE = 10;
    private static final int DEFAULT_MAX_VERTICAL_DISTANCE = 7;

    // Preserve vanilla's search reach and directional spread when returning toward a village.
    private static final int VILLAGE_SECTION_SEARCH_RADIUS = 2;
    private static final double TOWARD_VILLAGE_MAX_ANGLE = Math.PI / 2;

    private static final List<MemoryModuleType<GlobalPos>> HELD_POI_MEMORIES = List.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.MEETING_POINT);

    /**
     * Creates {@link #randomStroll(float, int, int, int)} with the default stroll search ranges.
     */
    public static OneShot<PathfinderMob> randomStroll(float speedModifier, int maxPoiDistanceBlocks) {
        return randomStroll(speedModifier, DEFAULT_MAX_HORIZONTAL_DISTANCE, DEFAULT_MAX_VERTICAL_DISTANCE, maxPoiDistanceBlocks);
    }

    /**
     * Creates a behavior that fills an absent walk target using the supplied horizontal and vertical candidate search
     * ranges. Terrain adjustment can raise the selected destination beyond the vertical search range.
     * <p>
     * When starting within maxPoiDistanceBlocks of a remembered POI, it accepts only destinations within that distance
     * of a remembered POI. Outside every bound, it directs the search toward the nearest remembered POI. With no
     * remembered POIs, it uses vanilla village-bound strolling. No target is set when no candidate qualifies.
     */
    public static OneShot<PathfinderMob> randomStroll(float speedModifier,
                                                      int maxHorizontalDistance,
                                                      int maxVerticalDistance,
                                                      int maxPoiDistanceBlocks) {
        double maxPoiDistanceSquared = (double) maxPoiDistanceBlocks * maxPoiDistanceBlocks;
        return BehaviorBuilder.create(instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET))
                .apply(instance, walkTarget -> (level, mob, gameTime) -> {
                    List<BlockPos> heldPois = findRememberedHeldPois(mob);
                    Vec3 destination = heldPois.isEmpty()
                            ? chooseVillageBoundDestination(level, mob, maxHorizontalDistance, maxVerticalDistance)
                            : choosePoiBoundDestination(mob, heldPois, maxHorizontalDistance, maxVerticalDistance, maxPoiDistanceSquared);
                    walkTarget.setOrErase(Optional.ofNullable(destination)
                            .map(position -> new WalkTarget(position, speedModifier, 0)));
                    return true;
                }));
    }

    /**
     * Creates a behavior that fills an absent walk target from the current look target. If the entity remembers any
     * POIs, the look target must initially be within maxPoiDistanceBlocks of one. Leaving the bound does not cancel
     * an existing walk target.
     */
    public static OneShot<LivingEntity> walkToLookTarget(float speedModifier,
                                                         int closeEnoughDistance,
                                                         int maxPoiDistanceBlocks) {
        double maxPoiDistanceSquared = (double) maxPoiDistanceBlocks * maxPoiDistanceBlocks;
        return SetWalkTargetFromLookTarget.create(
                entity -> isLookTargetNearRememberedHeldPoi(entity, maxPoiDistanceSquared),
                entity -> speedModifier,
                closeEnoughDistance);
    }

    @Nullable
    private static Vec3 choosePoiBoundDestination(@Nonnull PathfinderMob mob,
                                                  @Nonnull List<BlockPos> heldPois,
                                                  int maxHorizontalDistance,
                                                  int maxVerticalDistance,
                                                  double maxPoiDistanceSquared) {
        BlockPos position = mob.blockPosition();
        if (isNearAnyPoi(position, heldPois, maxPoiDistanceSquared)) {
            // A finite penalty could still select an out-of-bounds destination when every candidate is outside.
            return LandRandomPos.getPos(mob, maxHorizontalDistance, maxVerticalDistance,
                    candidate -> isNearAnyPoi(candidate, heldPois, maxPoiDistanceSquared)
                            ? mob.getWalkTargetValue(candidate)
                            : Double.NEGATIVE_INFINITY);
        }

        BlockPos nearestPoi = Collections.min(heldPois, Comparator.comparingDouble(poi -> poi.distSqr(position)));
        return DefaultRandomPos.getPosTowards(mob, maxHorizontalDistance, maxVerticalDistance,
                Vec3.atBottomCenterOf(nearestPoi), TOWARD_VILLAGE_MAX_ANGLE);
    }

    @Nullable
    private static Vec3 chooseVillageBoundDestination(@Nonnull ServerLevel level,
                                                      @Nonnull PathfinderMob mob,
                                                      int maxHorizontalDistance,
                                                      int maxVerticalDistance) {
        BlockPos position = mob.blockPosition();
        if (level.isVillage(position)) {
            return LandRandomPos.getPos(mob, maxHorizontalDistance, maxVerticalDistance);
        }

        SectionPos section = SectionPos.of(position);
        SectionPos closerSection = BehaviorUtils.findSectionClosestToVillage(level, section, VILLAGE_SECTION_SEARCH_RADIUS);
        if (!closerSection.equals(section)) {
            return DefaultRandomPos.getPosTowards(mob, maxHorizontalDistance, maxVerticalDistance,
                    Vec3.atBottomCenterOf(closerSection.center()), TOWARD_VILLAGE_MAX_ANGLE);
        }
        return LandRandomPos.getPos(mob, maxHorizontalDistance, maxVerticalDistance);
    }

    private static boolean isLookTargetNearRememberedHeldPoi(@Nonnull LivingEntity entity, double maxPoiDistanceSquared) {
        Optional<PositionTracker> lookTarget = entity.getBrain().getMemory(MemoryModuleType.LOOK_TARGET);
        if (lookTarget.isEmpty()) {
            return false;
        }

        List<BlockPos> heldPois = findRememberedHeldPois(entity);
        return heldPois.isEmpty() || isNearAnyPoi(lookTarget.get().currentBlockPosition(), heldPois, maxPoiDistanceSquared);
    }

    private static List<BlockPos> findRememberedHeldPois(@Nonnull LivingEntity entity) {
        // Use the brain's current positions to avoid world scans on repeated movement attempts.
        Brain<?> brain = entity.getBrain();
        ResourceKey<Level> dimension = entity.level().dimension();
        List<BlockPos> heldPois = new ArrayList<>(HELD_POI_MEMORIES.size());
        for (MemoryModuleType<GlobalPos> memory : HELD_POI_MEMORIES) {
            // Guard unregistered memories as well as empty ones; direct reads of unregistered memories throw.
            if (!brain.hasMemoryValue(memory)) {
                continue;
            }

            GlobalPos poi = brain.getMemory(memory).orElseThrow();
            if (poi.dimension() == dimension) {
                heldPois.add(poi.pos());
            }
        }
        return heldPois;
    }

    private static boolean isNearAnyPoi(@Nonnull BlockPos position, @Nonnull List<BlockPos> pois, double maxDistanceSquared) {
        for (BlockPos poi : pois) {
            if (poi.distSqr(position) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

}
