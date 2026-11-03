package dev.breezes.settlements.domain.ai.naming;

/**
 * The villager names available from the loaded datapacks.
 */
public interface VillagerNamePoolRegistry {

    /**
     * Returns the current merged pool, which may be empty.
     */
    VillagerNamePool pool();

}
