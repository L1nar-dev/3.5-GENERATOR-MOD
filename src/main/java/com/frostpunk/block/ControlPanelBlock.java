package com.frostpunk.block;

import com.frostpunk.util.LuckPermsUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.frostpunk.block.ModBlockEntities;

public class ControlPanelBlock extends BlockWithEntity {
    public static final MapCodec<ControlPanelBlock> CODEC = createCodec(ControlPanelBlock::new);

    public ControlPanelBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.CONTROL_PANEL_ENTITY.instantiate(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Проверка роли инженера через LuckPerms
            if (!LuckPermsUtil.hasEngineerRole(player)) {
                player.sendMessage(Text.literal("§c[GENERATOR] §7Только §6Инженер §7может управлять генератором."), true);
                return ActionResult.FAIL;
            }

            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof NamedScreenHandlerFactory factory) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.CONTROL_PANEL_ENTITY, (w, p, s, be) -> {
            // Сюда можно вписать логику тиков, если Claude добавил метод tick
        });
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
