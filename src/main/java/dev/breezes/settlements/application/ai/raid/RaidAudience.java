package dev.breezes.settlements.application.ai.raid;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The living players inside a raid's current area.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RaidAudience {

    /**
     * The players in the level who currently belong to the supplied raid's audience.
     */
    public static List<ServerPlayer> playersIn(@Nonnull ServerLevel level, @Nonnull Raid raid) {
        return level.getPlayers(player -> player.isAlive() && level.getRaidAt(player.blockPosition()) == raid);
    }

}
