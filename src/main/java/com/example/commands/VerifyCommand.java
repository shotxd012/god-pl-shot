package com.example.commands;

import com.example.ShotPL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class VerifyCommand implements CommandExecutor {
    private final ShotPL plugin;
    private final Random random;

    public VerifyCommand(ShotPL plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /verify <code>");
            return true;
        }

        String inputCode = args[0];
        String storedCode = plugin.getDatabaseManager().getVerificationCode(player.getUniqueId());

        if (storedCode == null) {
            // Generate new code if player doesn't have one
            String newCode = generateVerificationCode();
            plugin.getDatabaseManager().saveVerificationCode(player.getUniqueId(), newCode);
            player.sendMessage(ChatColor.YELLOW + "Your verification code is: " + ChatColor.GREEN + newCode);
            player.sendMessage(ChatColor.YELLOW + "Please use this code to verify your account.");
            return true;
        }

        if (inputCode.equals(storedCode)) {
            plugin.getDatabaseManager().setPlayerVerified(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GREEN + "Successfully verified your account!");
        } else {
            player.sendMessage(ChatColor.RED + "Invalid verification code!");
        }

        return true;
    }

    private String generateVerificationCode() {
        // Generate a random 4-character code (letters and numbers)
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }
        return code.toString();
    }
} 