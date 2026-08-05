package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ModelPhysicsCache
{
    public static final class CompiledChain
    {
        private final String id;
        private final String attach;
        private final String targetBone;
        private final List<String> chainRootToEnd;
        private final float[] restLengths;
        private final float gravity;
        private final float damping;
        private final float stiffness;
        private final int iterations;
        private final boolean relativeGravity;
        private final boolean hasGravityRotation;
        private final Quaternionf gravityRotation;
        private final boolean collisions;
        private final float radius;
        private final float weight;

        public CompiledChain(String id, String attach, String targetBone, List<String> chainRootToEnd, float[] restLengths, ModelPhysicsConfig.Bone bone)
        {
            this.id = id;
            this.attach = attach;
            this.targetBone = targetBone;
            this.chainRootToEnd = chainRootToEnd;
            this.restLengths = restLengths;
            this.gravity = bone.gravity();
            this.damping = bone.damping();
            this.stiffness = bone.stiffness();
            this.iterations = bone.iterations();
            this.relativeGravity = bone.relativeGravity();
            this.hasGravityRotation = bone.hasRelativeGravityRotation();
            this.gravityRotation = this.hasGravityRotation
                ? Matrices.toQuaternionZYXDegrees(bone.relativeGravityRotateX(), bone.relativeGravityRotateY(), bone.relativeGravityRotateZ())
                : new Quaternionf();
            this.collisions = bone.collisions();
            this.radius = bone.radius();
            this.weight = bone.weight();
        }

        public String id()
        {
            return this.id;
        }

        public String attach()
        {
            return this.attach;
        }

        public String targetBone()
        {
            return this.targetBone;
        }

        public List<String> chainRootToEnd()
        {
            return this.chainRootToEnd;
        }

        public float[] restLengths()
        {
            return this.restLengths;
        }

        public float gravity()
        {
            return this.gravity;
        }

        public float damping()
        {
            return this.damping;
        }

        public float stiffness()
        {
            return this.stiffness;
        }

        public int iterations()
        {
            return this.iterations;
        }

        public boolean relativeGravity()
        {
            return this.relativeGravity;
        }

        public boolean hasGravityRotation()
        {
            return this.hasGravityRotation;
        }

        public void applyGravityRotation(Vector3f direction)
        {
            if (this.hasGravityRotation)
            {
                this.gravityRotation.transform(direction);
            }
        }

        public boolean collisions()
        {
            return this.collisions;
        }

        public float radius()
        {
            return this.radius;
        }

        public float weight()
        {
            return this.weight;
        }
    }

    /** The two skeleton points that give a ragdoll body a real, collidable centre of mass. */
    public record RagdollRig(String rootBone, String chestBone)
    {
    }

    public record Compiled(List<CompiledChain> chains, ModelPhysicsConfig.Wind wind, RagdollRig ragdoll)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();
    private static final Map<RagdollKey, RagdollCompiled> RAGDOLLS = new HashMap<>();

    private record EmbeddedCompiled(IModel model, List<CompiledChain> chains, ModelPhysicsConfig.Wind wind)
    {
    }

    private record RagdollCompiled(List<CompiledChain> chains, RagdollRig rig)
    {
    }

    private ModelPhysicsCache()
    {
    }

    public static void clear()
    {
        EMBEDDED.clear();
        RAGDOLLS.clear();
    }

    public static Compiled getFromData(IModel model, MapType data)
    {
        if (model == null || data == null)
        {
            return null;
        }

        EmbeddedCompiled cached = EMBEDDED.get(data);

        if (cached != null && cached.model == model)
        {
            return new Compiled(cached.chains, cached.wind, null);
        }

        ModelPhysicsConfig config = ModelPhysicsIO.fromData(data);
        List<CompiledChain> compiled = compile(model, config);
        ModelPhysicsConfig.Wind wind = config != null ? config.wind() : ModelPhysicsConfig.Wind.NONE;

        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled, wind);
        EMBEDDED.put(data, next);

        return new Compiled(compiled, wind, null);
    }

    /**
     * A whole-skeleton rig, derived from the model rather than configured.
     *
     * <p>A ragdoll is not something an author sets up per model - it has to work on whatever
     * skeleton the shot happens to use - so the limbs are read off the hierarchy: every bone with
     * no children is the end of a limb, and the limb starts at the nearest ancestor that forks
     * (the chest for the arms, the hips for the legs) or at the model's root. That fork stays
     * where the animation puts it and everything below it goes limp, which is what a body does
     * when it stops holding itself up.</p>
     *
     * <p>Cached on the model and the settings, because the shape of the rig only changes when
     * either does, and rebuilding it per frame would cost more than simulating it.</p>
     */
    public static Compiled getRagdoll(IModel model, RagdollControl control)
    {
        if (model == null || control == null)
        {
            return null;
        }

        RagdollKey key = new RagdollKey(model, control.gravity, control.damping, control.stiffness, control.radius, control.collisions);
        RagdollCompiled cached = RAGDOLLS.get(key);

        if (cached != null)
        {
            return new Compiled(cached.chains, ModelPhysicsConfig.Wind.NONE, cached.rig);
        }

        List<CompiledChain> out = new ArrayList<>();
        List<String> groups = new ArrayList<>(model.getAllGroupKeys());

        Collections.sort(groups);

        ModelPhysicsConfig.Bone settings = new ModelPhysicsConfig.Bone(
            "", "", control.gravity, control.damping, control.stiffness, 4,
            false, 0F, 0F, 0F, control.collisions, control.radius, 1F
        );

        for (String leaf : groups)
        {
            if (!model.getDirectChildrenKeys(leaf).isEmpty())
            {
                continue;
            }

            String root = leaf;

            /* Walk up while the bone is an only child: the first fork above the limb is where the
             * body still holds together, so that is where the limb hangs from. */
            while (true)
            {
                String parent = model.getParentGroupKey(root);

                if (parent == null || parent.isEmpty() || parent.equals(root) || model.getDirectChildrenKeys(parent).size() != 1)
                {
                    break;
                }

                root = parent;
            }

            /* The fork itself anchors the limb - it is the last bone the animation still owns,
             * and everything from there down is what goes slack. */
            String attach = model.getParentGroupKey(root);

            if (attach == null || attach.isEmpty() || attach.equals(root))
            {
                /* A limb that reaches the model's own root has nothing to hang from. */
                continue;
            }

            List<String> ids = buildChainIds(model, leaf, attach);

            if (ids.size() < 2)
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            out.add(new CompiledChain(attach + ":" + leaf, attach, "", ids, lengths, settings));
        }

        RagdollRig rig = deriveRagdollRig(model);

        RAGDOLLS.put(key, new RagdollCompiled(out, rig));

        return new Compiled(out, ModelPhysicsConfig.Wind.NONE, rig);
    }

    /**
     * Finds the body axis without asking each model author to name their hips or chest.
     *
     * <p>The biggest root is the model's actual skeleton when decorative roots are present. From
     * there a spine usually travels through single-child bones until it reaches the first fork
     * (arms, head and legs). Models that put every limb directly below their root still need an
     * axis, so their largest child is a better physical chest point than abandoning the body
     * solve altogether.</p>
     */
    private static RagdollRig deriveRagdollRig(IModel model)
    {
        List<String> roots = new ArrayList<>(model.getRootGroupKeys());

        Collections.sort(roots);

        String root = null;
        int largest = -1;

        for (String candidate : roots)
        {
            int size = descendantCount(model, candidate);

            if (size > largest)
            {
                root = candidate;
                largest = size;
            }
        }

        if (root == null || root.isEmpty())
        {
            return null;
        }

        String chest = root;

        while (true)
        {
            List<String> children = new ArrayList<>(model.getDirectChildrenKeys(chest));

            if (children.size() != 1)
            {
                break;
            }

            chest = children.get(0);
        }

        if (chest.equals(root))
        {
            List<String> children = new ArrayList<>(model.getDirectChildrenKeys(root));

            Collections.sort(children);

            int largestChild = -1;

            for (String child : children)
            {
                int size = descendantCount(model, child);

                if (size > largestChild)
                {
                    chest = child;
                    largestChild = size;
                }
            }
        }

        return chest.equals(root) ? null : new RagdollRig(root, chest);
    }

    private static int descendantCount(IModel model, String root)
    {
        int count = 0;
        List<String> pending = new ArrayList<>();

        pending.add(root);

        for (int i = 0; i < pending.size(); i++)
        {
            count++;
            pending.addAll(model.getDirectChildrenKeys(pending.get(i)));
        }

        return count;
    }

    private record RagdollKey(IModel model, float gravity, float damping, float stiffness, float radius, boolean collisions)
    {}

    private static List<CompiledChain> compile(IModel model, ModelPhysicsConfig config)
    {
        if (config == null || config.bones() == null || config.bones().isEmpty())
        {
            return Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>();

        List<String> roots = new ArrayList<>(config.bones().keySet());
        Collections.sort(roots);

        for (String rootId : roots)
        {
            ModelPhysicsConfig.Bone chain = config.bones().get(rootId);

            if (chain == null)
            {
                continue;
            }

            String endId = chain.end();

            if (!model.getAllGroupKeys().contains(rootId) || !model.getAllGroupKeys().contains(endId))
            {
                continue;
            }

            List<String> ids = buildChainIds(model, endId, rootId);

            if (ids.isEmpty())
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            String attach = rootId;

            String id = rootId + ":" + endId;
            out.add(new CompiledChain(id, attach, chain.targetBone(), ids, lengths, chain));
        }

        return out;
    }

    private static List<String> buildChainIds(IModel model, String endId, String rootId)
    {
        List<String> list = new ArrayList<>();
        String group = endId;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (group.equals(rootId))
            {
                Collections.reverse(list);
                return list;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        return Collections.emptyList();
    }

    private static float[] computeRestLengths(IModel model, List<String> ids)
    {
        PhysicsRig rig = PhysicsRig.of(model);

        if (rig == null)
        {
            return null;
        }

        int n = ids.size();
        float[] lengths = new float[n];

        if (n == 1)
        {
            float len = rig.restLength(ids.get(0), null);

            if (len < 0F)
            {
                return null;
            }

            lengths[0] = len;

            return lengths;
        }

        for (int i = 0; i < n - 1; i++)
        {
            float len = rig.restLength(ids.get(i), ids.get(i + 1));

            if (len < 0F)
            {
                return null;
            }

            lengths[i] = len;
        }

        lengths[n - 1] = lengths[n - 2];

        return lengths;
    }
}
