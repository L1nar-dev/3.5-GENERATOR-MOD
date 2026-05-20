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

public class ControlPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<GeneratorScreenHandler.SyncData> {

    private int powerLevel = 1;
    private int radiusLevel = 1;
    private boolean boostUnlocked = false;
    private boolean boostActive = false;
    private int overloadPercent = 0;
    private boolean broken = false;
    private boolean running = false;
    private int outsideTemp = -10;
    private final SimpleInventory coalInventory = new SimpleInventory(1);
    private int tickCounter = 0;
    private final Random random = new Random();
    private static final int COAL_TICK_INTERVAL = 400;
    private static final int OVERLOAD_PER_SECOND = 1;
    public static final int[] COAL_PER_MIN = {0, 1, 2, 4, 7};
    public static final int[] RADIUS_BLOCKS = {0, 30, 45, 65, 100};

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_PANEL_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ControlPanelBlockEntity be) {
        if (world.isClient) return;
        be.tickCounter++;

        if (!be.boostUnlocked && !be.broken && be.running) {
            if (be.tickCounter % 20 == 0 && be.random.nextInt(200) == 0) {
                be.triggerBreakdown(world);
            }
        }

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

        if (be.tickCounter % COAL_TICK_INTERVAL == 0) {
            if (!be.broken) {
                be.consumeCoal(world, pos);
            }
        }

        if (be.tickCounter % 100 == 0 && be.running && !be.broken) {
            be.meltSnowInRadius(world, pos);
        }

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
            running = false;
            broadcastMessage(world, "§c[ГЕНЕРАТОР] §7Уголь закончился! Генератор отключается...");
            markDirty();
            sync();
            return;
        }
        stack.decrement(coalNeeded);
        if (stack.isEmpty()) coalInventory.setStack(0, ItemStack.EMPTY);
        int remaining = coalInventory.getStack(0).getCount();
        if (remaining <= 8) {
            broadcastMessage(world, "§e[ГЕНЕРАТОР] §7Уголь на исходе! Осталось: §c" + remaining + " §7ед.");
        }
        markDirty();
        sync();
    }

    private int getCoalPerInterval() {
        int perMin = COAL_PER_MIN[powerLevel];
        int boost = boostActive ? 5 : 0;
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
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(radius * 2) - radius;
            int dz = random.nextInt(radius * 2) - radius;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            BlockPos checkPos = pos.add(dx, 0, dz);
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

    public void setPowerLevel(int level, ServerPlayerEntity player) {
        if (broken) { player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Генератор сломан!"), true); return; }
        if (level < 1 || level > 4) return;
        this.powerLevel = level;
        this.running = true;
        GeneratorEventLog.log("POWER", player.getName().getString() + " set power to level " + level);
        markDirty(); sync();
    }

    public void setRadiusLevel(int level, ServerPlayerEntity player) {
        if (level < 1 || level > 4) return;
        this.radiusLevel = level;
        GeneratorEventLog.log("RADIUS", player.getName().getString() + " set radius to level " + level);
        markDirty(); sync();
    }

    public void setBoostActive(boolean active, ServerPlayerEntity player) {
        if (!boostUnlocked) { player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Форсаж заблокирован!"), true); return; }
        if (broken) { player.sendMessage(Text.literal("§c[ГЕНЕРАТОР] §7Генератор сломан!"), true); return; }
        this.boostActive = active;
        GeneratorEventLog.log("BOOST", player.getName().getString() + (active ? " activated" : " deactivated") + " overdrive");
        if (active) broadcastMessage(player.getWorld(), "§c[ГЕНЕРАТОР] §7⚡ Форсаж активирован!");
        markDirty(); sync();
    }

    public void unlockBoost() { boostUnlocked = true; overloadPercent = 0; GeneratorEventLog.log("SYSTEM", "Overdrive unlocked"); markDirty(); sync(); }
    public void lockBoost() { boostUnlocked = false; boostActive = false; GeneratorEventLog.log("SYSTEM", "Overdrive locked"); markDirty(); sync(); }
    public void repair() { broken = false; overloadPercent = 0; boostActive = false; GeneratorEventLog.log("SYSTEM", "Generator repaired"); markDirty(); sync(); }
    public void setOutsideTemp(int celsius) { this.outsideTemp = celsius; GeneratorEventLog.log("TEMP", "Temperature set to " + celsius); markDirty(); sync(); }

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
        if (!running || broken) return 6;
        int effectiveTemp = outsideTemp + (powerLevel * 8) + (boostActive ? 12 : 0);
        if (effectiveTemp >= -5) return 1;
        if (effectiveTemp >= -15) return 2;
        if (effectiveTemp >= -25) return 3;
        if (effectiveTemp >= -35) return 4;
        if (effectiveTemp >= -45) return 5;
        return 6;
    }

    public int getCoalCount() { return coalInventory.getStack(0).getCount(); }
    public int getMinutesOfCoalLeft() {
        int perMin = COAL_PER_MIN[powerLevel] + (boostActive ? 5 : 0);
        if (perMin == 0) return 999;
        return getCoalCount() / perMin;
    }

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
        if (!coalStack.isEmpty()) {
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
