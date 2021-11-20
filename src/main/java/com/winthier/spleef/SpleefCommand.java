package com.winthier.spleef;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpleefCommand extends AbstractCommand<SpleefPlugin> {
    protected SpleefCommand(final SpleefPlugin plugin) {
        super(plugin, "spleef");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start").arguments("<world>")
            .description("Start a game")
            .completers(CommandArgCompleter.supplyList(() -> plugin.worlds))
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop games")
            .senderCaller(this::stop);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event state")
            .completers(CommandArgCompleter.list(List.of("true", "false")))
            .senderCaller(this::event);
        rootNode.addChild("suddendeathtime").arguments("<seconds>")
            .description("Set sudden death timer")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::suddenDeathTime);
    }

    protected boolean start(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        String worldName = args[0];
        boolean debug = false;
        for (int i = 1; i < args.length; i += 1) {
            String arg = args[i];
            switch (arg) {
            case "debug": debug = true; break;
            default: throw new CommandWarn("Invalid flag: " + arg);
            }
        }
        sender.sendMessage("Starting game in " + worldName + "...");
        SpleefGame game = plugin.startGame(worldName);
        List<Player> playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(playerList);
        for (Player player : playerList) {
            if (player.hasPermission("group.streamer") && player.isPermissionSet("group.streamer")) {
                game.addSpectator(player);
            } else {
                game.addPlayer(player);
            }
        }
        if (debug) game.setDebug(true);
        game.changeState(State.COUNTDOWN);
        return true;
    }

    protected boolean stop(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (plugin.spleefGameList.isEmpty()) {
            throw new CommandWarn("No games are running!");
        }
        for (SpleefGame game : List.copyOf(plugin.spleefGameList)) {
            sender.sendMessage(Component.text("Stopping game: " + game.getWorldName()));
            game.stop();
        }
        return true;
    }

    protected boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            try {
                plugin.save.event = Boolean.parseBoolean(args[0]);
                plugin.save();
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid value: " + args[0]);
            }
            sender.sendMessage(plugin.save.event
                               ? Component.text("Event mode enabled", NamedTextColor.GREEN)
                               : Component.text("Event mode disabled", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Event mode: " + plugin.save.event, NamedTextColor.YELLOW));
        return true;
    }

    protected boolean suddenDeathTime(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            try {
                plugin.save.suddenDeathTime = Long.parseLong(args[0]);
                plugin.save();
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid value: " + args[0]);
            }
        }
        sender.sendMessage(Component.text("Sudden death time: " + plugin.save.suddenDeathTime, NamedTextColor.YELLOW));
        return true;
    }
}
