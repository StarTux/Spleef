package com.winthier.minigames.spleef;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.player.PlayerInfo;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

public class Spleef extends Game implements Listener
{
    // Const
    enum State {
        INIT(60),
        WAIT_FOR_PLAYERS(60),
        COUNTDOWN(5),
        SPLEEF(60*5),
        END(60);
        long seconds;
        State(long seconds) { this.seconds = seconds; }
    };
    final static long TIME_BEFORE_SPLEEF = 5;
    // Config
    String mapId = "Default";
    String mapPath = "/home/creative/minecraft/worlds/KoontzySpleef";
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
    boolean spawnLocationsRandomized = false;
    int spawnLocationsIndex = 0;
    int spleefLevel = 0;
    boolean allowBlockBreaking = false;
    boolean suddenDeath = false;
    // State
    State state = State.INIT;
    long stateTicks = 0;
    BukkitRunnable task;
    boolean hasWinner = false;
    String winnerName = "";
    int round = 0;
    boolean roundShouldEnd = false;
    int roundShouldEndTicks = 0;
    // Scoreboard
    Scoreboard scoreboard;
    Objective sidebar;
    
    @Override
    public void onEnable()
    {
        mapId = getConfig().getString("MapID", mapId);
        mapPath = getConfig().getString("MapPath", mapPath);
        debug = getConfig().getBoolean("Debug", debug);
        WorldLoader.loadWorlds(
            this,
            new BukkitFuture<WorldLoader>() {
                @Override public void run() {
                    onWorldsLoaded(get());
                }
            },
            mapPath);
    }

    void onWorldsLoaded(WorldLoader loader)
    {
        world = loader.getWorld(0);
        world.setTime(1000L);
        scanChunks();
        scanSpleefBlocks();
        copySpleefBlocks();
        MinigamesPlugin.getEventManager().registerEvents(this, this);
        task = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        task.runTaskTimer(MinigamesPlugin.getInstance(), 1, 1);
        setupScoreboard();
        world.setPVP(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("sendCommandFeedback", "false");
        world.setGameRuleValue("doFireTick", "false");
        lives = getConfigFile("config").getInt("Lives", 5);
        ready();
    }

    @Override
    public void onDisable()
    {
        task.cancel();
    }

    @Override
    public void onPlayerReady(Player player)
    {
        Players.reset(player);
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
        }.runTaskLater(MinigamesPlugin.getInstance(), 20*5);
    }

    @Override
    public Location getSpawnLocation(Player player)
    {
        if (getSpleefPlayer(player).isPlayer()) {
            return getSpleefPlayer(player).getSpawnLocation();
        } else if (getSpleefPlayer(player).isSpectator()) {
        }
        return world.getSpawnLocation();
    }

    @Override
    public boolean joinPlayers(List<UUID> uuids)
    {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
            return super.joinPlayers(uuids);
        default: return false;
        }
    }

    @Override
    public boolean joinSpectators(List<UUID> uuids)
    {
        if (super.joinSpectators(uuids)) {
            for (UUID uuid : uuids) {
                getSpleefPlayer(uuid).setSpectator();
            }
            return true;
        }
        return false;
    }

    private void setupScoreboard() {
        scoreboard = MinigamesPlugin.getInstance().getServer().getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("Sidebar", "dummy");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.setDisplayName(Msg.format("&aSpleef"));
        for (PlayerInfo info : getPlayers()) {
            if (info.isOnline()) {
                info.getPlayer().setScoreboard(scoreboard);
            }
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
        }
    }

    void setSidebarTitle(String title, long ticks)
    {
        setSidebarTitle(title, ticks, state.seconds);
    }

    void setSidebarTitle(String title, long ticks, long secondsLeft)
    {
        ticks = secondsLeft * 20 - ticks;
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        sidebar.setDisplayName(Msg.format("&a%s &f%02d&a:&f%02d", title, minutes, seconds % 60));
    }
    
    void scanChunks()
    {
        Chunk spawnChunk = world.getSpawnLocation().getChunk();
        final int RADIUS = 5;
        for (int x = -RADIUS; x <= RADIUS; ++x) {
            for (int z = -RADIUS; z <= RADIUS; ++z) {
                scanChunk(spawnChunk.getX() + x, spawnChunk.getZ() + z);
            }
        }
    }

    void scanChunk(int x, int z)
    {
        Chunk chunk = world.getChunkAt(x, z);
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Chest) scanChest((Chest)state);
            if (state instanceof Sign) scanSign((Sign)state);
        }
    }

    void scanChest(Chest chest)
    {
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

    void scanSign(Sign sign)
    {
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

    boolean isSpleefMat(Material mat)
    {
        return spleefMats.contains(mat);
    }

    boolean isSpleefBlock(Block block)
    {
        return spleefBlocks.contains(block);
    }

    void scanSpleefBlocks()
    {
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

    void copySpleefBlocks()
    {
        for (Block block : spleefBlocks) {
            spleefBlockStates.add(block.getState());
        }
    }

    void restoreSpleefBlocks()
    {
        putFloorUnderSpleefBlocks();
        for (BlockState state : spleefBlockStates) {
            if (state.getBlock().getType() == Material.AIR) {
                state.update(true, false);
            }
        }
        new BukkitRunnable() {
            @Override public void run() {
                removeFloorUnderSpleefBlocks();
            }
        }.runTaskLater(MinigamesPlugin.getInstance(), 20L);
    }

    void removeFloorUnderSpleefBlocks()
    {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.AIR, false);
        }
    }

    void putFloorUnderSpleefBlocks()
    {
        for (Block block : spleefBlocks) {
            block.getRelative(0, -1, 0).setType(Material.BARRIER, false);
        }
    }
    
    Object button(String chat, String tooltip, String command)
    {
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

    Location dealSpawnLocation()
    {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        if (!spawnLocationsRandomized) {
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations);
        }
        if (spawnLocationsIndex >= spawnLocations.size()) spawnLocationsIndex = 0;
        return spawnLocations.get(spawnLocationsIndex++);
    }

    SpleefPlayer getSpleefPlayer(UUID uuid)
    {
        return getPlayer(uuid).<SpleefPlayer>getCustomData(SpleefPlayer.class);
    }

    SpleefPlayer getSpleefPlayer(Player player)
    {
        return getSpleefPlayer(player.getUniqueId());
    }

    void makeImmobile(Player player)
    {
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

    void makeMobile(Player player)
    {
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    void showCredits(Player player)
    {
        StringBuilder sb = new StringBuilder();
        for (String credit : credits) sb.append(" ").append(credit);
        Msg.send(player, "&b&l%s&r built by&b%s", mapId, sb.toString());
    }

    void sendDeathOption(Player player)
    {
        List<Object> list = new ArrayList<>();
        list.add("You got spleef'd. Click here to leave the game: ");
        list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
        Msg.sendRaw(player, list);
    }
    
    void sendEndOption(Player player)
    {
        List<Object> list = new ArrayList<>();
        list.add("Game Over. Click here to leave the game: ");
        list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
        Msg.sendRaw(player, list);
    }

    // Ticking

    void onTick()
    {
        if (state != State.INIT && state != State.WAIT_FOR_PLAYERS && getOnlinePlayers().isEmpty()) {
            cancel();
            return;
        }
        if (state != State.INIT && getPlayers().isEmpty()) {
            cancel();
            return;
        }
        long ticks = stateTicks++;
        State nextState = tickState(state, ticks);
        if (nextState != null && nextState != state) changeState(nextState);
    }

    State tickState(State state, long ticks)
    {
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

    void changeState(State newState)
    {
        State oldState = this.state;
        this.state = newState;
        stateTicks = 0;
        switch (newState) {
        case WAIT_FOR_PLAYERS:
            break;
        case COUNTDOWN:
            round += 1;
            restoreSpleefBlocks();
            setupScoreboard();
            for (PlayerInfo info : getPlayers()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                if (sp.getLives() > 0) {
                    sp.setPlayer();
                    sp.setPlayed(true);
                    sidebar.getScore(sp.getName()).setScore(sp.getLives());
                }
            }
            int playerCount = 0;
            for (Player player : getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    player.setGameMode(GameMode.SURVIVAL);
                    makeImmobile(player);
                    playerCount += 1;
                }
            }
            if (playerCount < 2) {
                if (!debug) {
                    cancel();
                    return;
                } else {
                    solo = true;
                }
            }
            break;
        case SPLEEF:
            allowBlockBreaking = false;
            for (PlayerInfo info : getPlayers()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                sp.setDied(false);
            }
            for (Player player : getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) makeMobile(player);
                ItemStack shovel = new ItemStack(Material.DIAMOND_SPADE);
                shovel.addEnchantment(Enchantment.DIG_SPEED, 5);
                player.setItemInHand(shovel);
            }
            roundShouldEnd = false;
            roundShouldEndTicks = 0;
            break;
        case END:
            int survivorCount = 0;
            SpleefPlayer survivor = null;
            for (PlayerInfo info : getPlayers()) {
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
            for (Player player : getOnlinePlayers()) {
                if (getSpleefPlayer(player).isPlayer()) {
                    getSpleefPlayer(player).setSpectator();
                    player.setGameMode(GameMode.SPECTATOR);
                }
                player.playSound(player.getEyeLocation(), Sound.ENDERDRAGON_DEATH, 1f, 1f);
            }
            for (PlayerInfo info : getPlayers()) {
                getSpleefPlayer(info.getUuid()).reward();
            }
            break;
        }
    }

    State tickInit(long ticks)
    {
        if (getOnlinePlayers().size() > 0) return State.WAIT_FOR_PLAYERS;
        if (ticks > state.seconds * 20) cancel();
        return null;
    }

    State tickWaitForPlayers(long ticks)
    {
        if (ticks > state.seconds * 20) return State.COUNTDOWN;
        if (ticks % 20 == 0) setSidebarTitle("Waiting", ticks);
        if (ticks % (20*5) == 0) {
            for (Player player : getOnlinePlayers()) {
                if (!getSpleefPlayer(player).isReady()) {
                    List<Object> list = new ArrayList<>();
                    list.add(Msg.format("&fClick here when ready: "));
                    list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                    list.add(Msg.format("&f or "));
                    list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
                    Msg.sendRaw(player, list);
                }
            }
        }
        int notReadyCount = 0;
        int playerCount = 0;
        for (PlayerInfo info : getPlayers()) {
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
            if (sp.isPlayer()) {
                playerCount += 1;
                if (!sp.isReady()) notReadyCount += 1;
            }
        }
        if (notReadyCount == 0 && (debug || playerCount > 1)) return State.COUNTDOWN;
        return null;
    }

    State tickCountdown(long ticks)
    {
        if (ticks > state.seconds * 20) return State.SPLEEF;
        if (ticks % 20 == 0) {
            setSidebarTitle("Countdown", ticks);
            long timeLeft = state.seconds - ticks/20;
            if (timeLeft == 0) {
                for (Player player : getOnlinePlayers()) {
                    Msg.send(player, "&a&lRUN!");
                    Title.show(player, "", "&a&lRUN");
                    player.playSound(player.getEyeLocation(), Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
                }
            } else {
                for (Player player : getOnlinePlayers()) {
                    Msg.send(player, "&aCountdown: %d", timeLeft);
                    Title.show(player, "&a" + timeLeft, "&aRound " + this.round);
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)timeLeft));
                }
            }
        }
        return null;
    }

    State tickSpleef(long ticks)
    {
        if (ticks > state.seconds * 20) return State.COUNTDOWN;
        if (!allowBlockBreaking && ticks > 20*5) {
            allowBlockBreaking = true;
            for (Player player : getOnlinePlayers()) {
                Msg.send(player, "&a&lSPLEEF!");
                Title.show(player, "", "&a&lSPLEEF");
                player.playSound(player.getEyeLocation(), Sound.WITHER_SPAWN, 1f, 1f);
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
        for (PlayerInfo info : getPlayers()) {
            SpleefPlayer sp = getSpleefPlayer(info.getUuid());
            if (sp.isPlayer()) {
                if (!info.isOnline()) {
                    if (sp.addOfflineTick() > 20*30) {
                        MinigamesPlugin.leavePlayer(sp.getUuid());
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
                        for (Player other : getOnlinePlayers()) {
                            if (sp.getLives() > 0) {
                                Title.show(other, "", "&c" + player.getName() + " got spleef'd and lost a life");
                                Msg.send(other, "&c%s got spleef'd and lost a life", player.getName());
                            } else {
                                Title.show(other, "", "&c" + player.getName() + " got spleef'd and is out of the game");
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
        if (roundShouldEnd && roundShouldEndTicks++ >= 20*3) {
            int survivorCount = 0;
            for (PlayerInfo info : getPlayers()) {
                SpleefPlayer sp = getSpleefPlayer(info.getUuid());
                if (sp.getLives() > 0) {
                    survivorCount += 1;
                }
            }
            if (survivorCount <= 1) return State.END;
            return State.COUNTDOWN;
        }
        return null;
    }

    State tickEnd(long ticks)
    {
        if (ticks > state.seconds * 20) {
            cancel();
            return null;
        }
        if (ticks % 20 == 0) {
            setSidebarTitle("End", ticks);
        }
        if (ticks % (20*5) == 0) {
            if (hasWinner) {
                for (Player player : getOnlinePlayers()) {
                    Title.show(player, "&a"+winnerName, "&aWins the Game!");
                    Msg.send(player, "&a%s wins the game!", winnerName);
                    sendEndOption(player);
                }
            } else {
                for (Player player : getOnlinePlayers()) {
                    Title.show(player, "&cDraw", "&cNobody wins!");
                    Msg.send(player, "&cDraw! Nobody wins.");
                    sendEndOption(player);
                }
            }
        }
        return null;
    }

    // Listeners

    @Override
    public boolean onCommand(Player player, String command, String[] args) {
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
            for (Player target : getOnlinePlayers()) {
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
        } else {
            return false;
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        getSpleefPlayer(player).setName(player.getName());
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
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
        case END:
            MinigamesPlugin.leavePlayer(event.getPlayer());
            return;
        }
        if (getSpleefPlayer(event.getPlayer()).isSpectator()) MinigamesPlugin.leavePlayer(event.getPlayer());
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
    public void onBlockBreak(BlockBreakEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event)
    {
        if (state == State.SPLEEF) {
            Block block = event.getBlock();
            if (allowBlockBreaking && isSpleefBlock(block) && block.getType() != Material.AIR) {
                world.spigot().playEffect(block.getLocation().add(0.5, 0.5, 0.5),
                                          Effect.TILE_BREAK,
                                          block.getType().getId(),
                                          (int)block.getData(),
                                          .3f, .3f, .3f,
                                          .01f,
                                          64, 64);
                block.setType(Material.AIR, false);
                getSpleefPlayer(event.getPlayer()).addBlockBroken();
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.getEntity().teleport(world.getSpawnLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event)
    {
        event.setCancelled(true);
    }
}
