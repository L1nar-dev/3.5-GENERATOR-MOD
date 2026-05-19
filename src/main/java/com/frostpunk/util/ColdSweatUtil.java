package com.frostpunk.util;

import com.frostpunk.FrostpunkMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Cold Sweat integration.
 * Cold Sweat uses its own temperature system where:
 * - 0.0 = neutral (comfortable)
 * - Positive = hot
 * - Negative = cold
 * - Range roughly -150 to 150 (in their internal units)
 *
 * We convert Celsius to Cold Sweat units:
 * 0°C = -0.5 CS units (slightly cold)
 * -10°C = ~-1.0 CS units
 * -30°C = ~-2.5 CS units
 * etc.
 */
public class ColdSweatUtil {

    private static boolean coldSweatLoaded = false;

    static {
        try {
            Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            coldSweatLoaded = true;
            FrostpunkMod.LOGGER.info("Cold Sweat detected - temperature integration active!");
        } catch (ClassNotFoundException e) {
            FrostpunkMod.LOGGER.info("Cold Sweat not found - temperature integration disabled.");
        }
    }

    /**
     * Updates the ambient temperature in Cold Sweat's system based on generator state.
     *
     * @param world       Server world
     * @param generatorPos Position of the generator
     * @param radiusLevel  Current radius level (0 = off)
     * @param outsideCelsius Outside temperature in Celsius
     * @param powerLevel   Generator power level 1-4
     * @param boostActive  Whether boost is active
     */
    public static void updateZoneTemperature(World world, BlockPos generatorPos,
                                              int radiusLevel, int outsideCelsius,
                                              int powerLevel, boolean boostActive) {
        if (!coldSweatLoaded) return;

        try {
            updateInternal(world, generatorPos, radiusLevel, outsideCelsius, powerLevel, boostActive);
        } catch (Exception e) {
            // Cold Sweat API changed or error - fail silently
        }
    }

    private static void updateInternal(World world, BlockPos pos,
                                        int radiusLevel, int outsideCelsius,
                                        int powerLevel, boolean boostActive) {
        // Convert Celsius to Cold Sweat temperature units
        // CS unit ≈ celsius * 0.08 (approximate mapping)
        double baseCS = celsiusToCS(outsideCelsius);

        // Generator warmth bonus based on power level
        double warmthBonus = switch (powerLevel) {
            case 1 -> 0.8;
            case 2 -> 1.4;
            case 3 -> 2.0;
            case 4 -> 2.8;
            default -> 0.0;
        };
        if (boostActive) warmthBonus += 1.5;

        int radius = radiusLevel > 0 ? new int[]{30, 45, 65, 100}[radiusLevel - 1] : 0;

        // Apply to all players in range
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            for (net.minecraft.server.network.ServerPlayerEntity player : serverWorld.getPlayers()) {
                double dist = player.getPos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos));

                double playerCS;
                if (radiusLevel > 0 && dist <= radius) {
                    // Inside generator warmth zone - calculate falloff
                    double falloff = 1.0 - (dist / radius) * 0.5; // 50% falloff at edge
                    playerCS = baseCS + (warmthBonus * falloff);
                } else {
                    // Outside - just outside temperature
                    playerCS = baseCS;
                }

                // Apply via Cold Sweat API
                applyColdSweatTemp(player, playerCS);
            }
        }
    }

    private static void applyColdSweatTemp(net.minecraft.server.network.ServerPlayerEntity player, double csTemp) {
        try {
            // Cold Sweat 2.x API - set world temperature modifier
            com.momosoftworks.coldsweat.api.util.Temperature.set(player,
                csTemp,
                com.momosoftworks.coldsweat.api.util.Temperature.Trait.WORLD);
        } catch (Exception e) {
            // API version mismatch - ignore
        }
    }

    /**
     * Convert Celsius to Cold Sweat internal temperature units.
     * Cold Sweat neutral is around 0.0, comfortable range is roughly -0.5 to 0.5
     */
    public static double celsiusToCS(int celsius) {
        // Mapping:
        // +20°C (warm) -> +0.5 CS
        //   0°C        -> -0.3 CS (slightly chilly)
        // -10°C        -> -0.8 CS
        // -30°C        -> -2.0 CS
        // -50°C        -> -3.5 CS
        return celsius * 0.065 - 0.3;
    }

    public static boolean isColdSweatLoaded() {
        return coldSweatLoaded;
    }
}
