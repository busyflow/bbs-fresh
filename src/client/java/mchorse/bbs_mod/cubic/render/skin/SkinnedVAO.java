package mchorse.bbs_mod.cubic.render.skin;

import mchorse.bbs_mod.cubic.render.vao.Attributes;
import org.lwjgl.opengl.GL30;

/**
 * One material's geometry for a whole cubic model, every bone of it, in a single buffer.
 *
 * <p>The ordinary path bakes a VAO per bone and draws it with the bone's own matrix, which costs a
 * draw call and a uniform upload per bone per model — thirteen of each for a villager, and a crowd
 * multiplies that by its size until the frame is nothing but driver overhead. Here the vertices
 * stay in their bone's local space and carry the bone's index instead, so the model draws once
 * against a palette of bone matrices (see model_skinned.vsh).</p>
 */
public class SkinnedVAO
{
    private int vao;
    private final int count;

    public SkinnedVAO(float[] vertices, float[] normals, float[] uvs, float[] bones, int boneAttribute)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        this.count = vertices.length / 3;
        this.vao = GL30.glGenVertexArrays();

        GL30.glBindVertexArray(this.vao);

        this.buffer(vertices, Attributes.POSITION, 3);
        this.buffer(normals, Attributes.NORMAL, 3);
        this.buffer(uvs, Attributes.TEXTURE_UV, 2);
        this.buffer(bones, boneAttribute, 1);

        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(boneAttribute);

        /* Colour, overlay and light are the same for every vertex of a draw, so they ride as
         * constant attributes set at draw time rather than as buffers. */
        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
        GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);

        GL30.glBindVertexArray(currentVAO);
    }

    private void buffer(float[] data, int attribute, int size)
    {
        int buffer = GL30.glGenBuffers();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, buffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(attribute, size, GL30.GL_FLOAT, false, 0, 0);
    }

    public void delete()
    {
        if (this.vao != 0)
        {
            GL30.glDeleteVertexArrays(this.vao);

            this.vao = 0;
        }
    }

    public void render(float r, float g, float b, float a, int light, int overlay)
    {
        GL30.glBindVertexArray(this.vao);

        GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & 0xFFFF, overlay >> 16 & 0xFFFF);
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & 0xFFFF, light >> 16 & 0xFFFF);

        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
        GL30.glBindVertexArray(0);
    }
}
