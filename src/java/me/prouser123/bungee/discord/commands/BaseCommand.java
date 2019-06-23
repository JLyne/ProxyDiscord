package me.prouser123.bungee.discord.commands;

import java.util.List;

import net.md_5.bungee.api.plugin.Command;

abstract class BaseCommand extends Command {
    private static String[] stringListToArray(List<String> list) {
        return list.toArray(new String[0]);
    }

    BaseCommand(String name) {
        this(name, "");
    }

    private BaseCommand(String name, String permission) {
        this(name, permission, new String[0]);
    }

// --Commented out by Inspection START (23/06/2019 17:18):
//    public BaseCommand(String name, List<String> aliases) {
//        this(name, "", aliases);
//    }
// --Commented out by Inspection STOP (23/06/2019 17:18)

// --Commented out by Inspection START (23/06/2019 17:19):
//    public BaseCommand(String name, String[] aliases) {
//        this(name, "", aliases);
//    }
// --Commented out by Inspection STOP (23/06/2019 17:19)

// --Commented out by Inspection START (23/06/2019 17:20):
//    private BaseCommand(String name, String permission, List<String> aliases) {
//        this(name, permission, stringListToArray(aliases));
//    }
// --Commented out by Inspection STOP (23/06/2019 17:20)

    private BaseCommand(String name, String permission, String[] aliases) {
        super(name, permission, aliases);
    }
}