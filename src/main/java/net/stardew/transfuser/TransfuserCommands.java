package net.stardew.transfuser;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class TransfuserCommands implements TabCompleter, CommandExecutor {

    private final Transfuser plugin;

    public TransfuserCommands(Transfuser plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can execute this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /transfuser [create|destroy|get <amount>|give <player> <amount>|rename]");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (player.hasPermission("transfuser.create")) {
                plugin.openCreateTransfuserMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to create a transfuser.");
            }
        } else if (args[0].equalsIgnoreCase("destroy")) {
            if (player.hasPermission("transfuser.destroy")) {
                plugin.openDestroyTransfuserMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to destroy a transfuser.");
            }
        } else if (args[0].equalsIgnoreCase("get") && args.length == 2) {
            if (player.hasPermission("transfuser.admin")) {
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                plugin.giveTransfuserRemote(player, amount);
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
        } else if (args[0].equalsIgnoreCase("give") && args.length == 3) {
            if (player.hasPermission("transfuser.admin")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid amount.");
                        return true;
                    }
                    plugin.giveTransfuserRemote(target, amount);
                } else {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
        } else if (args[0].equalsIgnoreCase("rename")) {
            if (player.hasPermission("transfuser.rename")) {
                if (args.length == 2) {
                    String newName = args[1];
                    plugin.openRenameTransfuserMenu(player, newName);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /transfuser rename <newname>");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to rename a transfuser.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /transfuser [create|destroy|get <amount>|give <player> <amount>|rename]");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "destroy", "get", "give", "rename"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        return completions;
    }
}