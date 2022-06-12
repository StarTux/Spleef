package com.winthier.spleef;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
        rootNode.addChild("floorremovaltime").arguments("<seconds>")
            .description("Set floor removal timer")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::floorRemovalTime);
        CommandNode scoreNode = rootNode.addChild("score")
            .description("Score commands");
        scoreNode.addChild("add")
            .description("Manipulate score")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i != 0))
            .senderCaller(this::scoreAdd);
        scoreNode.addChild("clear").denyTabCompletion()
            .description("Clear all scores")
            .senderCaller(this::scoreClear);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Reward players")
            .senderCaller(this::scoreReward);
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
            sender.sendMessage(text("Stopping game: " + game.getWorldName()));
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
                               ? text("Event mode enabled", GREEN)
                               : text("Event mode disabled", RED));
            return true;
        }
        sender.sendMessage(text("Event mode: " + plugin.save.event, YELLOW));
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
        sender.sendMessage(text("Sudden death time: " + plugin.save.suddenDeathTime, YELLOW));
        return true;
    }

    protected boolean floorRemovalTime(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            try {
                plugin.save.floorRemovalTime = Long.parseLong(args[0]);
                plugin.save();
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid value: " + args[0]);
            }
        }
        sender.sendMessage(text("Floor removal time: " + plugin.save.floorRemovalTime, YELLOW));
        return true;
    }

    private boolean scoreClear(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.save.scores.clear();
        plugin.computeHighscore();
        sender.sendMessage(text("All scores cleared", AQUA));
        return true;
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> i != 0);
        plugin.save.addScore(target.uuid, value);
        plugin.computeHighscore();
        sender.sendMessage(text("Score of " + target.name + " manipulated by " + value, AQUA));
        return true;
    }

    private void scoreReward(CommandSender sender) {
        int count = Highscore.reward(plugin.save.scores,
                                     "spleef",
                                     TrophyCategory.SPLEEF,
                                     plugin.TITLE,
                                     hi -> ("You broke "
                                            + hi.score + " block" + (hi.score == 1 ? "" : "s")
                                            + " while playing Spleef"));
        sender.sendMessage(text("Rewarded " + count + " players", AQUA));
    }
}
