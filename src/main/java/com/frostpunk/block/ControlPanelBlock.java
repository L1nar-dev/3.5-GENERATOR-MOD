package com.frostpunk.block;

import com.frostpunk.util.LuckPermsUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ControlPanelBlock extends BlockWithEntity {

    public ControlPanelBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ControlPanelBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Check LuckPerms engineer role
            if (!LuckPermsUtil.hasEngineerRole(player)) {
                player.sendMessage(
                    Text.literal("§c[GENERATOR] §7Только §6Инженер §7может управлять генератором."),
                    true
                );
                return ActionResult.FAIL;
            }

            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ControlPanelBlockEntity panel) {
                NamedScreenHandlerFactory factory = panel.createScreenHandlerFactory(state, world, pos);
                if (factory != null) {
                    player.openHandledScreen(factory);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.CONTROL_PANEL_ENTITY,
                ControlPanelBlockEntity::tick);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
