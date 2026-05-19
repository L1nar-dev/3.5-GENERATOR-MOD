package com.frostpunk;

import com.frostpunk.block.ModBlocks;
import com.frostpunk.block.ModBlockEntities;
import com.frostpunk.command.GeneratorCommands;
import com.frostpunk.screen.GeneratorScreenHandler;
import com.frostpunk.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrostpunkMod implements ModInitializer {

    public static final String MOD_ID = "frostpunk_generator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ScreenHandlerType<GeneratorScreenHandler> GENERATOR_SCREEN_HANDLER;

    @Override
    public void onInitialize() {
        LOGGER.info("Frostpunk Generator initializing...");

        ModBlocks.register();
        ModBlockEntities.register();
        ModPackets.registerServer();
        GeneratorCommands.register();

        GENERATOR_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(MOD_ID, "generator"),
            new ExtendedScreenHandlerType<>(GeneratorScreenHandler::new, GeneratorScreenHandler.DATA_CODEC)
        );

        LOGGER.info("Frostpunk Generator ready. The city must survive.");
    }
}
