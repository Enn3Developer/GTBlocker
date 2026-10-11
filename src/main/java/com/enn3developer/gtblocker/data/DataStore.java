package com.enn3developer.gtblocker.data;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.GTBlocker;
import com.enn3developer.gtblocker.lock.LockKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Reads and writes {@code <world>/gtblocker.json}. The file lives in the world save rather than {@code config/}
 * because GTNH updates replace {@code config/}. It is plain, hand-editable JSON on purpose: with the server
 * stopped, editing it is the recovery path if the admin list ever ends up wrong.
 * <p>
 * Built with Gson's tree API by hand: Minecraft ships Gson 2.2.4, which predates record support.
 */
public final class DataStore {

    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private final Path file;

    public DataStore(File worldDirectory) {
        this.file = worldDirectory.toPath()
            .resolve("gtblocker.json");
    }

    public boolean exists() {
        return Files.exists(file);
    }

    public Path path() {
        return file;
    }

    /** Loads the state, or returns an empty one if the file doesn't exist yet. Throws if it exists but is broken. */
    public BlockerState load() throws IOException {
        BlockerState state = new BlockerState();
        if (!Files.exists(file)) {
            return state;
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Malformed " + file, e);
        }

        state.nextPartyId = intOr(root, "nextPartyId", 1);
        state.importedServerStats = root.has("importedServerStats") && root.get("importedServerStats")
            .getAsBoolean();
        for (JsonElement element : arrayOr(root, "locks")) {
            LockKey key = LockKey.parse(element.getAsString());
            if (key != null) {
                state.locks.add(key);
            } else {
                GTBlocker.LOG.warn("Ignoring unreadable lock entry {}", element);
            }
        }
        for (JsonElement element : arrayOr(root, "admins")) {
            JsonObject admin = element.getAsJsonObject();
            state.admins.put(
                UUID.fromString(
                    admin.get("uuid")
                        .getAsString()),
                stringOr(admin, "name", "?"));
        }
        for (JsonElement element : arrayOr(root, "parties")) {
            Party party = readParty(element.getAsJsonObject());
            if (party != null) {
                state.parties.add(party);
            }
        }
        for (JsonElement element : arrayOr(root, "history")) {
            HistoryEntry entry = readHistory(element.getAsJsonObject());
            if (entry != null) {
                state.history.add(entry);
            }
        }
        if (root.has("lastSeen")) {
            for (Map.Entry<String, JsonElement> seen : root.getAsJsonObject("lastSeen")
                .entrySet()) {
                state.lastSeen.put(
                    UUID.fromString(seen.getKey()),
                    seen.getValue()
                        .getAsLong());
            }
        }
        return state;
    }

    /** Writes atomically (temp file + move), so a crash mid-write never leaves a truncated file behind. */
    public void save(BlockerState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("nextPartyId", state.nextPartyId);
        root.addProperty("importedServerStats", state.importedServerStats);

        JsonArray locks = new JsonArray();
        state.locks.forEach(key -> locks.add(new JsonPrimitive(key.serialize())));
        root.add("locks", locks);

        JsonArray admins = new JsonArray();
        state.admins.forEach((uuid, name) -> {
            JsonObject admin = new JsonObject();
            admin.addProperty("uuid", uuid.toString());
            admin.addProperty("name", name);
            admins.add(admin);
        });
        root.add("admins", admins);

        JsonArray parties = new JsonArray();
        state.parties.forEach(party -> parties.add(writeParty(party)));
        root.add("parties", parties);

        JsonArray history = new JsonArray();
        state.history.forEach(entry -> history.add(writeHistory(entry)));
        root.add("history", history);

        JsonObject lastSeen = new JsonObject();
        state.lastSeen.forEach((uuid, time) -> lastSeen.addProperty(uuid.toString(), time));
        root.add("lastSeen", lastSeen);

        Path temp = file.resolveSibling("gtblocker.json.tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static @Nullable Party readParty(JsonObject json) {
        List<LockKey> items = new ArrayList<>();
        for (JsonElement element : arrayOr(json, "items")) {
            LockKey key = LockKey.parse(element.getAsString());
            if (key != null) {
                items.add(key);
            }
        }
        if (items.isEmpty() || !json.has("id") || !json.has("revealAt")) {
            GTBlocker.LOG.warn("Ignoring unreadable party entry {}", json);
            return null;
        }
        Spot spot = null;
        if (json.has("spot")) {
            JsonObject s = json.getAsJsonObject("spot");
            spot = new Spot(intOr(s, "dim", 0), intOr(s, "x", 0), intOr(s, "y", 64), intOr(s, "z", 0));
        }
        return new Party(
            json.get("id")
                .getAsInt(),
            List.copyOf(items),
            json.get("revealAt")
                .getAsLong(),
            stringOr(json, "message", ""),
            spot,
            stringOr(json, "createdBy", "?"));
    }

    private static JsonObject writeParty(Party party) {
        JsonObject json = new JsonObject();
        json.addProperty("id", party.id());
        JsonArray items = new JsonArray();
        party.items()
            .forEach(key -> items.add(new JsonPrimitive(key.serialize())));
        json.add("items", items);
        json.addProperty("revealAt", party.revealAt());
        json.addProperty("message", party.message());
        if (party.spot() != null) {
            JsonObject spot = new JsonObject();
            spot.addProperty("dim", party.spot()
                .dim());
            spot.addProperty("x", party.spot()
                .x());
            spot.addProperty("y", party.spot()
                .y());
            spot.addProperty("z", party.spot()
                .z());
            json.add("spot", spot);
        }
        json.addProperty("createdBy", party.createdBy());
        return json;
    }

    private static @Nullable HistoryEntry readHistory(JsonObject json) {
        LockKey key = LockKey.parse(stringOr(json, "item", null));
        if (key == null || !json.has("unlockedAt")) {
            return null;
        }
        return new HistoryEntry(
            key,
            json.get("unlockedAt")
                .getAsLong(),
            stringOr(json, "unlockedBy", "?"),
            intOr(json, "party", 0),
            stringOr(json, "firstCraftBy", null),
            json.has("firstCraftAt") ? json.get("firstCraftAt")
                .getAsLong() : 0L);
    }

    private static JsonObject writeHistory(HistoryEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("item", entry.key()
            .serialize());
        json.addProperty("unlockedAt", entry.unlockedAt());
        json.addProperty("unlockedBy", entry.unlockedBy());
        json.addProperty("party", entry.partyId());
        if (entry.firstCraftBy() != null) {
            json.addProperty("firstCraftBy", entry.firstCraftBy());
            json.addProperty("firstCraftAt", entry.firstCraftAt());
        }
        return json;
    }

    private static JsonArray arrayOr(JsonObject json, String name) {
        return json.has(name) ? json.getAsJsonArray(name) : new JsonArray();
    }

    private static int intOr(JsonObject json, String name, int fallback) {
        return json.has(name) ? json.get(name)
            .getAsInt() : fallback;
    }

    private static String stringOr(JsonObject json, String name, String fallback) {
        return json.has(name) && !json.get(name)
            .isJsonNull() ? json.get(name)
                .getAsString() : fallback;
    }
}
