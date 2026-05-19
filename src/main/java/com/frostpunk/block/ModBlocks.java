package com.frostpunk.block;

import com.frostpunk.FrostpunkMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final ControlPanelBlock CONTROL_PANEL = new ControlPanelBlock(
        AbstractBlock.Settings.create()
            .strength(5.0f, 1200.0f)
            .sounds(BlockSoundGroup.METAL)
            .nonOpaque()
    );

    public static void register() {
        registerBlock("control_panel", CONTROL_PANEL);

        // Add to tools/utilities creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(CONTROL_PANEL);
        });

        FrostpunkMod.LOGGER.info("Blocks registered.");
    }

    private static void registerBlock(String name, Block block) {
        Identifier id = Identifier.of(FrostpunkMod.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id,
            new BlockItem(block, new Item.Settings()));
    }
}
