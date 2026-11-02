package dev.breezes.settlements.application.ai.behavior.usecases.villager.trading;

import dev.breezes.settlements.application.ai.trading.TradeSession;
import dev.breezes.settlements.application.ui.bubble.BubbleChannel;
import dev.breezes.settlements.application.ui.bubble.BubbleCommand;
import dev.breezes.settlements.application.ui.bubble.BubbleMessage;
import dev.breezes.settlements.application.ui.bubble.BubbleSegment;
import dev.breezes.settlements.application.ui.bubble.TradeMarker;
import dev.breezes.settlements.application.ui.bubble.VillagerBubbleService;
import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.animation.AnimationArchetype;
import dev.breezes.settlements.domain.animation.InteractAnimations;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.domain.world.location.Location;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.network.features.ui.toss.packet.ClientBoundItemTossPacket;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual and audio presentation for villager trade sessions.
 */
@ServerScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class TradeSessionPresenter {

    private static final ClockTicks BUBBLE_TTL = ClockTicks.seconds(2);
    private static final String TRADE_SOURCE = "trade";

    // Every emerald toss but the last carries this many, and the last carries the remainder
    private static final int EMERALDS_PER_TOSS = 8;
    private static final int MAX_EMERALD_TOSSES = 5;

    private static final ClockTicks LEAD_IN_BEFORE_FIRST_REACH = ClockTicks.of(6);
    // Separate reaches by a full clip to avoid restarting an unfinished animation.
    private static final ClockTicks INTERVAL_BETWEEN_EMERALD_REACHES = ClockTicks.of(InteractAnimations.INTERACT_DURATION_TICKS);
    // Each item leaves at the top of its reach
    private static final ClockTicks REACH_TO_LAUNCH = ClockTicks.of(InteractAnimations.INTERACT_PEAK_TICK);
    private static final ClockTicks EMERALD_FLIGHT = ClockTicks.of(12);
    private static final ClockTicks PAUSE_AFTER_LAST_EMERALD_CATCH = ClockTicks.of(7);
    private static final ClockTicks GOODS_FLIGHT = ClockTicks.of(14);
    private static final ClockTicks SETTLE_AFTER_GOODS_CATCH = ClockTicks.of(12);

    private final VillagerBubbleService villagerBubbleService;

    public void presentOpeningOffer(@Nonnull TradeSession session, @Nonnull BaseVillager self) {
        TradeRole role = this.roleOf(session, self);
        TradeMarker marker = role == TradeRole.SELLER ? TradeMarker.UP : null;
        this.upsert(self, session, this.createMessage(session, role, marker));
        Location.fromEntity(self, true).playSound(SoundEvents.VILLAGER_TRADE, 0.8f, 1.0f, SoundSource.NEUTRAL);
    }

    public void presentNegotiationUpdate(@Nonnull TradeSession session, @Nonnull BaseVillager self) {
        TradeRole role = this.roleOf(session, self);
        TradeMarker marker = switch (role) {
            case BUYER -> TradeMarker.UP;
            case SELLER -> TradeMarker.DOWN;
        };

        this.upsert(self, session, this.createMessage(session, role, marker));

        Location.fromEntity(self, true).playSound(SoundEvents.VILLAGER_TRADE, 0.8f, 1.0f, SoundSource.NEUTRAL);
    }

    public void presentDeal(@Nonnull TradeSession session, @Nonnull BaseVillager self) {
        List<BubbleSegment> segments = this.createBaseSegments(session, session.getBuyerOffer());
        segments.add(TradeMarker.CHECK.asSegment());
        this.upsert(self, session, this.createMessage(segments));
    }

    /**
     * Returns the exchange duration in ticks since DEAL entry, including the final settling pause.
     */
    public int exchangeLengthTicks(@Nonnull TradeSession session) {
        long goodsCatchTick = goodsReachTick(emeraldTossCount(session.getBuyerOffer()))
                + REACH_TO_LAUNCH.getTicks() + GOODS_FLIGHT.getTicks();
        return Math.toIntExact(goodsCatchTick + SETTLE_AFTER_GOODS_CATCH.getTicks());
    }

    /**
     * Presents the buyer's emerald tosses followed by the seller's goods toss.
     * Only beats in (afterTick, throughTick] play, with ticks counted from DEAL entry.
     * Nothing plays unless both villagers are present and alive.
     *
     * @param seller the seller, or null when it is not in the buyer's level
     */
    public void presentExchangeBeats(@Nonnull TradeSession session,
                                     @Nonnull BaseVillager buyer,
                                     @Nullable BaseVillager seller,
                                     long afterTick,
                                     long throughTick) {
        // A catch needs its giver too, so the catch of a toss that never launched is never heard
        if (seller == null || !seller.isAlive() || !buyer.isAlive()) {
            return;
        }

        int price = session.getBuyerOffer();
        int emeraldTossCount = emeraldTossCount(price);
        for (int tossIndex = 0; tossIndex < emeraldTossCount; tossIndex++) {
            long reachTick = emeraldReachTick(tossIndex);
            long launchTick = reachTick + REACH_TO_LAUNCH.getTicks();
            long catchTick = launchTick + EMERALD_FLIGHT.getTicks();
            if (isBeatDue(reachTick, afterTick, throughTick)) {
                reach(buyer);
            }
            if (isBeatDue(launchTick, afterTick, throughTick)) {
                ItemStack emeralds = new ItemStack(Items.EMERALD, emeraldsInToss(price, emeraldTossCount, tossIndex));
                tossItem(buyer, seller, emeralds, EMERALD_FLIGHT);
            }
            if (isBeatDue(catchTick, afterTick, throughTick)) {
                // Each catch rings a whole step above the one before
                float pitch = (float) Math.pow(2.0, 2.0 * tossIndex / 12.0);
                Location.fromEntity(seller, true).playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, pitch,
                        SoundSource.NEUTRAL);
            }
        }

        long goodsReachTick = goodsReachTick(emeraldTossCount);
        long goodsLaunchTick = goodsReachTick + REACH_TO_LAUNCH.getTicks();
        long goodsCatchTick = goodsLaunchTick + GOODS_FLIGHT.getTicks();
        if (isBeatDue(goodsReachTick, afterTick, throughTick)) {
            reach(seller);
        }
        if (isBeatDue(goodsLaunchTick, afterTick, throughTick)) {
            ItemStack goods = new ItemStack(session.getMatchedItem(), session.getBundleSize());
            tossItem(seller, buyer, goods, GOODS_FLIGHT);
        }
        if (isBeatDue(goodsCatchTick, afterTick, throughTick)) {
            Location.fromEntity(buyer, true).playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.5f, SoundSource.NEUTRAL);
            celebrate(buyer);
            celebrate(seller);
        }
    }

    public void presentWalkAway(@Nonnull TradeSession session, @Nonnull BaseVillager self) {
        TradeRole role = this.roleOf(session, self);
        this.upsert(self, session, this.createMessage(session, role, TradeMarker.CROSS));
        Location selfHead = Location.fromEntity(self, true);
        selfHead.displayParticles(ParticleTypes.ANGRY_VILLAGER, 6, 0.2, 0.2, 0.2, 0.01);
        selfHead.playSound(SoundEvents.VILLAGER_NO, 0.8f, 1.0f, SoundSource.NEUTRAL);
    }

    private void upsert(@Nonnull BaseVillager self, @Nonnull TradeSession session, @Nonnull BubbleMessage message) {
        this.villagerBubbleService.applyCommand(
                self,
                new BubbleCommand.Upsert(BubbleChannel.BEHAVIOR, ownerKey(session), message),
                self.level().getGameTime());
    }

    private BubbleMessage createMessage(@Nonnull TradeSession session,
                                        @Nonnull TradeRole role,
                                        @Nullable TradeMarker marker) {
        int emeraldCount = switch (role) {
            case BUYER -> session.getBuyerOffer();
            case SELLER -> session.getSellerAsk();
        };

        List<BubbleSegment> segments = this.createBaseSegments(session, emeraldCount);
        if (marker != null) {
            segments.add(marker.asSegment());
        }

        return this.createMessage(segments);
    }

    private List<BubbleSegment> createBaseSegments(@Nonnull TradeSession session, int emeraldCount) {
        List<BubbleSegment> segments = new ArrayList<>();
        segments.add(BubbleSegment.Item.builder()
                .itemId(BuiltInRegistries.ITEM.getKey(session.getMatchedItem()))
                .count(session.getBundleSize())
                .build());
        segments.add(BubbleSegment.Item.builder()
                .itemId(BuiltInRegistries.ITEM.getKey(Items.EMERALD))
                .count(emeraldCount)
                .build());

        return segments;
    }

    private BubbleMessage createMessage(@Nonnull List<BubbleSegment> segments) {
        return BubbleMessage.builder()
                .priority(10)
                .ttl(BUBBLE_TTL)
                .sourceType(TRADE_SOURCE)
                .segments(segments)
                .build();
    }

    private TradeRole roleOf(@Nonnull TradeSession session, @Nonnull BaseVillager self) {
        if (self.getUUID().equals(session.getBuyerId())) {
            return TradeRole.BUYER;
        }
        if (self.getUUID().equals(session.getSellerId())) {
            return TradeRole.SELLER;
        }
        throw new IllegalArgumentException("Villager is not a participant in trade session " + session.getSessionId());
    }

    private static String ownerKey(@Nonnull TradeSession session) {
        return "trade-session-" + session.getSessionId();
    }

    private static int emeraldTossCount(int price) {
        return Math.min(Math.ceilDiv(price, EMERALDS_PER_TOSS), MAX_EMERALD_TOSSES);
    }

    private static int emeraldsInToss(int price, int emeraldTossCount, int tossIndex) {
        return tossIndex < emeraldTossCount - 1
                ? EMERALDS_PER_TOSS
                : price - EMERALDS_PER_TOSS * (emeraldTossCount - 1);
    }

    private static long emeraldReachTick(int tossIndex) {
        return LEAD_IN_BEFORE_FIRST_REACH.getTicks() + INTERVAL_BETWEEN_EMERALD_REACHES.getTicks() * tossIndex;
    }

    private static long goodsReachTick(int emeraldTossCount) {
        long lastEmeraldCatchTick = emeraldReachTick(emeraldTossCount - 1) + REACH_TO_LAUNCH.getTicks()
                + EMERALD_FLIGHT.getTicks();
        return lastEmeraldCatchTick + PAUSE_AFTER_LAST_EMERALD_CATCH.getTicks();
    }

    private static boolean isBeatDue(long beatTick, long afterTick, long throughTick) {
        return beatTick > afterTick && beatTick <= throughTick;
    }

    /**
     * Plays the giver's reach only when its motion is NONE or INTERACT.
     */
    private static void reach(@Nonnull BaseVillager giver) {
        AnimationArchetype motion = giver.getMotion();
        // The reach must not interrupt another action's motion.
        if (motion == AnimationArchetype.NONE || motion == AnimationArchetype.INTERACT) {
            giver.triggerMotion(AnimationArchetype.INTERACT);
        }
    }

    /**
     * Sends an item-toss presentation to players tracking the giver.
     */
    private static void tossItem(@Nonnull BaseVillager giver,
                                 @Nonnull BaseVillager receiver,
                                 @Nonnull ItemStack stack,
                                 @Nonnull ClockTicks flight) {
        PacketDistributor.sendToPlayersTrackingEntity(giver,
                new ClientBoundItemTossPacket(giver.getId(), receiver.getId(), stack, flight.getTicksAsInt()));
    }

    private static void celebrate(@Nonnull BaseVillager villager) {
        Location head = Location.fromEntity(villager, true);
        head.displayParticles(ParticleTypes.HAPPY_VILLAGER, 6, 0.2, 0.2, 0.2, 0.01);
        head.playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8f, 1.0f, SoundSource.NEUTRAL);
    }

    private enum TradeRole {
        BUYER,
        SELLER,
    }

}
