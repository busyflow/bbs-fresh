package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.actions.types.crowd.CrowdPreview;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin
{
    /**
     * Leave out the crowd members the preview is thinning, before anything is drawn for them.
     *
     * <p>Here rather than around the model draw further down, so a skipped member costs its
     * shadow and its nameplate as well as its body - and so the pose it would have been drawn in
     * is never worked out either.</p>
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void bbs$thinCrowd(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo info)
    {
        if (CrowdPreview.isThinnedOut(entity, x, y, z))
        {
            info.cancel();
        }
    }

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        )
    )
    private <E extends Entity> void wrapRender(EntityRenderer<E> renderer, E entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light, Operation<Void> original)
    {
        if (entity instanceof LivingEntity livingEntity)
        {
            float whiteOverlayProgress = 0;

            if (renderer instanceof LivingEntityRendererInvoker invoker)
            {
                whiteOverlayProgress = invoker.bbs$getAnimationCounter(livingEntity, tickDelta);
            }

            int o = LivingEntityRenderer.getOverlay(livingEntity, whiteOverlayProgress);

            if (MorphRenderer.renderLivingEntity(livingEntity, yaw, tickDelta, matrices, vcp, light, o))
            {
                return;
            }
        }

        original.call(renderer, entity, yaw, tickDelta, matrices, vcp, light);
    }
}