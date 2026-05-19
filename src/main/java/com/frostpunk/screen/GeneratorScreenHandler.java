package com.frostpunk.screen;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.block.ControlPanelBlockEntity;
import com.frostpunk.network.ModPackets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class GeneratorScreenHandler extends ScreenHandler {

    private final ControlPanelBlockEntity blockEntity;
    private final SimpleInventory coalInventory;

    // Sync data sent when opening screen
    public record SyncData(BlockPos pos) {
        public static final Codec<SyncData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(SyncData::pos)
        ).apply(inst, SyncData::new));
    }

    public static final Codec<SyncData> DATA_CODEC = SyncData.CODEC;

    // Client-side constructor
    public GeneratorScreenHandler(int syncId, PlayerInventory playerInventory, SyncData data) {
        this(syncId, playerInventory,
            (ControlPanelBlockEntity) playerInventory.player.getWorld().getBlockEntity(data.pos()));
    }

    // Server-side constructor
    public GeneratorScreenHandler(int syncId, PlayerInventory playerInventory, ControlPanelBlockEntity blockEntity) {
        super(FrostpunkMod.GENERATOR_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.coalInventory = blockEntity != null ? blockEntity.getCoalInventory() : new SimpleInventory(1);

        // Coal slot — only accepts coal items
        this.addSlot(new Slot(coalInventory, 0, 26, 145) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.COAL) || stack.isOf(Items.CHARCOAL);
            }
        });

        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 174 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 232));
        }
    }

    public ControlPanelBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (index < 1) {
                if (!this.insertItem(originalStack, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.insertItem(originalStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return blockEntity != null;
    }

    // Getters for screen rendering
    public int getPowerLevel() { return blockEntity != null ? blockEntity.getPowerLevel() : 1; }
    public int getRadiusLevel() { return blockEntity != null ? blockEntity.getRadiusLevel() : 1; }
    public boolean isBoostUnlocked() { return blockEntity != null && blockEntity.isBoostUnlocked(); }
    public boolean isBoostActive() { return blockEntity != null && blockEntity.isBoostActive(); }
    public int getOverloadPercent() { return blockEntity != null ? blockEntity.getOverloadPercent() : 0; }
    public boolean isBroken() { return blockEntity != null && blockEntity.isBroken(); }
    public boolean isRunning() { return blockEntity != null && blockEntity.isRunning(); }
    public int getOutsideTemp() { return blockEntity != null ? blockEntity.getOutsideTemp() : -10; }
    public int getHeatZone() { return blockEntity != null ? blockEntity.getHeatZone() : 6; }
    public int getCoalCount() { return blockEntity != null ? blockEntity.getCoalCount() : 0; }
    public int getMinutesOfCoalLeft() { return blockEntity != null ? blockEntity.getMinutesOfCoalLeft() : 0; }
}
