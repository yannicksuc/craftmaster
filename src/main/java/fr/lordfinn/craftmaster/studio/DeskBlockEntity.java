package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The name on a contestant's desk plate ("" = blank plate). */
public class DeskBlockEntity extends SyncedBlockEntity {

    public static final int MAX_NAME = 24;

    private String name = "";

    public DeskBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.DESK_BE, pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        String n = name == null ? "" : name.strip();
        this.name = n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        name = in.getStringOr("Name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Name", name);
    }
}
