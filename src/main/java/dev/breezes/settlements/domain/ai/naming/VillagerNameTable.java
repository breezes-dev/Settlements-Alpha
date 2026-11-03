package dev.breezes.settlements.domain.ai.naming;

import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stored villager display names with content-change tracking.
 */
public final class VillagerNameTable {

    private final Map<UUID, String> namesByUuid;
    @Getter
    private boolean dirty;

    public VillagerNameTable() {
        this(new HashMap<>());
    }

    public VillagerNameTable(@Nonnull Map<UUID, String> initialEntries) {
        this.namesByUuid = new HashMap<>(initialEntries);
    }

    /**
     * Returns the stored name or a name derived from the current pool. If neither is available, returns the UUID string.
     * Generated fallbacks are not stored.
     */
    public String resolve(@Nonnull UUID uuid, @Nonnull VillagerNamePool pool) {
        String stored = this.namesByUuid.get(uuid);
        return stored != null ? stored : pool.selectNameForUuid(uuid).orElseGet(uuid::toString);
    }

    public void upsert(@Nonnull UUID uuid, @Nonnull String name) {
        String previous = this.namesByUuid.put(uuid, name);
        if (!name.equals(previous)) {
            this.dirty = true;
        }
    }

    public void remove(@Nonnull UUID uuid) {
        String previous = this.namesByUuid.remove(uuid);
        if (previous != null) {
            this.dirty = true;
        }
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Snapshot of all entries, for the persistence shell's serialization step.
     */
    public Map<UUID, String> entriesView() {
        return Map.copyOf(this.namesByUuid);
    }

}
