package com.enn3developer.gtblocker.client.gui;

import java.util.Collection;
import java.util.function.Consumer;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.net.Codec;
import com.enn3developer.gtblocker.net.Network;

/**
 * Requests from the GUI to the server. The server re-checks permissions on every one and answers in chat; the GUI
 * redraws when the resulting state arrives, never optimistically.
 */
final class Actions {

    private Actions() {}

    static void lock(LockKey key) {
        send("lock", tag -> tag.setString("item", key.serialize()));
    }

    static void relock(LockKey from, LockKey to) {
        send("relock", tag -> {
            tag.setString("from", from.serialize());
            tag.setString("to", to.serialize());
        });
    }

    static void unlock(Collection<LockKey> keys) {
        send("unlock", tag -> tag.setTag("items", Codec.writeKeys(keys)));
    }

    static void party(Collection<LockKey> keys, long revealAt, String message, boolean atMySpot) {
        send("party", tag -> {
            tag.setTag("items", Codec.writeKeys(keys));
            tag.setLong("at", revealAt);
            tag.setString("msg", message);
            tag.setBoolean("spot", atMySpot);
        });
    }

    static void startParty(int id) {
        send("party_start", tag -> tag.setInteger("id", id));
    }

    static void cancelParty(int id) {
        send("party_cancel", tag -> tag.setInteger("id", id));
    }

    static void partySpot(int id, boolean clear) {
        send("party_spot", tag -> {
            tag.setInteger("id", id);
            tag.setBoolean("clear", clear);
        });
    }

    static void addAdmin(String name) {
        send("admin_add", tag -> tag.setString("name", name));
    }

    static void removeAdmin(String name) {
        send("admin_remove", tag -> tag.setString("name", name));
    }

    static void voice(boolean join) {
        send("voice", tag -> tag.setBoolean("join", join));
    }

    private static void send(String action, Consumer<NBTTagCompound> body) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("a", action);
        body.accept(tag);
        Network.sendAction(tag);
    }
}
