package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A graduated container: a measuring glass (1 L) or a tank (8 L). Water goes in with a bucket (1000 mL) or a
 * water bottle (250 mL), comes out the same way, and trickles in from water running just above it. Empty hand:
 * the exact level, with the buttons to write it on the results sheet; an organiser sneaking draws the line at
 * the current level ("fill it up to the line"), the reading then says how far it is. Comparator: the level.
 */
public class MeasuringGlassBlock extends Block implements EntityBlock {

    public enum Size {
        GLASS("verre doseur", 1000, Block.box(5, 0, 5, 11, 10, 11)),
        TANK("cuve graduée", 8000, Block.box(1, 0, 1, 15, 16, 15));

        public final String fr;
        public final int capacity;
        final VoxelShape shape;

        Size(String fr, int capacity, VoxelShape shape) {
            this.fr = fr;
            this.capacity = capacity;
            this.shape = shape;
        }
    }

    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, GlassLevel.STAGES);

    private final Size size;

    public MeasuringGlassBlock(Size size, Properties props) {
        super(props);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    public Size size() {
        return size;
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FILL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeasuringGlassBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != Toys.GLASS_ENTITY ? null
                : (l, p, s, be) -> ((MeasuringGlassBlockEntity) be).serverTick((ServerLevel) l, p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return size.shape;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MeasuringGlassBlockEntity be)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        GlassLevel g = be.level();
        ItemStack result = null;
        SoundEvent sound = null;
        if (stack.is(Items.WATER_BUCKET)) {
            if (g.full()) return full(player);
            g.pour(GlassLevel.BUCKET);
            result = new ItemStack(Items.BUCKET);
            sound = SoundEvents.BUCKET_EMPTY;
        } else if (stack.is(Items.BUCKET)) {
            if (!g.take(GlassLevel.BUCKET)) return notEnough(player, g);
            result = new ItemStack(Items.WATER_BUCKET);
            sound = SoundEvents.BUCKET_FILL;
        } else if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            if (g.full()) return full(player);
            g.pour(GlassLevel.BOTTLE);
            result = new ItemStack(Items.GLASS_BOTTLE);
            sound = SoundEvents.BOTTLE_EMPTY;
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            if (!g.take(GlassLevel.BOTTLE)) return notEnough(player, g);
            result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            sound = SoundEvents.BOTTLE_FILL;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel sl) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
            sl.playSound(null, pos, sound, SoundSource.BLOCKS, 1f, 1f);
            be.changed();
            if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(reading(g));
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult full(Player player) {
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("Plein !").withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult notEnough(Player player, GlassLevel g) {
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("Pas assez d'eau (" + GlassLevel.format(g.ml()) + ")").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static Component reading(GlassLevel g) {
        return Component.literal("≈ " + g.reading()).withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof MeasuringGlassBlockEntity be))
            return InteractionResult.SUCCESS;
        GlassLevel g = be.level();
        if (sp.isShiftKeyDown() && Permissions.isAdmin(sp)) {
            g.setLine(g.line() == g.ml() ? -1 : g.ml());
            be.changed();
            sp.sendOverlayMessage(Component.literal(g.line() < 0 ? "Trait effacé" : "Trait tracé à " + GlassLevel.format(g.line())).withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        sp.sendOverlayMessage(reading(g));
        Judging.offerMeasure(sp, Component.literal("≈ " + capital(size.fr) + " : ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(g.reading()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)), g.ml(), ResultSheet.Kind.COUNT);
        return InteractionResult.SUCCESS;
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MeasuringGlassBlockEntity be ? be.level().signal() : 0;
    }
}
