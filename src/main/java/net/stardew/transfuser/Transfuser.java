package net.stardew.transfuser;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public final class Transfuser extends JavaPlugin implements Listener, TabExecutor {

    private final HashMap<UUID, HashMap<String, Inventory>> playerTransfusers = new HashMap<>();
    private int customModelData;
    private boolean useTransfuserCommand;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("transfuser").setExecutor(new TransfuserCommands(this));
        getCommand("transfuser").setTabCompleter(new TransfuserCommands(this));
        saveDefaultConfig();
        customModelData = getConfig().getInt("Transfuser-Remote-Item.CustomModelData");
        useTransfuserCommand = getConfig().getBoolean("Use-Transfuser-Command");
        loadAllTransfusers();
    }

    @Override
    public void onDisable() {
        saveAllTransfusers();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null) {
            FileConfiguration config = getConfig();
            String materialName = config.getString("Transfuser-Remote-Item.Material");
            Material material = Material.valueOf(materialName);
            ItemMeta meta = event.getItem().getItemMeta();

            if (event.getItem().getType() == material && meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == customModelData) {
                openTransfuserMenu(event.getPlayer());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("Rename Transfuser to ")) {
            event.setCancelled(true);
            handleRenameTransfuserClick(event);
        } else if (event.getView().getTitle().equals("Create Transfuser")) {
            event.setCancelled(true);
            handleCreateTransfuserClick(event);
        } else if (event.getView().getTitle().equals("Your Transfusers")) {
            event.setCancelled(true);
            handleTransfuserClick(event);
        } else if (event.getView().getTitle().equals("Destroy Transfuser")) {
            event.setCancelled(true);
            handleDestroyTransfuserClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals("Create Transfuser") && !title.equals("Your Transfusers") && !title.equals("Destroy Transfuser") && !title.equals("Rename Transfuser to ")) {
            saveTransfuserContent((Player) event.getPlayer(), title, event.getInventory());
        }
    }

    private void handleCreateTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ENDER_CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                String uniqueID = UUID.randomUUID().toString();
                if (displayName.equals("Small Transfuser (8 slots)")) {
                    createTransfuser(player, "Small Transfuser - " + uniqueID, 9);
                } else if (displayName.equals("Medium Transfuser (18 slots)")) {
                    createTransfuser(player, "Medium Transfuser - " + uniqueID, 18);
                } else if (displayName.equals("Large Transfuser (27 slots)")) {
                    createTransfuser(player, "Large Transfuser - " + uniqueID, 27);
                }
                player.closeInventory();
            }
        }
    }

    private void handleTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ENDER_CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                openTransfuser(player, displayName);
            }
        }
    }

    private void handleDestroyTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ENDER_CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                destroyTransfuser(player, displayName);
                player.closeInventory();
            }
        }
    }

    private void handleRenameTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ENDER_CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String oldName = meta.getDisplayName();
                String newName = ChatColor.stripColor(event.getView().getTitle().replace("Rename Transfuser to ", ""));
                
                if (!newName.isEmpty()) {
                    renameTransfuser(player, oldName, newName);
                } else {
                    player.sendMessage("Invalid new name.");
                }
                player.closeInventory();
            }
        }
    }

    public void openTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Your Transfusers");
        List<String> transfusers = getPlayerConfig(player).getStringList("transfusers");
        for (String transfuser : transfusers) {
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(transfuser);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    private void openTransfuser(Player player, String name) {
        UUID playerUUID = player.getUniqueId();
        if (playerTransfusers.containsKey(playerUUID) && playerTransfusers.get(playerUUID).containsKey(name)) {
            player.openInventory(playerTransfusers.get(playerUUID).get(name));
        } else {
            FileConfiguration playerConfig = getPlayerConfig(player);
            int size = playerConfig.getInt("contents." + name + ".size", 27); // Default size
            Inventory transfuserInv = Bukkit.createInventory(null, size, name);
            List<?> itemList = playerConfig.getList("contents." + name + ".items");
            if (itemList != null) {
                ItemStack[] items = itemList.toArray(new ItemStack[0]);
                transfuserInv.setContents(items);
            }
            playerTransfusers.computeIfAbsent(playerUUID, k -> new HashMap<>()).put(name, transfuserInv);
            player.openInventory(transfuserInv);
        }
    }

    private void createTransfuser(Player player, String name, int size) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration playerConfig = getPlayerConfig(player);
        List<String> transfusers = playerConfig.getStringList("transfusers");
        transfusers.add(name);
        playerConfig.set("transfusers", transfusers);
        savePlayerConfig(player, playerConfig);

        Inventory transfuserInv = Bukkit.createInventory(null, size, name);
        playerTransfusers.computeIfAbsent(playerUUID, k -> new HashMap<>()).put(name, transfuserInv);
        saveTransfuserContent(player, name, transfuserInv);

        player.sendMessage("Created a " + name);
    }

    public void destroyTransfuser(Player player, String name) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration playerConfig = getPlayerConfig(player);
        List<String> transfusers = playerConfig.getStringList("transfusers");
        if (transfusers.remove(name)) {
            playerConfig.set("transfusers", transfusers);
            playerConfig.set("contents." + name, null);
            savePlayerConfig(player, playerConfig);

            playerTransfusers.get(playerUUID).remove(name);
            player.sendMessage("Destroyed the " + name);
        } else {
            player.sendMessage("No such transfuser found.");
        }
    }

    public void renameTransfuser(Player player, String oldName, String newName) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration playerConfig = getPlayerConfig(player);

        List<String> transfusers = playerConfig.getStringList("transfusers");

        // Check if the new name already exists or if old name does not exist
        if (transfusers.contains(newName) || !transfusers.contains(oldName)) {
            player.sendMessage("Cannot rename transfuser: " + oldName + " to " + newName);
            return;
        }

        // Update transfusers list
        int index = transfusers.indexOf(oldName);
        transfusers.set(index, newName);
        playerConfig.set("transfusers", transfusers);

        // Update contents section
        ConfigurationSection contents = playerConfig.getConfigurationSection("contents");
        if (contents != null && contents.contains(oldName)) {
            ConfigurationSection oldTransfuser = contents.getConfigurationSection(oldName);
            if (oldTransfuser != null) {
                int size = oldTransfuser.getInt("size");
                List<?> items = oldTransfuser.getList("items");
                playerConfig.set("contents." + newName + ".size", size);
                playerConfig.set("contents." + newName + ".items", items);
            }
            // Remove old transfuser entry
            playerConfig.set("contents." + oldName, null);
        }

        savePlayerConfig(player, playerConfig);
        player.sendMessage("Renamed the transfuser from " + oldName + " to " + newName);
    }

    public void saveTransfuserContent(Player player, String name, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration playerConfig = getPlayerConfig(player);
        List<ItemStack> items = Arrays.asList(inventory.getContents());
        playerConfig.set("contents." + name + ".size", inventory.getSize());
        playerConfig.set("contents." + name + ".items", items);
        savePlayerConfig(player, playerConfig);
    }

    public void loadAllTransfusers() {
        File playersDir = new File(getDataFolder(), "playerdata");
        if (playersDir.exists() && playersDir.isDirectory()) {
            for (File playerFile : playersDir.listFiles()) {
                if (playerFile.isFile() && playerFile.getName().endsWith(".yml")) {
                    UUID playerUUID = UUID.fromString(playerFile.getName().replace(".yml", ""));
                    FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                    playerTransfusers.put(playerUUID, new HashMap<>());

                    List<String> transfusers = playerConfig.getStringList("transfusers");
                    for (String transfuser : transfusers) {
                        List<?> itemList = playerConfig.getList("contents." + transfuser + ".items");
                        int size = playerConfig.getInt("contents." + transfuser + ".size", 27);
                        Inventory inventory = Bukkit.createInventory(null, size, transfuser);
                        if (itemList != null) {
                            ItemStack[] items = itemList.toArray(new ItemStack[0]);
                            inventory.setContents(items);
                        }
                        playerTransfusers.get(playerUUID).put(transfuser, inventory);
                    }
                }
            }
        }
    }

    public void saveAllTransfusers() {
        for (UUID playerUUID : playerTransfusers.keySet()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                FileConfiguration playerConfig = getPlayerConfig(player);
                for (Map.Entry<String, Inventory> entry : playerTransfusers.get(playerUUID).entrySet()) {
                    saveTransfuserContent(player, entry.getKey(), entry.getValue());
                }
                savePlayerConfig(player, playerConfig);
            }
        }
    }

    private FileConfiguration getPlayerConfig(Player player) {
        File playerFile = new File(getDataFolder(), "playerdata/" + player.getUniqueId() + ".yml");
        if (!playerFile.exists()) {
            try {
                playerFile.getParentFile().mkdirs();
                playerFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(playerFile);
    }

    private void savePlayerConfig(Player player, FileConfiguration playerConfig) {
        File playerFile = new File(getDataFolder(), "playerdata/" + player.getUniqueId() + ".yml");
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openCreateTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "Create Transfuser");

        ItemStack smallTransfuser = new ItemStack(Material.ENDER_CHEST);
        ItemMeta smallMeta = smallTransfuser.getItemMeta();
        smallMeta.setDisplayName("Small Transfuser (8 slots)");
        smallTransfuser.setItemMeta(smallMeta);
        gui.setItem(0, smallTransfuser);

        ItemStack mediumTransfuser = new ItemStack(Material.ENDER_CHEST);
        ItemMeta mediumMeta = mediumTransfuser.getItemMeta();
        mediumMeta.setDisplayName("Medium Transfuser (18 slots)");
        mediumTransfuser.setItemMeta(mediumMeta);
        gui.setItem(1, mediumTransfuser);

        ItemStack largeTransfuser = new ItemStack(Material.ENDER_CHEST);
        ItemMeta largeMeta = largeTransfuser.getItemMeta();
        largeMeta.setDisplayName("Large Transfuser (27 slots)");
        largeTransfuser.setItemMeta(largeMeta);
        gui.setItem(2, largeTransfuser);

        player.openInventory(gui);
    }

    public void openDestroyTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Destroy Transfuser");
        List<String> transfusers = getPlayerConfig(player).getStringList("transfusers");
        for (String transfuser : transfusers) {
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(transfuser);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    public void openRenameTransfuserMenu(Player player, String newName) {
        Inventory gui = Bukkit.createInventory(null, 27, "Rename Transfuser to " + ChatColor.translateAlternateColorCodes('&', newName));
        List<String> transfusers = getPlayerConfig(player).getStringList("transfusers");
        for (String transfuser : transfusers) {
            ItemStack item = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(transfuser);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    public void giveTransfuserRemote(Player player, int amount) {
        FileConfiguration config = getConfig();
        String materialName = config.getString("Transfuser-Remote-Item.Material");
        Material material = Material.valueOf(materialName);

        ItemStack remote = new ItemStack(material, amount);
        ItemMeta meta = remote.getItemMeta();

        String displayName = ChatColor.translateAlternateColorCodes('&', config.getString("Transfuser-Remote-Item.Name"));
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        for (String line : config.getString("Transfuser-Remote-Item.Lore").split("\\\\n")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);

        int customModelData = config.getInt("Transfuser-Remote-Item.CustomModelData");
        meta.setCustomModelData(customModelData);

        remote.setItemMeta(meta);
        player.getInventory().addItem(remote);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been given " + amount + " Transfuser Remote(s)."));
    }

}
