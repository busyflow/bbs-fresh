package mchorse.bbs_mod.mixin;

import net.minecraft.block.entity.StructureBlockBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Raise the vanilla structure block's 48-block-per-axis cap to 2000, so a whole build can be saved to
 * one .nbt and used as a BBS structure form. The 48 is the [0..48] / [-48..48] clamp the block entity
 * applies to its size and offset when the values arrive; widening it lets larger regions save and load.
 */
@Mixin(StructureBlockBlockEntity.class)
public class StructureBlockSizeMixin
{
    @ModifyConstant(method = "readNbt", constant = @Constant(intValue = 48), require = 0)
    private int bbs$raiseSizeCap(int original)
    {
        return 2000;
    }
}
