package com.enn3developer.gtblocker.server;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.data.BlockerState;
import com.enn3developer.gtblocker.lock.LockKey;

import cpw.mods.fml.common.Loader;

/**
 * One-time import of the ServerStatistics draft's {@code blocking.blockedItems} list, so switching mods doesn't
 * silently unlock anything. Runs once per world; the flag stops it from resurrecting locks admins removed later.
 */
final class ServerStatsImport {

    private ServerStatsImport() {}

    static void importOnce(BlockerState state) {
        warnIfDraftStillInstalled();
        if (state.importedServerStats) {
            return;
        }
        state.importedServerStats = true;
        File file = new File(Loader.instance().getConfigDir(), "serverstats.cfg");
        if (!file.isFile()) {
            return;
        }
        // Read-only: never saved back, so the ServerStatistics file is left exactly as it was.
        String[] entries = new Configuration(file).get("blocking", "blockedItems", new String[0])
            .getStringList();
        int imported = 0;
        for (String entry : entries) {
            LockKey key = LockKey.parse(entry);
            if (key != null && state.locks.add(key)) {
                imported++;
            }
        }
        GTBlocker.LOG.info("Imported {} locks from {}", imported, file);
    }

    private static void warnIfDraftStillInstalled() {
        try {
            Class.forName("com.enn3developer.serverstats.CraftBlocker", false, ServerStatsImport.class.getClassLoader());
            GTBlocker.LOG.warn(
                "ServerStatistics still ships its own craft blocker: it removes recipes behind GTBlocker's back and "
                    + "unlocking here will not bring them back. Remove CraftBlocker from ServerStatistics.");
        } catch (ClassNotFoundException absent) {
            // Expected: the draft blocker is gone.
        }
    }
}
