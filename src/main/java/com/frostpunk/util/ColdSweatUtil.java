package com.frostpunk.util;

import com.frostpunk.FrostpunkMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ColdSweatUtil {

    private static boolean coldSweatLoaded = false;

    static {
        try {
            Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            coldSweatLoaded = true;
            FrostpunkMod.LOGGER.info("Cold Sweat detected!");
        } catch (ClassNotFoundException e) {
            FrostpunkMod.LOGGER.info("Cold Sweat not found - integration disabled.");
        }
    }

    public static void updateZoneTemperature(World world, BlockPos generatorPos,
                                              int radiusLevel, int outsideCelsius,
                                              int powerLevel, boolean boostActive) {
        if (!coldSweatLoaded) return;
        try {
            updateInternal(world, generatorPos, radiusLevel, outsideCelsius, powerLevel, boostActive);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void updateInternal(World world, BlockPos pos,
                                        int radiusLevel, int outsideCelsius,
                                        int powerLevel, boolean boostActive) {
        double baseCS = celsiusToCS(outsideCelsius);
        double warmthBonus = switch (powerLevel) {
            case 1 -> 0.8; case 2 -> 1.4; case 3 -> 2.0; case 4 -> 2.8; default -> 0.0;
        };
        if (boostActive) warmthBonus += 1.5;
        int radius = radiusLevel > 0 ? new int[]{30, 45, 65, 100}[radiusLevel - 1] : 0;

        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            for (net.minecraft.server.network.ServerPlayerEntity player : serverWorld.getPlayers()) {
                double dist = player.getPos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos));
                double playerCS = (radiusLevel > 0 && dist <= radius)
                    ? baseCS + warmthBonus * (1.0 - (dist / radius) * 0.5)
                    : baseCS;
                try {
                    Object tempClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
                    java.lang.reflect.Method setMethod = tempClass.getClass().getMethod("set",
                        net.minecraft.entity.player.PlayerEntity.class, double.class, String.class);
                    setMethod.invoke(null, player, playerCS, "world");
                } catch (Exception ignored) {}
            }
        }
    }

    public static double celsiusToCS(int celsius) {
        return celsius * 0.065 - 0.3;
    }

    public static boolean isColdSweatLoaded() { return coldSweatLoaded; }
}
