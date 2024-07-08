package net.stardew.transfuser;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Transfuser extends JavaPlugin implements Listener, TabExecutor {

    private final HashMap<UUID, HashMap<String, Inventory>> playerTransfusers = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("transfuser").setExecutor(this);
        this.getCommand("transfuser").setTabCompleter(this);
        saveDefaultConfig();
        loadAllTransfusers();
    }

    @Override
    public void onDisable() {
        saveAllTransfusers();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.FEATHER) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 10027) {
                openTransfuserMenu(event.getPlayer());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Create Transfuser")) {
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
        if (!title.equals("Create Transfuser") && !title.equals("Your Transfusers") && !title.equals("Destroy Transfuser")) {
            saveTransfuserContent((Player) event.getPlayer(), title, event.getInventory());
        }
    }

    private void handleCreateTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.CHEST) {
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
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                openTransfuser(player, displayName);
            }
        }
    }

    private void handleDestroyTransfuserClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.CHEST) {
            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta != null) {
                String displayName = meta.getDisplayName();
                destroyTransfuser(player, displayName);
                player.closeInventory();
            }
        }
    }

    private void openTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Your Transfusers");
        List<String> transfusers = getPlayerConfig(player).getStringList("transfusers");
        for (String transfuser : transfusers) {
            ItemStack item = new ItemStack(Material.CHEST);
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
            int size = playerConfig.getInt("contents." + name + ".size", 27); // Default size, customize based on transfuser type if needed
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

    private void destroyTransfuser(Player player, String name) {
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

    private void saveTransfuserContent(Player player, String name, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration playerConfig = getPlayerConfig(player);
        List<ItemStack> items = Arrays.asList(inventory.getContents());
        playerConfig.set("contents." + name + ".size", inventory.getSize());
        playerConfig.set("contents." + name + ".items", items);
        savePlayerConfig(player, playerConfig);
    }

    private void loadAllTransfusers() {
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

    private void saveAllTransfusers() {
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length > 0 && args[0].equalsIgnoreCase("create")) {
                openCreateTransfuserMenu(player);
            } else if (args.length > 0 && args[0].equalsIgnoreCase("destroy")) {
                openDestroyTransfuserMenu(player);
            } else {
                openTransfuserMenu(player);
            }
            return true;
        }
        return false;
    }

    private void openCreateTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "Create Transfuser");

        ItemStack smallTransfuser = new ItemStack(Material.CHEST);
        ItemMeta smallMeta = smallTransfuser.getItemMeta();
        smallMeta.setDisplayName("Small Transfuser (8 slots)");
        smallTransfuser.setItemMeta(smallMeta);
        gui.setItem(0, smallTransfuser);

        ItemStack mediumTransfuser = new ItemStack(Material.CHEST);
        ItemMeta mediumMeta = mediumTransfuser.getItemMeta();
        mediumMeta.setDisplayName("Medium Transfuser (18 slots)");
        mediumTransfuser.setItemMeta(mediumMeta);
        gui.setItem(1, mediumTransfuser);

        ItemStack largeTransfuser = new ItemStack(Material.CHEST);
        ItemMeta largeMeta = largeTransfuser.getItemMeta();
        largeMeta.setDisplayName("Large Transfuser (27 slots)");
        largeTransfuser.setItemMeta(largeMeta);
        gui.setItem(2, largeTransfuser);

        player.openInventory(gui);
    }

    private void openDestroyTransfuserMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Destroy Transfuser");
        List<String> transfusers = getPlayerConfig(player).getStringList("transfusers");
        for (String transfuser : transfusers) {
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(transfuser);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "destroy");
        }
        return null;
    }
}
