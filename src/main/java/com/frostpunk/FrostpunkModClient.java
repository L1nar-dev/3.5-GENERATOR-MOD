package com.frostpunk;

import com.frostpunk.network.ModPackets;
import com.frostpunk.screen.GeneratorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class FrostpunkModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(FrostpunkMod.GENERATOR_SCREEN_HANDLER, GeneratorScreen::new);
        ModPackets.registerClient();
    }
}
