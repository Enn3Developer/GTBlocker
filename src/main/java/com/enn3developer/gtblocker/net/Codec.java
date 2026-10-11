package com.enn3developer.gtblocker.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.data.HistoryEntry;
import com.enn3developer.gtblocker.data.Party;
import com.enn3developer.gtblocker.data.Spot;
import com.enn3developer.gtblocker.lock.LockKey;

/** NBT wire format shared by the server writer and the client reader. */
public final class Codec {

    private Codec() {}

    public static NBTTagList writeKeys(Collection<LockKey> keys) {
        NBTTagList list = new NBTTagList();
        keys.forEach(key -> list.appendTag(new NBTTagString(key.serialize())));
        return list;
    }

    public static List<LockKey> readKeys(NBTTagCompound tag, String name) {
        NBTTagList list = tag.getTagList(name, Constants.NBT.TAG_STRING);
        List<LockKey> keys = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            LockKey key = LockKey.parse(list.getStringTagAt(i));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static NBTTagCompound writeSpot(Spot spot) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", spot.dim());
        tag.setInteger("x", spot.x());
        tag.setInteger("y", spot.y());
        tag.setInteger("z", spot.z());
        return tag;
    }

    public static @Nullable Spot readSpot(NBTTagCompound tag, String name) {
        if (!tag.hasKey(name, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound spot = tag.getCompoundTag(name);
        return new Spot(spot.getInteger("dim"), spot.getInteger("x"), spot.getInteger("y"), spot.getInteger("z"));
    }

    public static NBTTagCompound writeParty(Party party) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", party.id());
        tag.setTag("items", writeKeys(party.items()));
        tag.setLong("at", party.revealAt());
        tag.setString("msg", party.message());
        if (party.spot() != null) {
            tag.setTag("spot", writeSpot(party.spot()));
        }
        tag.setString("by", party.createdBy());
        return tag;
    }

    public static Party readParty(NBTTagCompound tag) {
        return new Party(
            tag.getInteger("id"),
            List.copyOf(readKeys(tag, "items")),
            tag.getLong("at"),
            tag.getString("msg"),
            readSpot(tag, "spot"),
            tag.getString("by"));
    }

    public static NBTTagCompound writeHistory(HistoryEntry entry) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("item", entry.key()
            .serialize());
        tag.setLong("at", entry.unlockedAt());
        tag.setString("by", entry.unlockedBy());
        tag.setInteger("party", entry.partyId());
        if (entry.firstCraftBy() != null) {
            tag.setString("first", entry.firstCraftBy());
            tag.setLong("firstAt", entry.firstCraftAt());
        }
        return tag;
    }

    public static @Nullable HistoryEntry readHistory(NBTTagCompound tag) {
        LockKey key = LockKey.parse(tag.getString("item"));
        if (key == null) {
            return null;
        }
        return new HistoryEntry(
            key,
            tag.getLong("at"),
            tag.getString("by"),
            tag.getInteger("party"),
            tag.hasKey("first") ? tag.getString("first") : null,
            tag.getLong("firstAt"));
    }
}
