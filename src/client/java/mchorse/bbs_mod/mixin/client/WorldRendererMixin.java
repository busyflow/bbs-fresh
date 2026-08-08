package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    public Framebuffer entityOutlinesFramebuffer;

    /* Deferred form translucency spans the frame: forms enqueue their translucent pass while
     * entities render, and the queue flushes right before the translucent terrain layer so the
     * blending sits under water/glass the way vanilla entities do. The RETURN hook is a safety
     * net for frames where the translucent layer never draws (e.g. a replaced terrain pipeline). */
    @Inject(method = "render", at = @At("HEAD"))
    public void onRenderWorldStart(CallbackInfo info)
    {
        FormTranslucentQueue.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void onRenderWorldEnd(CallbackInfo info)
    {
        FormTranslucentQueue.flush();
    }

    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    public void onRenderSky(CallbackInfo info)
    {
        if (BBSRendering.isChromaSkyActive())
        {
            Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
            int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
            Color color = Color.rgba(argb);

            GL11.glClearColor(color.r, color.g, color.b, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.setShaderFogColor(color.r, color.g, color.b, 1F);

            info.cancel();
        }
    }

    /**
     * Swing the sun, moon and stars round the compass without moving the clock.
     *
     * <p>This is the turn that puts the sun's path east to west - the one rotation in the sky
     * that is about direction rather than time of day. Adding to it carries the whole celestial
     * sphere with it, so sunrise can be made to happen in the north while the hour, the light
     * level and the colour of the sky all stay where the film put them.</p>
     *
     * <p>Vanilla's sky only. A shader pack works the sun out from the world time for itself, so
     * with Iris loaded the sun moves here and its lighting does not follow.</p>
     */
    @ModifyArg(
        method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;",
            ordinal = 3
        )
    )
    private float bbs$rotateSunDirection(float degrees)
    {
        Double direction = BBSRendering.getSunDirection();

        return direction == null ? degrees : degrees + direction.floatValue();
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    public void onRenderLayer(RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix, CallbackInfo info)
    {
        /* Iris' shadow pass re-runs renderLayer into the shadow map early in the frame — its
         * translucent layer must not fire the flush, or the queue deactivates before the main
         * pass has even rendered the forms. */
        if (renderLayer == RenderLayer.getTranslucent() && !BBSRendering.isIrisShadowPass())
        {
            FormTranslucentQueue.flush();
        }

        if (BBSRendering.isChromaSkyActive() && !BBSRendering.isChromaSkyTerrainVisible())
        {
            BBSRendering.onRenderChunkLayer(matrices);

            info.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("TAIL"))
    public void onRenderChunkLayer(RenderLayer layer, MatrixStack stack, double x, double y, double z, Matrix4f positionMatrix, CallbackInfo info)
    {
        if (layer == RenderLayer.getSolid())
        {
            BBSRendering.onRenderChunkLayer(stack);
        }
    }

    @Inject(at = @At("RETURN"), method = "loadEntityOutlinePostProcessor")
    private void onLoadEntityOutlineShader(CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(at = @At("RETURN"), method = "onResized")
    private void onResized(CallbackInfo info)
    {
        if (this.entityOutlinesFramebuffer == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }
}