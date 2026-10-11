package com.enn3developer.gtblocker.compat.voice;

import java.util.ArrayList;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.IGroupManager;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * Flat, distance-free voice for everyone at the party. Not exclusive: players standing next to a member still hear
 * them through local proximity, the party only adds the far-away listeners.
 */
final class GtnhVoiceParty implements IGroup, PartyVoice {

    private final IGroupManager manager;

    private GtnhVoiceParty(IGroupManager manager) {
        this.manager = manager;
    }

    static GtnhVoiceParty register() {
        IGroupManager manager = GtnhVoiceApi.groupManager();
        GtnhVoiceParty group = new GtnhVoiceParty(manager);
        manager.registerGroup(group);
        return group;
    }

    @Override
    public @NotNull String getName() {
        return "gtblocker_party";
    }

    @Override
    public int priority() {
        // Above default addon groups (0), so party members hear each other flat even if also in another group.
        return 1;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Unlock Party";
    }

    @Override
    public void route(@NotNull RoutingContext context) {
        context.getSessionsForGroup()
            .excludeSelf()
            .excludeNoAddress()
            .send(SourceState.FLAT);
    }

    @Override
    public void join(UUID player) {
        manager.join(player, this);
    }

    @Override
    public void leave(UUID player) {
        manager.leave(player, this);
    }

    @Override
    public void leaveAll() {
        for (UUID member : new ArrayList<>(manager.membersOf(this))) {
            manager.leave(member, this);
        }
    }
}
