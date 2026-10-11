package com.enn3developer.gtblocker.data;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.lock.LockKey;

/**
 * One unlock, kept for the history tab and "while you were away". {@code partyId} is 0 for a quiet unlock; only
 * party unlocks race for a first craft.
 */
public record HistoryEntry(LockKey key, long unlockedAt, String unlockedBy, int partyId,
    @Nullable String firstCraftBy, long firstCraftAt) {

    public boolean celebrated() {
        return partyId != 0;
    }

    public boolean awaitingFirstCraft() {
        return celebrated() && firstCraftBy == null;
    }

    public HistoryEntry withFirstCraft(String player, long time) {
        return new HistoryEntry(key, unlockedAt, unlockedBy, partyId, player, time);
    }
}
