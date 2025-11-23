package com.winthier.spleef;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.player.PlayerTPAEvent;
import com.winthier.spawn.Spawn;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    protected final SpleefPlugin plugin;

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.spleefGameList.isEmpty()) {
            event.setSpawnLocation(Spawn.get());
            return;
        }
        event.setSpawnLocation(plugin.spleefGameList.get(0).getSpawnLocation(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerJoin(event));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerQuit(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerMove(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onBlockBreak(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onBlockPlace(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onBlockDamage(event));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        plugin.applyGame(event.getEntity().getWorld(), game -> game.onEntityDamage(event));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.applyGame(event.getEntity().getWorld(), game -> game.onPlayerDeath(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerToggleFlight(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        plugin.applyGame(event.getEntity().getWorld(), game -> game.onFoodLevelChange(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerDropItem(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        plugin.applyGame(event.getEntity().getWorld(), game -> game.onEntityExplode(event));
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        plugin.applyGame(event.getEntity().getWorld(), game -> game.onCreatureSpawn(event));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerInteract(event));
    }

    @EventHandler
    public void onPlayerHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.TITLE);
        plugin.applyGame(event.getPlayer().getWorld(), game -> game.onPlayerSidebar(event.getPlayer(), lines));
        final Player player = event.getPlayer();
        if (plugin.save.event && player.getWorld().equals(plugin.getLobbyWorld())) {
            lines.addAll(plugin.highscoreLines);
        }
        if (!lines.isEmpty()) event.sidebar(PlayerHudPriority.HIGHEST, lines);
    }

    @EventHandler
    private void onPlayerTPA(PlayerTPAEvent event) {
        // Cancel any and all requests for now.
        event.setCancelled(true);
    }
}
