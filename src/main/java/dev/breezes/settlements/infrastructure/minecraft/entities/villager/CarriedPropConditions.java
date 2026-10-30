package dev.breezes.settlements.infrastructure.minecraft.entities.villager;

import dev.breezes.settlements.domain.common.DebouncedSignal;
import dev.breezes.settlements.domain.entities.OwnedLanternType;
import dev.breezes.settlements.domain.time.CivilTime;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.domain.time.ITickable;
import dev.breezes.settlements.domain.time.Tickable;
import dev.breezes.settlements.domain.time.TimeOfDay;
import dev.breezes.settlements.infrastructure.minecraft.mixins.EntityMixin;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Client-side world conditions requesting a villager's carried props.
 * Requests are false before the first client tick and remain false on the server.
 */
final class CarriedPropConditions {

    private static final ClockTicks RAIN_EXPOSURE_SAMPLE_COOLDOWN_TICKS = ClockTicks.seconds(1);
    private static final int RAIN_EXPOSURE_OBSERVATIONS_TO_CHANGE = 2;
    private static final ClockTicks LANTERN_SAMPLE_COOLDOWN_TICKS = ClockTicks.seconds(3);

    private final ITickable rainExposureCooldown = Tickable.staggered(RAIN_EXPOSURE_SAMPLE_COOLDOWN_TICKS);
    private final ITickable lanternCooldown = Tickable.staggered(LANTERN_SAMPLE_COOLDOWN_TICKS);
    @Nullable
    private DebouncedSignal rainExposure;
    private boolean lanternRequested;

    /**
     * Samples the given villager's world conditions on its first client tick and at staggered intervals thereafter.
     */
    @ClientSide
    void tickClient(@Nonnull BaseVillager villager) {
        boolean firstClientTick = !this.hasSampled();
        if (firstClientTick || this.rainExposureCooldown.tickCheckAndReset(1)) {
            boolean inRain = ((EntityMixin) villager).invokeIsInRain() && !villager.isUnderWater();
            if (this.rainExposure == null) {
                // A newly tracked villager needs an immediate answer instead of waiting out the debounce.
                this.rainExposure = new DebouncedSignal(inRain, RAIN_EXPOSURE_OBSERVATIONS_TO_CHANGE);
            } else {
                this.rainExposure.observe(inRain);
            }
        }

        if (firstClientTick || this.lanternCooldown.tickCheckAndReset(1)) {
            Level level = villager.level();
            this.lanternRequested = villager.getOwnedLanternType() != OwnedLanternType.NONE
                    && level.canSeeSky(villager.blockPosition())
                    && isDarkForLantern(level);
        }
    }

    @ClientSide
    boolean isUmbrellaRequested() {
        return this.rainExposure != null && this.rainExposure.value();
    }

    @ClientSide
    boolean isLanternRequested() {
        return this.lanternRequested;
    }

    /**
     * Whether the lantern conditions have been sampled on the client.
     */
    @ClientSide
    boolean isLanternCheckDecided() {
        return this.hasSampled();
    }

    private boolean hasSampled() {
        // Every check runs on the first client tick, so the rain signal existing marks that tick as past
        return this.rainExposure != null;
    }

    private static boolean isDarkForLantern(@Nonnull Level level) {
        // The client's skyDarken stays stale, so its day-time clock must decide darkness instead of isNight().
        int civilTick = CivilTime.civilFromDayTime(level.getDayTime());
        boolean nightByClock = !level.dimensionType().hasFixedTime()
                && (civilTick >= TimeOfDay.AT_18_30.getCivilTick()
                || civilTick < TimeOfDay.AT_05_30.getCivilTick());
        return nightByClock || level.isRaining();
    }

}
