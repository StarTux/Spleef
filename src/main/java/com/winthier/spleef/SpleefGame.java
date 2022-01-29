package com.winthier.spleef;

import com.cavetale.core.font.Unicode;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.WardrobeItem;
import com.winthier.spawn.Spawn;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

@Getter @RequiredArgsConstructor
public final class SpleefGame {
    static final long TIME_BEFORE_SPLEEF = 5;
    protected final SpleefPlugin plugin;
    protected final World world;
    protected final String worldName;
    // Config
    @Setter protected boolean debug = false;
    protected boolean solo = false;
    protected int lives = 3;
    // World
    protected final Set<Material> spleefMats = EnumSet.noneOf(Material.class);
    protected final Set<Block> spleefBlocks = new HashSet<>();
    protected final List<BlockState> spleefBlockStates = new ArrayList<>();
    protected final List<Location> spawnLocations = new ArrayList<>();
    protected final List<String> credits = new ArrayList<>();
    protected final Map<Block, Integer> suddenDeathTicks = new HashMap<>();
    protected boolean suddenDeathActive = false;
    protected int floorRemoveCountdownTicks = 0;
    protected boolean spawnLocationsRandomized = false;
    protected int spawnLocationsIndex = 0;
    protected int deathLevel = 0;
    protected List<Integer> spleefLevels = new ArrayList<>();
    protected boolean allowBlockBreaking = false;
    protected int suddenDeathBlockTicks = 40;
    protected boolean creeperSpawning = true;
    protected int creeperCooldown = 5;
    protected double creeperChance = 0.125;
    protected double creeperPowerChance = 0.33;
    protected double creeperSpeedChance = 0.33;
    protected double creeperSpeedMultiplier = 1.0;
    protected boolean giveTNT = true;
    protected boolean giveCreeperEggs = true;
    protected boolean placeCreeperSnowBlocks = true;
    // State
    protected State state = State.INIT;
    protected long stateTicks = 0;
    protected boolean hasWinner = false;
    protected String winnerName = "";
    protected int round = 0;
    protected boolean roundShouldEnd = false;
    protected int roundShouldEndTicks = 0;
    protected int creeperTimer = 0;
    protected final Random random = new Random(System.currentTimeMillis());
    protected final Map<UUID, SpleefPlayer> spleefPlayers = new HashMap<>();
    protected long secondsLeft;
    protected BukkitTask task;

    protected void enable() {
        world.setPVP(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(999999999);
        world.setDifficulty(Difficulty.EASY);
        scanChunks();
        scanSpleefBlocks();
        copySpleefBlocks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    protected void disable() {
        task.cancel();
        for (Player player : getPresentPlayers()) {
            clearInventory(player);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            player.setGameMode(GameMode.ADVENTURE);
            Spawn.warp(player);
        }
        Worlds.deleteWorld(plugin, world);
        plugin.spleefGameList.remove(this);
    }

    protected void stop() {
        disable();
    }

    public void addPlayer(Player player) {
        SpleefPlayer sp = getSpleefPlayer(player);
        sp.setPlayer();
        sp.setSpawnLocation(dealSpawnLocation());
        sp.setLives(lives);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        makeImmobile(player);
    }

    public void addSpectator(Player player) {
        SpleefPlayer sp = getSpleefPlayer(player);
        sp.setSpectator();
        sp.setSpawnLocation(world.getSpawnLocation());
        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    public Location getSpawnLocation(Player player) {
        if (getSpleefPlayer(player).isPlayer()) {
            return getSpleefPlayer(player).getSpawnLocation();
        }
        return world.getSpawnLocation();
    }

    protected void scanChunks() {
        Chunk spawnChunk = world.getSpawnLocation().getChunk();
        final int radius = 5;
        for (int x = -radius; x <= radius; ++x) {
            for (int z = -radius; z <= radius; ++z) {
                scanChunk(spawnChunk.getX() + x, spawnChunk.getZ() + z);
            }
        }
        if (spleefLevels.isEmpty()) {
            throw new IllegalStateException("No chests, no spleef levels!");
        }
        Collections.sort(spleefLevels);
        deathLevel = spleefLevels.get(0);
    }

    protected void scanChunk(int x, int z) {
        Chunk chunk = world.getChunkAt(x, z);
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState instanceof Chest) scanChest((Chest) blockState);
            if (blockState instanceof Sign) scanSign((Sign) blockState);
        }
    }

    protected void scanChest(Chest chest) {
        Inventory inv = chest.getBlockInventory();
        String name = chest.getCustomName();
        if ("[spleef]".equalsIgnoreCase(name)) {
            info("Found spleef chest");
            spleefBlocks.add(chest.getBlock().getRelative(0, -1, 0));
            spleefLevels.add(chest.getBlock().getY() - 1);
            for (ItemStack item : inv.getContents()) {
                if (item == null) continue;
                if (item.getType() == Material.AIR) continue;
                spleefMats.add(item.getType());
                info("Spleef mat: " + item.getType());
            }
            inv.clear();
        } else {
            return;
        }
        chest.getBlock().setType(Material.AIR, false);
    }

    protected void scanSign(Sign sign) {
        String name = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase();
        if ("[spawn]".equals(name)) {
            Location location = sign.getBlock().getLocation();
            location = location.add(0.5, 0.5, 0.5);
            Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
            location = location.setDirection(lookAt);
            spawnLocations.add(location);
        } else if ("[credits]".equals(name)) {
            for (int i = 1; i < 4; ++i) {
                String credit = PlainTextComponentSerializer.plainText().serialize(sign.line(i));
                if (credit != null) credits.add(credit);
            }
        } else if ("[time]".equals(name)) {
            long time = 0;
            String arg = PlainTextComponentSerializer.plainText().serialize(sign.line(1)).toLowerCase();
            if ("day".equals(arg)) {
                time = 1000;
            } else if ("night".equals(arg)) {
                time = 13000;
            } else if ("noon".equals(arg)) {
                time = 6000;
            } else if ("midnight".equals(arg)) {
                time = 18000;
            } else {
                try {
                    time = Long.parseLong(PlainTextComponentSerializer.plainText().serialize(sign.line(1)));
                } catch (NumberFormatException nfe) { }
            }
            world.setTime(time);
            if ("lock".equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(sign.line(2)))) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
        } else {
            return;
        }
        sign.getBlock().setType(Material.AIR, false);
    }

    protected boolean isSpleefMat(Material mat) {
        return spleefMats.contains(mat);
    }

    protected boolean isSpleefBlock(Block block) {
        return spleefBlocks.contains(block);
    }

    protected void scanSpleefBlocks() {
        Set<Block> searchedBlocks = new HashSet<>();
        Queue<Block> blocksToSearch = new ArrayDeque<>();
        searchedBlocks.addAll(spleefBlocks);
        blocksToSearch.addAll(spleefBlocks);
        while (!blocksToSearch.isEmpty()) {
            Block block = blocksToSearch.remove();
            if (!isSpleefMat(block.getType())) continue;
            spleefBlocks.add(block);
            final BlockFace[] faces = {
                BlockFace.NORTH,
                BlockFace.EAST,
                BlockFace.SOUTH,
                BlockFace.WEST
            };
            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                if (!searchedBlocks.contains(neighbor)) {
                    searchedBlocks.add(neighbor);
                    blocksToSearch.add(neighbor);
                }
            }
        }
        info("Found " + spleefBlocks.size() + " spleef blocks");
    }

    protected void copySpleefBlocks() {
        for (Block block : spleefBlocks) {
            spleefBlockStates.add(block.getState());
        }
    }

    protected void restoreSpleefBlocks() {
        putFloorUnderSpleefBlocks();
        for (BlockState blockState : spleefBlockStates) {
            if (blockState.getBlock().getType() != blockState.getType()) {
                blockState.update(true, false);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeFloorUnderSpleefBlocks();
            }, 20L);
    }

    protected void removeFloorUnderSpleefBlocks() {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.AIR, false);
        }
    }

    protected void putFloorUnderSpleefBlocks() {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.BARRIER, false);
        }
    }

    /**
     * Get all the spleef blocks under the player's feet or on any
     * layer above, in order to break them past sudden death.
     */
    protected List<Block> spleefBlocksAtPlayer(Player player) {
        List<Block> result = new ArrayList<>();
        BoundingBox bb = player.getBoundingBox();
        Vector min = bb.getMin();
        Vector max = bb.getMax();
        if (min.getBlockY() < deathLevel) return result;
        int ay = spleefLevels.get(0);
        for (int i : spleefLevels) {
            if (i > ay && i <= min.getBlockY()) ay = i;
        }
        final int ax = min.getBlockX();
        final int az = min.getBlockZ();
        final int bx = max.getBlockX();
        final int bz = max.getBlockZ();
        for (Block block : spleefBlocks) {
            if (block.isEmpty()) continue;
            int y = block.getY();
            if (y < ay) continue;
            int x = block.getX();
            if (x < ax || x > bx) continue;
            int z = block.getZ();
            if (z < az || z > bz) continue;
            result.add(block);
        }
        return result;
    }

    protected Location dealSpawnLocation() {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        if (!spawnLocationsRandomized) {
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations);
        }
        if (spawnLocationsIndex >= spawnLocations.size()) spawnLocationsIndex = 0;
        return spawnLocations.get(spawnLocationsIndex++);
    }

    protected SpleefPlayer getSpleefPlayer(UUID uuid) {
        return spleefPlayers.computeIfAbsent(uuid, u -> new SpleefPlayer(this, u));
    }

    protected SpleefPlayer getSpleefPlayer(Player player) {
        SpleefPlayer result = getSpleefPlayer(player.getUniqueId());
        result.setName(player.getName());
        return result;
    }

    protected void makeImmobile(Player player) {
        if (!getSpleefPlayer(player).isPlayer()) return;
        Location location = getSpleefPlayer(player).getSpawnLocation();
        if (!player.getLocation().getWorld().equals(location.getWorld())
            || player.getLocation().distanceSquared(location) > 1.0) {
            player.teleport(location);
            info("Teleported " + player.getName() + " to their spawn location");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    protected void makeMobile(Player player) {
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    protected void showCredits(Player player) {
        if (credits == null || credits.isEmpty()) return;
        player.showTitle(Title.title(Component.empty(),
                                     Component.text("Built by " + String.join(" ", credits), NamedTextColor.GREEN)));
    }

    private void tick() {
        if (state != State.INIT && getPresentPlayers().isEmpty()) {
            stop();
            return;
        }
        if (state != State.INIT && spleefPlayers.values().isEmpty()) {
            stop();
            return;
        }
        long ticks = stateTicks++;
        State nextState = tickState(state, ticks);
        if (nextState != null && nextState != state) changeState(nextState);
        for (Entity e : world.getEntities()) {
            if (placeCreeperSnowBlocks && (e.getType() == EntityType.CREEPER || e.getType() == EntityType.SKELETON)) {
                Block b = e.getLocation().getBlock().getRelative(0, -1, 0);
                if (b.isEmpty() && spleefBlocks.contains(b)) {
                    b.setType(Material.SNOW_BLOCK, false);
                    suddenDeathTicks.put(b, suddenDeathBlockTicks - 1);
                }
                List<BlockFace> nbors = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
                for (BlockFace face: nbors) {
                    Block b2 = b.getRelative(face);
                    if (b2.isEmpty() && spleefBlocks.contains(b2)) {
                        b2.setType(Material.SNOW_BLOCK, false);
                        suddenDeathTicks.put(b, suddenDeathBlockTicks - 1);
                    }
                }
            }
        }
    }

    private State tickState(State theState, long ticks) {
        switch (theState) {
        case INIT: return tickInit(ticks);
        case COUNTDOWN: return tickCountdown(ticks);
        case SPLEEF: return tickSpleef(ticks);
        case END: return tickEnd(ticks);
        default: throw new IllegalStateException("State not handled: " + theState);
        }
    }

    public List<Player> getPresentPlayers() {
        return world.getPlayers();
    }

    public List<SpleefPlayer> getAlivePlayers() {
        List<SpleefPlayer> result = new ArrayList<>();
        for (SpleefPlayer it : spleefPlayers.values()) {
            if (it.getLives() > 0) result.add(it);
        }
        return result;
    }

    protected void changeState(State newState) {
        State oldState = this.state;
        this.state = newState;
        stateTicks = 0;
        for (Entity e: world.getEntities()) {
            switch (e.getType()) {
            case CREEPER:
            case PRIMED_TNT:
            case SKELETON:
                e.remove();
            default:
                break;
            }
        }
        switch (newState) {
        case COUNTDOWN:
            round += 1;
            restoreSpleefBlocks();
            for (SpleefPlayer sp : spleefPlayers.values()) {
                Player player = sp.getPlayer();
                if (player == null) continue;
                if (sp.getLives() > 0) {
                    sp.setPlayer();
                    sp.setPlayed(true);
                    clearInventory(player);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setFireTicks(0);
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                    makeImmobile(sp.getPlayer());
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        other.hidePlayer(plugin, player);
                        other.showPlayer(plugin, player);
                    }
                }
            }
            int playerCount = 0;
            for (Player player : getPresentPlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    playerCount += 1;
                }
            }
            if (playerCount < 2) {
                if (!debug) {
                    stop();
                    return;
                } else {
                    solo = true;
                }
            }
            break;
        case SPLEEF:
            allowBlockBreaking = false;
            for (SpleefPlayer sp : spleefPlayers.values()) {
                sp.setDied(false);
            }
            for (Player player : getPresentPlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    makeMobile(player);
                    if (!player.getInventory().contains(Material.NETHERITE_PICKAXE)) {
                        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
                        pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 9);
                        player.getInventory().addItem(pickaxe);
                    }
                    if (!player.getInventory().contains(Material.NETHERITE_SHOVEL)) {
                        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
                        shovel.addUnsafeEnchantment(Enchantment.DIG_SPEED, 9);
                        player.getInventory().addItem(shovel);
                    }
                    if (!player.getInventory().contains(Material.NETHERITE_AXE)) {
                        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
                        axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 9);
                        player.getInventory().addItem(axe);
                    }
                }
            }
            roundShouldEnd = false;
            roundShouldEndTicks = 0;
            suddenDeathTicks.clear();
            suddenDeathActive = false;
            creeperTimer = 0;
            break;
        case END:
            int survivorCount = 0;
            SpleefPlayer survivor = null;
            for (SpleefPlayer sp : spleefPlayers.values()) {
                if (sp.getBlocksBroken() > 0 && plugin.save.event) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + sp.getName());
                }
                if (sp.getLives() > 0) {
                    survivorCount += 1;
                    survivor = sp;
                }
            }
            if (survivorCount == 1) {
                hasWinner = true;
                winnerName = survivor.getName();
                survivor.setWinner(true);
                if (plugin.save.event) {
                    String cmd = "titles unlockset " + winnerName + " " + String.join(" ", plugin.WINNER_TITLES);
                    info("Dispatching command: " + cmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
            for (Player player : getPresentPlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    getSpleefPlayer(player).setSpectator();
                    player.setGameMode(GameMode.SPECTATOR);
                }
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1f);
            }
            break;
        default: break;
        }
    }

    protected State tickInit(long ticks) {
        if (ticks > state.seconds * 20) stop();
        return null;
    }

    protected State tickCountdown(long ticks) {
        int totalSeconds = round <= 1 ? 30 : 10;
        if (ticks > totalSeconds * 20) return State.SPLEEF;
        if (ticks % 20 == 0) {
            secondsLeft = totalSeconds - ticks / 20;
            if (secondsLeft == 0) {
                Component message = Component.text("RUN!", NamedTextColor.GREEN, TextDecoration.BOLD, TextDecoration.ITALIC);
                for (Player player : getPresentPlayers()) {
                    player.sendMessage(message);
                    player.showTitle(Title.title(Component.empty(), message));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 1f);
                }
            } else if (secondsLeft <= 10) {
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(Component.text(secondsLeft, NamedTextColor.GREEN),
                                                 Component.text("Round " + round, NamedTextColor.GREEN)));
                    if (secondsLeft >= 0L && secondsLeft <= 24L) {
                        player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) (24L - secondsLeft)));
                    }
                }
            } else if (secondsLeft == 20) {
                for (Player player : getPresentPlayers()) {
                    showCredits(player);
                }
            }
        }
        return null;
    }

    /**
     * Never ends!
     */
    protected State tickSpleef(long ticks) {
        if (!allowBlockBreaking && ticks > 20 * 5) {
            allowBlockBreaking = true;
            Component message = Component.text("SPLEEF!", NamedTextColor.GREEN, TextDecoration.BOLD, TextDecoration.ITALIC);
            for (Player player : getPresentPlayers()) {
                player.sendMessage(message);
                player.showTitle(Title.title(Component.empty(), message));
                player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
            }
        }
        int aliveCount = 0;
        for (SpleefPlayer sp : spleefPlayers.values()) {
            if (!sp.isPlayer()) continue;
            tickSpleefPlayer(sp);
            if (sp.isPlayer()) aliveCount += 1;
        }
        if (aliveCount == 0 || (!solo && aliveCount <= 1)) roundShouldEnd = true;
        if (roundShouldEnd && roundShouldEndTicks++ >= 20 * 3) {
            int survivorCount = 0;
            for (SpleefPlayer sp : spleefPlayers.values()) {
                if (sp.getLives() > 0) {
                    survivorCount += 1;
                }
            }
            if (!solo && survivorCount <= 1) return State.END;
            if (survivorCount <= 0) return State.END;
            return State.COUNTDOWN;
        }
        long seconds = (ticks - 1) / 20L + 1L;
        if (!suddenDeathActive) {
            secondsLeft = plugin.save.suddenDeathTime - seconds;
            if (secondsLeft <= 0) {
                suddenDeathActive = true;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(Component.empty(),
                                                 Component.text("Sudden Death", NamedTextColor.DARK_RED)));
                    player.sendMessage(Component.text("Sudden Death activated. Blocks will fade under your feet.",
                                                      NamedTextColor.DARK_RED));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                }
                floorRemoveCountdownTicks = 20 * (int) plugin.save.floorRemovalTime;
            }
        } else {
            for (Player player: getPresentPlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    for (Block block : spleefBlocksAtPlayer(player)) {
                        int blockTicks = suddenDeathTicks.compute(block, (b, i) -> i == null ? 1 : i + 1);
                        if (blockTicks == suddenDeathBlockTicks) {
                            block.setType(Material.AIR, false);
                        } else if (blockTicks == suddenDeathBlockTicks - 20) {
                            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                            loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
                            loc.getWorld().spawnParticle(Particle.BLOCK_DUST,
                                                         loc,
                                                         64,
                                                         .3f, .3f, .3f,
                                                         .01f,
                                                         block.getBlockData());
                            block.setType(Material.BARRIER, false);
                        }
                    }
                }
            }
            if (floorRemoveCountdownTicks > 0) {
                floorRemoveCountdownTicks -= 1;
                secondsLeft = (floorRemoveCountdownTicks - 1) / 20 + 1;
            } else if (floorRemoveCountdownTicks == 0) {
                floorRemoveCountdownTicks = 20 * (int) plugin.save.floorRemovalTime;
                Map<Integer, List<Block>> floorBlocks = new HashMap<>();
                int maxFloor = Integer.MIN_VALUE;
                for (Block block : spleefBlocks) {
                    if (block.isEmpty()) continue;
                    int y = block.getY();
                    floorBlocks.computeIfAbsent(y, yy -> new ArrayList<>()).add(block);
                    if (y > maxFloor) maxFloor = y;
                }
                if (floorBlocks.size() > 1) {
                    List<Block> floor = floorBlocks.get(maxFloor);
                    for (Block block : floor) {
                        block.setType(Material.AIR);
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(Title.title(Component.empty(),
                                                     Component.text("Layer Removed!", NamedTextColor.DARK_RED)));
                        player.sendMessage(Component.text("Layer Removed!", NamedTextColor.DARK_RED));
                        player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2f);
                    }
                    if (floorBlocks.size() == 2) {
                        floorRemoveCountdownTicks = -1;
                        secondsLeft = -1;
                    }
                }
            }
        }
        if (solo || (allowBlockBreaking && creeperSpawning)) {
            creeperTimer += 1;
            if (creeperTimer % 20 == 0 && creeperTimer >= creeperCooldown * 20 && random.nextDouble() < creeperChance) {
                creeperTimer = 0;
                List<Block> blockList = new ArrayList<>();
                for (Block b: spleefBlocks) {
                    if (b.getType() != Material.AIR) {
                        blockList.add(b);
                    }
                }
                if (!blockList.isEmpty()) {
                    if (random.nextDouble() < 0.15) {
                        Block skellyBlock = blockList.get(random.nextInt(blockList.size()));
                        Skeleton skeleton = skellyBlock.getWorld().spawn(skellyBlock.getLocation().add(0.5, 1.0, 0.5), Skeleton.class, s -> {
                                s.setPersistent(false);
                                s.setShouldBurnInDay(false);
                            });
                    } else {
                        Block creeperBlock = blockList.get(random.nextInt(blockList.size()));
                        Creeper creeper = creeperBlock.getWorld().spawn(creeperBlock.getLocation().add(0.5, 1.0, 0.5), Creeper.class);
                        if (creeper != null) {
                            creeper.setPersistent(false);
                            if (random.nextDouble() < creeperPowerChance) {
                                creeper.setPowered(true);
                            }
                            if (random.nextDouble() < creeperSpeedChance) {
                                creeper.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
                                    .addModifier(new AttributeModifier("Spleef", (float) creeperSpeedMultiplier,
                                                                       AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    protected void tickSpleefPlayer(SpleefPlayer sp) {
        Player player = sp.getPlayer();
        if (player.getLocation().getBlockY() < deathLevel) {
            player.damage(4.0);
        }
    }

    protected State tickEnd(long ticks) {
        if (ticks > state.seconds * 20) {
            stop();
            return null;
        }
        if (ticks % (20 * 5) == 0) {
            if (hasWinner) {
                info(winnerName + " wins the game");
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(Component.text(winnerName, NamedTextColor.GREEN),
                                                 Component.text("Wins the Game!", NamedTextColor.GREEN)));
                    player.sendMessage(Component.text(winnerName + " wins the game!", NamedTextColor.GREEN));
                }
            } else {
                info("Draw! Nobody wins the game");
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(Component.text("Draw", NamedTextColor.RED),
                                                 Component.text("Nobody wins!", NamedTextColor.RED)));
                    player.sendMessage(Component.text("Draw! Nobody wins.", NamedTextColor.RED));
                }
            }
        }
        return null;
    }

    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    public void onPlayerQuit(PlayerQuitEvent event) {
        spleefPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        switch (state) {
        case INIT:
        case COUNTDOWN:
            SpleefPlayer sp = getSpleefPlayer(player);
            if (sp.getLives() > 0 && event.getTo().distanceSquared(sp.getSpawnLocation()) >= 1.0) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> makeImmobile(player));
            }
            break;
        default: break;
        }
    }

    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
        if (state != State.SPLEEF || !allowBlockBreaking) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.TNT) {
            Block block = event.getBlock();
            Location loc = block.getLocation().add(0.5, 0.0, 0.5);
            TNTPrimed tnt = block.getWorld().spawn(loc, TNTPrimed.class);
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(item);
                } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(item);
                }
            } else {
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }

    public void onBlockDamage(BlockDamageEvent event) {
        event.setCancelled(true);
        if (state != State.SPLEEF || !allowBlockBreaking) return;
        Player player = event.getPlayer();
        SpleefPlayer sp = getSpleefPlayer(player);
        if (!sp.isPlayer()) return;
        if (player.getLocation().getBlockY() < deathLevel) return;
        Block block = event.getBlock();
        if (!isSpleefBlock(block) || block.isEmpty()) return;
        world.spawnParticle(Particle.BLOCK_DUST,
                            block.getLocation().add(0.5, 0.5, 0.5),
                            64,
                            .3f, .3f, .3f,
                            .01f,
                            block.getBlockData());
        block.setType(Material.AIR, false);
        sp.addBlockBroken();
        int broken = sp.getBlocksBroken();
        if (giveTNT && broken > 0 && broken % 100 == 0) {
            ItemStack item = new ItemStack(Material.TNT);
            event.getPlayer().getInventory().addItem(item);
        }
        if (giveCreeperEggs && broken > 0 && broken % 200 == 0) {
            ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG);
            event.getPlayer().getInventory().addItem(item);
        }
    }

    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                event.setDamage(0);
            } else if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
                event.setDamage(0);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        Player player = (Player) event.getEntity();
        SpleefPlayer sp = getSpleefPlayer(player);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            player.teleport(world.getSpawnLocation());
            player.setHealth(0.0);
        }
        if (sp.isPlayer()) return;
        event.setCancelled(true);
    }

    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        SpleefPlayer sp = getSpleefPlayer(player);
        if (state != State.SPLEEF) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.teleport(sp.getSpawnLocation());
            return;
        }
        sp.setSpectator();
        sp.setDied(true);
        sp.setLives(sp.getLives() - 1);
        if (sp.getLives() <= 0) {
            sp.setSpectator();
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        event.setKeepInventory(true);
        event.getDrops().clear();
        info(player.getName() + " lost a life: " + sp.getLives());
        for (Player other : getPresentPlayers()) {
            if (sp.getLives() > 0) {
                Component message = Component.text(player.getName() + " got spleef'd and lost a life",
                                                   NamedTextColor.RED);
                other.sendActionBar(message);
            } else {
                Component message = Component.text(player.getName() + " got spleef'd and is out of the game",
                                                   NamedTextColor.RED);
                other.sendActionBar(message);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.teleport(world.getSpawnLocation()));
    }

    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        event.setCancelled(true);
    }

    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    public void onPlayerDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    public void onEntityExplode(EntityExplodeEvent event) {
        event.setCancelled(true);
        if (state != State.SPLEEF || !allowBlockBreaking) return;
        for (Block block : event.blockList()) {
            if (!block.isEmpty() && spleefBlocks.contains(block)) {
                block.setType(Material.AIR, false);
            }
        }
        event.blockList().clear();
        Location loc = event.getEntity().getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0.1f, 0.1f, 0.1f, 0.1f);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
    }

    private void info(String msg) {
        plugin.getLogger().info("[" + worldName + "] " + msg);
    }

    protected void onPlayerSidebar(Player player, List<Component> lines) {
        SpleefPlayer sp = getSpleefPlayer(player);
        if (state == State.COUNTDOWN && secondsLeft > 0) {
            lines.add(Component.text("Countdown ", NamedTextColor.GRAY)
                      .append(Component.text(secondsLeft, NamedTextColor.WHITE)));
        }
        if (state == State.SPLEEF) {
            if (suddenDeathActive) {
                lines.add(Component.text("Sudden Death!", NamedTextColor.RED));
            }
            if (secondsLeft >= 0) {
                lines.add(Component.text("Time ", NamedTextColor.GRAY)
                          .append(Component.text(secondsLeft, NamedTextColor.WHITE)));
            }
        }
        if (sp.isPlayed()) {
            if (sp.getLives() > 0) {
                lines.add(Component.text("Your Lives ", NamedTextColor.GRAY)
                          .append(Component.text(sp.getLives(), NamedTextColor.WHITE)));
            } else {
                lines.add(Component.text("Game Over", NamedTextColor.RED));
            }
            lines.add(Component.text("Blocks Broken ", NamedTextColor.GRAY)
                      .append(Component.text(sp.getBlocksBroken(), NamedTextColor.WHITE)));
        }
        List<SpleefPlayer> highscore = getAlivePlayers();
        Collections.sort(highscore, (a, b) -> Integer.compare(b.lives, a.lives));
        for (SpleefPlayer it : highscore) {
            if (!it.isPlayed() || it.getLives() == 0) continue;
            Player itPlayer = it.getPlayer();
            if (itPlayer == null) continue;
            if (it.isPlayer()) {
                lines.add(Component.text(Unicode.HEART.string + it.getLives() + " ", NamedTextColor.RED)
                          .append(itPlayer.displayName()));
            } else {
                lines.add(Component.text(Unicode.HEART.string + it.getLives() + " ", NamedTextColor.DARK_GRAY)
                          .append(itPlayer.displayName()));
            }
        }
    }

    public static void clearInventory(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i += 1) {
            ItemStack item = player.getInventory().getItem(i);
            Mytems mytems = Mytems.forItem(item);
            if (mytems != null && mytems.getMytem() instanceof WardrobeItem) {
                continue;
            }
            player.getInventory().clear(i);
        }
    }
}
