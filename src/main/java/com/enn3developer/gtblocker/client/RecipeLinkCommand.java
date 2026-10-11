package com.enn3developer.gtblocker.client;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;

import com.enn3developer.gtblocker.client.nei.NeiCompat;
import com.enn3developer.gtblocker.lock.LockKey;
import com.enn3developer.gtblocker.text.Chat;

import cpw.mods.fml.common.Loader;

/** Client-side target of the item links in chat: shows the item's recipes in NEI. */
public final class RecipeLinkCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return Chat.RECIPE_COMMAND;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + Chat.RECIPE_COMMAND + " <modid:name:meta>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !Loader.isModLoaded("NotEnoughItems")) {
            return;
        }
        LockKey key = LockKey.parse(args[0]);
        ItemStack stack = key == null ? null : key.displayStack();
        if (stack != null) {
            NeiCompat.showRecipes(stack);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }
}
