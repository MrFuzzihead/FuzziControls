package com.mrfuzzihead.fuzzicontrols.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;

public class CommandReloadControllers extends CommandBase {

    @Override
    public String getCommandName() {
        return "fuzzicontrols";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + getCommandName() + " reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Client-side command — no permission level required.
        return -1;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentTranslation("Usage: /fuzzicontrols reload"));
            return;
        }

        ControllerManager manager = ControllerManager.getInstance();
        manager.init();
        boolean active = manager.isActive();

        if (active) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    EnumChatFormatting.GREEN + "Controllers reloaded. Active driver: %s",
                    manager.getActiveDriverName()));
        } else {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    EnumChatFormatting.YELLOW + "Controllers reloaded. No controller detected."));
        }
    }
}
