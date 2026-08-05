package mchorse.bbs_mod.cubic.render.skin;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The bone matrices of one posed cubic model, laid out for a single uniform upload.
 *
 * <p>Same walk and same maths as {@link mchorse.bbs_mod.cubic.render.ICubicRenderer#applyGroupTransformations}
 * — it has to be, or skinned models would sit differently from unskinned ones — but it writes into
 * reused scratch instead of a {@link net.minecraft.client.util.math.MatrixStack}, whose every push
 * allocates two matrices. At a five-figure crowd that difference alone is hundreds of thousands of
 * short-lived objects a frame.</p>
 *
 * <p>One instance is shared by the render thread across all models; it holds no state between
 * {@link #compute} calls.</p>
 */
public class SkinnedPalette
{
    /** Deepest bone chain supported before the model gives up on the skinned path. */
    private static final int MAX_DEPTH = 64;

    private final Matrix4f[] frames = new Matrix4f[MAX_DEPTH];
    private final Matrix4f identity = new Matrix4f();
    private float[] data = new float[0];
    private int bones;

    public SkinnedPalette()
    {
        for (int i = 0; i < MAX_DEPTH; i++)
        {
            this.frames[i] = new Matrix4f();
        }
    }

    public float[] data()
    {
        return this.data;
    }

    public int bones()
    {
        return this.bones;
    }

    /**
     * Fill the palette from the model's current pose.
     *
     * @return false when this model can't be drawn skinned this frame — a bone carries its own
     * colour or light level (which the merged draw has no way to express, since every bone shares
     * the draw's constant colour attribute), or the rig is deeper than the scratch allows. The
     * caller falls back to the per-bone path, which handles all of it.
     */
    public boolean compute(Model model)
    {
        int count = model.getOrderedGroups().size();

        if (count > SkinnedModel.MAX_BONES)
        {
            return false;
        }

        if (this.data.length < count * 16)
        {
            this.data = new float[count * 16];
        }

        this.bones = count;

        for (ModelGroup group : model.topGroups)
        {
            if (!this.walk(group, this.identity, 0))
            {
                return false;
            }
        }

        return true;
    }

    private boolean walk(ModelGroup group, Matrix4f parent, int depth)
    {
        if (depth >= this.frames.length)
        {
            return false;
        }

        /* Per-bone colour and lighting are set by the pose and by animation channels. The merged
         * draw carries one colour for the whole model, so a bone that wants its own sends the
         * model back to the per-bone path rather than rendering it wrong. */
        if (group.lighting != 0F || group.color.r != 1F || group.color.g != 1F || group.color.b != 1F || group.color.a != 1F)
        {
            return false;
        }

        Matrix4f matrix = this.frames[depth].set(parent);
        Vector3f offset = group.offset;
        Vector3f translate = group.current.translate;
        Vector3f pivot = group.initial.translate;
        Vector3f scale = group.current.scale;

        if (offset != null)
        {
            matrix.translate(offset.x, offset.y, offset.z);
        }

        matrix.translate(-(translate.x - pivot.x) / 16F, (translate.y - pivot.y) / 16F, (translate.z - pivot.z) / 16F);
        matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (group.orient != null)
        {
            matrix.rotate(group.orient);
        }
        else if (group.current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            matrix.rotate(group.current.quat);
        }
        else
        {
            Vector3f rotate = group.current.rotate;

            /* Rest bones skip the trig, as the stack renderer does; cubic channels are degrees. */
            if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
            {
                matrix.rotateZYX(
                    (float) (rotate.z * Math.PI / 180D),
                    (float) (rotate.y * Math.PI / 180D),
                    (float) (rotate.x * Math.PI / 180D)
                );
            }
        }

        matrix.scale(scale.x, scale.y, scale.z);
        matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);

        int offsetInData = group.index * 16;

        if (group.visible)
        {
            matrix.get(this.data, offsetInData);
        }
        else
        {
            /* A hidden bone collapses to a point instead of being skipped: its triangles become
             * degenerate and cover no pixels, which is how one merged draw hides part of itself
             * (the first-person arm renders exactly this way). */
            for (int i = 0; i < 16; i++)
            {
                this.data[offsetInData + i] = 0F;
            }
        }

        for (int i = 0; i < group.children.size(); i++)
        {
            if (!this.walk(group.children.get(i), matrix, depth + 1))
            {
                return false;
            }
        }

        return true;
    }
}
