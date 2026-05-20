package com.frostpunk.util;

import com.frostpunk.FrostpunkMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ThermooUtil {

    private static boolean thermooLoaded = false;

    static {
        try {
            Class.forName("net.theluckycoder.thermoo.api.temperature.TemperatureManager");
            thermooLoaded = true;
            FrostpunkMod.LOGGER.info("Thermoo detected - temperature integration active!");
        } catch (ClassNotFoundException e) {
            FrostpunkMod.LOGGER.info("Thermoo not found - temperature integration disabled.");
        }
    }

    public static void updateZoneTemperature(World world, BlockPos generatorPos,
                                              int radiusLevel, int outsideCelsius,
                                              int powerLevel, boolean boostActive) {
        if (!thermooLoaded) return;
        try {
            updateInternal(world, generatorPos, radiusLevel, outsideCelsius, powerLevel, boostActive);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void updateInternal(World world, BlockPos pos,
                                        int radiusLevel, int outsideCelsius,
                                        int powerLevel, boolean boostActive) {
        int radius = radiusLevel > 0 ? new int[]{30, 45, 65, 100}[radiusLevel - 1] : 0;

        double warmthBonus = switch (powerLevel) {
            case 1 -> 0.08; case 2 -> 0.15; case 3 -> 0.25; case 4 -> 0.35; default -> 0.0;
        };
        if (boostActive) warmthBonus += 0.2;

        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            for (net.minecraft.server.network.ServerPlayerEntity player : serverWorld.getPlayers()) {
                double dist = player.getPos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos));

                try {
                    var livingEntity = (net.minecraft.entity.LivingEntity) player;
                    var tempManager = net.theluckycoder.thermoo.api.temperature.TemperatureManager.of(livingEntity);

                    if (radiusLevel > 0 && dist <= radius) {
                        double falloff = 1.0 - (dist / radius) * 0.6;
                        int warmthTicks = (int)(warmthBonus * falloff * 100);
                        tempManager.thermoo$addTemperature(warmthTicks, net.theluckycoder.thermoo.api.temperature.TemperatureManager.TemperatureChangeSource.ENVIRONMENT);
                    } else {
                        // Outside radius - apply cold based on outside temperature
                        int coldTicks = Math.max(0, (-outsideCelsius / 5));
                        tempManager.thermoo$addTemperature(-coldTicks, net.theluckycoder.thermoo.api.temperature.TemperatureManager.TemperatureChangeSource.ENVIRONMENT);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isThermooLoaded() { return thermooLoaded; }
}
