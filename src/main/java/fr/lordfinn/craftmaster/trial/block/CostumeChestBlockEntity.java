package fr.lordfinn.craftmaster.trial.block;

import fr.lordfinn.craftmaster.trial.TrialContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The stock of a {@link CostumeChestBlock}, and the endless view of it the players get. */
public class CostumeChestBlockEntity extends BlockEntity {

    public static final int SIZE = 27;

    private final SimpleContainer stock = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            CostumeChestBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !CostumeChestBlockEntity.this.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64;
        }
    };

    public CostumeChestBlockEntity(BlockPos pos, BlockState state) {
        super(TrialContent.COSTUME_CHEST_ENTITY, pos, state);
        fillWithCostumes();
    }

    /** The stock itself (organisers edit it as a chest). */
    public Container stock() {
        return stock;
    }

    /** What players see: the stock, but taking gives a copy and putting back makes the item go away. */
    public Container copies() {
        return new Copies();
    }

    private final class Copies implements Container {
        @Override public int getContainerSize() { return SIZE; }
        @Override public boolean isEmpty() { return stock.isEmpty(); }
        /** A fresh copy each time: whatever the menu does to it never touches the stock. */
        @Override public ItemStack getItem(int slot) { return stock.getItem(slot).copy(); }
        @Override public ItemStack removeItem(int slot, int count) {
            ItemStack s = stock.getItem(slot);
            return s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(Math.min(count, s.getCount()));
        }
        @Override public ItemStack removeItemNoUpdate(int slot) { return stock.getItem(slot).copy(); }
        /** Put back (or swapped): the item goes back into the trunk, the stock stays as it is. */
        @Override public void setItem(int slot, ItemStack stack) {}
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return stock.stillValid(player); }
        @Override public void clearContent() {}
    }

    /** The default stock of a new trunk: something to dress up and something to play with. */
    private void fillWithCostumes() {
        int i = 0;
        int[] colors = {0xB02E26, 0x3C44AA, 0xF9FFFE, 0x1D1D21, 0xFED83D, 0x5E7C16};
        String[] colorNames = {"rouge", "bleu", "blanc", "noir", "jaune", "vert"};
        // A royal outfit, a detective, a chef... in a few colours
        stock.setItem(i++, named(Items.GOLDEN_HELMET, "Couronne"));
        stock.setItem(i++, dyed(Items.LEATHER_CHESTPLATE, colors[0], "Cape " + colorNames[0]));
        stock.setItem(i++, dyed(Items.LEATHER_HELMET, colors[3], "Chapeau " + colorNames[3]));
        stock.setItem(i++, dyed(Items.LEATHER_CHESTPLATE, colors[1], "Veste " + colorNames[1]));
        stock.setItem(i++, dyed(Items.LEATHER_LEGGINGS, colors[2], "Pantalon " + colorNames[2]));
        stock.setItem(i++, dyed(Items.LEATHER_BOOTS, colors[4], "Bottes " + colorNames[4]));
        stock.setItem(i++, dyed(Items.LEATHER_HELMET, colors[2], "Toque de chef"));
        stock.setItem(i++, dyed(Items.LEATHER_CHESTPLATE, colors[5], "Gilet " + colorNames[5]));
        stock.setItem(i++, new ItemStack(Items.CARVED_PUMPKIN));
        stock.setItem(i++, new ItemStack(Items.ZOMBIE_HEAD));
        stock.setItem(i++, new ItemStack(Items.SKELETON_SKULL));
        stock.setItem(i++, new ItemStack(Items.CREEPER_HEAD));
        stock.setItem(i++, new ItemStack(Items.PIGLIN_HEAD));
        stock.setItem(i++, new ItemStack(Items.TURTLE_HELMET));
        stock.setItem(i++, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        stock.setItem(i++, new ItemStack(Items.POPPY));
        stock.setItem(i++, new ItemStack(Items.SUNFLOWER));
        stock.setItem(i++, new ItemStack(Items.FEATHER));
        stock.setItem(i++, named(Items.STICK, "Baguette magique"));
        stock.setItem(i++, named(Items.BLAZE_ROD, "Sceptre"));
        stock.setItem(i++, new ItemStack(Items.BELL));
        stock.setItem(i++, new ItemStack(Items.SHIELD));
        stock.setItem(i++, new ItemStack(Items.LANTERN));
        stock.setItem(i++, new ItemStack(Items.BOOK));
        stock.setItem(i++, new ItemStack(Items.SPYGLASS));
        stock.setItem(i++, new ItemStack(Items.CAKE));
        stock.setItem(i, new ItemStack(Items.NAME_TAG));
    }

    private static ItemStack named(Item item, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
        return s;
    }

    private static ItemStack dyed(Item item, int rgb, String name) {
        ItemStack s = named(item, name);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return s;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        if (in.childrenList("Items").isPresent()) {
            stock.clearContent();
            ContainerHelper.loadAllItems(in, stock.getItems());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, stock.getItems(), true);
    }
}
