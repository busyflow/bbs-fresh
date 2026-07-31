package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.FirstPersonReplayHands;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    private boolean bbs$pushedReplayHandTransform;

    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm)
    {}

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    public void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info)
    {
        this.bbs$pushedReplayHandTransform = false;

        if (!FirstPersonReplayHands.shouldRenderHand(hand))
        {
            info.cancel();
            return;
        }

        if (FirstPersonReplayHands.isRendering())
        {
            matrices.push();
            this.bbs$pushedReplayHandTransform = true;
            FirstPersonReplayHands.applyTransform(matrices, hand);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    public void onRenderFirstPersonItemTail(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info)
    {
        if (!this.bbs$pushedReplayHandTransform)
        {
            return;
        }

        if (FirstPersonReplayHands.shouldRenderExtraOffhandArm(hand, item))
        {
            this.renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, swingProgress, FirstPersonReplayHands.getArm(hand));
        }

        matrices.pop();
        this.bbs$pushedReplayHandTransform = false;
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
