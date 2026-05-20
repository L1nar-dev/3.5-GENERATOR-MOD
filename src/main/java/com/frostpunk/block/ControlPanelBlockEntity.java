package com.frostpunk.block;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.screen.GeneratorScreenHandler;
import com.frostpunk.util.ColdSweatUtil;
import com.frostpunk.util.GeneratorEventLog;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class ControlPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    // Power levels 1-4
    private int powerLevel = 1;
    // Radius levels 1-4
    private int radiusLevel = 1;
    // Is boost unlocked by operator
    private boolean boostUnlocked = false;
    // Is boost currently active
    private boolean boostActive = false;
    // Boost overload 0-100
    private int overloadPercent = 0;
    // Is generator broken
    private boolean broken = false;
    // Is generator running
    private boolean running = false;
    // Outside temperature in Celsius (set by command)
    private int outsideTemp = -10;
    // Coal inventory (1 slot)
    private final SimpleInventory coalInventory = new SimpleInventory(1);
    // Ticks counter
    private int tickCounter = 0;
    // Random for failures
    private final Random random = new Random();
    // Coal consumption ticks (20 sec = 400 ticks)
    private static final int COAL_TICK_INTERVAL = 400;
    // Overload increase per second when boost active
    private static final int OVERLOAD_PER_SECOND = 1; // 100 seconds to explode

    // Coal per minute per power level
    public static final int[] COAL_PER_MIN = {0, 1, 2, 4, 7};
    // Coal consumed per interval (every 20 sec = 1/3 of per-minute)
    // We consume every 20 ticks*20 = 400 ticks, so per interval = perMin / 3
    // Actually we just tick every 400 ticks and consume perMin/3, rounded

    // Radius in blocks per level
    public static final int[] RADIUS_BLOCKS = {0, 30, 45, 65, 100};

    // Heat zone thresholds (Celsius)
    // comfortable >= -5, acceptable >= -15, cool >= -25, cold >= -35, very cold >= -45, freezing < -45
    public static final int[] HEAT_THRESHOLDS = {-5, -15, -25, -35, -45};

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_PANEL_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ControlPanelBlockEntity be) {
        if (world.isClient) return;
        be.tickCounter++;

        // Random breakdown when boost is locked (generator in bad condition)
        if (!be.boostUnlocked && !be.broken && be.running) {
            // ~0.5% chance per second (every 20 ticks)
            if (be.tickCounter % 20 == 0 && be.random.nextInt(200) == 0) {
                be.triggerBreakdown(world);
            }
        }

        // Boost overload
        if (be.boostActive && !be.broken && be.running) {
            if (be.tickCounter % 20 == 0) {
                be.overloadPercent += OVERLOAD_PER_SECOND;
                if (be.overloadPercent >= 100) {
                    be.triggerExplosion(world, pos);
                    return;
                }
                be.markDirty();
                be.sync();
            }
        }

        // Coal consumption every 400 ticks (20 sec)
        if (be.tickCounter % COAL_TICK_INTERVAL == 0) {
            if (!be.broken) {
                be.consumeCoal(world, pos);
            }
        }

        // Snow melting in radius every 5 seconds
        if (be.tickCounter % 100 == 0 && be.running && !be.broken) {
            be.meltSnowInRadius(world, pos);
        }

        // Update Cold Sweat temperature every 5 seconds
        if (be.tickCounter % 100 == 0) {
            ColdSweatUtil.updateZoneTemperature(world, pos,
                be.running && !be.broken ? be.radiusLevel : 0,
                be.outsideTemp, be.powerLevel, be.boostActive);
        }
    }

    private void consumeCoal(World world, BlockPos pos) {
        if (!running) return;

        int coalNeeded = getCoalPerInterval();
        ItemStack stack = coalInventory.getStack(0);

        if (stack.isEmpty() || stack.getCount() < coalNeeded) {
            // Not enough coal - shutdown
            running = false;
            broadcastMessage(world, "§c[ГЕНЕРАТОР] §7Уголь закончился! Генератор отключается...");
            markDirty();
            sync();
            return;
        }

        stack.decrement(coalNeeded);
        if (stack.isEmpty()) coalInventory.setStack(0, ItemStack.EMPTY);

        // Warn when low
        int remaining = coalInventory.getStack(0).getCount();
        if (remaining <= 8) {
            broadcastMessage(world, "§e[ГЕНЕРАТОР] §7Уголь на исходе! Осталось: §c" + remaining + " §7ед.");
        }

        markDirty();
        sync();
    }

    private int getCoalPerInterval() {
        // Every 20 sec = 1/3 of per-minute consumption
        int perMin = COAL_PER_MIN[powerLevel];
        int boost = boostActive ? 5 : 0;
        // 3 intervals per minute, round up
        return Math.max(1, (int) Math.ceil((perMin + boost) / 3.0));
    }

    private void triggerBreakdown(World world) {
        broken = true;
        running = false;
        broadcastMessage(world, "§c[ГЕНЕРАТОР] §7⚠ Генератор сломался! Требуется ремонт.");
        GeneratorEventLog.log("BREAKDOWN", "Generator broke down spontaneously");
        markDirty();
        sync();
    }

    private void triggerExplosion(World world, BlockPos pos) {
        overloadPercent = 100;
        broken = true;
        running = false;
        boostActive = false;
        broadcastMessage(world, "§4[ГЕНЕРАТОР] §c☠ ПЕРЕГРУЗКА! ГЕНЕРАТОР ВЗОРВАЛСЯ!");
        GeneratorEventLog.log("EXPLOSION", "Generator exploded from overdrive overload");
        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            3.5f, World.ExplosionSourceType.BLOCK);
        markDirty();
        sync();
    }

    private void meltSnowInRadius(World world, BlockPos pos) {
        int radius = RADIUS_BLOCKS[radiusLevel];
        // Only melt snow within current radius, check a sample of blocks for performance
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(radius * 2) - radius;
            int dz = random.nextInt(radius * 2) - radius;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;

            BlockPos checkPos = pos.add(dx, 0, dz);
            // Check a few Y levels
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos snowPos = checkPos.add(0, dy, 0);
                BlockState snowState = world.getBlockState(snowPos);
                if (snowState.isOf(Blocks.SNOW) || snowState.isOf(Blocks.SNOW_BLOCK)) {
                    world.setBlockState(snowPos, Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    private void broadcastMessage(World world, String message) {
        if (world.getServer() == null) return;
        world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
    }

    // === Actions called from screen handler ===

    public void setPowerLevel(int level, ServerPlayerEntity player) {
        if (broken) {
            player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Генератор сломан!"), true);
            return;
        }
        if (level < 1 || level > 4) return;
        this.powerLevel = level;
        this.running = true;
        GeneratorEventLog.log("POWER", player.getName().getString() + " set power to level " + level);
        markDirty();
        sync();
    }

    public void setRadiusLevel(int level, ServerPlayerEntity player) {
        if (level < 1 || level > 4) return;
        this.radiusLevel = level;
        GeneratorEventLog.log("RADIUS", player.getName().getString() + " set radius to level " + level + " (" + RADIUS_BLOCKS[level] + " blocks)");
        markDirty();
        sync();
    }

    public void setBoostActive(boolean active, ServerPlayerEntity player) {
        if (!boostUnlocked) {
            player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Форсаж заблокирован!"), true);
            return;
        }
        if (broken) {
            player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Генератор сломан!"), true);
            return;
        }
        this.boostActive = active;
        GeneratorEventLog.log("BOOST", player.getName().getString() + (active ? " activated" : " deactivated") + " overdrive");
        if (active) {
            broadcastMessage(player.getWorld(), "§c[ГЕНЕРАТОР] §7⚡ Форсаж активирован! Следите за перегрузкой!");
        }
        markDirty();
        sync();
    }

    // === Operator commands ===

    public void unlockBoost() {
        boostUnlocked = true;
        overloadPercent = 0;
        GeneratorEventLog.log("SYSTEM", "Overdrive unlocked by operator");
        markDirty();
        sync();
    }

    public void lockBoost() {
        boostUnlocked = false;
        boostActive = false;
        GeneratorEventLog.log("SYSTEM", "Overdrive locked by operator");
        markDirty();
        sync();
    }

    public void repair() {
        broken = false;
        overloadPercent = 0;
        boostActive = false;
        GeneratorEventLog.log("SYSTEM", "Generator repaired by operator");
        markDirty();
        sync();
    }

    public void setOutsideTemp(int celsius) {
        this.outsideTemp = celsius;
        GeneratorEventLog.log("TEMP", "Outside temperature set to " + celsius + "°C");
        markDirty();
        sync();
    }

    // === Getters ===

    public int getPowerLevel() { return powerLevel; }
    public int getRadiusLevel() { return radiusLevel; }
    public boolean isBoostUnlocked() { return boostUnlocked; }
    public boolean isBoostActive() { return boostActive; }
    public int getOverloadPercent() { return overloadPercent; }
    public boolean isBroken() { return broken; }
    public boolean isRunning() { return running; }
    public int getOutsideTemp() { return outsideTemp; }
    public SimpleInventory getCoalInventory() { return coalInventory; }

    public int getHeatZone() {
        if (!running || broken) return 6; // freezing
        int temp = outsideTemp;
        // Each power level adds effective warmth
        int effectiveTemp = temp + (powerLevel * 8) + (boostActive ? 12 : 0);
        if (effectiveTemp >= -5) return 1;   // comfortable
        if (effectiveTemp >= -15) return 2;  // acceptable
        if (effectiveTemp >= -25) return 3;  // cool
        if (effectiveTemp >= -35) return 4;  // cold
        if (effectiveTemp >= -45) return 5;  // very cold
        return 6;                            // freezing
    }

    public int getCoalCount() {
        return coalInventory.getStack(0).getCount();
    }

    public int getMinutesOfCoalLeft() {
        int perMin = COAL_PER_MIN[powerLevel] + (boostActive ? 5 : 0);
        if (perMin == 0) return 999;
        return getCoalCount() / perMin;
    }

    // === NBT ===

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("PowerLevel", powerLevel);
        nbt.putInt("RadiusLevel", radiusLevel);
        nbt.putBoolean("BoostUnlocked", boostUnlocked);
        nbt.putBoolean("BoostActive", boostActive);
        nbt.putInt("OverloadPercent", overloadPercent);
        nbt.putBoolean("Broken", broken);
        nbt.putBoolean("Running", running);
        nbt.putInt("OutsideTemp", outsideTemp);
        ItemStack coalStack = coalInventory.getStack(0);
            NbtCompound coalNbt = new NbtCompound();
            coalNbt.put("Stack", coalStack.encode(registries));
            nbt.put("Coal", coalNbt);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        powerLevel = nbt.getInt("PowerLevel");
        radiusLevel = nbt.getInt("RadiusLevel");
        boostUnlocked = nbt.getBoolean("BoostUnlocked");
        boostActive = nbt.getBoolean("BoostActive");
        overloadPercent = nbt.getInt("OverloadPercent");
        broken = nbt.getBoolean("Broken");
        running = nbt.getBoolean("Running");
        outsideTemp = nbt.getInt("OutsideTemp");
        if (nbt.contains("Coal")) {
            NbtCompound coalNbt = nbt.getCompound("Coal");
            if (coalNbt.contains("Stack")) {
                coalInventory.setStack(0, ItemStack.fromNbt(registries, coalNbt.getCompound("Stack")).orElse(ItemStack.EMPTY));
            }
        }
    }

    // === Sync ===

    public void sync() {
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    // === Screen handler factory ===

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GeneratorScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Generator Control Panel");
    }

    @Override
    public GeneratorScreenHandler.SyncData getScreenOpeningData(ServerPlayerEntity player) {
        return new GeneratorScreenHandler.SyncData(pos);
    }
}
