package mchorse.bbs_mod.forms.renderers;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.crowd.CrowdJumpEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdLookEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdLookTarget;
import mchorse.bbs_mod.actions.crowd.CrowdMotionEvaluator;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdRagdollActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSource;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class CrowdFormRenderer extends FormRenderer<CrowdForm>
{
    private static final int MAX_TEXTURES = 256;
    private static final int UI_DENSITY_TARGET = 20_000;
    private static final int RECORDING_DENSITY_TARGET = 100_000;
    private static final int NEAR_DENSITY_TARGET = 50_000;
    private static final int MID_DENSITY_TARGET = 30_000;
    private static final int FAR_DENSITY_TARGET = 20_000;
    private static final int DISTANT_DENSITY_TARGET = 10_000;
    private static final int MAX_TERRAIN_CACHE_COLUMNS = 262_144;
    private static final int MISSING_TERRAIN_HEIGHT = Integer.MIN_VALUE;

    private int cachedCount = -1;
    private int cachedBudget = -1;
    private int cachedFormation = -1;
    private int cachedSeed;
    private boolean cachedPerBlock;
    private float cachedSpacing;
    private float cachedRadius;
    private float cachedHollow;
    private float cachedVariation;
    private long cachedSourceSignature;
    private int cachedTextureSignature;
    private Link cachedTextureFolder;
    private boolean cachedRecursiveTextures;
    private int cachedTextureRevision = Integer.MIN_VALUE;
    private boolean cachedExcludeLive;
    private List<Link> textures = List.of();
    private final Map<SourceTexture, Form> texturedForms = new HashMap<>();
    private final Set<Integer> untexturedSources = new HashSet<>();
    private final double[] motionPosition = new double[3];
    private final float[] lookRotation = new float[2];
    private final Vector3f worldPosition = new Vector3f();
    private final BlockPos.Mutable terrainProbe = new BlockPos.Mutable();
    private final Long2IntOpenHashMap terrainHeights = new Long2IntOpenHashMap();
    private World cachedTerrainWorld;
    private MemberLayout members = MemberLayout.EMPTY;
    private int cachedVisibleStride = -1;
    private int[] visibleSlots = new int[0];

    public CrowdFormRenderer(CrowdForm form)
    {
        super(form);

        this.terrainHeights.defaultReturnValue(MISSING_TERRAIN_HEIGHT);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Form member = this.form.getMemberForm();

        if (member != null && member != this.form)
        {
            FormUtilsClient.renderUI(member, context, x1, y1, x2, y2);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        if (this.form.getMemberForm() == null)
        {
            return;
        }

        this.updateLayout(context.entity instanceof StubEntity);
        StubEntity stub = context.entity instanceof StubEntity value ? value : null;
        Replay replay = stub == null ? null : stub.getReplay();
        float replayTick = stub == null ? 0F : stub.getReplayTick();
        CrowdMotionEvaluator.Frame motion = CrowdMotionEvaluator.frame(replay, replayTick);
        double ambientJumpRate = this.form.behaviorEnabled.get() && this.form.behaviorRandomJump.get()
            ? this.form.behaviorJumpRate.get()
            : 0D;
        CrowdJumpEvaluator.Frame jumps = CrowdJumpEvaluator.frame(replay, replayTick, ambientJumpRate);

        if (jumps != null && !jumps.hasPotential())
        {
            jumps = null;
        }

        CrowdLookEvaluator.Sample look = stub == null
            ? null
            : CrowdLookEvaluator.sample(stub.getFilm(), replay, replayTick);
        CrowdRagdollActionClip ragdoll = this.getRagdoll(replay, MathHelper.floor(replayTick));
        float ragdollProgress = ragdoll == null ? 0F : ragdoll.progress(MathHelper.floor(replayTick));
        MatrixStack parentWorld = context.world;
        int lodStride = this.getLodStride(context, parentWorld);
        int[] visibleSlots = this.getVisibleSlots(lodStride);
        boolean suppressStencilUpdates = context.suppressStencilUpdates;
        StubPose stubPose = stub == null ? null : StubPose.capture(stub);
        boolean sprinting = motion != null && motion.moving() && motion.path().run;
        World crowdWorld = stub == null ? null : stub.getWorld();
        boolean terrainFollow = crowdWorld != null && parentWorld != null && !context.ui
            && (motion == null || motion.path().terrainFollow);
        Matrix4f worldMatrix = parentWorld == null ? null : parentWorld.peek().getPositionMatrix();
        Form batchedForm = null;
        FormRenderer batchedRenderer = null;
        boolean batchOpen = false;

        if (stub != null)
        {
            stub.setSprinting(sprinting);

            if (jumps == null)
            {
                stub.setOnGround(true);
            }
        }

        context.suppressStencilUpdates = true;

        try
        {
            for (int slot : visibleSlots)
            {
                int logicalIndex = this.members.logicalIndices[slot];

                float x = this.members.x[slot];
                float y = 0F;
                float z = this.members.z[slot];
                float yaw = this.members.yaw[slot];

                if (motion != null)
                {
                    CrowdMotionEvaluator.memberPosition(motion, x, 0D, z, this.motionPosition);
                    x = (float) this.motionPosition[0];
                    y = (float) this.motionPosition[1];
                    z = (float) this.motionPosition[2];

                    if (motion.moving() && motion.path().faceTravel)
                    {
                        yaw += (float) (Math.atan2(motion.forward().z, motion.forward().x) * 180D / Math.PI - 90D);
                    }
                }

                if (terrainFollow)
                {
                    worldMatrix.transformPosition(this.worldPosition.set(x, 0F, z));

                    int groundY = this.getSurfaceY(crowdWorld,
                        MathHelper.floor(this.worldPosition.x), MathHelper.floor(this.worldPosition.z),
                        MathHelper.floor(this.worldPosition.y));
                    float verticalScale = worldMatrix.m11();

                    if (groundY != MISSING_TERRAIN_HEIGHT && Math.abs(verticalScale) > 0.000001F)
                    {
                        y += (groundY - this.worldPosition.y) / verticalScale;
                    }
                }

                double jumpHeight = jumps == null ? 0D : jumps.height(logicalIndex);

                y += (float) jumpHeight;

                if (stubPose != null)
                {
                    if (look != null)
                    {
                        /* Look overrides mutate the shared stub, so only those members need
                         * a full restore. Ordinary members keep the one frame-level pose. */
                        stubPose.restore(stub);
                        stub.setSprinting(sprinting);

                        if (worldMatrix != null)
                        {
                            worldMatrix.transformPosition(this.worldPosition.set(x, y, z));
                        }
                        else
                        {
                            this.worldPosition.set((float) (stub.getX() + x), (float) (stub.getY() + y),
                                (float) (stub.getZ() + z));
                        }

                        if (CrowdLookEvaluator.rotation(
                            this.worldPosition.x, this.worldPosition.y + stub.getEyeHeight(), this.worldPosition.z,
                            look, this.lookRotation))
                        {
                            CrowdLookTarget control = look.control();
                            float bodyYaw = stubPose.bodyYaw;
                            float delta = MathHelper.wrapDegrees(this.lookRotation[0] - bodyYaw);
                            float limit = Math.max(0F, this.form.behaviorHeadYawLimit.get());

                            if (Math.abs(delta) > limit)
                            {
                                bodyYaw += delta - Math.copySign(limit, delta);
                            }

                            stubPose.applyLook(stub, bodyYaw, this.lookRotation[0], this.lookRotation[1], control);

                            if (control.yaw() || control.bodyYaw())
                            {
                                yaw += MathHelper.wrapDegrees(bodyYaw - stubPose.bodyYaw);
                            }
                        }
                    }

                    if (look != null || jumps != null)
                    {
                        stub.setOnGround(jumpHeight <= 0.000001D);
                    }
                }

                int sourceIndex = this.members.sourceIndices[slot];
                Form member = this.getMemberForm(sourceIndex);

                if (member == null || member instanceof CrowdForm)
                {
                    continue;
                }

                member = this.getTexturedForm(sourceIndex, member, this.members.textureIndices[slot]);
                boolean batchable = member.parts.getAllTyped().isEmpty();

                if (!batchable || member != batchedForm)
                {
                    if (batchOpen)
                    {
                        batchedRenderer.endBatch();
                        batchOpen = false;
                    }

                    batchedForm = batchable ? member : null;
                    batchedRenderer = batchable ? FormUtilsClient.getRenderer(member) : null;
                    batchOpen = batchedRenderer != null && batchedRenderer.beginBatch(context);
                }

                if (batchable && !batchOpen)
                {
                    continue;
                }

                context.stack.push();

                if (!batchable && parentWorld != null)
                {
                    parentWorld.push();
                }

                context.world = batchable ? null : parentWorld;

                try
                {
                    this.applyMemberTransform(context.stack, x, y, z, yaw, this.members.scale[slot],
                        ragdoll, ragdollProgress, logicalIndex);

                    if (!batchable && parentWorld != null)
                    {
                        this.applyMemberTransform(parentWorld, x, y, z, yaw, this.members.scale[slot],
                            ragdoll, ragdollProgress, logicalIndex);
                    }

                    if (batchable)
                    {
                        /* The batch already owns this renderer and the crowd stack scope.
                         * Avoid another renderer lookup and another matrix push/pop. */
                        batchedRenderer.renderPreparedInPlace(context);
                    }
                    else
                    {
                        FormUtilsClient.render(member, context);
                    }
                }
                finally
                {
                    context.stack.pop();

                    if (!batchable && parentWorld != null)
                    {
                        parentWorld.pop();
                    }

                    context.world = parentWorld;
                }
            }
        }
        finally
        {
            if (batchOpen)
            {
                batchedRenderer.endBatch();
            }

            context.world = parentWorld;
            context.suppressStencilUpdates = suppressStencilUpdates;

            if (stubPose != null)
            {
                stubPose.restore(stub);
            }
        }
    }

    /** Cache loaded heightmap columns so dense members sharing a block never repeat the query. */
    private int getSurfaceY(World world, int x, int z, int fallbackY)
    {
        if (world != this.cachedTerrainWorld)
        {
            this.cachedTerrainWorld = world;
            this.terrainHeights.clear();
        }

        long key = (long) x << 32 ^ z & 0xffffffffL;
        int cached = this.terrainHeights.get(key);

        if (cached != MISSING_TERRAIN_HEIGHT)
        {
            return cached;
        }

        this.terrainProbe.set(x, fallbackY, z);

        if (!world.isChunkLoaded(this.terrainProbe))
        {
            return MISSING_TERRAIN_HEIGHT;
        }

        int height = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        if (this.terrainHeights.size() >= MAX_TERRAIN_CACHE_COLUMNS)
        {
            this.terrainHeights.clear();
        }

        this.terrainHeights.put(key, height);

        return height;
    }

    private CrowdRagdollActionClip getRagdoll(Replay replay, int tick)
    {
        if (replay == null)
        {
            return null;
        }

        CrowdRagdollActionClip selected = null;

        for (Clip clip : replay.actions.getClips(tick))
        {
            if (clip instanceof CrowdRagdollActionClip value && value.enabled.get())
            {
                selected = value;
            }
        }

        return selected;
    }

    /**
     * Use one LOD density for the whole formation. Per-member distance bands
     * make the camera-facing half denser and visibly deform circles.
     */
    private int getLodStride(FormRenderingContext context, MatrixStack world)
    {
        int size = this.members.size;
        int renderLimit = Math.max(1, Math.min(size, this.form.renderBudget.get()));

        if (size <= Math.min(UI_DENSITY_TARGET, renderLimit))
        {
            return 1;
        }

        if (context.ui)
        {
            return strideForTarget(size, Math.min(UI_DENSITY_TARGET, renderLimit));
        }

        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording())
        {
            return strideForTarget(size, Math.min(RECORDING_DENSITY_TARGET, renderLimit));
        }

        if (world == null)
        {
            return strideForTarget(size, Math.min(MID_DENSITY_TARGET, renderLimit));
        }

        world.peek().getPositionMatrix().transformPosition(this.worldPosition.set(0F, 0F, 0F));

        double dx = this.worldPosition.x - context.camera.position.x;
        double dy = this.worldPosition.y - context.camera.position.y;
        double dz = this.worldPosition.z - context.camera.position.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared <= 24D * 24D)
        {
            return strideForTarget(size, Math.min(NEAR_DENSITY_TARGET, renderLimit));
        }

        if (distanceSquared <= 48D * 48D)
        {
            return strideForTarget(size, Math.min(MID_DENSITY_TARGET, renderLimit));
        }

        if (distanceSquared <= 96D * 96D)
        {
            return strideForTarget(size, Math.min(FAR_DENSITY_TARGET, renderLimit));
        }

        return strideForTarget(size, Math.min(DISTANT_DENSITY_TARGET, renderLimit));
    }

    private static int strideForTarget(int size, int target)
    {
        return Math.max(1, (size + Math.max(1, target) - 1) / Math.max(1, target));
    }

    private int[] getVisibleSlots(int stride)
    {
        stride = Math.max(1, stride);

        if (stride == this.cachedVisibleStride)
        {
            return this.visibleSlots;
        }

        int[] slots = new int[(this.members.size + stride - 1) / stride];
        int cursor = 0;

        for (int slot = 0; slot < this.members.size; slot++)
        {
            if (this.members.lodRanks[slot] % stride == 0)
            {
                slots[cursor++] = slot;
            }
        }

        this.cachedVisibleStride = stride;
        this.visibleSlots = cursor == slots.length ? slots : Arrays.copyOf(slots, cursor);

        return this.visibleSlots;
    }

    private void applyMemberTransform(MatrixStack stack, float x, float y, float z, float yaw, float scale,
        CrowdRagdollActionClip ragdoll, float ragdollProgress, int logicalIndex)
    {
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.scale(scale, scale, scale);

        if (ragdoll == null || ragdollProgress <= 0F)
        {
            return;
        }

        float direction = ragdoll.randomDirection.get()
            ? this.random(logicalIndex, 97) * 360F - 180F
            : ragdoll.direction.get();

        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ragdoll.tilt.get() * ragdollProgress));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-direction));
    }

    private void updateLayout(boolean excludeLive)
    {
        this.updateTextures();

        int count = this.form.count.get();
        int budget = count;
        long sourceSignature = this.form.sources.getAllTyped().isEmpty()
            ? System.identityHashCode(this.form.getMemberForm())
            : this.form.sources.signature();
        int textureSignature = Objects.hash(
            this.form.textureOverride.get(),
            this.form.textureFolder.get(),
            this.form.randomTextures.get(),
            this.form.randomTextureFolder.get(),
            this.form.recursiveTextures.get(),
            this.form.textureRevision.get(),
            this.textures.size()
        );

        if (sourceSignature != this.cachedSourceSignature)
        {
            this.texturedForms.clear();
            this.untexturedSources.clear();
        }

        if (count == this.cachedCount && budget == this.cachedBudget && this.form.formation.get() == this.cachedFormation
            && this.form.seed.get() == this.cachedSeed && this.form.perBlock.get() == this.cachedPerBlock
            && this.form.spacing.get() == this.cachedSpacing && this.form.radius.get() == this.cachedRadius
            && this.form.hollow.get() == this.cachedHollow
            && this.form.variation.get() == this.cachedVariation && sourceSignature == this.cachedSourceSignature
            && textureSignature == this.cachedTextureSignature && excludeLive == this.cachedExcludeLive)
        {
            return;
        }

        this.cachedCount = count;
        this.cachedBudget = budget;
        this.cachedFormation = this.form.formation.get();
        this.cachedSeed = this.form.seed.get();
        this.cachedPerBlock = this.form.perBlock.get();
        this.cachedSpacing = this.form.spacing.get();
        this.cachedRadius = this.form.radius.get();
        this.cachedHollow = this.form.hollow.get();
        this.cachedVariation = this.form.variation.get();
        this.cachedSourceSignature = sourceSignature;
        this.cachedTextureSignature = textureSignature;
        this.cachedExcludeLive = excludeLive;
        int[] indices;

        if (excludeLive)
        {
            indices = CrowdUtils.visualFormationIndices(count, budget, CrowdSpawnActionClip.MAX_LIVE_MEMBERS);
        }
        else
        {
            indices = new int[budget];

            for (int i = 0; i < budget; i++)
            {
                indices[i] = budget == count ? i : (int) ((long) i * count / budget);
            }
        }

        MemberLayout members = new MemberLayout(indices.length);

        for (int i = 0; i < indices.length; i++)
        {
            this.point(indices[i], count, members, i);
        }

        this.members = members.sortedByAppearance();
        this.cachedVisibleStride = -1;
        this.visibleSlots = new int[0];
    }

    private void updateTextures()
    {
        Link override = this.form.textureOverride.get();
        Link folder = this.form.randomTextures.get() && this.form.randomTextureFolder.get() != null
            ? this.form.randomTextureFolder.get()
            : this.form.textureFolder.get();
        boolean recursive = this.form.recursiveTextures.get();
        int revision = this.form.textureRevision.get();

        if (override != null)
        {
            folder = override;
        }

        if (Objects.equals(folder, this.cachedTextureFolder)
            && recursive == this.cachedRecursiveTextures
            && revision == this.cachedTextureRevision)
        {
            return;
        }

        this.cachedTextureFolder = folder;
        this.cachedRecursiveTextures = recursive;
        this.cachedTextureRevision = revision;
        this.texturedForms.clear();
        this.untexturedSources.clear();

        if (override != null)
        {
            this.textures = List.of(override);

            return;
        }

        if (folder == null || folder.source.isEmpty())
        {
            this.textures = List.of();

            return;
        }

        List<Link> textures = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(folder, recursive))
        {
            if (!link.path.endsWith("/") && link.path.toLowerCase().endsWith(".png"))
            {
                textures.add(link);
            }
        }

        textures.sort(Comparator.comparing(Link::toString));

        if (textures.size() > MAX_TEXTURES)
        {
            textures = new ArrayList<>(textures.subList(0, MAX_TEXTURES));
        }

        this.textures = textures;
    }

    private Form getTexturedForm(int sourceIndex, Form original, int textureIndex)
    {
        if (textureIndex < 0 || textureIndex >= this.textures.size())
        {
            return original;
        }

        if (this.untexturedSources.contains(sourceIndex))
        {
            return original;
        }

        Link texture = this.textures.get(textureIndex);
        SourceTexture key = new SourceTexture(sourceIndex, texture);
        Form cached = this.texturedForms.get(key);

        if (cached != null)
        {
            return cached;
        }

        Form copy = FormUtils.copy(original);

        if (copy == null)
        {
            this.untexturedSources.add(sourceIndex);

            return original;
        }

        BaseValue property = FormUtils.getProperty(copy, "texture");

        if (property instanceof ValueLink value)
        {
            value.set(texture);
            this.texturedForms.put(key, copy);

            return copy;
        }

        this.untexturedSources.add(sourceIndex);

        return original;
    }

    private void point(int index, int count, MemberLayout members, int slot)
    {
        float spacing = this.form.spacing.get();
        float radius = this.form.radius.get();
        float yaw = (this.random(index, 11) - 0.5F) * this.form.variation.get();
        float x;
        float z;

        CrowdFormation formation = CrowdFormation.get(this.form.formation.get());

        if (formation == CrowdFormation.CIRCLE)
        {
            Vec3d point = CrowdUtils.circlePoint(index, count, radius, spacing, this.form.hollow.get());

            x = (float) point.x;
            z = (float) point.z;
        }
        else if (formation == CrowdFormation.CIRCLE_OUTLINE)
        {
            float angle = (float) (Math.PI * 2D * index / count);
            x = (float) Math.cos(angle) * radius;
            z = (float) Math.sin(angle) * radius;
            yaw += (float) Math.toDegrees(-angle) + 90F;
        }
        else if (formation == CrowdFormation.LINE)
        {
            x = (index - (count - 1) * 0.5F) * spacing;
            z = 0F;
        }
        else if (formation == CrowdFormation.SQUARE_OUTLINE || formation == CrowdFormation.BOX_OUTLINE)
        {
            float side = Math.max(radius, spacing);
            float progress = index / (float) count;
            float edge = progress * 4F;

            if (edge < 1F) { x = -side + edge * side * 2F; z = -side; }
            else if (edge < 2F) { x = side; z = -side + (edge - 1F) * side * 2F; }
            else if (edge < 3F) { x = side - (edge - 2F) * side * 2F; z = side; }
            else { x = -side; z = side - (edge - 3F) * side * 2F; }
        }
        else if (formation == CrowdFormation.SQUARE || formation == CrowdFormation.BOX)
        {
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
            int rows = Math.max(1, (int) Math.ceil(count / (float) columns));
            float side = Math.max(radius, spacing);
            x = columns == 1 ? 0F : -side + (index % columns) * side * 2F / (columns - 1);
            z = rows == 1 ? 0F : -side + (index / columns) * side * 2F / (rows - 1);
        }
        else
        {
            int columns = (int) Math.ceil(Math.sqrt(count));
            x = (index % columns - (columns - 1) * 0.5F) * spacing;
            z = (index / columns - ((count - 1) / columns) * 0.5F) * spacing;
        }

        long memberId = this.form.getStableMemberId(index);
        int sourceIndex = this.form.sources.getAllTyped().isEmpty() ? 0 : this.form.sources.selectSource(memberId);
        CrowdMemberSource source = this.form.sources.getSource(sourceIndex);
        float scale = source == null ? 1F : source.getScale(this.random(index, 43));
        int textureIndex = this.form.getStableTextureIndex(index, this.textures.size());

        members.logicalIndices[slot] = index;
        members.lodRanks[slot] = slot;
        members.x[slot] = x;
        members.z[slot] = z;
        members.yaw[slot] = yaw;
        members.sourceIndices[slot] = sourceIndex;
        members.textureIndices[slot] = (short) textureIndex;
        members.scale[slot] = scale;
    }

    private Form getMemberForm(int sourceIndex)
    {
        CrowdMemberSource source = this.form.sources.getSource(sourceIndex);

        return source == null ? this.form.getMemberForm() : source.getForm();
    }

    private float random(int index, int salt)
    {
        int value = index * 0x9e3779b9 ^ this.form.seed.get() * 0x85ebca6b ^ salt * 0xc2b2ae35;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return (value & 0x00ffffff) / 16777216F;
    }

    /** Compact structure-of-arrays layout. It avoids one heap object per visual member and
     * keeps the hot render loop reading contiguous primitive memory. */
    private static final class MemberLayout
    {
        private static final MemberLayout EMPTY = new MemberLayout(0);

        private final int size;
        private final int[] logicalIndices;
        private final int[] lodRanks;
        private final int[] sourceIndices;
        private final short[] textureIndices;
        private final float[] x;
        private final float[] z;
        private final float[] yaw;
        private final float[] scale;

        private MemberLayout(int size)
        {
            this.size = Math.max(0, size);
            this.logicalIndices = new int[this.size];
            this.lodRanks = new int[this.size];
            this.sourceIndices = new int[this.size];
            this.textureIndices = new short[this.size];
            this.x = new float[this.size];
            this.z = new float[this.size];
            this.yaw = new float[this.size];
            this.scale = new float[this.size];
        }

        private MemberLayout sortedByAppearance()
        {
            if (this.size < 2)
            {
                return this;
            }

            long[] order = new long[this.size];

            for (int i = 0; i < this.size; i++)
            {
                long source = Integer.toUnsignedLong(this.sourceIndices[i]);
                long texture = (this.textureIndices[i] + 1L) & 0x1ffL;

                order[i] = source << 32 | texture << 23 | (i & 0x7fffffL);
            }

            Arrays.sort(order);

            MemberLayout sorted = new MemberLayout(this.size);

            for (int i = 0; i < order.length; i++)
            {
                int sourceSlot = (int) (order[i] & 0x7fffffL);

                sorted.logicalIndices[i] = this.logicalIndices[sourceSlot];
                sorted.lodRanks[i] = this.lodRanks[sourceSlot];
                sorted.sourceIndices[i] = this.sourceIndices[sourceSlot];
                sorted.textureIndices[i] = this.textureIndices[sourceSlot];
                sorted.x[i] = this.x[sourceSlot];
                sorted.z[i] = this.z[sourceSlot];
                sorted.yaw[i] = this.yaw[sourceSlot];
                sorted.scale[i] = this.scale[sourceSlot];
            }

            return sorted;
        }
    }

    private record SourceTexture(int sourceIndex, Link texture)
    {}

    private record StubPose(float yaw, float prevYaw, float headYaw, float prevHeadYaw,
                            float pitch, float prevPitch, float bodyYaw, float prevBodyYaw,
                            float prevPrevBodyYaw, boolean sprinting, boolean onGround)
    {
        private static StubPose capture(StubEntity stub)
        {
            return new StubPose(
                stub.getYaw(), stub.getPrevYaw(),
                stub.getHeadYaw(), stub.getPrevHeadYaw(),
                stub.getPitch(), stub.getPrevPitch(),
                stub.getBodyYaw(), stub.getPrevBodyYaw(), stub.getPrevPrevBodyYaw(),
                stub.isSprinting(), stub.isOnGround()
            );
        }

        private void restore(StubEntity stub)
        {
            stub.setYaw(this.yaw);
            stub.setPrevYaw(this.prevYaw);
            stub.setHeadYaw(this.headYaw);
            stub.setPrevHeadYaw(this.prevHeadYaw);
            stub.setPitch(this.pitch);
            stub.setPrevPitch(this.prevPitch);
            stub.setBodyYaw(this.bodyYaw);
            stub.setPrevBodyYaw(this.prevBodyYaw);
            stub.setPrevPrevBodyYaw(this.prevPrevBodyYaw);
            stub.setSprinting(this.sprinting);
            stub.setOnGround(this.onGround);
        }

        private void applyLook(StubEntity stub, float bodyYaw, float headYaw, float pitch,
            CrowdLookTarget control)
        {
            float yaw = control.yaw() ? bodyYaw : this.yaw;
            float appliedBodyYaw = control.bodyYaw() ? bodyYaw : this.bodyYaw;
            float appliedHeadYaw = control.headYaw() ? headYaw : this.headYaw;
            float appliedPitch = control.pitch() ? pitch : this.pitch;

            stub.setYaw(yaw);
            stub.setPrevYaw(yaw);
            stub.setBodyYaw(appliedBodyYaw);
            stub.setPrevBodyYaw(appliedBodyYaw);
            stub.setPrevPrevBodyYaw(appliedBodyYaw);
            stub.setHeadYaw(appliedHeadYaw);
            stub.setPrevHeadYaw(appliedHeadYaw);
            stub.setPitch(appliedPitch);
            stub.setPrevPitch(appliedPitch);
        }
    }
}
