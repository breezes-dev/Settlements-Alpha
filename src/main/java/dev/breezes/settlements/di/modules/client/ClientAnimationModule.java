package dev.breezes.settlements.di.modules.client;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.breezes.settlements.di.ClientSessionScope;
import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.animation.AmbientAnimationLibrary;
import dev.breezes.settlements.domain.animation.AmbientAnimations;
import dev.breezes.settlements.domain.animation.AnimationArchetype;
import dev.breezes.settlements.domain.animation.AnimationKey;
import dev.breezes.settlements.domain.animation.AnimationLibrary;
import dev.breezes.settlements.domain.animation.BakedAnimationRegistry;
import dev.breezes.settlements.domain.animation.CartographerAnimations;
import dev.breezes.settlements.domain.animation.ChopAnimations;
import dev.breezes.settlements.domain.animation.DefaultUmbrellaAnimatorFactory;
import dev.breezes.settlements.domain.animation.DigAnimations;
import dev.breezes.settlements.domain.animation.EatingAnimations;
import dev.breezes.settlements.domain.animation.FishingAnimations;
import dev.breezes.settlements.domain.animation.ForgeAnimations;
import dev.breezes.settlements.domain.animation.HarvestCropAnimations;
import dev.breezes.settlements.domain.animation.IdleAnimationLibrary;
import dev.breezes.settlements.domain.animation.IdleAnimations;
import dev.breezes.settlements.domain.animation.InMemoryAmbientAnimationLibrary;
import dev.breezes.settlements.domain.animation.InMemoryAnimationLibrary;
import dev.breezes.settlements.domain.animation.InMemoryIdleAnimationLibrary;
import dev.breezes.settlements.domain.animation.InMemoryLocomotionAnimationLibrary;
import dev.breezes.settlements.domain.animation.InteractAnimations;
import dev.breezes.settlements.domain.animation.LocomotionAnimationLibrary;
import dev.breezes.settlements.domain.animation.LocomotionAnimations;
import dev.breezes.settlements.domain.animation.PickUpAnimations;
import dev.breezes.settlements.domain.animation.ReadBookAnimations;
import dev.breezes.settlements.domain.animation.RepairIronGolemAnimations;
import dev.breezes.settlements.domain.animation.SleepingAnimations;
import dev.breezes.settlements.domain.animation.ThrowEggAnimations;
import dev.breezes.settlements.domain.animation.TillAnimations;
import dev.breezes.settlements.domain.animation.UmbrellaAnimatorFactory;
import dev.breezes.settlements.domain.animation.WaveAnimations;
import dev.breezes.settlements.domain.presentation.ItemCategory;

import java.util.List;
import java.util.Map;

@Module
public abstract class ClientAnimationModule {

    @Binds
    abstract UmbrellaAnimatorFactory umbrellaAnimatorFactory(DefaultUmbrellaAnimatorFactory implementation);

    /**
     * Bakes this session's own library instances.
     * A baked form is found by its clip's instance, so the registry finds only the clips these same instances resolve.
     */
    @Provides
    @ClientSessionScope
    static BakedAnimationRegistry bakedAnimationRegistry(AnimationLibrary animationLibrary,
                                                         IdleAnimationLibrary idleAnimationLibrary,
                                                         AmbientAnimationLibrary ambientAnimationLibrary,
                                                         LocomotionAnimationLibrary locomotionAnimationLibrary) {
        return BakedAnimationRegistry.forVillager(animationLibrary, idleAnimationLibrary, ambientAnimationLibrary,
                locomotionAnimationLibrary);
    }

    @Provides
    @ClientSessionScope
    static AnimationLibrary animationLibrary() {
        return InMemoryAnimationLibrary.builder()
                .animations(Map.ofEntries(
                        Map.entry(AnimationKey.of(AnimationArchetype.SWING_HEAVY, ItemCategory.GENERIC), ChopAnimations.chopCrossedArms()),
                        Map.entry(AnimationKey.of(AnimationArchetype.CAST, ItemCategory.FISHING_ROD), FishingAnimations.cast()),
                        Map.entry(AnimationKey.of(AnimationArchetype.FISHING_WAIT, ItemCategory.FISHING_ROD), FishingAnimations.fishingLoop()),
                        Map.entry(AnimationKey.of(AnimationArchetype.REEL_OUT, ItemCategory.FISHING_ROD), FishingAnimations.fightFish()),
                        Map.entry(AnimationKey.of(AnimationArchetype.REEL_IN, ItemCategory.FISHING_ROD), FishingAnimations.reelIn()),
                        Map.entry(AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), InteractAnimations.interact()),
                        Map.entry(AnimationKey.of(AnimationArchetype.SURVEY_WITH_SPYGLASS, ItemCategory.SPYGLASS), CartographerAnimations.surveyWithSpyglass()),
                        Map.entry(AnimationKey.of(AnimationArchetype.SIGHT_WITH_SPYGLASS, ItemCategory.SPYGLASS), CartographerAnimations.sightWithSpyglass()),
                        Map.entry(AnimationKey.of(AnimationArchetype.WRITE_TO_MAP, ItemCategory.MAP), CartographerAnimations.markMap()),
                        Map.entry(AnimationKey.of(AnimationArchetype.EAT, ItemCategory.GENERIC), EatingAnimations.eat()),
                        Map.entry(AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), WaveAnimations.wave()),
                        Map.entry(AnimationKey.of(AnimationArchetype.PICK_UP, ItemCategory.GENERIC), PickUpAnimations.pickUp()),
                        Map.entry(AnimationKey.of(AnimationArchetype.HARVEST, ItemCategory.GENERIC), HarvestCropAnimations.harvestCrop()),
                        Map.entry(AnimationKey.of(AnimationArchetype.THROW, ItemCategory.GENERIC), ThrowEggAnimations.throwEgg()),
                        Map.entry(AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()),
                        Map.entry(AnimationKey.of(AnimationArchetype.REPAIR_IRON_GOLEM, ItemCategory.GENERIC), RepairIronGolemAnimations.repairIronGolem()),
                        Map.entry(AnimationKey.of(AnimationArchetype.DIG, ItemCategory.SHOVEL), DigAnimations.dig()),
                        Map.entry(AnimationKey.of(AnimationArchetype.TILL, ItemCategory.GENERIC), TillAnimations.till()),
                        Map.entry(AnimationKey.of(AnimationArchetype.FORGE, ItemCategory.GENERIC), ForgeAnimations.forge()),
                        Map.entry(AnimationKey.of(AnimationArchetype.READ_BOOK, ItemCategory.GENERIC), ReadBookAnimations.readBook())))
                .build();
    }

    @Provides
    @ClientSessionScope
    static IdleAnimationLibrary idleAnimationLibrary() {
        return InMemoryIdleAnimationLibrary.builder()
                .idleAnimations(List.of(
                        IdleAnimations.ponder()))
                .build();
    }

    @Provides
    @ClientSessionScope
    static AmbientAnimationLibrary ambientAnimationLibrary() {
        return InMemoryAmbientAnimationLibrary.builder()
                .breathingCycle(AmbientAnimations.breathingCycle())
                .blink(AmbientAnimations.blink())
                .browMotions(List.of(
                        AmbientAnimations.browRaise(),
                        AmbientAnimations.browRaiseLeftEnd(),
                        AmbientAnimations.browRaiseRightEnd()))
                .build();
    }

    @Provides
    @ClientSessionScope
    static LocomotionAnimationLibrary locomotionAnimationLibrary() {
        return InMemoryLocomotionAnimationLibrary.builder()
                .standingPose(LocomotionAnimations.standingPose())
                .animations(Map.of(
                        NavigationType.STROLL, LocomotionAnimations.stroll(),
                        NavigationType.WALK, LocomotionAnimations.walk(),
                        NavigationType.RUN, LocomotionAnimations.jog(),
                        NavigationType.SPRINT, LocomotionAnimations.run(),
                        NavigationType.PANIC, LocomotionAnimations.panicRun()))
                .build();
    }

}
