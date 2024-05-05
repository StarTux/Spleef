package com.winthier.spleef;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class SpleefAdminCommand extends AbstractCommand<SpleefPlugin> {
    protected SpleefAdminCommand(final SpleefPlugin plugin) {
        super(plugin, "spleefadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start").arguments("<world>")
            .description("Start a game")
            .completers(CommandArgCompleter.supplyList(this::listSpleefWorldPaths))
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop games")
            .senderCaller(this::stop);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event state")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::event);
        rootNode.addChild("pause").arguments("true|false")
            .description("Set pause state")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::pause);
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

    public List<String> listSpleefWorldPaths() {
        List<String> result = new ArrayList<>();
        for (BuildWorld it : BuildWorld.findMinigameWorlds(plugin.MINIGAME_TYPE, false)) {
            result.add(it.getPath());
        }
        return result;
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
        final BuildWorld buildWorld = BuildWorld.findWithPath(worldName);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Not a spleef world: " + worldName);
        }
        sender.sendMessage(text("Starting game: " + buildWorld.getName(), YELLOW));
        final boolean finalDebug = debug;
        buildWorld.makeLocalCopyAsync(world -> {
                SpleefGame game = plugin.startGame(world, buildWorld);
                List<Player> playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
                Collections.shuffle(playerList);
                for (Player player : playerList) {
                    if (player.hasPermission("group.streamer") && player.isPermissionSet("group.streamer")) {
                        game.addSpectator(player);
                    } else {
                        game.addPlayer(player);
                    }
                }
                if (finalDebug) game.setDebug(true);
                game.changeState(State.COUNTDOWN);
            });
        return true;
    }

    protected boolean stop(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        if (plugin.spleefGameList.isEmpty()) {
            throw new CommandWarn("No games are running!");
        }
        for (SpleefGame game : List.copyOf(plugin.spleefGameList)) {
            sender.sendMessage(text("Stopping game: " + game.getWorld().getName()));
            game.stop();
        }
        return true;
    }

    protected boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.save.event = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
            return true;
        }
        sender.sendMessage(text("Event mode: " + plugin.save.event, YELLOW));
        return true;
    }

    protected boolean pause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.save.pause = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
            return true;
        }
        sender.sendMessage(text("Pause mode: " + plugin.save.pause, YELLOW));
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
                                     hi -> ("You scored "
                                            + hi.score + " point" + (hi.score == 1 ? "" : "s")
                                            + " while playing Spleef"));
        sender.sendMessage(text("Rewarded " + count + " players", AQUA));
    }
}
