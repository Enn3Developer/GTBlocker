package com.enn3developer.gtblocker.lock;

/**
 * The two live lock tables: the server's authoritative one and the client's mirror of it. They are kept apart even
 * in singleplayer, where both live in one JVM, so a sync packet still in flight can never roll the integrated
 * server back to an older snapshot.
 * <p>
 * Hooks that can run on either side ({@code IRecipe.matches}) pick a table with {@link #current()}: the client main
 * thread reads the mirror, every other thread (server thread, AE2's crafting pool, NEI workers) reads the server
 * table. On a dedicated server the client thread is never set; on a client connected to a remote server the server
 * table stays empty, so worker threads there simply see no locks.
 */
public final class Locks {

    private static volatile LockTable server = LockTable.EMPTY;
    private static volatile LockTable client = LockTable.EMPTY;
    private static volatile Thread clientThread;

    private Locks() {}

    public static LockTable server() {
        return server;
    }

    public static LockTable client() {
        return client;
    }

    public static LockTable current() {
        return Thread.currentThread() == clientThread ? client : server;
    }

    public static boolean isClientThread() {
        return Thread.currentThread() == clientThread;
    }

    public static void setServer(LockTable table) {
        server = table;
    }

    public static void setClient(LockTable table) {
        client = table;
    }

    public static void setClientThread(Thread thread) {
        clientThread = thread;
    }
}
