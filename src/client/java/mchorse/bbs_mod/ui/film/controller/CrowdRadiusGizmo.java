package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

/**
 * The selected crowd's footprint, drawn on the ground: an outer ring at the authored radius,
 * an inner ring when a centre hole is set, and quarter markers.
 *
 * <p>Crowd radius is otherwise invisible until members are actually placed, which makes it
 * impossible to judge how far a crowd will reach before committing to a population. The rings
 * are drawn depth-tested so terrain occludes them and the shape reads as sitting on the
 * ground rather than floating over it.</p>
 */
public class CrowdRadiusGizmo
{
    /** Ring thickness as a share of radius, so a huge crowd's outline stays readable. */
    private static final float THICKNESS_RATIO = 0.006F;
    private static final float MIN_THICKNESS = 0.05F;
    private static final float MAX_THICKNESS = 1.5F;

    public static void render(WorldRenderContext context, Replay replay, float tick)
    {
        if (replay == null || replay.relative.get() || !(replay.form.get() instanceof CrowdForm crowd))
        {
            return;
        }

        CrowdFormation formation = CrowdFormation.get(crowd.formation.get());

        if (formation == CrowdFormation.LINE || formation == CrowdFormation.GRID)
        {
            return;
        }

        float radius = crowd.radius.get();

        if (!Float.isFinite(radius) || radius <= 0F)
        {
            return;
        }

        Camera camera = context.camera();
        double x = replay.keyframes.x.interpolate(tick) - camera.getPos().x;
        double y = replay.keyframes.y.interpolate(tick) - camera.getPos().y;
        double z = replay.keyframes.z.interpolate(tick) - camera.getPos().z;

        /* A ring far past the render distance is never useful and costs a full torus. */
        if (Math.abs(x) - radius > 512D || Math.abs(z) - radius > 512D)
        {
            return;
        }

        float thickness = Math.min(MAX_THICKNESS, Math.max(MIN_THICKNESS, radius * THICKNESS_RATIO));
        float hole = formation == CrowdFormation.CIRCLE ? crowd.hollow.get() : 0F;
        MatrixStack stack = context.matrixStack();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();

        /* Ghost pass first, with depth off and the writes masked: uneven ground otherwise
         * buries most of the ring and leaves two floating arcs that read as nothing at all.
         * The dim copy keeps the whole circle legible while the bright pass below still
         * shows which parts genuinely sit on visible ground. */
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        stack.push();
        stack.translate(x, y + thickness, z);
        ring(builder, stack, radius, hole, thickness, 0.12F, 0.36F, 0.45F);
        stack.pop();
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.depthMask(true);

        RenderSystem.enableDepthTest();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        stack.push();
        stack.translate(x, y + thickness, z);
        ring(builder, stack, radius, hole, thickness, 0.25F, 0.85F, 1F);
        stack.pop();
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
    }

    /**
     * Outer rim, optional centre hole, a half-radius reference ring and four rim markers.
     * The reference ring is what makes the absolute size readable: a lone circle looks the
     * same at every radius once it fills the view.
     */
    private static void ring(BufferBuilder builder, MatrixStack stack, float radius, float hole,
        float thickness, float r, float g, float b)
    {
        Draw.arc3D(builder, stack, Axis.Y, radius, thickness, r, g, b);
        Draw.arc3D(builder, stack, Axis.Y, radius * 0.5F, thickness * 0.5F, r, g, b);

        if (hole > 0.001F)
        {
            Draw.arc3D(builder, stack, Axis.Y, radius * hole, thickness, r, g * 0.65F, b * 0.3F);
        }

        for (int quarter = 0; quarter < 4; quarter++)
        {
            Draw.arc3D(builder, stack, Axis.Y, radius * 1.04F, thickness * 2F,
                r, g, b, quarter * 90F - 2F, 4F);
        }
    }
}
