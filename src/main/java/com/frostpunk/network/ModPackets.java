package com.frostpunk.network;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.block.ControlP
cat > src/main/java/com/frostpunk/network/ModPackets.java << 'JAVAEOF'
package com.frostpunk.network;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.block.ControlPanelBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModPackets {

    public static final CustomPayload.Id<SetPowerPacket> SET_POWER_ID =
        new CustomPayload.Id<>(Identifier.of(FrostpunkMod.MOD_ID, "set_power"));
    public static final CustomPayload.Id<SetRadiusPacket> SET_RADIUS_ID =
        new CustomPayload.Id<>(Identifier.of(FrostpunkMod.MOD_ID, "set_radius"));
    public static final CustomPayload.Id<ToggleBoostPacket> TOGGLE_BOOST_ID =
        new CustomPayload.Id<>(Identifier.of(FrostpunkMod.MOD_ID, "toggle_boost"));

    public record SetPowerPacket(BlockPos pos, int level) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SetPowerPacket> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, SetPowerPacket::pos,
                PacketCodecs.INTEGER, SetPowerPacket::level, SetPowerPacket::new);
        public Id<? extends CustomPayload> getId() { return SET_POWER_ID; }
    }

    public record SetRadiusPacket(BlockPos pos, int level) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SetRadiusPacket> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, SetRadiusPacket::pos,
                PacketCodecs.INTEGER, SetRadiusPacket::level, SetRadiusPacket::new);
        public Id<? extends CustomPayload> getId() { return SET_RADIUS_ID; }
    }

    public record ToggleBoostPacket(BlockPos pos) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ToggleBoostPacket> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, ToggleBoostPacket::pos, ToggleBoostPacket::new);
        public Id<? extends CustomPayload> getId() { return TOGGLE_BOOST_ID; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(SET_POWER_ID, SetPowerPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SET_RADIUS_ID, SetRadiusPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TOGGLE_BOOST_ID, ToggleBoostPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SET_POWER_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockEntity be = player.getWorld().getBlockEntity(payload.pos());
                if (be instanceof ControlPanelBlockEntity panel) {
                    panel.setPowerLevel(payload.level(), player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SET_RADIUS_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockEntity be = player.getWorld().getBlockEntity(payload.pos());
                if (be instanceof ControlPanelBlockEntity panel) {
                    panel.setRadiusLevel(payload.level(), player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_BOOST_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockEntity be = player.getWorld().getBlockEntity(payload.pos());
                if (be instanceof ControlPanelBlockEntity panel) {
                    panel.setBoostActive(!panel.isBoostActive(), player);
                }
            });
        });
    }

    public static void registerClient() {}
}
