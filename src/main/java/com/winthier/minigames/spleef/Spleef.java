package com.winthier.minigames.spleef;

import com.winthier.connect.Connect;
import com.winthier.connect.Message;
import com.winthier.connect.bukkit.event.ConnectMessageEvent;
import java.io.FileReader;
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
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class Spleef extends JavaPlugin implements Listener {
    // Const
    enum State {
        INIT(60),
        WAIT_FOR_PLAYERS(60),
        COUNTDOWN(5),
        SPLEEF(2 * 60),
        END(60);
        final long seconds;
        State(long seconds) {
            this.seconds = seconds;
        }
    };
    static final long TIME_BEFORE_SPLEEF = 5;
    static final long SUDDEN_DEATH_TIME = 60;
    // Config
    String mapId = "Default";
    UUID gameId;
    boolean debug = false;
    boolean solo = false;
    int lives = 5;
    // World
    World world;
    final Set<Material> spleefMats = EnumSet.noneOf(Material.class);
    final Set<Block> spleefBlocks = new HashSet<>();
    final List<BlockState> spleefBlockStates = new ArrayList<>();
    final List<Location> spawnLocations = new ArrayList<>();
    final List<String> credits = new ArrayList<>();
    final Map<Block, Integer> suddenDeathTicks = new HashMap<>();
    boolean suddenDeathActive = false;
    boolean spawnLocationsRandomized = false;
    int spawnLocationsIndex = 0;
    int spleefLevel = 0;
    boolean allowBlockBreaking = false;
    boolean suddenDeath = false;
    int suddenDeathBlockTicks;
    boolean creeperSpawning;
    int creeperCooldown = 5;
    double creeperChance;
    double creeperPowerChance;
    double creeperSpeedChance;
    double creeperSpeedMultiplier;
    // State
    State state = State.INIT;
    long stateTicks = 0;
    BukkitRunnable task;
    boolean hasWinner = false;
    String winnerName = "";
    int round = 0;
    boolean shovelsGiven = false;
    boolean roundShouldEnd = false;
    int roundShouldEndTicks = 0;
    int creeperTimer = 0;
    final Random random = new Random(System.currentTimeMillis());
    // Scoreboard
    Scoreboard scoreboard;
    Objective sidebar;
    final Map<UUID, SpleefPlayer> spleefPlayers = new HashMap<>();

    @Override @SuppressWarnings("unchecked")
    public void onEnable() {
        ConfigurationSection gameConfig;
        ConfigurationSection worldConfig;
        try {
            gameConfig = new YamlConfiguration().createSection("tmp", (Map<String, Object>)JSONValue.parse(new FileReader("game_config.json")));
            worldConfig = YamlConfiguration.loadConfiguration(new FileReader("GameWorld/config.yml"));
        } catch (Throwable t) {
            t.printStackTrace();
            getServer().shutdown();
            return;
        }
        mapId = gameConfig.getString("map_id", mapId);
        gameId = UUID.fromString(gameConfig.getString("unique_id"));
        debug = gameConfig.getBoolean("debug", debug);

        WorldCreator wc = WorldCreator.name("GameWorld");
        wc.generator("VoidGenerator");
        wc.type(WorldType.FLAT);
        try {
            wc.environment(World.Environment.valueOf(worldConfig.getString("world.Environment").toUpperCase()));
        } catch (Throwable t) {
            wc.environment(World.Environment.NORMAL);
        }
        world = wc.createWorld();

        world.setTime(1000L);
        scanChunks();
        scanSpleefBlocks();
        copySpleefBlocks();
        getServer().getPluginManager().registerEvents(this, this);
        task = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        task.runTaskTimer(this, 1, 1);
        setupScoreboard();
        world.setPVP(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("sendCommandFeedback", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "true");
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(999999999);
        world.setDifficulty(Difficulty.EASY);
        lives = getConfig().getInt("Lives", 5);
        suddenDeathBlockTicks = getConfig().getInt("SuddenDeathBlockTicks", 40);
        creeperSpawning = getConfig().getBoolean("creeper.Spawn", false);
        creeperCooldown = getConfig().getInt("creeper.Cooldown", 5);
        creeperChance = getConfig().getDouble("creeper.Chance", 0.5);
        creeperPowerChance = getConfig().getDouble("creeper.PowerChance", 0.33);
        creeperSpeedChance = getConfig().getDouble("creeper.SpeedChance", 0.33);
        creeperSpeedMultiplier = getConfig().getDouble("creeper.SpeedMultiplier", 1.0);
    }

    public void onPlayerReady(Player player) {
        if (getSpleefPlayer(player).isPlayer()) {
            getSpleefPlayer(player).setLives(lives);
            sidebar.getScore(player.getName()).setScore(0);
        } else if (getSpleefPlayer(player).isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isValid()) {
                    // TODO
                    // showHighscore(player);
                    player.sendMessage("");
                    showCredits(player);
                    player.sendMessage("");
                }
            }
        }.runTaskLater(this, 20*5);
    }

    void removePlayer(Player player) {
        removePlayer(player.getUniqueId());
    }

    void removePlayer(UUID uuid) {
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            player.kickPlayer("Leaving Game");
        }
        daemonRemovePlayer(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (!getSpleefPlayer(event.getPlayer().getUniqueId()).hasJoinedBefore) {
            event.setSpawnLocation(getSpawnLocation(event.getPlayer()));
        }
    }

    public Location getSpawnLocation(Player player) {
        if (getSpleefPlayer(player).isPlayer()) {
            return getSpleefPlayer(player).getSpawnLocation();
        } else if (getSpleefPlayer(player).isSpectator()) {
        }
        return world.getSpawnLocation();
    }

    private void setupScoreboard() {
        scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("Sidebar", "dummy", "Spleef");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.setDisplayName(Msg.format("&aSpleef"));
        for (SpleefPlayer info : spleefPlayers.values()) {
            if (info.isOnline()) {
                info.getPlayer().setScoreboard(scoreboard);
            }
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
        }
    }

    void setSidebarTitle(String title, long ticks) {
        setSidebarTitle(title, ticks, state.seconds);
    }

    void setSidebarTitle(String title, long ticks, long secondsLeft) {
        ticks = secondsLeft * 20 - ticks;
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        sidebar.setDisplayName(Msg.format("&a%s &f%02d&a:&f%02d", title, minutes, seconds % 60));
    }

    void scanChunks() {
        Chunk spawnChunk = world.getSpawnLocation().getChunk();
        final int RADIUS = 5;
        for (int x = -RADIUS; x <= RADIUS; ++x) {
            for (int z = -RADIUS; z <= RADIUS; ++z) {
                scanChunk(spawnChunk.getX() + x, spawnChunk.getZ() + z);
            }
        }
    }

    void scanChunk(int x, int z) {
        Chunk chunk = world.getChunkAt(x, z);
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Chest) scanChest((Chest)state);
            if (state instanceof Sign) scanSign((Sign)state);
        }
    }

    void scanChest(Chest chest) {
        Inventory inv = chest.getBlockInventory();
        String name = inv.getName();
        if ("[spleef]".equalsIgnoreCase(name)) {
            getLogger().info("Found spleef chest");
            spleefBlocks.add(chest.getBlock().getRelative(0, -1, 0));
            spleefLevel = chest.getBlock().getY() - 1;
            for (ItemStack item : inv.getContents()) {
                if (item == null) continue;
                if (item.getType() == Material.AIR) continue;
                spleefMats.add(item.getType());
                getLogger().info("Spleef mat: " + item.getType());
            }
            inv.clear();
        } else {
            return;
        }
        chest.getBlock().setType(Material.AIR, false);
    }

    void scanSign(Sign sign) {
        String name = sign.getLine(0).toLowerCase();
        if ("[spawn]".equals(name)) {
            Location location = sign.getBlock().getLocation();
            location = location.add(0.5, 0.5, 0.5);
            Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
            location = location.setDirection(lookAt);
            spawnLocations.add(location);
        } else if ("[credits]".equals(name)) {
            for (int i = 1; i < 4; ++i) {
                String credit = sign.getLine(i);
                if (credit != null) credits.add(credit);
            }
        } else if ("[time]".equals(name)) {
            long time = 0;
            String arg = sign.getLine(1).toLowerCase();
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
                    time = Long.parseLong(sign.getLine(1));
                } catch (NumberFormatException nfe) {}
            }
            world.setTime(time);
            if ("lock".equalsIgnoreCase(sign.getLine(2))) {
                world.setGameRuleValue("doDaylightCycle", "false");
            } else {
                world.setGameRuleValue("doDaylightCycle", "true");
            }
        } else {
            return;
        }
        sign.getBlock().setType(Material.AIR, false);
    }

    boolean isSpleefMat(Material mat) {
        return spleefMats.contains(mat);
    }

    boolean isSpleefBlock(Block block) {
        return spleefBlocks.contains(block);
    }

    void scanSpleefBlocks() {
        Set<Block> searchedBlocks = new HashSet<>();
        Queue<Block> blocksToSearch = new ArrayDeque<>();
        searchedBlocks.addAll(spleefBlocks);
        blocksToSearch.addAll(spleefBlocks);
        while (!blocksToSearch.isEmpty()) {
            Block block = blocksToSearch.remove();
            if (!isSpleefMat(block.getType())) continue;
            spleefBlocks.add(block);
            final BlockFace[] faces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                if (!searchedBlocks.contains(neighbor)) {
                    searchedBlocks.add(neighbor);
                    blocksToSearch.add(neighbor);
                }
            }
        }
        getLogger().info("Found " + spleefBlocks.size() + " spleef blocks");
    }

    void copySpleefBlocks() {
        for (Block block : spleefBlocks) {
            spleefBlockStates.add(block.getState());
        }
    }

    void restoreSpleefBlocks() {
        putFloorUnderSpleefBlocks();
        for (BlockState state : spleefBlockStates) {
            if (state.getBlock().getType() != state.getType()) {
                state.update(true, false);
            }
        }
        new BukkitRunnable() {
            @Override public void run() {
                removeFloorUnderSpleefBlocks();
            }
        }.runTaskLater(this, 20L);
    }

    void removeFloorUnderSpleefBlocks() {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.AIR, false);
        }
    }

    void putFloorUnderSpleefBlocks() {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.BARRIER, false);
        }
    }

    Block spleefBlockUnderPlayer(Player player) {
        Location loc = player.getLocation();
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        int y = spleefLevel;
        Block block = player.getWorld().getBlockAt(bx, y, bz);
        if (block.getType() != Material.AIR && spleefBlocks.contains(block)) return block;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                block = player.getWorld().getBlockAt(bx+dx, y, bz+dz);
                if (block.getType() != Material.AIR && spleefBlocks.contains(block)) return block;
            }
        }
        return null;
    }

    Object button(String chat, String tooltip, String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", Msg.format(chat));
        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);
        map2.put("action", "run_command");
        map2.put("value", command);
        map2 = new HashMap<>();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", Msg.format(tooltip));
        return map;
    }

    Location dealSpawnLocation() {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        if (!spawnLocationsRandomized) {
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations);
        }
        if (spawnLocationsIndex >= spawnLocations.size()) spawnLocationsIndex = 0;
        return spawnLocations.get(spawnLocationsIndex++);
    }

    SpleefPlayer getSpleefPlayer(UUID uuid) {
        SpleefPlayer result = spleefPlayers.get(uuid);
        if (result == null) {
            result = new SpleefPlayer(this, uuid);
            spleefPlayers.put(uuid, result);
        }
        return result;
    }

    SpleefPlayer getSpleefPlayer(Player player) {
        SpleefPlayer result = getSpleefPlayer(player.getUniqueId());
        result.setName(player.getName());
        return result;
    }

    void makeImmobile(Player player) {
        if (!getSpleefPlayer(player).isPlayer()) return;
        Location location = getSpleefPlayer(player).getSpawnLocation();
        if (!player.getLocation().getWorld().equals(location.getWorld()) ||
            player.getLocation().distanceSquared(location) > 4.0) {
            player.teleport(location);
            getLogger().info("Teleported " + player.getName() + " to their spawn location");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    void makeMobile(Player player) {
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    void showCredits(Player player) {
        StringBuilder sb = new StringBuilder();
        for (String credit : credits) sb.append(" ").append(credit);
        Msg.send(player, "&b&l%s&r built by&b%s", mapId, sb.toString());
    }

    void sendDeathOption(Player player) {
        List<Object> list = new ArrayList<>();
        list.add("You got spleef'd. Click here to leave the game: ");
        list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
        Msg.sendRaw(player, list);
    }

    void sendEndOption(Player player) {
        List<Object> list = new ArrayList<>();
        list.add("Game Over. Click here to leave the game: ");
        list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
        Msg.sendRaw(player, list);
    }

    // Ticking

    void onTick() {
        if (state != State.INIT && state != State.WAIT_FOR_PLAYERS && getServer().getOnlinePlayers().isEmpty()) {
            getServer().shutdown();
            return;
        }
        if (state != State.INIT && spleefPlayers.values().isEmpty()) {
            getServer().shutdown();
            return;
        }
        long ticks = stateTicks++;
        State nextState = tickState(state, ticks);
        if (nextState != null && nextState != state) changeState(nextState);
        for (Entity e: world.getEntities()) {
            if (e instanceof Creeper) {
                Block b = e.getLocation().getBlock().getRelative(0, -1, 0);
                if (b.getType() == Material.AIR && spleefBlocks.contains(b)) {
                    b.setType(Material.SNOW_BLOCK, false);
                }
                List<BlockFace> nbors = Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
                for (BlockFace face: nbors) {
                    Block b2 = b.getRelative(face);
                    if (b2.getType() == Material.AIR && spleefBlocks.contains(b2)) {
                        b2.setType(Material.SNOW_BLOCK, false);
                    }
                }
            }
        }
    }

    State tickState(State state, long ticks) {
        switch (state) {
        case INIT: return tickInit(ticks);
        case WAIT_FOR_PLAYERS: return tickWaitForPlayers(ticks);
        case COUNTDOWN: return tickCountdown(ticks);
        case SPLEEF: return tickSpleef(ticks);
        case END: return tickEnd(ticks);
        default: getLogger().warning("State not handled: " + state);
        }
        return null;
    }

    void changeState(State newState) {
        State oldState = this.state;
        this.state = newState;
        stateTicks = 0;
        for (Entity e: world.getEntities()) {
            switch (e.getType()) {
            case CREEPER:
            case PRIMED_TNT:
                e.remove();
            default:
                break;
            }
        }
        switch (newState) {
        case WAIT_FOR_PLAYERS:
            break;
        case COUNTDOWN:
            daemonGameConfig("players_may_join", false);
            round += 1;
            restoreSpleefBlocks();
            setupScoreboard();
            for (SpleefPlayer info : spleefPlayers.values()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                if (sp.getLives() > 0) {
                    sp.setPlayer();
                    sp.setPlayed(true);
                    sidebar.getScore(sp.getName()).setScore(sp.getLives());
                }
            }
            int playerCount = 0;
            for (Player player : getServer().getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    player.setGameMode(GameMode.SURVIVAL);
                    getSpleefPlayer(player).setSpawnLocation(null);
                    makeImmobile(player);
                    playerCount += 1;
                }
            }
            if (playerCount < 2) {
                if (!debug) {
                    getServer().shutdown();
                    return;
                } else {
                    solo = true;
                }
            }
            break;
        case SPLEEF:
            allowBlockBreaking = false;
            for (SpleefPlayer info : spleefPlayers.values()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                sp.setDied(false);
            }
            for (Player player : getServer().getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    makeMobile(player);
                    if (!shovelsGiven) {
                        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
                        pickaxe.addEnchantment(Enchantment.DIG_SPEED, 5);
                        player.getInventory().addItem(pickaxe);
                        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
                        shovel.addEnchantment(Enchantment.DIG_SPEED, 5);
                        player.getInventory().addItem(shovel);
                    }
                }
            }
            shovelsGiven = true;
            roundShouldEnd = false;
            roundShouldEndTicks = 0;
            suddenDeathTicks.clear();
            suddenDeathActive = false;
            creeperTimer = 0;
            break;
        case END:
            daemonGameEnd();
            int survivorCount = 0;
            SpleefPlayer survivor = null;
            for (SpleefPlayer info : spleefPlayers.values()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                if (sp.getLives() > 0) {
                    survivorCount += 1;
                    survivor = sp;
                }
            }
            if (survivorCount == 1) {
                hasWinner = true;
                winnerName = survivor.getName();
                survivor.setWinner(true);
            }
            for (Player player : getServer().getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    getSpleefPlayer(player).setSpectator();
                    player.setGameMode(GameMode.SPECTATOR);
                }
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f);
            }
            break;
        }
    }

    State tickInit(long ticks) {
        if (getServer().getOnlinePlayers().size() > 0) return State.WAIT_FOR_PLAYERS;
        if (ticks > state.seconds * 20) getServer().shutdown();
        return null;
    }

    State tickWaitForPlayers(long ticks) {
        if (ticks > state.seconds * 20) return State.COUNTDOWN;
        if (ticks % 20 == 0) setSidebarTitle("Waiting", ticks);
        if (ticks % (20*5) == 0) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (!getSpleefPlayer(player).isReady()) {
                    List<Object> list = new ArrayList<>();
                    list.add(Msg.format("&fClick here when ready: "));
                    list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                    list.add(Msg.format("&f or "));
                    list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
                    Msg.sendRaw(player, list);
                }
            }
        }
        int notReadyCount = 0;
        int playerCount = 0;
        for (SpleefPlayer info : spleefPlayers.values()) {
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
            if (sp.isPlayer()) {
                playerCount += 1;
                if (!sp.isReady()) notReadyCount += 1;
            }
        }
        if (notReadyCount == 0 && (debug || playerCount > 1)) return State.COUNTDOWN;
        return null;
    }

    State tickCountdown(long ticks) {
        if (ticks > state.seconds * 20) return State.SPLEEF;
        if (ticks % 20 == 0) {
            setSidebarTitle("Countdown", ticks);
            long timeLeft = state.seconds - ticks/20;
            if (timeLeft == 0) {
                for (Player player : getServer().getOnlinePlayers()) {
                    Msg.send(player, "&a&lRUN!");
                    Msg.sendTitle(player, "", "&a&lRUN");
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
                }
            } else {
                for (Player player : getServer().getOnlinePlayers()) {
                    Msg.send(player, "&aCountdown: %d", timeLeft);
                    Msg.sendTitle(player, "&a" + timeLeft, "&aRound " + this.round);
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)timeLeft));
                }
            }
        }
        return null;
    }

    State tickSpleef(long ticks) {
        if (ticks > state.seconds * 20) return State.COUNTDOWN;
        long ticksLeft = state.seconds * 20 - ticks;
        long secondsLeft = ticksLeft / 20;
        if (!allowBlockBreaking && ticks > 20*5) {
            allowBlockBreaking = true;
            for (Player player : getServer().getOnlinePlayers()) {
                Msg.send(player, "&a&lSPLEEF!");
                Msg.sendTitle(player, "", "&a&lSPLEEF");
                player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            }
        }
        if (ticks % 20 == 0) {
            if (!allowBlockBreaking) {
                setSidebarTitle("Run", ticks, TIME_BEFORE_SPLEEF);
            } else {
                setSidebarTitle("Spleef", ticks);
            }
        }
        int aliveCount = 0;
        for (SpleefPlayer info : spleefPlayers.values()) {
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
            if (sp.isPlayer()) {
                if (!info.isOnline()) {
                    if (sp.addOfflineTick() > 20*30) {
                        removePlayer(sp.getUuid());
                    }
                } else {
                    Player player = info.getPlayer();
                    if (player.getLocation().getBlockY() < spleefLevel) {
                        sp.setSpectator();
                        sp.setDied(true);
                        sp.setLives(sp.getLives() - 1);
                        sidebar.getScore(sp.getName()).setScore(sp.getLives());
                        player.setGameMode(GameMode.SPECTATOR);
                        player.getWorld().strikeLightningEffect(player.getLocation());
                        for (Player other : getServer().getOnlinePlayers()) {
                            if (sp.getLives() > 0) {
                                Msg.sendTitle(other, "", "&c" + player.getName() + " got spleef'd and lost a life");
                                Msg.send(other, "&c%s got spleef'd and lost a life", player.getName());
                            } else {
                                Msg.sendTitle(other, "", "&c" + player.getName() + " got spleef'd and is out of the game");
                                Msg.send(other, "&c%s got spleef'd and is out of the game", player.getName());
                            }
                        }
                    } else {
                        aliveCount += 1;
                    }
                }
            }
            if (info.isOnline() && sp.isDied() && sp.getLives() <= 0 && ticks % (20*10) == 0) {
                sendDeathOption(info.getPlayer());
            }
        }
        if (aliveCount == 0 || (!solo && aliveCount <= 1)) roundShouldEnd = true;
        if (roundShouldEnd && roundShouldEndTicks++ >= 20 * 3) {
            int survivorCount = 0;
            for (SpleefPlayer info : spleefPlayers.values()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                if (sp.getLives() > 0) {
                    survivorCount += 1;
                }
            }
            if (!solo && survivorCount <= 1) return State.END;
            if (survivorCount <= 0) return State.END;
            return State.COUNTDOWN;
        }
        if (!suddenDeathActive) {
            if (secondsLeft <= SUDDEN_DEATH_TIME) {
                suddenDeathActive = true;
                Msg.announceTitle("", "&4Sudden Death");
                Msg.announce("&3&lSpleef&r Sudden Death activated. Blocks will fade under your feet.");
                for (Player player: getServer().getOnlinePlayers()) {
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
                }
            }
        } else {
            for (Player player: getServer().getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    Block block = spleefBlockUnderPlayer(player);
                    if (block != null) {
                        Integer blockTicks = suddenDeathTicks.get(block);
                        if (blockTicks == null) {
                            blockTicks = 1;
                        } else {
                            blockTicks += 1;
                        }
                        suddenDeathTicks.put(block, blockTicks);
                        if (blockTicks == suddenDeathBlockTicks) {
                            block.setType(Material.AIR, false);
                        } else if (blockTicks == suddenDeathBlockTicks - 20) {
                            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                            World world = loc.getWorld();
                            loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            world.spawnParticle(Particle.BLOCK_DUST,
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
        }
        if (solo || (allowBlockBreaking && creeperSpawning)) {
            creeperTimer += 1;
            if (creeperTimer % 20 == 0 && creeperTimer >= creeperCooldown * 20 && random.nextDouble() < creeperChance) {
                creeperTimer = 0;
                List<Block> blockList = new ArrayList<>();
                for (Block b: spleefBlocks) if (b.getType() != Material.AIR) blockList.add(b);
                if (!blockList.isEmpty()) {
                    Block creeperBlock = blockList.get(random.nextInt(blockList.size()));
                    Creeper creeper = creeperBlock.getWorld().spawn(creeperBlock.getLocation().add(0.5, 1.0, 0.5), Creeper.class);
                    if (creeper != null) {
                        if (random.nextDouble() < creeperPowerChance) {
                            creeper.setPowered(true);
                        }
                        if (random.nextDouble() < creeperSpeedChance) {
                            creeper.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("Spleef", (float)creeperSpeedMultiplier, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                        }
                    }
                }
            }
        }
        return null;
    }

    State tickEnd(long ticks) {
        if (ticks > state.seconds * 20) {
            getServer().shutdown();
            return null;
        }
        if (ticks % 20 == 0) {
            setSidebarTitle("End", ticks);
        }
        if (ticks % (20 * 5) == 0) {
            if (hasWinner) {
                for (Player player : getServer().getOnlinePlayers()) {
                    Msg.sendTitle(player, "&a"+winnerName, "&aWins the Game!");
                    Msg.send(player, "&a%s wins the game!", winnerName);
                    sendEndOption(player);
                }
            } else {
                for (Player player : getServer().getOnlinePlayers()) {
                    Msg.sendTitle(player, "&cDraw", "&cNobody wins!");
                    Msg.send(player, "&cDraw! Nobody wins.");
                    sendEndOption(player);
                }
            }
        }
        return null;
    }

    // Listeners

    @Override
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player)sender;
        if (command.equalsIgnoreCase("ready") && state == State.WAIT_FOR_PLAYERS) {
            getSpleefPlayer(player).setReady(true);
            sidebar.getScore(player.getName()).setScore(1);
            Msg.send(player, "&aMarked as ready");
        } else if (command.equalsIgnoreCase("tp") && getSpleefPlayer(player).isSpectator()) {
            if (args.length != 1) {
                Msg.send(player, "&cUsage: /tp <player>");
                return true;
            }
            String arg = args[0];
            for (Player target : getServer().getOnlinePlayers()) {
                if (arg.equalsIgnoreCase(target.getName())) {
                    player.teleport(target);
                    Msg.send(player, "&bTeleported to %s", target.getName());
                    return true;
                }
            }
            Msg.send(player, "&cPlayer not found: %s", arg);
            return true;
        } else if (command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi")) {
            // TODO
            // showHighscore(player);
        } else if (command.equalsIgnoreCase("quit")) {
            removePlayer(player);
        } else {
            return false;
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SpleefPlayer sp = getSpleefPlayer(player);
        if (!sp.hasJoinedBefore) {
            sp.hasJoinedBefore = true;
            onPlayerReady(player);
        }
        player.setScoreboard(scoreboard);
        if (getSpleefPlayer(player).isPlayer()) {
            switch (state) {
            case INIT: case WAIT_FOR_PLAYERS: case COUNTDOWN:
                makeImmobile(player);
                break;
            default:
                makeMobile(player);
                break;
            }
        } else if (getSpleefPlayer(player).isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
        case END:
            removePlayer(event.getPlayer());
            return;
        }
        if (getSpleefPlayer(event.getPlayer()).isSpectator()) removePlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
            if (event.getFrom().distanceSquared(event.getTo()) > 2.0) {
                makeImmobile(player);
                event.setCancelled(true);
            }
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (state == State.SPLEEF) {
            Block block = event.getBlock();
            if (allowBlockBreaking && isSpleefBlock(block) && block.getType() != Material.AIR) {
                world.spawnParticle(Particle.BLOCK_DUST,
                                    block.getLocation().add(0.5, 0.5, 0.5),
                                    64,
                                    .3f, .3f, .3f,
                                    .01f,
                                    block.getBlockData());
                block.setType(Material.AIR, false);
                SpleefPlayer sp = getSpleefPlayer(event.getPlayer());
                sp.addBlockBroken();
                int broken = sp.getBlocksBroken();
                if (broken > 0 && broken % 100 == 0) {
                    ItemStack item = new ItemStack(Material.TNT);
                    event.getPlayer().getInventory().addItem(item);
                }
                if (broken > 0 && broken % 200 == 0) {
                    ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG);
                    event.getPlayer().getInventory().addItem(item);
                }
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.getEntity().teleport(world.getSpawnLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.setCancelled(true);
        if (state != State.SPLEEF || !allowBlockBreaking) return;
        for (Block block: event.blockList()) {
            if (block.getType() != Material.AIR
                && spleefBlocks.contains(block)) {
                block.setType(Material.AIR, false);
            }
        }
        Location loc = event.getEntity().getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0.1f, 0.1f, 0.1f, 0.1f);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    // Daemon stuff

    // Request from a player to join this game.  It gets sent to us by
    // the daemon when the player enters the appropriate remote
    // command.  Tell the daemon that that the request has been
    // accepted, then wait for the daemon to send the player here.
    @EventHandler @SuppressWarnings("unchecked")
    public void onConnectMessage(ConnectMessageEvent event) {
        final Message message = event.getMessage();
        if (message.getFrom().equals("daemon") && message.getChannel().equals("minigames")) {
            Map<String, Object> payload = (Map<String, Object>)message.getPayload();
            if (payload == null) return;
            boolean join = false;
            boolean leave = false;
            boolean spectate = false;
            switch ((String)payload.get("action")) {
            case "player_join_game":
                join = true;
                spectate = false;
                break;
            case "player_spectate_game":
                join = true;
                spectate = true;
                break;
            case "player_leave_game":
                leave = true;
                break;
            default:
                return;
            }
            if (join) {
                final UUID gameId = UUID.fromString((String)payload.get("game"));
                if (!gameId.equals(gameId)) return;
                final UUID player = UUID.fromString((String)payload.get("player"));
                if (spectate) {
                    getSpleefPlayer(player).setSpectator();
                    daemonAddSpectator(player);
                } else {
                    if (state != State.WAIT_FOR_PLAYERS) return;
                    if (spleefPlayers.containsKey(player)) return;
                    daemonAddPlayer(player);
                }
            } else if (leave) {
                final UUID playerId = UUID.fromString((String)payload.get("player"));
                Player player = getServer().getPlayer(playerId);
                if (player != null) player.kickPlayer("Leaving game");
            }
        }
    }

    void daemonRemovePlayer(UUID uuid) {
        spleefPlayers.remove(uuid);
        Map<String, Object> map = new HashMap<>();
        map.put("action", "player_leave_game");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonAddPlayer(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_player");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonAddSpectator(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_spectator");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameEnd() {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_end");
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameConfig(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_config");
        map.put("game", gameId.toString());
        map.put("key", key);
        map.put("value", value);
        Connect.getInstance().send("daemon", "minigames", map);
    }

    // End of Daemon stuff
}
