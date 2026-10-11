package com.enn3developer.gtblocker.data;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.lock.LockKey;

/**
 * A scheduled unlock: at {@code revealAt} (epoch millis, server clock) every item still locked is unlocked and
 * celebrated. Immutable; edits replace the instance.
 */
public record Party(int id, List<LockKey> items, long revealAt, String message, @Nullable Spot spot,
    String createdBy) {

    public Party withRevealAt(long time) {
        return new Party(id, items, time, message, spot, createdBy);
    }

    public Party withSpot(@Nullable Spot newSpot) {
        return new Party(id, items, revealAt, message, newSpot, createdBy);
    }

    public Party withItems(List<LockKey> newItems) {
        return new Party(id, List.copyOf(newItems), revealAt, message, spot, createdBy);
    }
}
