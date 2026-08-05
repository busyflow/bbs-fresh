package mchorse.bbs_mod.cubic.render.skin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cubic model baked once for GPU skinning: one merged buffer per material, drawn with a palette
 * of bone matrices instead of one draw call per bone.
 *
 * <p>This is the whole of the "high-bone model optimization". A custom model of a villager costs
 * the same to simulate as a vanilla one but ten to fifteen times as much to submit, because every
 * bone is its own draw call with its own uniform block — at a crowd of thousands that is the entire
 * frame. Skinned, the bone count stops mattering: the model submits like a single mesh, and the
 * bones ride along as 64 floats each in one uniform upload.</p>
 *
 * <p>It is deliberately a narrow path. Anything it can't express exactly — welds, shape keys,
 * per-bone colour, stencil picking, Iris — is declined at the call site or by
 * {@link SkinnedPalette#compute}, and the ordinary per-bone renderer handles it as before.</p>
 */
public class SkinnedModel
{
    public static final int MAX_BONES = BBSShaders.MAX_SKINNED_BONES;

    private static final SkinnedPalette PALETTE = new SkinnedPalette();
    private static final FloatBuffer BUFFER = BufferUtils.createFloatBuffer(MAX_BONES * 16);

    /** Locations are per linked program, so they're re-read whenever the program itself changes. */
    private static int program = 0;
    private static int boneAttribute = -1;
    private static int boneUniform = -1;

    private final Map<String, SkinnedVAO> vaos = new LinkedHashMap<>();

    /** Whether the setting is on and this machine loaded the skinned program at all. */
    public static boolean isEnabled()
    {
        return BBSSettings.highBoneModelOptimization != null
            && BBSSettings.highBoneModelOptimization.get()
            && BBSShaders.getModelSkinned() != null
            && !BBSRendering.isIrisShadersEnabled();
    }

    /**
     * Bake the model into merged per-material buffers, or return null when the program's
     * {@code BoneIndex} attribute couldn't be resolved (in which case nothing can be skinned and
     * the caller stays on the ordinary path for good).
     */
    public static SkinnedModel build(Model model)
    {
        if (!resolveLocations() || model.getOrderedGroups().size() > MAX_BONES)
        {
            return null;
        }

        Map<String, CubicVAOBuilderRenderer.MaterialBucket> buckets = new LinkedHashMap<>();

        CubicRenderer.processRenderModel(CubicVAOBuilderRenderer.merging(buckets), null, new MatrixStack(), model);

        SkinnedModel skinned = new SkinnedModel();

        for (Map.Entry<String, CubicVAOBuilderRenderer.MaterialBucket> entry : buckets.entrySet())
        {
            CubicVAOBuilderRenderer.MaterialBucket bucket = entry.getValue();

            if (bucket.vertices.isEmpty())
            {
                continue;
            }

            skinned.vaos.put(entry.getKey(), new SkinnedVAO(
                CollectionUtils.toArray(bucket.vertices),
                CollectionUtils.toArray(bucket.normals),
                CollectionUtils.toArray(bucket.uvs),
                CollectionUtils.toArray(bucket.bones),
                boneAttribute
            ));
        }

        return skinned;
    }

    /**
     * The bone matrices for the model's current pose, or null when this frame's pose can't be
     * drawn skinned. Shared scratch — valid until the next call, which is all the caller needs
     * since it uploads it immediately.
     */
    public static SkinnedPalette pose(Model model)
    {
        return PALETTE.compute(model) ? PALETTE : null;
    }

    public Map<String, SkinnedVAO> getVaos()
    {
        return this.vaos;
    }

    public boolean isEmpty()
    {
        return this.vaos.isEmpty();
    }

    public void delete()
    {
        for (SkinnedVAO vao : this.vaos.values())
        {
            vao.delete();
        }

        this.vaos.clear();
    }

    /** Draw one material of the model: the shader's usual uniforms, then the palette, then one call. */
    public static void render(ShaderProgram shader, SkinnedVAO vao, Matrix4f modelView, Matrix3f normalMat, SkinnedPalette palette, float r, float g, float b, float a, int light, int overlay)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        ModelVAORenderer.setupUniforms(shader, modelView, normalMat);

        shader.bind();

        BUFFER.clear();
        BUFFER.put(palette.data(), 0, palette.bones() * 16);
        BUFFER.flip();
        GL20.glUniformMatrix4fv(boneUniform, false, BUFFER);

        vao.render(r, g, b, a, light, overlay);
        shader.unbind();

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
    }

    /**
     * Read the skinned program's {@code BoneIndex} attribute and {@code BoneMats} uniform slots.
     *
     * <p>Both are read off the currently bound program rather than off the program object, which
     * keeps this independent of how the game exposes its shader handles. Cached against the
     * program's own id, so a resource reload (which relinks everything) re-reads them.</p>
     */
    private static boolean resolveLocations()
    {
        ShaderProgram shader = BBSShaders.getModelSkinned();

        if (shader == null)
        {
            return false;
        }

        shader.bind();

        int current = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (current != program)
        {
            program = current;
            boneAttribute = GL20.glGetAttribLocation(current, "BoneIndex");
            boneUniform = GL20.glGetUniformLocation(current, "BoneMats");
        }

        shader.unbind();

        return boneAttribute >= 0 && boneUniform >= 0;
    }
}
