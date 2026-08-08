package mchorse.bbs_mod.cubic.physics;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves bone-physics collisions against solid world blocks. Each chain particle is a sphere of the
 * chain radius and each bone segment a capsule sampled as spheres along its length. Contacts are solved
 * by continuous swept contacts followed by closest-point depenetration. Coulomb friction is applied
 * directly in Verlet velocity space: the inward normal velocity is removed (no bounce) and the
 * tangential velocity is scaled by the friction coefficient.
 */
public final class ModelPhysicsWorldCollisions
{
    private static final float EPS = 1.0e-5f;
    private static final int RELAXATIONS = 4;
    private static final int DEPENETRATION_STEPS = 6;
    private static final float SEGMENT_SAMPLE_STEP = 0.35F;
    private static final float CONTACT_SLOP = 1.0E-4F;

    private ModelPhysicsWorldCollisions()
    {
    }

    public static void resolve(World world, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius, float friction)
    {
        if (world == null || pos == null || prev == null || from < 0 || to > pos.length || to > prev.length || from >= to || radius <= 0F)
        {
            return;
        }

        List<float[]> boxes = gatherSolidBoxes(world, pos, prev, from, to, radius);

        if (boxes.isEmpty())
        {
            return;
        }

        float f = MathHelper.clamp(friction, 0F, 1F);
        Vector3f normal = new Vector3f();
        Vector3f sweepNormal = new Vector3f();
        Vector3f sample = new Vector3f();
        Vector3f previousSample = new Vector3f();

        for (int relax = 0; relax < RELAXATIONS; relax++)
        {
            /* Friction is a velocity change, so it must land once per solve, not once per relaxation —
             * applying it every pass would compound (1-friction) and stick contacts far harder than the
             * coefficient asks. Depenetration runs every pass; friction only on the last. */
            boolean applyFriction = relax == RELAXATIONS - 1;

            for (int i = from; i < to; i++)
            {
                collideSphere(pos[i], prev[i], radius, f, boxes, normal, sweepNormal, applyFriction);
            }

            for (int i = from; i < to - 1; i++)
            {
                collideSegment(pos[i], pos[i + 1], prev[i], prev[i + 1], radius, boxes,
                    normal, sweepNormal, sample, previousSample);
            }
        }
    }

    private static void collideSphere(Vector3f p, Vector3f prev, float radius, float friction,
        List<float[]> boxes, Vector3f normal, Vector3f sweepNormal, boolean applyFriction)
    {
        boolean swept = sweepSphere(p, prev, radius, boxes, sweepNormal);
        float pushX = 0F;
        float pushY = 0F;
        float pushZ = 0F;

        for (int step = 0; step < DEPENETRATION_STEPS; step++)
        {
            float pen = deepestPenetration(p.x, p.y, p.z, radius, boxes, normal);

            if (pen <= EPS)
            {
                break;
            }

            p.x += normal.x * pen;
            p.y += normal.y * pen;
            p.z += normal.z * pen;

            pushX += normal.x * pen;
            pushY += normal.y * pen;
            pushZ += normal.z * pen;
        }

        if (!applyFriction)
        {
            return;
        }

        float pushLenSq = pushX * pushX + pushY * pushY + pushZ * pushZ;

        if (pushLenSq > EPS * EPS)
        {
            float inv = 1F / (float) Math.sqrt(pushLenSq);
            applyFriction(p, prev, pushX * inv, pushY * inv, pushZ * inv, friction);
        }
        else if (swept)
        {
            applyFriction(p, prev, sweepNormal.x, sweepNormal.y, sweepNormal.z, friction);
        }
    }

    private static void collideSegment(Vector3f a, Vector3f b, Vector3f previousA, Vector3f previousB,
        float radius, List<float[]> boxes, Vector3f normal, Vector3f sweepNormal, Vector3f sample,
        Vector3f previousSample)
    {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float dz = b.z - a.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        int samples = (int) Math.ceil(len / (radius * SEGMENT_SAMPLE_STEP));

        if (samples <= 1)
        {
            return;
        }

        for (int s = 1; s < samples; s++)
        {
            float t = s / (float) samples;

            sample.set(a.x + dx * t, a.y + dy * t, a.z + dz * t);
            previousSample.set(previousA).lerp(previousB, t);

            float originalX = sample.x;
            float originalY = sample.y;
            float originalZ = sample.z;

            sweepSphere(sample, previousSample, radius, boxes, sweepNormal);

            float correctionX = sample.x - originalX;
            float correctionY = sample.y - originalY;
            float correctionZ = sample.z - originalZ;
            float correctionSq = correctionX * correctionX + correctionY * correctionY + correctionZ * correctionZ;

            if (correctionSq > EPS * EPS)
            {
                distributeSegmentCorrection(a, b, t, correctionX, correctionY, correctionZ);
            }

            float pen = deepestPenetration(sample.x, sample.y, sample.z, radius, boxes, normal);

            if (pen <= EPS)
            {
                continue;
            }

            distributeSegmentCorrection(a, b, t, normal.x * pen, normal.y * pen, normal.z * pen);
        }
    }

    private static void distributeSegmentCorrection(Vector3f a, Vector3f b, float t,
        float correctionX, float correctionY, float correctionZ)
    {
        float wa = 1F - t;
        float wb = t;
        float distribute = 1F / (wa * wa + wb * wb);

        a.x += correctionX * wa * distribute;
        a.y += correctionY * wa * distribute;
        a.z += correctionZ * wa * distribute;
        b.x += correctionX * wb * distribute;
        b.y += correctionY * wb * distribute;
        b.z += correctionZ * wb * distribute;
    }

    /**
     * Sweep a particle from its previous position to its proposed one against collision boxes
     * expanded by its radius. This catches a floor or wall even when the particle crosses the
     * whole block between two solver samples.
     */
    private static boolean sweepSphere(Vector3f p, Vector3f previous, float radius,
        List<float[]> boxes, Vector3f outNormal)
    {
        float dx = p.x - previous.x;
        float dy = p.y - previous.y;
        float dz = p.z - previous.z;

        if (dx * dx + dy * dy + dz * dz <= EPS * EPS)
        {
            return false;
        }

        float best = Float.POSITIVE_INFINITY;
        float[] hit = new float[4];

        for (int i = 0, n = boxes.size(); i < n; i++)
        {
            float[] box = boxes.get(i);

            if (rayExpandedBox(previous.x, previous.y, previous.z, dx, dy, dz,
                box[0] - radius, box[1] - radius, box[2] - radius,
                box[3] + radius, box[4] + radius, box[5] + radius, hit)
                && hit[0] < best)
            {
                best = hit[0];
                outNormal.set(hit[1], hit[2], hit[3]);
            }
        }

        if (!Float.isFinite(best))
        {
            return false;
        }

        p.set(previous.x + dx * best, previous.y + dy * best, previous.z + dz * best)
            .add(outNormal.x * CONTACT_SLOP, outNormal.y * CONTACT_SLOP, outNormal.z * CONTACT_SLOP);

        return true;
    }

    /** Segment versus AABB slab test. Returns the first inward contact in [0, 1]. */
    private static boolean rayExpandedBox(float ox, float oy, float oz, float dx, float dy, float dz,
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float[] out)
    {
        /* A particle already inside the expanded box needs closest-point depenetration instead;
         * treating its start as a swept hit can choose an unrelated face and pull it deeper. */
        if (ox > minX + EPS && ox < maxX - EPS
            && oy > minY + EPS && oy < maxY - EPS
            && oz > minZ + EPS && oz < maxZ - EPS)
        {
            return false;
        }

        float near = 0F;
        float far = 1F;
        float nx = 0F;
        float ny = 0F;
        float nz = 0F;

        if (Math.abs(dx) < EPS)
        {
            if (ox < minX || ox > maxX) return false;
        }
        else
        {
            float a = (minX - ox) / dx;
            float b = (maxX - ox) / dx;
            float entry = Math.min(a, b);
            float exit = Math.max(a, b);

            if (entry > near)
            {
                near = entry;
                nx = dx > 0F ? -1F : 1F;
                ny = 0F;
                nz = 0F;
            }

            far = Math.min(far, exit);
            if (near > far) return false;
        }

        if (Math.abs(dy) < EPS)
        {
            if (oy < minY || oy > maxY) return false;
        }
        else
        {
            float a = (minY - oy) / dy;
            float b = (maxY - oy) / dy;
            float entry = Math.min(a, b);
            float exit = Math.max(a, b);

            if (entry > near)
            {
                near = entry;
                nx = 0F;
                ny = dy > 0F ? -1F : 1F;
                nz = 0F;
            }

            far = Math.min(far, exit);
            if (near > far) return false;
        }

        if (Math.abs(dz) < EPS)
        {
            if (oz < minZ || oz > maxZ) return false;
        }
        else
        {
            float a = (minZ - oz) / dz;
            float b = (maxZ - oz) / dz;
            float entry = Math.min(a, b);
            float exit = Math.max(a, b);

            if (entry > near)
            {
                near = entry;
                nx = 0F;
                ny = 0F;
                nz = dz > 0F ? -1F : 1F;
            }

            far = Math.min(far, exit);
            if (near > far) return false;
        }

        float inward = dx * nx + dy * ny + dz * nz;

        if (near < 0F || near > 1F || inward >= -EPS || (nx == 0F && ny == 0F && nz == 0F))
        {
            return false;
        }

        out[0] = near;
        out[1] = nx;
        out[2] = ny;
        out[3] = nz;

        return true;
    }

    private static void applyFriction(Vector3f p, Vector3f prev, float nx, float ny, float nz, float friction)
    {
        float vx = p.x - prev.x;
        float vy = p.y - prev.y;
        float vz = p.z - prev.z;

        float vn = vx * nx + vy * ny + vz * nz;

        float tangentX = vx - nx * vn;
        float tangentY = vy - ny * vn;
        float tangentZ = vz - nz * vn;

        /* Keep outward separation but kill motion driving into the surface, so contacts don't bounce. */
        float normalScale = vn > 0F ? vn : 0F;
        float tangentScale = 1F - friction;

        prev.x = p.x - (nx * normalScale + tangentX * tangentScale);
        prev.y = p.y - (ny * normalScale + tangentY * tangentScale);
        prev.z = p.z - (nz * normalScale + tangentZ * tangentScale);
    }

    private static float deepestPenetration(float px, float py, float pz, float radius, List<float[]> boxes, Vector3f outNormal)
    {
        float best = 0F;

        for (int i = 0, n = boxes.size(); i < n; i++)
        {
            float[] box = boxes.get(i);

            float cx = clamp(px, box[0], box[3]);
            float cy = clamp(py, box[1], box[4]);
            float cz = clamp(pz, box[2], box[5]);

            float dx = px - cx;
            float dy = py - cy;
            float dz = pz - cz;
            float d2 = dx * dx + dy * dy + dz * dz;

            if (d2 > radius * radius)
            {
                continue;
            }

            float pen;
            float nx;
            float ny;
            float nz;

            if (d2 > EPS * EPS)
            {
                float d = (float) Math.sqrt(d2);
                float inv = 1F / d;
                nx = dx * inv;
                ny = dy * inv;
                nz = dz * inv;
                pen = radius - d;
            }
            else
            {
                /* Centre is inside the box: push out through the nearest face. */
                float dMinX = px - box[0];
                float dMaxX = box[3] - px;
                float dMinY = py - box[1];
                float dMaxY = box[4] - py;
                float dMinZ = pz - box[2];
                float dMaxZ = box[5] - pz;

                float face = dMinX;
                nx = -1F;
                ny = 0F;
                nz = 0F;

                if (dMaxX < face) { face = dMaxX; nx = 1F; ny = 0F; nz = 0F; }
                if (dMinY < face) { face = dMinY; nx = 0F; ny = -1F; nz = 0F; }
                if (dMaxY < face) { face = dMaxY; nx = 0F; ny = 1F; nz = 0F; }
                if (dMinZ < face) { face = dMinZ; nx = 0F; ny = 0F; nz = -1F; }
                if (dMaxZ < face) { face = dMaxZ; nx = 0F; ny = 0F; nz = 1F; }

                pen = radius + face;
            }

            if (pen > best)
            {
                best = pen;
                outNormal.set(nx, ny, nz);
            }
        }

        return best;
    }

    private static List<float[]> gatherSolidBoxes(World world, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius)
    {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = from; i < to; i++)
        {
            minX = Math.min(minX, Math.min(pos[i].x, prev[i].x));
            minY = Math.min(minY, Math.min(pos[i].y, prev[i].y));
            minZ = Math.min(minZ, Math.min(pos[i].z, prev[i].z));
            maxX = Math.max(maxX, Math.max(pos[i].x, prev[i].x));
            maxY = Math.max(maxY, Math.max(pos[i].y, prev[i].y));
            maxZ = Math.max(maxZ, Math.max(pos[i].z, prev[i].z));
        }

        int bx1 = MathHelper.floor(minX - radius);
        int by1 = MathHelper.floor(minY - radius);
        int bz1 = MathHelper.floor(minZ - radius);
        int bx2 = MathHelper.floor(maxX + radius);
        int by2 = MathHelper.floor(maxY + radius);
        int bz2 = MathHelper.floor(maxZ + radius);

        List<float[]> boxes = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = bx1; x <= bx2; x++)
        {
            for (int y = by1; y <= by2; y++)
            {
                for (int z = bz1; z <= bz2; z++)
                {
                    mutable.set(x, y, z);

                    if (!world.isChunkLoaded(mutable))
                    {
                        continue;
                    }

                    BlockState state = world.getBlockState(mutable);

                    if (state == null)
                    {
                        continue;
                    }

                    if (state.isFullCube(world, mutable))
                    {
                        boxes.add(new float[] {x, y, z, x + 1F, y + 1F, z + 1F});
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());

                    if (shape.isEmpty())
                    {
                        continue;
                    }

                    for (Box box : shape.getBoundingBoxes())
                    {
                        boxes.add(new float[] {
                            (float) (x + box.minX), (float) (y + box.minY), (float) (z + box.minZ),
                            (float) (x + box.maxX), (float) (y + box.maxY), (float) (z + box.maxZ)
                        });
                    }
                }
            }
        }

        return boxes;
    }

    public static boolean hasFullCubeInAabb(World world, BlockPos.Mutable mutable, int minBX, int minBY, int minBZ, int maxBX, int maxBY, int maxBZ)
    {
        if (world == null)
        {
            return false;
        }

        for (int x = minBX; x <= maxBX; x++)
        {
            for (int y = minBY; y <= maxBY; y++)
            {
                for (int z = minBZ; z <= maxBZ; z++)
                {
                    mutable.set(x, y, z);

                    if (!world.isChunkLoaded(mutable))
                    {
                        continue;
                    }

                    BlockState block = world.getBlockState(mutable);

                    if (block == null)
                    {
                        continue;
                    }

                    if (block.isFullCube(world, mutable))
                    {
                        return true;
                    }

                    if (!block.getCollisionShape(world, mutable, ShapeContext.absent()).isEmpty())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static float clamp(float value, float min, float max)
    {
        return value < min ? min : (value > max ? max : value);
    }
}
