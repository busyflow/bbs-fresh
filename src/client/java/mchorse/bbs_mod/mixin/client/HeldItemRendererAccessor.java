package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererAccessor
{
    @Accessor("mainHand") ItemStack bbs$getMainHand();
    @Accessor("mainHand") void bbs$setMainHand(ItemStack stack);
    @Accessor("offHand") ItemStack bbs$getOffHand();
    @Accessor("offHand") void bbs$setOffHand(ItemStack stack);
    @Accessor("equipProgressMainHand") float bbs$getEquipProgressMainHand();
    @Accessor("equipProgressMainHand") void bbs$setEquipProgressMainHand(float progress);
    @Accessor("prevEquipProgressMainHand") float bbs$getPrevEquipProgressMainHand();
    @Accessor("prevEquipProgressMainHand") void bbs$setPrevEquipProgressMainHand(float progress);
    @Accessor("equipProgressOffHand") float bbs$getEquipProgressOffHand();
    @Accessor("equipProgressOffHand") void bbs$setEquipProgressOffHand(float progress);
    @Accessor("prevEquipProgressOffHand") float bbs$getPrevEquipProgressOffHand();
    @Accessor("prevEquipProgressOffHand") void bbs$setPrevEquipProgressOffHand(float progress);
}
