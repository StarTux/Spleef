package com.winthier.spleef;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitTask;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Store all available worlds and manage votes.
 */
@RequiredArgsConstructor
public final class SpleefMaps {
    private final SpleefPlugin plugin;
    protected final Map<String, SpleefMap> maps = new HashMap<>();
    @Getter private boolean voteActive;
    protected BukkitTask task;
    protected Map<UUID, String> votes = new HashMap<>();
    protected final int maxTicks = 20 * 60;
    protected int ticksLeft;
    private final Random random = new Random();

    public void startVote() {
        if (maps.isEmpty()) {
            load(plugin.getWorlds());
        }
        voteActive = true;
        votes.clear();
        ticksLeft = maxTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            remindToVote(player);
            openVoteBook(player);
        }
    }

    public void stopVote() {
        voteActive = false;
        task.cancel();
        task = null;
    }

    private void tick() {
        final int ticks = ticksLeft--;
        if (ticks <= 0) {
            finishVote();
        }
    }

    public float voteProgress() {
        return 1.0f - (float) ticksLeft / (float) maxTicks;
    }

    public void vote(UUID uuid, SpleefMap map) {
        votes.put(uuid, map.getPath());
    }

    public void finishVote() {
        stopVote();
        final Map<String, Integer> stats = new HashMap<>();
        final List<SpleefMap> randomMaps = new ArrayList<>();
        for (String it : votes.values()) {
            stats.compute(it, (s, i) -> i != null ? i + 1 : 1);
            SpleefMap spleefMap = maps.get(it);
            if (spleefMap != null) randomMaps.add(spleefMap);
        }
        plugin.getLogger().info("Votes: " + stats);
        if (randomMaps.isEmpty()) randomMaps.addAll(maps.values());
        SpleefMap spleefMap = randomMaps.get(random.nextInt(randomMaps.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(textOfChildren(text("Map ", GRAY), text(spleefMap.getDisplayName(), GREEN)));
            player.sendMessage(textOfChildren(text("By ", GRAY), text(spleefMap.getDescription(), GREEN)));
            player.sendMessage("");
        }
        maps.remove(spleefMap.getPath());
        plugin.startGameWithAllPlayers(spleefMap.getPath());
    }

    protected void load(List<String> worldNames) {
        final File creativeFile = new File("/home/cavetale/creative/plugins/Creative/worlds.yml");
        final ConfigurationSection creativeConfig = creativeFile.exists()
            ? YamlConfiguration.loadConfiguration(creativeFile)
            : new YamlConfiguration();
        maps.clear();
        for (Map<?, ?> map : creativeConfig.getMapList("worlds")) {
            ConfigurationSection worldConfig = creativeConfig.createSection("_tmp", map);
            String path = worldConfig.getString("path");
            if (!worldNames.contains(path)) continue;
            SpleefMap spleefMap = new SpleefMap();
            spleefMap.setPath(path);
            spleefMap.setDisplayName(worldConfig.getString("name"));
            String uuidString = worldConfig.getString("owner.uuid");
            if (uuidString != null) {
                UUID uuid = UUID.fromString(uuidString);
                spleefMap.setDescription(PlayerCache.nameForUuid(uuid));
            }
            maps.put(path, spleefMap);
        }
        plugin.getLogger().info(maps.size() + " worlds loaded");
    }

    public void remindToVote(Player player) {
        if (!voteActive) return;
        if (!player.hasPermission("spleef.spleef")) return;
        player.sendMessage(textOfChildren(newline(),
                                          Mytems.ARROW_RIGHT,
                                          (text(" Click here to vote on the next map", GREEN)
                                           .hoverEvent(showText(text("Map Selection", GRAY)))
                                           .clickEvent(runCommand("/spleef vote"))),
                                          newline()));
    }

    public void openVoteBook(Player player) {
        List<SpleefMap> spleefMaps = new ArrayList<>();
        spleefMaps.addAll(maps.values());
        Collections.sort(spleefMaps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getDisplayName(), b.getDisplayName()));
        List<Component> lines = new ArrayList<>();
        for (SpleefMap spleefMap : spleefMaps) {
            List<Component> tooltip = new ArrayList<>();
            Component displayName = text(spleefMap.getDisplayName(), BLUE);
            tooltip.add(displayName);
            tooltip.addAll(Text.wrapLore(spleefMap.getDescription(), c -> c.color(GRAY)));
            lines.add(displayName
                      .hoverEvent(showText(join(separator(newline()), tooltip)))
                      .clickEvent(runCommand("/spleef vote " + spleefMap.getPath())));
        }
        bookLines(player, lines);
    }

    private static List<Component> toPages(List<Component> lines) {
        final int lineCount = lines.size();
        final int linesPerPage = 10;
        List<Component> pages = new ArrayList<>((lineCount - 1) / linesPerPage + 1);
        for (int i = 0; i < lineCount; i += linesPerPage) {
            List<Component> page = new ArrayList<>(14);
            page.add(textOfChildren(SpleefPlugin.TITLE, text(" Worlds")));
            page.add(empty());
            page.addAll(lines.subList(i, Math.min(lines.size(), i + linesPerPage)));
            pages.add(join(separator(newline()), page));
        }
        return pages;
    }

    private static void bookLines(Player player, List<Component> lines) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(m -> {
                if (m instanceof BookMeta meta) {
                    meta.author(text("Cavetale"));
                    meta.title(text("Title"));
                    meta.pages(toPages(lines));
                }
            });
        player.closeInventory();
        player.openBook(book);
    }
}
