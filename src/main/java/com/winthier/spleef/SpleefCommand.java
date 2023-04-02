package com.winthier.spleef;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import java.util.List;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class SpleefCommand extends AbstractCommand<SpleefPlugin> {
    protected SpleefCommand(final SpleefPlugin plugin) {
        super(plugin, "spleef");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("vote").arguments("[path]")
            .completers(CommandArgCompleter.supplyList(() -> List.copyOf(plugin.spleefMaps.maps.keySet())))
            .description("Vote on a map")
            .hidden(true)
            .playerCaller(this::vote);
    }

    private boolean vote(Player player, String[] args) {
        if (args.length == 0) {
            if (!plugin.spleefMaps.isVoteActive()) throw new CommandWarn("The vote is over");
            plugin.spleefMaps.openVoteBook(player);
            return true;
        } else if (args.length == 1) {
            if (!plugin.spleefMaps.isVoteActive()) throw new CommandWarn("The vote is over");
            SpleefMap spleefMap = plugin.spleefMaps.maps.get(args[0]);
            if (spleefMap == null) throw new CommandWarn("Map not found!");
            plugin.spleefMaps.vote(player.getUniqueId(), spleefMap);
            player.sendMessage(text("You voted for " + spleefMap.getDisplayName(), GREEN));
            return true;
        } else {
            return false;
        }
    }
}
