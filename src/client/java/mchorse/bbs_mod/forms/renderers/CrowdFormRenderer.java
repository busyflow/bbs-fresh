package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSMod;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
    private MemberTransform[] members = new MemberTransform[0];

    public CrowdFormRenderer(CrowdForm form)
    {
        super(form);
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
        CrowdLookEvaluator.Sample look = stub == null
            ? null
            : CrowdLookEvaluator.sample(stub.getFilm(), replay, replayTick);
        CrowdRagdollActionClip ragdoll = this.getRagdoll(replay, MathHelper.floor(replayTick));
        float ragdollProgress = ragdoll == null ? 0F : ragdoll.progress(MathHelper.floor(replayTick));
        MatrixStack parentWorld = context.world;
        int lodStride = this.getLodStride(context, parentWorld);
        boolean suppressStencilUpdates = context.suppressStencilUpdates;
        StubPose stubPose = stub == null ? null : StubPose.capture(stub);
        Form batchedForm = null;
        FormRenderer batchedRenderer = null;
        boolean batchOpen = false;

        if (stub != null && motion != null)
        {
            stub.setSprinting(motion.moving() && motion.path().run);
        }

        context.suppressStencilUpdates = true;

        try
        {
            for (MemberTransform memberTransform : this.members)
            {
                float x = memberTransform.x;
                float y = 0F;
                float z = memberTransform.z;
                float yaw = memberTransform.yaw;

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

                double jumpHeight = jumps == null ? 0D : jumps.height(memberTransform.logicalIndex);

                y += (float) jumpHeight;

                if (memberTransform.logicalIndex % lodStride != 0)
                {
                    continue;
                }

                if (stubPose != null)
                {
                    stubPose.restore(stub);
                    if (motion != null)
                    {
                        stub.setSprinting(motion.moving() && motion.path().run);
                    }

                    if (look != null && CrowdLookEvaluator.rotation(
                        stub.getX() + x, stub.getY() + y + stub.getEyeHeight(), stub.getZ() + z,
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

                    stub.setOnGround(jumpHeight <= 0.000001D);
                }

                Form member = this.getMemberForm(memberTransform.sourceIndex);

                if (member == null || member instanceof CrowdForm)
                {
                    continue;
                }

                member = this.getTexturedForm(memberTransform.sourceIndex, member, memberTransform.textureIndex);
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
                    this.applyMemberTransform(context.stack, x, y, z, yaw, memberTransform.scale,
                        ragdoll, ragdollProgress, memberTransform.logicalIndex);

                    if (!batchable && parentWorld != null)
                    {
                        this.applyMemberTransform(parentWorld, x, y, z, yaw, memberTransform.scale,
                            ragdoll, ragdollProgress, memberTransform.logicalIndex);
                    }

                    if (batchable)
                    {
                        FormUtilsClient.renderPrepared(member, context);
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
        if (this.members.length <= 2048)
        {
            return 1;
        }

        if (context.ui)
        {
            return Math.max(1, (this.members.length + 1023) / 1024);
        }

        if (world == null)
        {
            return 1;
        }

        world.peek().getPositionMatrix().transformPosition(this.worldPosition.set(0F, 0F, 0F));

        double dx = this.worldPosition.x - context.camera.position.x;
        double dy = this.worldPosition.y - context.camera.position.y;
        double dz = this.worldPosition.z - context.camera.position.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared <= 24D * 24D)
        {
            return 1;
        }

        if (distanceSquared <= 48D * 48D)
        {
            return 2;
        }

        if (distanceSquared <= 96D * 96D)
        {
            return 4;
        }

        return 8;
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
        int budget = Math.min(count, this.form.renderBudget.get());
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

        this.members = new MemberTransform[indices.length];

        for (int i = 0; i < indices.length; i++)
        {
            this.members[i] = this.point(indices[i], count);
        }

        Arrays.sort(this.members, Comparator
            .comparingInt(MemberTransform::sourceIndex)
            .thenComparingInt(MemberTransform::textureIndex));
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

    private MemberTransform point(int index, int count)
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

        return new MemberTransform(index, x, z, yaw, sourceIndex, textureIndex, scale);
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

    private record MemberTransform(int logicalIndex, float x, float z, float yaw, int sourceIndex, int textureIndex, float scale)
    {}

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
