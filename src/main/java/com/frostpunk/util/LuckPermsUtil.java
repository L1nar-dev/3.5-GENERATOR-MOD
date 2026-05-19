package com.frostpunk.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.entity.player.PlayerEntity;

public class LuckPermsUtil {

    private static final String ENGINEER_GROUP = "engineer";
    private static LuckPerms luckPerms = null;
    private static boolean available = false;

    public static void init() {
        try {
            luckPerms = LuckPermsProvider.get();
            available = true;
        } catch (IllegalStateException e) {
            available = false;
        }
    }

    public static boolean hasEngineerRole(PlayerEntity player) {
        // If LuckPerms not installed - allow everyone (or ops only)
        if (!available || luckPerms == null) {
            // Fallback: ops can use it
            return player.hasPermissionLevel(2);
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) return player.hasPermissionLevel(2);

            // Check if player is in engineer group
            return user.getNodes().stream()
                .filter(node -> node instanceof InheritanceNode)
                .map(node -> (InheritanceNode) node)
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(ENGINEER_GROUP));
        } catch (Exception e) {
            return player.hasPermissionLevel(2);
        }
    }
}
