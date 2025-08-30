package com.winthier.spleef;

import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.vote.MapVote;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Getter
public final class SpleefPlugin extends JavaPlugin {
    protected static final List<String> WINNER_TITLES = List.of("Spleefer",
                                                                "ShovelKnight",
                                                                "IronShovel",
                                                                "GoldenShovel");
    protected final EventListener eventListener = new EventListener(this);
    protected final List<SpleefGame> spleefGameList = new ArrayList<>();
    protected final SpleefAdminCommand spleefAdminCommand = new SpleefAdminCommand(this);
    protected final SpleefCommand spleefCommand = new SpleefCommand(this);
    protected Save save;
    protected List<Highscore> highscore = List.of();
    protected List<Component> highscoreLines = List.of();
    public static final Component TITLE = Component.text("Spleef!", GREEN, BOLD);
    public static final MinigameMatchType MINIGAME_TYPE = MinigameMatchType.SPLEEF;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        Bukkit.getPluginManager().registerEvents(eventListener, this);
        spleefAdminCommand.enable();
        spleefCommand.enable();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (SpleefGame game : List.copyOf(spleefGameList)) {
            game.stop();
        }
        spleefGameList.clear();
        save();
    }

    protected void load() {
        save = Json.load(new File(getDataFolder(), "save.json"), Save.class, Save::new);
        computeHighscore();
    }

    protected void save() {
        getDataFolder().mkdirs();
        Json.save(new File(getDataFolder(), "save.json"), save);
    }

    public void applyGame(World world, Consumer<SpleefGame> callback) {
        for (SpleefGame game : List.copyOf(spleefGameList)) {
            if (game.world.equals(world)) {
                callback.accept(game);
            }
        }
    }

    public SpleefGame startGame(World world, BuildWorld buildWorld) {
        SpleefGame game = new SpleefGame(this, world, buildWorld);
        spleefGameList.add(game);
        game.enable();
        return game;
    }

    public SpleefGame startGame(World world, BuildWorld buildWorld, List<Player> playerList) {
        final SpleefGame game = startGame(world, buildWorld);
        Collections.shuffle(playerList);
        for (Player player : playerList) {
            if (player.hasPermission("group.streamer") && player.isPermissionSet("group.streamer")) {
                game.addSpectator(player);
            } else {
                game.addPlayer(player);
            }
        }
        game.changeState(State.COUNTDOWN);
        buildWorld.announceMap(world);
        return game;
    }

    protected void computeHighscore() {
        highscore = Highscore.of(save.scores);
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.SPLEEF);
    }

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }

    private void tick() {
        if (save.pause || !spleefGameList.isEmpty() || getLobbyWorld().getPlayers().size() < 2) {
            MapVote.stop(MINIGAME_TYPE);
            return;
        }
        if (!MapVote.isActive(MINIGAME_TYPE)) {
            MapVote.start(MINIGAME_TYPE, vote -> {
                    vote.setLobbyWorld(getLobbyWorld());
                    vote.setTitle(TITLE);
                    if (save.event) {
                        vote.setDesiredGroupSize(5);
                    }
                    vote.setCallback(result -> {
                            startGame(result.getLocalWorldCopy(), result.getBuildWorldWinner(), result.getPlayers());
                        });
                });
        }
    }
}
