package com.enn3developer.gtblocker.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.enn3developer.gtblocker.lock.LockKey;

/**
 * Everything GTBlocker persists for a world. Owned by the server thread; only {@link DataStore} and
 * {@link com.enn3developer.gtblocker.server.BlockerServer} touch it.
 */
public final class BlockerState {

    public final Set<LockKey> locks = new LinkedHashSet<>();
    /** Admin UUID to last known name, in the order they were added. */
    public final Map<UUID, String> admins = new LinkedHashMap<>();
    public final List<Party> parties = new ArrayList<>();
    public final List<HistoryEntry> history = new ArrayList<>();
    /** Player UUID to the last time they were online, for "while you were away". */
    public final Map<UUID, Long> lastSeen = new HashMap<>();
    public int nextPartyId = 1;
    /** Set once the ServerStatistics blocklist has been imported, so a later wipe of the list isn't undone. */
    public boolean importedServerStats;
}
