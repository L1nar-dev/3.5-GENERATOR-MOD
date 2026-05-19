package com.frostpunk.block;

import com.frostpunk.FrostpunkMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<ControlPanelBlockEntity> CONTROL_PANEL_ENTITY;

    public static void register() {
        CONTROL_PANEL_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FrostpunkMod.MOD_ID, "control_panel"),
            FabricBlockEntityTypeBuilder.create(ControlPanelBlockEntity::new, ModBlocks.CONTROL_PANEL).build()
        );
        FrostpunkMod.LOGGER.info("Block entities registered.");
    }
}
