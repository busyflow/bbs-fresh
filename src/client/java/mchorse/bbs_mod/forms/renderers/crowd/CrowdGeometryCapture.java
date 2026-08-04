package mchorse.bbs_mod.forms.renderers.crowd;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the geometry a form renderer emits for a single crowd member, then replays that
 * geometry for every other member that shares the same appearance.
 *
 * <p>Crowd members differ only by position, yaw and scale. Rendering each one through the
 * normal form pipeline repeats animation evaluation, model traversal and render state work
 * that produced identical vertices. Capturing once and replaying reduces a member to a
 * matrix multiply per vertex, which is what makes counts far beyond the old fixed ceilings
 * affordable.</p>
 *
 * <p>Replay is also the level-of-detail mechanism. Quads are ordered by screen-independent
 * area at capture time, so a distant member can draw only its largest faces and keep a solid
 * silhouette while dropping most of its vertices.</p>
 */
public class CrowdGeometryCapture
{
    private static final int HAS_COLOR = 1;
    private static final int HAS_TEXTURE = 2;
    private static final int HAS_OVERLAY = 4;
    private static final int HAS_LIGHT = 8;
    private static final int HAS_NORMAL = 16;

    private final Map<RenderLayer, LayerGeometry> layers = new LinkedHashMap<>();
    private final List<LayerGeometry> ordered = new ArrayList<>();
    private final Matrix4f transform = new Matrix4f();
    private final Matrix3f normalTransform = new Matrix3f();
    private final Vector4f position = new Vector4f();
    private final Vector3f normal = new Vector3f();

    private boolean recording;
    private boolean usable;

    public void reset()
    {
        for (LayerGeometry geometry : this.ordered)
        {
            geometry.clear();
        }

        this.recording = false;
        this.usable = false;
    }

    public boolean isUsable()
    {
        return this.usable;
    }

    /** Total quads across every recorded layer. Used to size level-of-detail budgets. */
    public int getQuadCount()
    {
        int quads = 0;

        for (LayerGeometry geometry : this.ordered)
        {
            quads += geometry.quadCount;
        }

        return quads;
    }

    public void beginRecording()
    {
        this.reset();
        this.recording = true;
    }

    public void endRecording()
    {
        this.recording = false;
        this.usable = false;

        for (LayerGeometry geometry : this.ordered)
        {
            geometry.finish();

            if (geometry.quadCount > 0)
            {
                this.usable = true;
            }
        }
    }

    /**
     * Layer substitute hook. Returns the recording consumer while a capture is open, which
     * swallows the capture pass entirely: the member being recorded is drawn by replay like
     * every other member, so nothing is emitted twice.
     */
    public VertexConsumer intercept(RenderLayer layer, VertexConsumer consumer)
    {
        if (!this.recording)
        {
            return null;
        }

        LayerGeometry geometry = this.layers.get(layer);

        if (geometry == null)
        {
            geometry = new LayerGeometry(layer);

            this.layers.put(layer, geometry);
            this.ordered.add(geometry);
        }

        return geometry;
    }

    /**
     * Draw one member. {@code fraction} is the share of each layer's quads to emit, ordered
     * largest first, so distant members shed detail without losing their outline.
     */
    public void replay(java.util.function.Function<RenderLayer, VertexConsumer> buffers,
        Matrix4f memberMatrix, float fraction)
    {
        if (!this.usable)
        {
            return;
        }

        this.transform.set(memberMatrix);
        this.normalTransform.set(this.transform.normal(new Matrix4f()));

        for (LayerGeometry geometry : this.ordered)
        {
            if (geometry.quadCount <= 0)
            {
                continue;
            }

            int quads = fraction >= 1F
                ? geometry.quadCount
                : Math.max(1, Math.min(geometry.quadCount, Math.round(geometry.quadCount * fraction)));
            VertexConsumer consumer = buffers.apply(geometry.layer);

            if (consumer == null)
            {
                continue;
            }

            for (int q = 0; q < quads; q++)
            {
                int quad = geometry.quadOrder[q];

                for (int v = 0; v < 4; v++)
                {
                    int index = quad * 4 + v;

                    this.position.set(geometry.pos[index * 3], geometry.pos[index * 3 + 1],
                        geometry.pos[index * 3 + 2], 1F);
                    this.transform.transform(this.position);

                    consumer.vertex(this.position.x, this.position.y, this.position.z);

                    if ((geometry.flags & HAS_COLOR) != 0)
                    {
                        int color = geometry.color[index];

                        consumer.color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24);
                    }

                    if ((geometry.flags & HAS_TEXTURE) != 0)
                    {
                        consumer.texture(geometry.uv[index * 2], geometry.uv[index * 2 + 1]);
                    }

                    if ((geometry.flags & HAS_OVERLAY) != 0)
                    {
                        int overlay = geometry.overlay[index];

                        consumer.overlay(overlay & 0xffff, overlay >>> 16);
                    }

                    if ((geometry.flags & HAS_LIGHT) != 0)
                    {
                        int light = geometry.light[index];

                        consumer.light(light & 0xffff, light >>> 16);
                    }

                    if ((geometry.flags & HAS_NORMAL) != 0)
                    {
                        this.normal.set(geometry.normal[index * 3], geometry.normal[index * 3 + 1],
                            geometry.normal[index * 3 + 2]);
                        this.normalTransform.transform(this.normal);

                        if (this.normal.lengthSquared() > 1.0E-8F)
                        {
                            this.normal.normalize();
                        }

                        consumer.normal(this.normal.x, this.normal.y, this.normal.z);
                    }

                    consumer.next();
                }
            }
        }
    }

    /**
     * One render layer's captured vertex stream. Stored as parallel primitive arrays so a
     * replay walks contiguous memory and allocates nothing.
     */
    private static final class LayerGeometry implements VertexConsumer
    {
        private final RenderLayer layer;

        private float[] pos = new float[0];
        private int[] color = new int[0];
        private float[] uv = new float[0];
        private int[] overlay = new int[0];
        private int[] light = new int[0];
        private float[] normal = new float[0];
        private int[] quadOrder = new int[0];

        private int vertices;
        private int quadCount;
        private int flags;
        private int pendingFlags;

        private float pendingX;
        private float pendingY;
        private float pendingZ;
        private int pendingColor = 0xffffffff;
        private float pendingU;
        private float pendingV;
        private int pendingOverlay;
        private int pendingLight;
        private float pendingNormalX;
        private float pendingNormalY;
        private float pendingNormalZ = 1F;

        private LayerGeometry(RenderLayer layer)
        {
            this.layer = layer;
        }

        private void clear()
        {
            this.vertices = 0;
            this.quadCount = 0;
            this.flags = 0;
            this.pendingFlags = 0;
        }

        /**
         * Sort quads by area so replay can cut detail from the smallest faces first. Streams
         * that are not quad-aligned keep their original order and never decimate.
         */
        private void finish()
        {
            this.quadCount = this.vertices % 4 == 0 ? this.vertices / 4 : 0;

            if (this.quadCount <= 0)
            {
                return;
            }

            if (this.quadOrder.length < this.quadCount)
            {
                this.quadOrder = new int[this.quadCount];
            }

            float[] areas = new float[this.quadCount];

            for (int q = 0; q < this.quadCount; q++)
            {
                this.quadOrder[q] = q;
                areas[q] = this.quadArea(q);
            }

            /* Insertion sort: quad counts per layer are small (tens), and this keeps the
             * capture free of boxing and comparator allocation. */
            for (int i = 1; i < this.quadCount; i++)
            {
                int quad = this.quadOrder[i];
                float area = areas[quad];
                int j = i - 1;

                while (j >= 0 && areas[this.quadOrder[j]] < area)
                {
                    this.quadOrder[j + 1] = this.quadOrder[j];
                    j--;
                }

                this.quadOrder[j + 1] = quad;
            }
        }

        private float quadArea(int quad)
        {
            int base = quad * 4 * 3;
            float ax = this.pos[base + 3] - this.pos[base];
            float ay = this.pos[base + 4] - this.pos[base + 1];
            float az = this.pos[base + 5] - this.pos[base + 2];
            float bx = this.pos[base + 9] - this.pos[base];
            float by = this.pos[base + 10] - this.pos[base + 1];
            float bz = this.pos[base + 11] - this.pos[base + 2];
            float cx = ay * bz - az * by;
            float cy = az * bx - ax * bz;
            float cz = ax * by - ay * bx;

            return cx * cx + cy * cy + cz * cz;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            this.pendingX = (float) x;
            this.pendingY = (float) y;
            this.pendingZ = (float) z;

            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            this.pendingColor = alpha << 24 | red << 16 | green << 8 | blue;
            this.pendingFlags |= HAS_COLOR;

            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            this.pendingU = u;
            this.pendingV = v;
            this.pendingFlags |= HAS_TEXTURE;

            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            this.pendingOverlay = u & 0xffff | v << 16;
            this.pendingFlags |= HAS_OVERLAY;

            return this;
        }

        @Override
        public VertexConsumer light(int u, int v)
        {
            this.pendingLight = u & 0xffff | v << 16;
            this.pendingFlags |= HAS_LIGHT;

            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            this.pendingNormalX = x;
            this.pendingNormalY = y;
            this.pendingNormalZ = z;
            this.pendingFlags |= HAS_NORMAL;

            return this;
        }

        @Override
        public void next()
        {
            int index = this.vertices;

            this.ensureCapacity(index + 1);

            this.pos[index * 3] = this.pendingX;
            this.pos[index * 3 + 1] = this.pendingY;
            this.pos[index * 3 + 2] = this.pendingZ;
            this.color[index] = this.pendingColor;
            this.uv[index * 2] = this.pendingU;
            this.uv[index * 2 + 1] = this.pendingV;
            this.overlay[index] = this.pendingOverlay;
            this.light[index] = this.pendingLight;
            this.normal[index * 3] = this.pendingNormalX;
            this.normal[index * 3 + 1] = this.pendingNormalY;
            this.normal[index * 3 + 2] = this.pendingNormalZ;

            this.flags |= this.pendingFlags;
            this.pendingFlags = 0;
            this.vertices = index + 1;
        }

        private void ensureCapacity(int vertices)
        {
            if (this.color.length >= vertices)
            {
                return;
            }

            int capacity = Math.max(256, Math.max(vertices, this.color.length * 2));

            this.pos = java.util.Arrays.copyOf(this.pos, capacity * 3);
            this.color = java.util.Arrays.copyOf(this.color, capacity);
            this.uv = java.util.Arrays.copyOf(this.uv, capacity * 2);
            this.overlay = java.util.Arrays.copyOf(this.overlay, capacity);
            this.light = java.util.Arrays.copyOf(this.light, capacity);
            this.normal = java.util.Arrays.copyOf(this.normal, capacity * 3);
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha)
        {}

        @Override
        public void unfixColor()
        {}
    }
}
