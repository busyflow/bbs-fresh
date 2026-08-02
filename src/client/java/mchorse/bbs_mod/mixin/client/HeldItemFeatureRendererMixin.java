package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.HeldItemPoseContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds BBS item-only poses to MobForm's vanilla Steve/Alex and mob item renderer. */
@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemFeatureRendererMixin
{
    private boolean bbs$pushedItemPose;

    @Inject(
        method = "renderItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        )
    )
    private void bbs$beforeRenderPosedItem(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info)
    {
        this.bbs$pushedItemPose = false;
        matrices.push();
        this.bbs$pushedItemPose = HeldItemPoseContext.apply(matrices, entity, arm);

        if (!this.bbs$pushedItemPose)
        {
            matrices.pop();
        }
    }

    @Inject(
        method = "renderItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbs$afterRenderPosedItem(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info)
    {
        if (this.bbs$pushedItemPose)
        {
            matrices.pop();
            this.bbs$pushedItemPose = false;
        }
    }
}
