package com.enn3developer.gtblocker.lock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of the locked outputs, built for the hot path: crafting hooks call {@link #isLocked} on every
 * successful recipe match, so a lookup is one identity-map probe plus a binary search over a handful of metas, with
 * no allocation. Changing locks means building a new table and swapping it in (see {@link Locks}); readers on any
 * thread (NEI workers, AE2's crafting pool, the server tick) never see a half-built state.
 */
public final class LockTable {

    public static final LockTable EMPTY = new LockTable(Collections.emptyList());

    private final List<LockKey> keys;
    private final Set<LockKey> keySet;
    private final Map<Item, MetaSet> byItem;

    private LockTable(Collection<LockKey> keys) {
        this.keySet = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        this.keys = Collections.unmodifiableList(new ArrayList<>(keySet));

        Map<Item, List<Integer>> metas = new IdentityHashMap<>();
        Set<Item> anyMeta = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LockKey key : keySet) {
            Item item = key.item();
            if (item == null) {
                continue;
            }
            if (key.anyMeta()) {
                anyMeta.add(item);
            } else {
                metas.computeIfAbsent(item, i -> new ArrayList<>())
                    .add(key.meta());
            }
        }
        Map<Item, MetaSet> resolved = new IdentityHashMap<>();
        for (Item item : anyMeta) {
            resolved.put(item, MetaSet.ANY);
        }
        metas.forEach((item, list) -> resolved.computeIfAbsent(item, i -> MetaSet.of(list)));
        this.byItem = resolved;
    }

    public static LockTable of(Collection<LockKey> keys) {
        return keys.isEmpty() ? EMPTY : new LockTable(keys);
    }

    /** True when nothing is effectively locked (keys whose item isn't loaded don't count). */
    public boolean isEmpty() {
        return byItem.isEmpty();
    }

    public boolean isLocked(@Nullable ItemStack stack) {
        if (stack == null || byItem.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == null) {
            return false;
        }
        MetaSet metas = byItem.get(item);
        return metas != null && metas.contains(stack.getItemDamage());
    }

    /** Allocation-free variant for callers holding an item and meta rather than a stack (AE2 stacks). */
    public boolean isLocked(@Nullable Item item, int meta) {
        if (item == null || byItem.isEmpty()) {
            return false;
        }
        MetaSet metas = byItem.get(item);
        return metas != null && metas.contains(meta);
    }

    /** True if any non-null stack in {@code stacks} is locked. */
    public boolean anyLocked(@Nullable ItemStack[] stacks) {
        if (stacks == null || byItem.isEmpty()) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (isLocked(stack)) {
                return true;
            }
        }
        return false;
    }

    /** The key that locks {@code stack}, preferring an exact-meta key over an any-meta one, or null. */
    public @Nullable LockKey keyFor(@Nullable ItemStack stack) {
        if (!isLocked(stack)) {
            return null;
        }
        LockKey exact = LockKey.of(stack, false);
        if (exact != null && keySet.contains(exact)) {
            return exact;
        }
        LockKey any = LockKey.of(stack, true);
        return any != null && keySet.contains(any) ? any : null;
    }

    public boolean contains(LockKey key) {
        return keySet.contains(key);
    }

    /** Every key, in the order they were locked, including inactive ones whose item isn't loaded. */
    public List<LockKey> keys() {
        return keys;
    }

    private record MetaSet(boolean any, int[] sorted) {

        static final MetaSet ANY = new MetaSet(true, new int[0]);

        static MetaSet of(List<Integer> metas) {
            int[] sorted = metas.stream()
                .mapToInt(Integer::intValue)
                .distinct()
                .sorted()
                .toArray();
            return new MetaSet(false, sorted);
        }

        boolean contains(int meta) {
            return any || Arrays.binarySearch(sorted, meta) >= 0;
        }
    }
}
