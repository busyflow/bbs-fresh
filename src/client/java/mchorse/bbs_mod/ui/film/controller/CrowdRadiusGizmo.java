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
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        stack.push();
        stack.translate(x, y + thickness, z);

        Draw.arc3D(builder, stack, Axis.Y, radius, thickness, 0.25F, 0.85F, 1F);

        if (hole > 0.001F)
        {
            Draw.arc3D(builder, stack, Axis.Y, radius * hole, thickness, 1F, 0.55F, 0.25F);
        }

        /* Quarter markers: short radial stubs that make the scale easier to read than a
         * bare outline, especially at large radii where the ring fills the screen. */
        for (int quarter = 0; quarter < 4; quarter++)
        {
            Draw.arc3D(builder, stack, Axis.Y, radius * 0.5F, thickness * 0.6F,
                0.25F, 0.85F, 1F, quarter * 90F - 1.5F, 3F);
        }

        stack.pop();

        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableCull();
    }
}
