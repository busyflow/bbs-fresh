package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.FirstPersonReplayHands;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    private Hand bbs$renderingReplayHand;

    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm)
    {}

    @Shadow
    private void renderMapInOneHand(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, Arm arm, float swingProgress, ItemStack stack)
    {}

    @Shadow
    private void renderMapInBothHands(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float pitch, float equipProgress, float swingProgress)
    {}

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    public void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info)
    {
        this.bbs$renderingReplayHand = hand;

        if (!FirstPersonReplayHands.shouldRenderHand(hand))
        {
            this.bbs$renderingReplayHand = null;
            info.cancel();
            return;
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    public void onRenderFirstPersonItemTail(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info)
    {
        if (FirstPersonReplayHands.shouldRenderExtraOffhandArm(hand, item))
        {
            this.renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, swingProgress, FirstPersonReplayHands.getArm(hand));
        }

        this.bbs$renderingReplayHand = null;
    }

    /**
     * The regular first-person item call happens after vanilla's arm animation.
     * Wrapping it here keeps the pose transform on the item rather than the arm.
     */
    @Redirect(
        method = "renderFirstPersonItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        )
    )
    private void bbs$renderPosedReplayItem(HeldItemRenderer renderer, LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        this.bbs$renderItemWithReplayPose(() -> renderer.renderItem(entity, stack, mode, leftHanded, matrices, consumers, light), matrices);
    }

    @Redirect(
        method = "renderFirstPersonItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderMapInOneHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFLnet/minecraft/util/Arm;FLnet/minecraft/item/ItemStack;)V"
        )
    )
    private void bbs$renderPosedReplayMapInOneHand(HeldItemRenderer renderer, MatrixStack matrices, VertexConsumerProvider consumers, int light, float equipProgress, Arm arm, float swingProgress, ItemStack stack)
    {
        this.bbs$renderItemWithReplayPose(() -> this.renderMapInOneHand(matrices, consumers, light, equipProgress, arm, swingProgress, stack), matrices);
    }

    @Redirect(
        method = "renderFirstPersonItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderMapInBothHands(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFFF)V"
        )
    )
    private void bbs$renderPosedReplayMapInBothHands(HeldItemRenderer renderer, MatrixStack matrices, VertexConsumerProvider consumers, int light, float pitch, float equipProgress, float swingProgress)
    {
        this.bbs$renderItemWithReplayPose(() -> this.renderMapInBothHands(matrices, consumers, light, pitch, equipProgress, swingProgress), matrices);
    }

    private void bbs$renderItemWithReplayPose(Runnable render, MatrixStack matrices)
    {
        if (!FirstPersonReplayHands.isRendering() || this.bbs$renderingReplayHand == null)
        {
            render.run();
            return;
        }

        matrices.push();
        FirstPersonReplayHands.applyItemTransform(matrices, this.bbs$renderingReplayHand);

        try
        {
            render.run();
        }
        finally
        {
            matrices.pop();
        }
    }

    @Inject(method = "getHandRenderType", at = @At("RETURN"), cancellable = true)
    private static void onGetHandRenderType(ClientPlayerEntity player, CallbackInfoReturnable info)
    {
        if (FirstPersonReplayHands.shouldForceRenderBothHands(player) && info.getReturnValue() != null)
        {
            info.setReturnValue(Enum.valueOf((Class) info.getReturnValue().getClass(), "RENDER_BOTH_HANDS"));
        }
    }
}
