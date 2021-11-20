package com.winthier.spleef;

import com.cavetale.core.util.Json;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpleefPlugin extends JavaPlugin {
    protected static final List<String> WINNER_TITLES = List.of("Spleefer",
                                                                "ShovelKnight");
    protected final EventListener eventListener = new EventListener(this);
    protected final List<SpleefGame> spleefGameList = new ArrayList<>();
    protected final SpleefCommand spleefCommand = new SpleefCommand(this);
    protected List<String> worlds;
    protected Save save;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        load();
        Bukkit.getPluginManager().registerEvents(eventListener, this);
        spleefCommand.enable();
    }

    @Override
    public void onDisable() {
        for (SpleefGame game : List.copyOf(spleefGameList)) {
            game.stop();
        }
        spleefGameList.clear();
        save();
    }

    private void loadConfiguration() {
        worlds = getConfig().getStringList("worlds");
    }

    protected void load() {
        save = Json.load(new File(getDataFolder(), "save.json"), Save.class, Save::new);
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

    public SpleefGame startGame(String worldName) {
        World world = Worlds.loadWorld(this, worldName);
        SpleefGame game = new SpleefGame(this, world, worldName);
        spleefGameList.add(game);
        game.enable();
        return game;
    }
}
