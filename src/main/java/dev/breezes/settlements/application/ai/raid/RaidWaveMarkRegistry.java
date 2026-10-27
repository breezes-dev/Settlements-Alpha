package dev.breezes.settlements.application.ai.raid;

import dev.breezes.settlements.application.ai.brain.ActivityArbiter;
import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.concurrency.RenewableLease;
import dev.breezes.settlements.infrastructure.minecraft.mixins.RaidMixin;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Unsaved next-wave claims and marks, confined to the server thread.
 * A committed mark belongs to its raid window independently of its cartographer.
 */
@ServerScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RaidWaveMarkRegistry {

    private final RaidWaveMarkPublisher publisher;
    private final Map<Raid, WindowMark> marks = new IdentityHashMap<>();

    /**
     * Whether the candidate may claim the raid's current window without changing registry state.
     */
    public boolean isAvailable(@Nonnull Raid raid, @Nonnull UUID candidateId) {
        int window = raid.getGroupsSpawned();
        if (!isWindowOpen(raid, window)) {
            return false;
        }

        WindowMark mark = this.marks.get(raid);
        return mark == null || mark.window != window
                || (!mark.marked && mark.lease.isFreeFor(candidateId, raid.getLevel().getGameTime()));
    }

    /**
     * Claims the specified current window when it is unmarked and available to the candidate.
     */
    public boolean acquire(@Nonnull Raid raid, int window, @Nonnull UUID candidateId) {
        if (!isWindowOpen(raid, window)) {
            return false;
        }

        WindowMark mark = this.marks.get(raid);
        if (mark == null || mark.window != window) {
            // A new duty can start before the tick hook has retired the preceding window
            mark = new WindowMark(window);
            this.marks.put(raid, mark);
        }
        return !mark.marked && mark.lease.acquire(candidateId, raid.getLevel().getGameTime());
    }

    /**
     * Renews an uncommitted live claim; a marked, closed, or different window is refused.
     */
    public boolean renew(@Nonnull Raid raid, int window, @Nonnull UUID candidateId) {
        WindowMark mark = this.marks.get(raid);
        return mark != null && mark.window == window && !mark.marked && isWindowOpen(raid, window)
                && mark.lease.renew(candidateId, raid.getLevel().getGameTime());
    }

    /**
     * Commits the live holder's mark once for the specified current window, releasing its lease.
     * A temporarily absent selection does not prevent commitment.
     */
    public boolean mark(@Nonnull Raid raid, int window, @Nonnull UUID candidateId) {
        WindowMark mark = this.marks.get(raid);
        if (mark == null || mark.window != window || mark.marked || !isWindowOpen(raid, window)
                || !mark.lease.isHeldBy(candidateId, raid.getLevel().getGameTime())) {
            return false;
        }

        mark.marked = true;
        mark.lease.release(candidateId);
        return true;
    }

    /**
     * Releases an outstanding claim without removing a committed mark or affecting another window's holder.
     */
    public void release(@Nonnull Raid raid, int window, @Nonnull UUID candidateId) {
        WindowMark mark = this.marks.get(raid);
        if (mark == null || mark.window != window || mark.marked) {
            return;
        }

        mark.lease.release(candidateId);
        if (!mark.lease.isLiveAt(raid.getLevel().getGameTime())) {
            this.marks.remove(raid);
        }
    }

    /**
     * Retires closed windows and publishes changes to marked selections after each server tick.
     * A spawned wave leaves its marker to expire on its own; a stopped or lost raid clears it.
     */
    public void tick() {
        if (this.marks.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Raid, WindowMark>> iterator = this.marks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Raid, WindowMark> entry = iterator.next();
            Raid raid = entry.getKey();
            WindowMark mark = entry.getValue();
            ServerLevel level = (ServerLevel) raid.getLevel();
            // Spawning empties the selection in the same tick, so it is checked before any selection change
            if (raid.getGroupsSpawned() != mark.window) {
                iterator.remove();
            } else if (raid.isStopped() || raid.isOver() || level.getRaids().get(raid.getId()) != raid) {
                if (mark.publishedSelection != null) {
                    // A stopped raid is outside every audience, so the clear goes to every player in its level
                    this.publish(level.players(), raid, null);
                }
                iterator.remove();
            } else if (!mark.marked) {
                if (!mark.lease.isLiveAt(level.getGameTime())) {
                    iterator.remove();
                }
            } else {
                this.publishSelectionChange(level, raid, mark);
            }
        }
    }

    private void publishSelectionChange(@Nonnull ServerLevel level, @Nonnull Raid raid, @Nonnull WindowMark mark) {
        BlockPos selection = ((RaidMixin) raid).getRaidWaveSpawnPos().orElse(null);
        if (Objects.equals(mark.publishedSelection, selection)) {
            return;
        }

        mark.publishedSelection = selection == null ? null : selection.immutable();
        this.publish(RaidAudience.playersIn(level, raid), raid, mark.publishedSelection);
    }

    private void publish(@Nonnull List<ServerPlayer> recipients, @Nonnull Raid raid, @Nullable BlockPos position) {
        for (ServerPlayer recipient : recipients) {
            this.publisher.send(recipient, raid.getLevel().dimension(), raid.getId(), position);
        }
    }

    private static boolean isWindowOpen(@Nonnull Raid raid, int window) {
        return raid.getGroupsSpawned() == window && raid.isActive() && ActivityArbiter.isRaidAwaitingWave(raid)
                && ((ServerLevel) raid.getLevel()).getRaids().get(raid.getId()) == raid;
    }

    /**
     * The claim or committed mark for one window.
     */
    private static final class WindowMark {

        private final int window;
        private final RenewableLease lease = new RenewableLease();
        private boolean marked;
        /**
         * The selection the audience last received; null before the first publication and after a clear.
         */
        @Nullable
        private BlockPos publishedSelection;

        private WindowMark(int window) {
            this.window = window;
        }

    }

}
