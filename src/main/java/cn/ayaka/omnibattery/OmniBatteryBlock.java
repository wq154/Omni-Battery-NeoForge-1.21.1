package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import cn.ayaka.omnibattery.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 万能电池方块（移植自 1.20.1 原版）。
 * 右键（物品或空手）打开设置界面；玩家破坏时能量回写到掉落的电池物品上。
 */
public class OmniBatteryBlock extends BaseEntityBlock {
    /** 外观电量档位：0-10，每档 10%（由方块实体按能量比例更新）。 */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 10);

    private final BatteryTier tier;

    public OmniBatteryBlock(BatteryTier tier) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f));
        this.tier = tier;
        // 必须显式设置默认状态，否则 charge 属性无默认值，blockstate 匹配失败 → 方块变透明。
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGE, 10));
    }

    public BatteryTier getTier() { return tier; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new OmniBatteryBlock(tier));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OmniBatteryBlockEntity(pos, state, tier);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.OMNI_BATTERY.get(), OmniBatteryBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 电池外形：body 12x14x12（含底盘和帽子）+ 顶部凸头 4x1x4。
     * 与模型的 elements 严格对齐，避免选择框超出真实网格造成"透视一圈"错觉。
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 15, 14),   // 底盘 + 主体 + 帽子（合并成一块 12x15x12）
            Block.box(6, 15, 6, 10, 16, 10)   // 顶部凸头
    );

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    /** 手持任意物品右键电池方块：打开设置界面（与原版一致，不再触发放置/物品 useOn）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        openGui(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 空手右键电池方块：打开设置界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        openGui(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openGui(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OmniBatteryBlockEntity battery && player instanceof ServerPlayer sp) {
            // 认领规则：放置时自动认领；打不开主界面时可用"潜行右键"补齐认领。
            // 普通右键只是查看/操作，绝不把别人（或公共）的电池变成自己的。
            if (!battery.isClaimed()) {
                if (player.isShiftKeyDown() && battery.ensureOwner(sp)) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "你已成为这个电池的主人"), true);
                } else {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "此电池尚未认领：潜行右键可认领为你的"), true);
                }
            }
            sp.openMenu(battery);
        }
    }

    /** 放置时认领：放置者即刻成为电池主人。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OmniBatteryBlockEntity battery) {
                battery.ensureOwner(p);
            }
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool) {
        // 任何模式（含创造）破坏都保留能量与配置到掉落物上
        if (!level.isClientSide && be instanceof OmniBatteryBlockEntity battery) {
            ItemStack drop = new ItemStack(itemFor(battery.getTier()));
            BatteryData.setEnergy(drop, battery.getEnergy(), battery.getTier());
            BatteryData.setMode(drop, battery.getMode());
            BatteryData.setRateIndex(drop, battery.getRateIndex());
            BatteryData.setRange(drop, battery.getTier(), battery.getRange());
            popResource(level, pos, drop);
        }
        super.playerDestroy(level, player, pos, state, be, tool);
    }

    private static Item itemFor(BatteryTier t) {
        return switch (t) {
            case LOW -> ModItems.LOW.get();
            case MEDIUM -> ModItems.MEDIUM.get();
            case ADVANCED -> ModItems.ADVANCED.get();
            case ELITE -> ModItems.ELITE.get();
            case ULTIMATE -> ModItems.ULTIMATE.get();
        };
    }
}
