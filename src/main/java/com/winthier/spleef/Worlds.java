package com.winthier.spleef;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Worlds {
    private Worlds() { }

    public static World loadWorld(JavaPlugin plugin, String worldName) {
        plugin.getLogger().info("loadWorld " + worldName);
        File folder = copyWorld(plugin, worldName);
        File configFile = new File(folder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        WorldCreator wc = new WorldCreator(folder.getName());
        wc.environment(World.Environment.valueOf(config.getString("world.Environment", "NORMAL")));
        wc.generateStructures(config.getBoolean("world.GenerateStructures", false));
        wc.generator(config.getString("world.Generator", "VoidGenerator"));
        wc.type(WorldType.valueOf(config.getString("world.WorldType", "NORMAL")));
        World result = Bukkit.createWorld(wc);
        result.setAutoSave(false);
        return result;
    }

    public static void deleteWorld(JavaPlugin plugin, World world) {
        plugin.getLogger().info("deleteWorld " + world.getName());
        File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalStateException("Unloading world " + world.getName());
        }
        deleteFile(plugin, folder);
    }

    private static void copyFileStructure(JavaPlugin plugin, File source, File target, int depth) {
        plugin.getLogger().info("copyFileStructure " + source + " => " + target);
        if (source.isDirectory()) {
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw new IllegalStateException("Couldn't create world directory: " + target);
                }
            }
            String[] files = source.list();
            for (String file : files) {
                File srcFile = new File(source, file);
                File destFile = new File(target, file);
                copyFileStructure(plugin, srcFile, destFile, depth + 1);
            }
        } else {
            if (depth == 1 && List.of("uid.dat", "session.lock").contains(source.getName())) return;
            try {
                InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(target);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                in.close();
                out.close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    private static void copyFileStructure(JavaPlugin plugin, File source, File target) {
        copyFileStructure(plugin, source, target, 0);
    }

    private static File copyWorld(JavaPlugin plugin, String worldName) {
        plugin.getLogger().info("copyWorld " + worldName);
        File source = new File(new File(plugin.getDataFolder(), "maps"), worldName);
        int suffix = 0;
        File dest;
        do {
            String fileName = plugin.getName().toLowerCase() + "_" + worldName + "_" + suffix++;
            dest = new File(Bukkit.getWorldContainer(), fileName);
        } while (dest.exists());
        copyFileStructure(plugin, source, dest);
        return dest;
    }

    private static void deleteFile(JavaPlugin plugin, File file) {
        plugin.getLogger().info("deleteFile " + file);
        if (!file.exists()) return;
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteFile(plugin, child);
            }
        }
        file.delete();
    }
}
