package dev.breezes.settlements.infrastructure.minecraft.entities.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.breezes.settlements.application.ai.behavior.runtime.VillagerStateMachineBehavior;
import dev.breezes.settlements.application.ai.behavior.teardown.ITeardownLedger;
import dev.breezes.settlements.application.ai.behavior.teardown.LedgerEntry;
import dev.breezes.settlements.application.ai.behavior.teardown.ProvidesTeardownLedger;
import dev.breezes.settlements.application.ai.behavior.teardown.TeardownObligation;
import dev.breezes.settlements.application.ai.brain.IVillagerBrain;
import dev.breezes.settlements.application.ai.brain.VanillaAmbientBehaviorPackages;
import dev.breezes.settlements.application.ai.brain.VanillaBehaviorPackages;
import dev.breezes.settlements.application.ai.brain.VillagerBrain;
import dev.breezes.settlements.application.ai.dialogue.Occasion;
import dev.breezes.settlements.application.ai.override.OverrideRunner;
import dev.breezes.settlements.application.ai.override.OverrideRuntimeState;
import dev.breezes.settlements.application.ai.planning.PlanRuntimeState;
import dev.breezes.settlements.application.ai.socialcue.SocialCueRuntimeState;
import dev.breezes.settlements.application.hunger.HungerConfig;
import dev.breezes.settlements.application.ui.bubble.BubbleChannel;
import dev.breezes.settlements.application.ui.bubble.BubbleCommand;
import dev.breezes.settlements.application.ui.bubble.BubbleMessage;
import dev.breezes.settlements.application.ui.bubble.VillagerBubbleService;
import dev.breezes.settlements.application.ui.bubble.VillagerBubbleState;
import dev.breezes.settlements.bootstrap.registry.activities.ActivityRegistry;
import dev.breezes.settlements.bootstrap.registry.attachments.AttachmentRegistry;
import dev.breezes.settlements.bootstrap.registry.entities.EntityRegistry;
import dev.breezes.settlements.bootstrap.registry.schedules.ScheduleRegistry;
import dev.breezes.settlements.bootstrap.registry.sensors.SensorTypeRegistry;
import dev.breezes.settlements.di.ServerComponent;
import dev.breezes.settlements.di.SettlementsDagger;
import dev.breezes.settlements.domain.ai.behavior.contracts.IBehavior;
import dev.breezes.settlements.domain.ai.behavior.model.BehaviorStatus;
import dev.breezes.settlements.domain.ai.catalog.BehaviorChannel;
import dev.breezes.settlements.domain.ai.catalog.BehaviorPlanningMetadata;
import dev.breezes.settlements.domain.ai.eventlane.EventLaneConfig;
import dev.breezes.settlements.domain.ai.knowledge.VillagerKnowledgeStore;
import dev.breezes.settlements.domain.ai.memory.MemoryTypeRegistry;
import dev.breezes.settlements.domain.ai.naming.VillagerNameDirectory;
import dev.breezes.settlements.domain.ai.naming.VillagerNameGenerator;
import dev.breezes.settlements.domain.ai.navigation.INavigationManager;
import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.ai.planning.DayPlan;
import dev.breezes.settlements.domain.animation.AnimationArchetype;
import dev.breezes.settlements.domain.animation.VillagerAnimator;
import dev.breezes.settlements.domain.economy.catalog.ItemMatch;
import dev.breezes.settlements.domain.entities.Expertise;
import dev.breezes.settlements.domain.entities.ISettlementsVillager;
import dev.breezes.settlements.domain.entities.OwnedLanternType;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import dev.breezes.settlements.domain.entities.hunger.IVillagerHunger;
import dev.breezes.settlements.domain.genetics.GeneticsProfile;
import dev.breezes.settlements.domain.inventory.EquipmentSlot;
import dev.breezes.settlements.domain.inventory.VillagerInventory;
import dev.breezes.settlements.domain.personality.OriginType;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.domain.time.ITickable;
import dev.breezes.settlements.domain.time.Tickable;
import dev.breezes.settlements.domain.world.location.Location;
import dev.breezes.settlements.infrastructure.config.annotations.GeneralConfig;
import dev.breezes.settlements.infrastructure.config.factory.ConfigFactory;
import dev.breezes.settlements.infrastructure.minecraft.ai.dialogue.ActivityOccasionMapper;
import dev.breezes.settlements.infrastructure.minecraft.attachments.VillagerHungerAttachment;
import dev.breezes.settlements.infrastructure.minecraft.attachments.VillagerOriginAttachment;
import dev.breezes.settlements.infrastructure.minecraft.attachments.VillagerPersonaLineageAttachment;
import dev.breezes.settlements.infrastructure.minecraft.behavior.planning.PlanRunnerBehavior;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.genetics.VillagerGeneticAttributes;
import dev.breezes.settlements.infrastructure.minecraft.mixins.VillagerMixin;
import dev.breezes.settlements.infrastructure.minecraft.navigation.SettlementsGroundPathNavigation;
import dev.breezes.settlements.infrastructure.minecraft.navigation.VanillaMemoryNavigationManager;
import dev.breezes.settlements.infrastructure.rendering.animation.VillagerAnimatorFactory;
import dev.breezes.settlements.infrastructure.rendering.bubbles.BubbleManager;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import dev.breezes.settlements.shared.annotations.functional.ServerSide;
import dev.breezes.settlements.shared.logging.LogCategory;
import dev.breezes.settlements.shared.util.SyncedDataWrapper;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// TODO: make this class abstract
@CustomLog
@Getter
public class BaseVillager extends Villager implements ISettlementsVillager, IVillagerHunger, ProvidesTeardownLedger {

    // Height of a full-size adult villager's crossed arms above its feet, in blocks
    private static final float HAND_HEIGHT = 1.19F;
    private static final double DEFAULT_ATTACK_DAMAGE = 2.5D;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.5D;
    private static final double DEFAULT_FOLLOW_RANGE = 48.0D;
    private static final int STARTING_BREAD_STACKS = 2;
    private static final int STARTING_BREAD_PER_STACK = 64;
    private static final int MAX_DEATH_DROP_STACKS = 10;
    private static final int MAX_DISCHARGE_ATTEMPTS = 10;
    private static final float BREED_HUNGER_THRESHOLD = 0.7F;
    private static final int BREED_FOOD_REQUIREMENT = 32;
    // Chance a bred child is born a nitwit, echoing the profession variety of vanilla natural-spawn villages
    private static final float NITWIT_BREED_CHANCE = 0.03F;
    private static final ItemMatch FOODS_MATCH = new ItemMatch.TagRef(Tags.Items.FOODS);

    private static final ClockTicks RECONCILER_COOLDOWN_TICKS = ClockTicks.seconds(5);
    // Keep perception updates within the event retention window so batching does not lose unseen events
    private static final ClockTicks PERCEPTION_COOLDOWN_TICKS = ClockTicks.seconds(1);
    // Occasional name sync for catching direct name data modifications
    private static final ClockTicks NAME_SYNC_COOLDOWN_TICKS = ClockTicks.minutes(1);
    // How often a villager looks for new settlements or re-checks the settlement it belongs to
    private static final ClockTicks MEMBERSHIP_CHECK_COOLDOWN_TICKS = ClockTicks.minutes(2);
    // Check frequently enough to catch a member at its bell between strolls out of check-in range
    private static final ClockTicks CHECK_IN_COOLDOWN_TICKS = ClockTicks.seconds(5);
    private static final ClockTicks OWNED_LANTERN_SAMPLE_COOLDOWN_TICKS = ClockTicks.seconds(5);

    private static final SyncedDataWrapper<Byte> DATA_MOTION_ARCHETYPE = SyncedDataWrapper.<Byte>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BYTE)
            .defaultValue(AnimationArchetype.NONE.toNetworkByte())
            .build();
    private static final SyncedDataWrapper<Byte> DATA_MOTION_GENERATION = SyncedDataWrapper.<Byte>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BYTE)
            .defaultValue((byte) 0)
            .build();
    private static final SyncedDataWrapper<Byte> DATA_LOCOMOTION_NAVIGATION_TYPE = SyncedDataWrapper.<Byte>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BYTE)
            .defaultValue(NavigationType.WALK.toNetworkByte())
            .build();
    private static final SyncedDataWrapper<Boolean> DATA_BOBBER_DEPLOYED = SyncedDataWrapper.<Boolean>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BOOLEAN)
            .defaultValue(false)
            .build();
    private static final SyncedDataWrapper<Boolean> DATA_SOOTY = SyncedDataWrapper.<Boolean>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BOOLEAN)
            .defaultValue(false)
            .build();
    private static final SyncedDataWrapper<Byte> DATA_OWNED_LANTERN_TYPE = SyncedDataWrapper.<Byte>builder()
            .entityClass(BaseVillager.class)
            .serializer(EntityDataSerializers.BYTE)
            .defaultValue(OwnedLanternType.NONE.toNetworkByte())
            .build();

    private final IVillagerBrain settlementsBrain;
    private final VanillaMemoryNavigationManager<BaseVillager> navigationManager;
    private final PlanRuntimeState planRuntimeState;
    private final OverrideRuntimeState overrideRuntimeState;
    private boolean brainGoalsInitialized;

    /**
     * Assigned daily plan, or null when none is available. Not persisted.
     */
    @Nullable
    private DayPlan dayPlan;

    private final SocialCueRuntimeState socialCueRuntimeState;

    // Client render state: built on first client use and held on the entity so it lives exactly as long as this object
    @ClientSide
    @Nullable
    private VillagerAnimator animator;
    @ClientSide
    @Nullable
    private BubbleManager bubbleManager;

    private final VillagerBubbleState bubbleState;
    @Nullable
    private ITickable hungerDrainTimer;
    /**
     * Transient restock reaction state, idle on a newly constructed villager.
     */
    private final RestockReaction restockReaction;
    @Getter(AccessLevel.NONE)
    private final CarriedPropConditions carriedPropConditions;

    private final ITickable reconcilerCooldown;
    private final ITickable perceptionCooldown;
    private final ITickable nameSyncCooldown;
    private final ITickable membershipCheckCooldown;
    private final ITickable checkInCooldown;
    private final ITickable ownedLanternSampleCooldown;
    @Nullable
    private String lastPublishedName;
    private long sootyExpiresAtGameTime;
    @Nullable
    private VillagerProfession cachedProfession;
    @Nullable
    private VillagerProfessionKey cachedProfessionKey;

    public BaseVillager(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);

        this.settlementsBrain = new VillagerBrain(this);

        this.navigationManager = new VanillaMemoryNavigationManager<>(this);

        this.planRuntimeState = new PlanRuntimeState();
        this.overrideRuntimeState = new OverrideRuntimeState();
        EventLaneConfig eventLaneConfig = SettlementsDagger.component().eventLaneConfig();
        this.socialCueRuntimeState = new SocialCueRuntimeState(eventLaneConfig.observationBufferCapacity());

        this.bubbleState = new VillagerBubbleState();
        this.hungerDrainTimer = null;
        this.restockReaction = new RestockReaction();
        this.carriedPropConditions = new CarriedPropConditions();

        this.reconcilerCooldown = Tickable.staggered(RECONCILER_COOLDOWN_TICKS);
        this.perceptionCooldown = Tickable.staggered(PERCEPTION_COOLDOWN_TICKS);
        this.nameSyncCooldown = Tickable.staggered(NAME_SYNC_COOLDOWN_TICKS);
        this.membershipCheckCooldown = Tickable.staggered(MEMBERSHIP_CHECK_COOLDOWN_TICKS);
        this.checkInCooldown = Tickable.staggered(CHECK_IN_COOLDOWN_TICKS);
        this.ownedLanternSampleCooldown = Tickable.staggered(OWNED_LANTERN_SAMPLE_COOLDOWN_TICKS);
        this.sootyExpiresAtGameTime = 0L;
    }

    @Override
    protected PathNavigation createNavigation(@Nonnull Level level) {
        // Mob construction invokes this on both logical sides before this class's fields are initialized.
        // Do not read instance fields or resolve server services here.
        return new SettlementsGroundPathNavigation(this, level);
    }

    @Override
    public void lookAt(@Nonnull Location target) {
        this.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.toBlockPos()));
    }

    public static AttributeSupplier createCustomAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, DEFAULT_FOLLOW_RANGE)
                .build();
    }

    @Override
    protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        DATA_MOTION_ARCHETYPE.define(builder);
        DATA_MOTION_GENERATION.define(builder);
        DATA_LOCOMOTION_NAVIGATION_TYPE.define(builder);
        DATA_BOBBER_DEPLOYED.define(builder);
        DATA_SOOTY.define(builder);
        DATA_OWNED_LANTERN_TYPE.define(builder);
    }

    /**
     * Sets the held animation state without restarting an unchanged archetype.
     *
     * @param archetype the animation state to hold
     * @see #triggerMotion(AnimationArchetype)
     */
    public void setMotion(@Nonnull AnimationArchetype archetype) {
        DATA_MOTION_ARCHETYPE.set(this.entityData, archetype.toNetworkByte());
    }

    /**
     * Triggers a one-shot animation from the beginning, including when the archetype is unchanged.
     *
     * @param archetype the animation to trigger
     */
    public void triggerMotion(@Nonnull AnimationArchetype archetype) {
        // Only a change in generation matters, so byte overflow intentionally wraps.
        DATA_MOTION_GENERATION.set(this.entityData, (byte) (DATA_MOTION_GENERATION.get(this.entityData) + 1));
        DATA_MOTION_ARCHETYPE.set(this.entityData, archetype.toNetworkByte());
    }

    public AnimationArchetype getMotion() {
        return AnimationArchetype.fromNetworkByte(DATA_MOTION_ARCHETYPE.get(this.entityData));
    }

    public byte getMotionGeneration() {
        return DATA_MOTION_GENERATION.get(this.entityData);
    }

    /**
     * Returns the height of this villager's crossed arms above its feet, in blocks, scaled by age and size.
     */
    public float getHandHeight() {
        return HAND_HEIGHT * this.getAgeScale() * this.getScale();
    }

    @Override
    public INavigationManager<BaseVillager> getNavigationManager() {
        return this.navigationManager;
    }

    public NavigationType getLocomotionNavigationType() {
        return NavigationType.fromNetworkByte(DATA_LOCOMOTION_NAVIGATION_TYPE.get(this.entityData));
    }

    public void setBobberDeployed(boolean deployed) {
        DATA_BOBBER_DEPLOYED.set(this.entityData, deployed);
    }

    public boolean isBobberDeployed() {
        return DATA_BOBBER_DEPLOYED.get(this.entityData);
    }

    public void setSooty(@Nonnull ClockTicks duration) {
        int ticks = Math.max(0, duration.getTicksAsInt());
        if (ticks <= 0) {
            this.sootyExpiresAtGameTime = 0L;
            DATA_SOOTY.set(this.entityData, false);
            return;
        }

        this.sootyExpiresAtGameTime = this.level().getGameTime() + ticks;
        DATA_SOOTY.set(this.entityData, true);
    }

    public boolean isSooty() {
        return DATA_SOOTY.get(this.entityData);
    }

    /**
     * Whether sampled rain conditions request an umbrella; false on the server and before the first client sample.
     */
    @ClientSide
    public boolean isUmbrellaRequested() {
        return this.carriedPropConditions.isUmbrellaRequested();
    }

    /**
     * Whether sampled darkness and sky exposure request this villager's owned lantern;
     * false on the server and before the first client sample.
     */
    @ClientSide
    public boolean isLanternRequested() {
        return this.carriedPropConditions.isLanternRequested();
    }

    /**
     * Whether this villager's lantern conditions have been sampled on the client; false on the server.
     */
    @ClientSide
    public boolean isLanternCheckDecided() {
        return this.carriedPropConditions.isLanternCheckDecided();
    }

    /**
     * Returns the sampled owned lantern on either logical side, defaulting to NONE before the first server sample.
     */
    public OwnedLanternType getOwnedLanternType() {
        return OwnedLanternType.fromNetworkByte(DATA_OWNED_LANTERN_TYPE.get(this.entityData));
    }

    /**
     * Gives this villager its starting food unless it already holds an inventory.
     */
    private void seedStartingInventoryIfAbsent() {
        if (this.hasSettlementsInventory()) {
            return;
        }

        VillagerInventory inventory = new VillagerInventory(() -> GeneralConfig.bypassInventoryRequirements);
        for (int i = 0; i < STARTING_BREAD_STACKS; i++) {
            inventory.add(new ItemStack(Items.BREAD, STARTING_BREAD_PER_STACK));
        }
        this.setData(AttachmentRegistry.VILLAGER_INVENTORY, inventory);
    }

    /**
     * Returns this villager's Settlements inventory.
     *
     * @throws IllegalStateException if no inventory has been initialized or loaded
     */
    @Override
    public VillagerInventory getSettlementsInventory() {
        return this.getData(AttachmentRegistry.VILLAGER_INVENTORY);
    }

    public boolean hasSettlementsInventory() {
        return this.hasData(AttachmentRegistry.VILLAGER_INVENTORY);
    }

    /**
     * Rolls genes for a villager that has none.
     */
    @ServerSide
    public GeneticsProfile getGenetics() {
        return this.getData(AttachmentRegistry.VILLAGER_GENETICS);
    }

    public VillagerKnowledgeStore getKnowledgeStore() {
        return this.getData(AttachmentRegistry.VILLAGER_KNOWLEDGE);
    }

    public VillagerProfessionKey getProfession() {
        VillagerProfession currentProfession = this.getVillagerData().getProfession();
        if (this.cachedProfession != currentProfession || this.cachedProfessionKey == null) {
            // Profession is stable for long stretches but can change through vanilla data updates, so invalidate lazily.
            this.cachedProfession = currentProfession;
            this.cachedProfessionKey = new VillagerProfessionKey(BuiltInRegistries.VILLAGER_PROFESSION.getKey(currentProfession));
        }

        return this.cachedProfessionKey;
    }

    /**
     * Assigns a UUID-derived name if this villager has no custom name, preserving any existing name.
     */
    private void assignNameIfAbsent() {
        if (this.getCustomName() != null) {
            return;
        }

        this.setCustomName(Component.literal(VillagerNameGenerator.generateName(this.getUUID())));
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();

        if (this.level() instanceof ServerLevel serverLevel) {
            // Initial construction uses vanilla's private goal registration. Defer replacement until
            // join so entity state and the server graph exist; loading may already have refreshed it.
            if (!this.brainGoalsInitialized) {
                this.refreshBrain(serverLevel);
            }

            // Sensors wait for the join because it runs once on each object that goes live. finalizeSpawn and load
            // also run on objects that never do, such as a spawner's client-side display entity, and load reruns on
            // a live villager under /data merge.
            this.settlementsBrain.initialize();

            // Publish after NBT has loaded so the directory can recover a missing or stale saved name.
            this.upsertNameInDirectory();

            // Members, including babies, may never check in, so repair missing roster entries on join.
            SettlementsDagger.serverOrThrow().settlementMembershipService().repair(this);
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);

        // Names can be set during construction or load, before this villager belongs in the server directory.
        if (!this.isAddedToLevel() || this.level().isClientSide()) {
            return;
        }

        this.upsertNameInDirectory();
    }

    private void upsertNameInDirectory() {
        String currentName = this.getName().getString();
        if (currentName.equals(this.lastPublishedName)) {
            return;
        }

        VillagerNameDirectory directory = SettlementsDagger.serverOrThrow().villagerNameDirectory();
        directory.upsert(this.getUUID(), currentName);
        this.lastPublishedName = currentName;
    }

    public Component getProfessionDisplayName() {
        return this.getTypeName();
    }

    @Nullable
    @Override
    public BaseVillager getBreedOffspring(@Nonnull ServerLevel level, @Nonnull AgeableMob otherParent) {
        BaseVillager child = EntityRegistry.BASE_VILLAGER.get().create(level);
        if (child == null) {
            return null;
        }

        VillagerType childType;
        double typeRoll = this.random.nextDouble();
        if (typeRoll < 0.50D) {
            childType = VillagerType.byBiome(level.getBiome(this.blockPosition()));
        } else if (typeRoll < 0.75D || !(otherParent instanceof Villager otherVillager)) {
            childType = this.getVillagerData().getType();
        } else {
            childType = otherVillager.getVillagerData().getType();
        }
        child.setVillagerData(child.getVillagerData().setType(childType));

        GeneticsProfile partnerGenetics = otherParent instanceof BaseVillager partner ? partner.getGenetics() : new GeneticsProfile();
        child.setData(AttachmentRegistry.VILLAGER_GENETICS, this.getGenetics().crossover(partnerGenetics, this.random));

        child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.BREEDING, null);

        if (this.random.nextFloat() < NITWIT_BREED_CHANCE) {
            child.setVillagerData(child.getVillagerData().setProfession(VillagerProfession.NITWIT));
        }

        VillagerOriginAttachment.stamp(child, OriginType.BRED);

        // Both parents are still alive and readable right here at breed time -- later either may
        // unload or die, so the persona snapshot has to be captured now rather than deferred.
        VillagerPersonaLineageAttachment.capture(child, this, otherParent instanceof BaseVillager p ? p : null);

        return child;
    }

    @Override
    public boolean canBreed() {
        if (this.getAge() != 0 || this.isSleeping() || !this.hasSettlementsInventory()) {
            return false;
        }

        if (this.getHunger() <= BREED_HUNGER_THRESHOLD) {
            return false;
        }

        VillagerInventory inventory = this.getSettlementsInventory();
        return inventory.requirementsBypassed() || inventory.countMatching(FOODS_MATCH) >= BREED_FOOD_REQUIREMENT;
    }

    /**
     * Returns whether this villager can run or accept social cues in its current lifecycle and activity state.
     */
    public boolean isSociallyAvailable() {
        if (this.isSleeping() || this.isBaby()) {
            return false;
        }

        Optional<Activity> activity = this.getBrain().getActiveNonCoreActivity();
        if (activity.isEmpty()) {
            return true;
        }

        if (activity.get() == Activity.HIDE) {
            return false;
        }

        return true;
    }

    public Occasion getCurrentOccasion() {
        return ActivityOccasionMapper.map(this.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE));
    }

    @Override
    public SpawnGroupData finalizeSpawn(@Nonnull ServerLevelAccessor level,
                                        @Nonnull DifficultyInstance difficulty,
                                        @Nonnull MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData finalizedSpawnData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.assignNameIfAbsent();
        VillagerGeneticAttributes.apply(this);
        this.seedStartingInventoryIfAbsent();
        this.stampOriginIfAbsent(spawnType);

        return finalizedSpawnData;
    }

    private void stampOriginIfAbsent(@Nonnull MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.BREEDING || VillagerOriginAttachment.isStamped(this)) {
            return;
        }

        OriginType origin = spawnType == MobSpawnType.STRUCTURE ? OriginType.WORLDGEN : OriginType.UNKNOWN;
        VillagerOriginAttachment.stamp(this, origin);
    }

    @Override
    public void load(@Nonnull CompoundTag nbtTag) {
        super.load(nbtTag);
        this.assignNameIfAbsent();

        // Client display copies must retain saved genetic attributes without generating genes or starting food.
        if (this.level().isClientSide()) {
            return;
        }

        // Restore attachments before filling missing genes or inventory so saved state takes precedence.
        VillagerGeneticAttributes.apply(this);
        this.seedStartingInventoryIfAbsent();
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.tickHunger();
            this.tickSooty();
            this.tickOwnedLantern();
            this.bubbleService().tick(this, this.level().getGameTime());
        } else {
            this.restockReaction.tickClient(this);
            this.carriedPropConditions.tickClient(this);
        }
    }

    /**
     * Restocks player trade offers and triggers a cosmetic restock reaction.
     */
    @Override
    public void restock() {
        super.restock();
        this.restockReaction.trigger(this);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (this.restockReaction.onEntityEvent(id, this.tickCount)) {
            return;
        }
        super.handleEntityEvent(id);
    }

    private void tickSooty() {
        if (!DATA_SOOTY.get(this.entityData)) {
            return;
        }

        if (this.sootyExpiresAtGameTime > this.level().getGameTime()) {
            return;
        }

        this.sootyExpiresAtGameTime = 0L;
        DATA_SOOTY.set(this.entityData, false);
    }

    @ServerSide
    private void tickOwnedLantern() {
        if (!this.ownedLanternSampleCooldown.tickCheckAndReset(1)) {
            return;
        }

        OwnedLanternType ownedLanternType = OwnedLanternType.NONE;
        if (this.hasSettlementsInventory()) {
            VillagerInventory inventory = this.getSettlementsInventory();
            if (inventory.count(Items.SOUL_LANTERN) > 0) {
                ownedLanternType = OwnedLanternType.SOUL_LANTERN;
            } else if (inventory.count(Items.LANTERN) > 0) {
                ownedLanternType = OwnedLanternType.LANTERN;
            }
        }
        DATA_OWNED_LANTERN_TYPE.set(this.entityData, ownedLanternType.toNetworkByte());
    }

    @Override
    protected void customServerAiStep() {
        ServerComponent server = SettlementsDagger.serverOrThrow();

        this.settlementsBrain.preVanillaAiStep();

        boolean cognitionEnabled = server.inferenceGate().isEnabled();
        if (cognitionEnabled) {
            // The cursor is transient, so every load starts it at zero and would otherwise replay the
            // whole retained log; seeding it to the high-water mark is the one-time skip.
            this.seedEventCursorOnFirstTick(server);
        }

        super.customServerAiStep();

        this.settlementsBrain.postVanillaAiStep();
        // Evaluate social cues after the brain so channel occupancy reflects this tick's plan behavior.
        this.tickSocialCue(server);

        if (cognitionEnabled) {
            this.tickPerception(server);
        }

        this.tickReconciler();
        this.tickNameSync();
        this.tickMembershipCheck(server);
        this.tickCheckIn(server);

        // Publish after every walk-target writer has run this tick
        NavigationType navigationIntent = this.navigationManager.resolveActiveNavigationIntent();
        DATA_LOCOMOTION_NAVIGATION_TYPE.set(this.entityData, navigationIntent.toNetworkByte());
    }

    /**
     * On the first server AI step, seeds the WorldEventBus cursor to the current high-water mark
     * so this villager does not replay events that predate its load.
     */
    private void seedEventCursorOnFirstTick(@Nonnull ServerComponent server) {
        if (this.socialCueRuntimeState.getLastSeenSeq() != 0L) {
            return;
        }

        long currentBusSeq = server.worldEventBus().currentSeq();
        if (currentBusSeq > 0L) {
            this.socialCueRuntimeState.seedCursor(currentBusSeq);
        }
    }

    /**
     * Advances this villager's social cues.
     */
    private void tickSocialCue(@Nonnull ServerComponent server) {
        server.socialCueArbiter().tick(this, this.socialCueRuntimeState, this.level().getGameTime());
    }

    /**
     * Runs the perception pipeline, throttled to {@link #PERCEPTION_COOLDOWN_TICKS}.
     */
    private void tickPerception(@Nonnull ServerComponent server) {
        if (!this.perceptionCooldown.tickCheckAndReset(1)) {
            return;
        }

        long perceptionGameTime = this.getServer() != null
                ? this.getServer().overworld().getGameTime()
                : this.level().getGameTime();
        server.perceptionPipeline().tick(this, this.socialCueRuntimeState, perceptionGameTime);
    }

    /**
     * Retries orphaned teardown obligations on a throttled cadence, deferring unloaded targets without counting failures.
     */
    private void tickReconciler() {
        if (!this.reconcilerCooldown.tickCheckAndReset(1)) {
            return;
        }

        ITeardownLedger ledger = this.getTeardownLedger();
        List<LedgerEntry> orphans = ledger.pendingOrphans();
        if (orphans.isEmpty()) {
            return;
        }

        ServerLevel level = (ServerLevel) this.level();

        // Snapshot to a new list: resolveOrphan() modifies the underlying collection,
        // so we cannot iterate the live view and remove concurrently.
        for (LedgerEntry entry : new ArrayList<>(orphans)) {
            TeardownObligation obligation = entry.getObligation();

            if (!level.isLoaded(obligation.targetPos())) {
                // Waiting for a chunk must not exhaust the cleanup retry limit.
                continue;
            }

            if (ledger.hasLiveObligationAt(obligation.targetPos())) {
                // A live behavior run re-acquired this resource; it owns cleanup now.
                continue;
            }

            if (!obligation.stillValid(level)) {
                ledger.resolveOrphan(obligation);
                continue;
            }

            try {
                obligation.discharge(level);
            } catch (Exception e) {
                log.as(LogCategory.BEHAVIOR).warn("Failed to discharge crash-orphan obligation '{}'", obligation.describe(), e);
            }

            if (!obligation.stillValid(level)) {
                ledger.resolveOrphan(obligation);
            } else {
                entry.incrementFailedAttempts();
                if (entry.getFailedAttempts() >= MAX_DISCHARGE_ATTEMPTS) {
                    log.as(LogCategory.BEHAVIOR).error(
                            "Abandoning crash-orphan obligation '{}' after {} failed attempts; manual cleanup may be required",
                            obligation.describe(), MAX_DISCHARGE_ATTEMPTS);
                    ledger.resolveOrphan(obligation);
                }
            }
        }
    }

    /**
     * Backstop for CustomName writes that bypass {@link #setCustomName}.
     */
    private void tickNameSync() {
        if (!this.nameSyncCooldown.tickCheckAndReset(1)) {
            return;
        }

        this.upsertNameInDirectory();
    }

    /**
     * Attempts settlement assignment or validates existing membership, throttled to
     * {@link #MEMBERSHIP_CHECK_COOLDOWN_TICKS}.
     */
    private void tickMembershipCheck(@Nonnull ServerComponent server) {
        if (!this.membershipCheckCooldown.tickCheckAndReset(1)) {
            return;
        }

        if (this.hasData(AttachmentRegistry.SETTLEMENT_MEMBERSHIP)) {
            server.settlementMembershipService().clearUnresolvableMembership(this);
        } else {
            server.settlementAssignmentService().assign(this);
        }
    }

    /**
     * Attempts to check this villager in at its settlement's gathering, throttled to
     * {@link #CHECK_IN_COOLDOWN_TICKS}.
     */
    private void tickCheckIn(@Nonnull ServerComponent server) {
        if (!this.checkInCooldown.tickCheckAndReset(1)) {
            return;
        }

        server.settlementMembershipService().checkIn(this);
    }

    /**
     * Also forgets the POI positions vanilla's death releases, including when the death event is canceled.
     */
    @Override
    public void die(@Nonnull DamageSource cause) {
        super.die(cause);

        // Vanilla POI reservation tickets don't record their holder, so a remembered position whose ticket is
        // released can free another villager's: removal stops a job-site walk still running from before death,
        // which releases its target again. A survivor of a canceled death must not go on claiming positions
        // nobody holds for it either.
        Villager.POI_MEMORIES.keySet().forEach(this.getBrain()::eraseMemory);
    }

    @Override
    public void remove(@Nonnull RemovalReason reason) {
        ServerComponent server = this.level().isClientSide() ? null : SettlementsDagger.serverOrNull();

        // Stop behaviors before removal so teardown can still resolve entities in this villager's level.
        // Chunk unload and dimension change use setRemoved directly and bypass this cleanup.
        if (this.level() instanceof ServerLevel serverLevel) {
            this.getBrain().stopAll(serverLevel, this);
        }

        this.planRuntimeState.clearPendingGeneration();

        // Preserve directory and roster entries for villagers that can return after a temporary unload.
        if (server != null && reason.shouldDestroy()) {
            server.villagerNameDirectory().remove(this.getUUID());
            // Drop the cache too: if this same instance is ever revived, onAddedToLevel must re-publish
            this.lastPublishedName = null;
            server.settlementMembershipService().recordRemoval(this, reason);
        }

        if (server != null && shouldEvictServerScopeCaches(reason)) {
            server.dialogueProvider().evict(this.getUUID());
        }

        super.remove(reason);
    }

    /**
     * When enabled, scatters a capped sample of this villager's Settlements inventory in addition to vanilla death loot.
     */
    @Override
    protected void dropCustomDeathLoot(@Nonnull ServerLevel level, @Nonnull DamageSource damageSource, boolean recentlyHit) {
        boolean scatterInventory = this.hasSettlementsInventory()
                && ConfigFactory.create(DropInventoryOnDeathConfig.class).enabled();

        if (scatterInventory) {
            // The held and off-hand items are mirrored into the vanilla equipment slots, so clearing those
            // slots here stops vanilla's equipment drop from duplicating the inventory drops below.
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        if (scatterInventory) {
            // The inventory is drained in full regardless of the cap, since the entity is going away either way.
            for (ItemStack stack : this.selectDeathDrops(this.getSettlementsInventory().drainAll())) {
                ItemEntity itemEntity = new ItemEntity(level, this.getX(), this.getEyeY() - 0.3D, this.getZ(), stack);

                double horizontalSpeed = this.random.nextFloat() * 0.25D;
                double angle = this.random.nextFloat() * (Math.PI * 2.0D);
                itemEntity.setDeltaMovement(-Math.sin(angle) * horizontalSpeed, 0.2D, Math.cos(angle) * horizontalSpeed);

                level.addFreshEntity(itemEntity);
            }
        }
    }

    /**
     * Returns a uniform sample without replacement of at most {@link #MAX_DEATH_DROP_STACKS} stacks after withholding
     * the starting bread grant. Does not modify the input list or its stacks.
     */
    private List<ItemStack> selectDeathDrops(@Nonnull List<ItemStack> drained) {
        List<ItemStack> droppable = new ArrayList<>(drained.size());
        // Withhold the starting grant so breeding and killing villagers cannot create bread
        int breadToWithhold = STARTING_BREAD_STACKS * STARTING_BREAD_PER_STACK;

        for (ItemStack stack : drained) {
            if (!stack.is(Items.BREAD) || breadToWithhold <= 0) {
                droppable.add(stack);
                continue;
            }

            int withheld = Math.min(breadToWithhold, stack.getCount());
            breadToWithhold -= withheld;
            if (withheld < stack.getCount()) {
                droppable.add(stack.copyWithCount(stack.getCount() - withheld));
            }
        }

        // Bound the item entities spawned in one tick even when an unbounded inventory has accumulated many stacks
        if (droppable.size() <= MAX_DEATH_DROP_STACKS) {
            return droppable;
        }

        // Partial Fisher-Yates avoids bias toward the inventory's iteration order without shuffling discarded stacks
        for (int i = 0; i < MAX_DEATH_DROP_STACKS; i++) {
            Collections.swap(droppable, i, i + this.random.nextInt(droppable.size() - i));
        }

        return droppable.subList(0, MAX_DEATH_DROP_STACKS);
    }

    /**
     * Stops active brain behaviors and resets this villager's plan state before installing fresh brain goals.
     */
    @Override
    public void refreshBrain(@Nonnull ServerLevel level) {
        Brain<Villager> brain = this.getBrain();
        brain.stopAll(level, this);
        this.brain = brain.copyWithoutBehaviors();
        this.dayPlan = null;
        this.planRuntimeState.reset();
        this.registerBrainGoals(this.getBrain());
        this.brainGoalsInitialized = true;
    }

    /**
     * Updates age-dependent state and records a loaded member's transition to adulthood on its settlement's roster.
     */
    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();

        // Loading a baby, a newborn's first age, and a conversion's copied age cross the boundary too, before the
        // villager is in a level, and none of them is growing up
        if (!this.isBaby() && this.isAddedToLevel() && this.level() instanceof ServerLevel) {
            SettlementsDagger.serverOrThrow().settlementMembershipService().recordGrowingUp(this);
        }
    }

    @Nullable
    public DayPlan getDayPlan() {
        return this.dayPlan;
    }

    public void setDayPlan(@Nonnull DayPlan dayPlan) {
        this.dayPlan = dayPlan;
    }

    /**
     * Whether this villager has an assigned day-plan behavior or an installed override.
     */
    public boolean hasActiveExecution() {
        return this.planRuntimeState.isBehaviorActive() || this.overrideRuntimeState.hasRunner();
    }

    /**
     * Returns a snapshot of the channels declared by this villager's current day-plan descriptor
     * and installed override. Missing sources contribute no channels.
     */
    public Set<BehaviorChannel> occupiedChannels() {
        Set<BehaviorChannel> channels = new HashSet<>();
        BehaviorPlanningMetadata planDescriptor = this.planRuntimeState.getCurrentDescriptor();
        if (planDescriptor != null) {
            channels.addAll(planDescriptor.getRequiredChannels());
        }
        OverrideRunner runner = this.overrideRuntimeState.getRunner();
        if (runner != null) {
            channels.addAll(runner.occupiedChannels());
        }
        return channels;
    }

    /**
     * Installs this villager's age-appropriate activities and schedule.
     */
    private void registerBrainGoals(Brain<Villager> brain) {
        VillagerProfession profession = this.getVillagerData().getProfession();

        List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> corePackage = new ArrayList<>(
                VanillaBehaviorPackages.getCorePackage(profession, this.navigationManager.speedFor(NavigationType.STROLL)));
        if (!this.isBaby()) {
            PlanRunnerBehavior planRunnerBehavior = SettlementsDagger.serverOrThrow()
                    .planRunnerBehaviorProvider()
                    .get();
            corePackage.add(Pair.of(20, planRunnerBehavior));
        }
        brain.addActivity(Activity.CORE, ImmutableList.copyOf(corePackage));

        if (this.isBaby()) {
            this.registerBabyBrainGoals(brain, profession);
        } else {
            this.registerAdultAmbientBrainGoals(brain, profession);
        }

        brain.addActivity(ActivityRegistry.COMBAT, ImmutableList.of()); // Empty: the fight runs in the override lane, outside the brain
        brain.addActivity(Activity.PANIC, VanillaBehaviorPackages.getPanicPackage(profession, this.navigationManager.speedFor(NavigationType.SPRINT)));
        brain.addActivity(Activity.PRE_RAID, VanillaBehaviorPackages.getPreRaidPackage(profession, this.navigationManager.speedFor(NavigationType.RUN)));
        brain.addActivity(ActivityRegistry.RAID_HIDE, VanillaBehaviorPackages.getRaidHidePackage(this.navigationManager.speedFor(NavigationType.RUN)));
        brain.addActivity(ActivityRegistry.RAID_CELEBRATE, VanillaBehaviorPackages.getRaidCelebratePackage(this.navigationManager.speedFor(NavigationType.RUN)));
        brain.addActivity(Activity.HIDE, VanillaBehaviorPackages.getHidePackage(profession, this.navigationManager.speedFor(NavigationType.RUN)));

        // Activating an activity snapshots the core set, so configure it before the first activation.
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));

        if (this.isBaby()) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
            brain.updateActivityFromSchedule(this.level().getDayTime(), this.level().getGameTime());
        } else {
            brain.setSchedule(ScheduleRegistry.SETTLEMENTS_SCHEDULE.get());
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private void registerBabyBrainGoals(Brain<Villager> brain,
                                        VillagerProfession profession) {
        brain.addActivity(Activity.IDLE, VanillaBehaviorPackages.getIdlePackage(this.navigationManager.speedFor(NavigationType.STROLL)));
        brain.addActivity(Activity.PLAY, VanillaBehaviorPackages.getPlayPackage(this.navigationManager.speedFor(NavigationType.RUN)));
        brain.addActivityWithConditions(Activity.MEET, VanillaBehaviorPackages.getMeetPackage(profession, this.navigationManager.speedFor(NavigationType.WALK)),
                Set.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, VanillaBehaviorPackages.getRestPackage(profession, this.navigationManager.speedFor(NavigationType.STROLL)));
    }

    private void registerAdultAmbientBrainGoals(Brain<Villager> brain,
                                                VillagerProfession profession) {
        brain.addActivityWithConditions(Activity.WORK,
                VanillaAmbientBehaviorPackages.getAmbientWorkPackage(profession, this.navigationManager.speedFor(NavigationType.WALK)),
                Set.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        brain.addActivityWithConditions(Activity.MEET,
                VanillaAmbientBehaviorPackages.getAmbientMeetPackage(this.navigationManager.speedFor(NavigationType.WALK)),
                Set.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.IDLE, VanillaAmbientBehaviorPackages.getAmbientIdlePackage(this.navigationManager.speedFor(NavigationType.STROLL)));
        brain.addActivity(Activity.REST, VanillaAmbientBehaviorPackages.getAmbientRestPackage(this.navigationManager.speedFor(NavigationType.WALK)));
    }

    @Override
    public Optional<ItemStack> getHeldItem() {
        if (this.hasSettlementsInventory()) {
            Optional<ItemStack> mainHand = this.getSettlementsInventory().getMainHand();
            if (mainHand.isPresent()) {
                return mainHand;
            }
        }

        // TODO: deprecate vanilla
        ItemStack vanillaMainHand = this.getItemInHand(InteractionHand.MAIN_HAND);
        return vanillaMainHand.isEmpty() ? Optional.empty() : Optional.of(vanillaMainHand);
    }

    @Override
    public void setHeldItem(@Nonnull ItemStack itemStack) {
        // TODO: deprecate vanilla
        this.setItemInHand(InteractionHand.MAIN_HAND, itemStack);
        if (this.hasSettlementsInventory()) {
            this.getSettlementsInventory().setEquipped(EquipmentSlot.MAIN_HAND, itemStack);
        }
    }

    @Override
    public void clearHeldItem() {
        // TODO: deprecate vanilla
        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (this.hasSettlementsInventory()) {
            this.getSettlementsInventory().setEquipped(EquipmentSlot.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    public void setOffhandItem(@Nonnull ItemStack itemStack) {
        // TODO: deprecate vanilla
        this.setItemInHand(InteractionHand.OFF_HAND, itemStack);
        if (this.hasSettlementsInventory()) {
            this.getSettlementsInventory().setEquipped(EquipmentSlot.OFF_HAND, itemStack);
        }
    }

    public void clearOffhandItem() {
        // TODO: deprecate vanilla
        this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (this.hasSettlementsInventory()) {
            this.getSettlementsInventory().setEquipped(EquipmentSlot.OFF_HAND, ItemStack.EMPTY);
        }
    }

    @Override
    public ITeardownLedger getTeardownLedger() {
        return this.getData(AttachmentRegistry.VILLAGER_TEARDOWN_LEDGER);
    }

    @Override
    public float getHunger() {
        return VillagerHungerAttachment.getHunger(this);
    }

    @Override
    public void setHunger(float hunger) {
        VillagerHungerAttachment.setHunger(this, Math.clamp(hunger, 0.0f, 1.0f));
    }

    public Expertise getExpertise() {
        return Expertise.fromLevel(this.getVillagerData().getLevel());
    }

    @Override
    public BaseVillager getMinecraftEntity() {
        return this;
    }

    /**
     * Returns this villager's client animation state, creating it with factory on first access.
     */
    @ClientSide
    public VillagerAnimator getAnimator(@Nonnull VillagerAnimatorFactory factory) {
        if (this.animator == null) {
            this.animator = factory.create();
        }
        return this.animator;
    }

    @ClientSide
    public BubbleManager getBubbleManager() {
        if (this.bubbleManager == null) {
            this.bubbleManager = new BubbleManager();
        }
        return this.bubbleManager;
    }

    @Override
    public VillagerBubbleState getBubbleState() {
        return this.bubbleState;
    }

    @Override
    public void upsertBubble(@Nonnull BubbleChannel channel, @Nonnull String ownerKey, @Nonnull BubbleMessage message) {
        BubbleCommand.Upsert command = new BubbleCommand.Upsert(channel, ownerKey, message);
        this.bubbleService().applyCommand(this, command, this.level().getGameTime());
    }

    @Override
    public void removeBubbleByOwner(@Nonnull BubbleChannel channel, @Nonnull String ownerKey) {
        BubbleCommand.RemoveByOwner command = new BubbleCommand.RemoveByOwner(channel, ownerKey);
        this.bubbleService().applyCommand(this, command, this.level().getGameTime());
    }

    /**
     * Returns whether entity removal invalidates this villager's server-scope caches.
     */
    private static boolean shouldEvictServerScopeCaches(@Nonnull RemovalReason reason) {
        // Chunk unload invalidates caches without permanently removing the villager from the name directory.
        return reason == RemovalReason.KILLED
                || reason == RemovalReason.DISCARDED
                || reason.shouldSave();
    }

    private VillagerBubbleService bubbleService() {
        // Minecraft controls entity construction via EntityType, so constructor injection is not possible here.
        return SettlementsDagger.serverOrThrow().villagerBubbleService();
    }

    @Override
    public int getNetworkingId() {
        return this.getId();
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.provider(memoryTypes(), sensorTypes());
    }

    public void gainExperience(int amount) {
        this.setVillagerXp(this.getVillagerXp() + amount);
        VillagerMixin mixin = (VillagerMixin) this;
        if (mixin.invokeShouldIncreaseLevel()) {
            mixin.setIncreaseProfessionLevelOnUpdate(true);
            mixin.setUpdateMerchantTimer(ClockTicks.seconds(2).getTicksAsInt());
        }
    }

    @Override
    protected void pickUpItem(@Nonnull ItemEntity itemEntity) {
        this.pickUp(itemEntity);
    }

    @Override
    public boolean wantsToPickUp(@Nonnull ItemStack itemStack) {
        // Do not passively pick up outside of behaviors
        return false;
    }

    public boolean isTradeAvailable() {
        if (!this.isAlive() || this.isRemoved()) {
            return false;
        }

        // TODO: check that no behaviors are running

        return SettlementsDagger.serverOrThrow()
                .tradeSessionRegistry()
                .getActiveSession(this.getUUID())
                .isEmpty();
    }

    private void tickHunger() {
        HungerConfig config = SettlementsDagger.component().hungerConfig();

        if (this.hungerDrainTimer == null) {
            this.hungerDrainTimer = Tickable.staggered(ClockTicks.seconds(config.tickIntervalSeconds()));
        }

        if (!this.hungerDrainTimer.tickCheckAndReset(1)) {
            return;
        }

        float baseDrain = config.drainPerInterval();
        double behaviorModifier = 1.0;
        IBehavior<BaseVillager> currentBehavior = this.planRuntimeState.getCurrentBehavior();
        if (currentBehavior != null && currentBehavior.getStatus() == BehaviorStatus.RUNNING
                && currentBehavior instanceof VillagerStateMachineBehavior villagerBehavior) {
            behaviorModifier = villagerBehavior.getHungerDrainModifier();
        }

        double sleepMultiplier = this.isSleeping() ? config.sleepingDrainMultiplier() : 1.0;
        this.setHunger((float) (this.getHunger() - baseDrain * behaviorModifier * sleepMultiplier));

        float hunger = this.getHunger();
        if (hunger < config.effectStartThreshold()) {
            int effectDuration = ClockTicks.seconds(config.tickIntervalSeconds() + 1).getTicksAsInt();
            int level = (int) Math.min(4, Math.ceil((config.effectStartThreshold() - hunger) / config.effectStartThreshold() * 4.0));

            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effectDuration, level - 1, true, false));
            this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectDuration, level - 1, true, false));
        }
    }

    @Override
    public void pickUp(ItemEntity itemEntity) {
        ItemStack itemstack = itemEntity.getItem();
        if (itemstack.isEmpty()) {
            return;
        }

        int takenCount = itemstack.getCount();
        this.getSettlementsInventory().add(itemstack);

        this.onItemPickup(itemEntity);
        this.take(itemEntity, takenCount);
        itemstack.setCount(0);
        itemEntity.setItem(itemstack);
    }

    private static ImmutableList<MemoryModuleType<?>> memoryTypes() {
        return ImmutableList.of(
                MemoryModuleType.HOME,
                MemoryModuleType.JOB_SITE,
                MemoryModuleType.POTENTIAL_JOB_SITE,
                MemoryModuleType.MEETING_POINT,
                MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.VISIBLE_VILLAGER_BABIES,
                MemoryModuleType.NEAREST_PLAYERS,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
                MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.INTERACTION_TARGET,
                // Required by LocateHidingPlace even when no breeding target is stored.
                MemoryModuleType.BREED_TARGET,
                MemoryModuleType.PATH,
                // Keep the vanilla slot registered for compatibility; barrier tracking uses BARRIERS_TO_CLOSE.
                MemoryModuleType.DOORS_TO_CLOSE,
                MemoryModuleType.NEAREST_BED,
                MemoryModuleType.HURT_BY_ENTITY,
                MemoryModuleType.NEAREST_HOSTILE,
                MemoryModuleType.SECONDARY_JOB_SITE,
                MemoryModuleType.HIDING_PLACE,
                MemoryModuleType.HEARD_BELL_TIME,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.LAST_SLEPT,
                MemoryModuleType.LAST_WOKEN,
                MemoryModuleType.LAST_WORKED_AT_POI,
                MemoryModuleType.GOLEM_DETECTED_RECENTLY,

                // Register custom memories used through the vanilla Brain API.
                MemoryTypeRegistry.BARRIERS_TO_CLOSE.getModuleType(),
                MemoryTypeRegistry.INTERACT_TARGET.getModuleType(),
                MemoryTypeRegistry.PLAN_BEHAVIOR_ACTIVE.getModuleType(),
                MemoryTypeRegistry.OWNED_WOLVES.getModuleType(),
                MemoryTypeRegistry.VILLAGE_CHESTS.getModuleType(),
                MemoryTypeRegistry.WILLING_COURTSHIP_PARTNERS.getModuleType(),
                MemoryTypeRegistry.NEARBY_SENSED_ENTITIES.getModuleType(),
                MemoryTypeRegistry.NEARBY_HOSTILES.getModuleType(),
                MemoryTypeRegistry.SIGHTED_HOSTILES.getModuleType(),
                MemoryTypeRegistry.UNSEEN_HOSTILES.getModuleType(),
                MemoryTypeRegistry.CULTIVATION_SITES.getModuleType(),
                MemoryTypeRegistry.DEMANDED_GROUND_ITEMS.getModuleType(),
                MemoryTypeRegistry.DEMANDED_ITEM_SELLER.getModuleType(),
                MemoryTypeRegistry.TRADE_INITIATE_COOLDOWN.getModuleType(),
                MemoryTypeRegistry.GOLEM_SUMMON_COOLDOWN.getModuleType(),
                MemoryTypeRegistry.CHART_RAIDERS_COOLDOWN.getModuleType(),
                MemoryTypeRegistry.BIND_VEXES_COOLDOWN.getModuleType(),
                MemoryTypeRegistry.REPAIR_IRON_GOLEM_RETRY_COOLDOWN.getModuleType(),
                MemoryTypeRegistry.REFUSED_BALLISTAS.getModuleType(),
                MemoryTypeRegistry.RETRIEVABLE_PROJECTILES.getModuleType()
        );
    }

    private static ImmutableList<SensorType<? extends Sensor<? super Villager>>> sensorTypes() {
        return ImmutableList.of(
                SensorType.NEAREST_LIVING_ENTITIES,
                SensorType.NEAREST_PLAYERS,
                SensorType.NEAREST_ITEMS,
                SensorType.NEAREST_BED,
                SensorType.SECONDARY_POIS,
                SensorType.GOLEM_DETECTED,
                SensorTypeRegistry.SETTLEMENTS_VILLAGER_BABIES_SENSOR.get(),
                SensorTypeRegistry.OWNED_PETS_SENSOR.get(),
                SensorTypeRegistry.VILLAGE_CHESTS_SENSOR.get(),
                SensorTypeRegistry.CULTIVATION_SITE_SENSOR.get(),
                SensorTypeRegistry.WILLING_COURTSHIP_PARTNERS_SENSOR.get()
        );
    }

}
