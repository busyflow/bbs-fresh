package mchorse.bbs_mod.client;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.mixin.client.HeldItemRendererAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

/**
 * Renders the selected replay through Minecraft's first-person hand pipeline.
 * The live player's render-only state is restored immediately after each frame.
 */
public class FirstPersonReplayHands
{
    private static Active active;
    private static Snapshot snapshot;

    public static boolean begin(float tickDelta)
    {
        if (snapshot != null)
        {
            return true;
        }

        active = resolve(tickDelta);

        if (active == null)
        {
            return false;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            active = null;

            return false;
        }

        snapshot = new Snapshot(player);
        apply(player, active);

        return true;
    }

    public static void end()
    {
        if (snapshot != null)
        {
            snapshot.restore();
            snapshot = null;
        }

        active = null;
    }

    public static boolean isRendering()
    {
        return active != null && snapshot != null;
    }

    public static boolean shouldRenderEditorHands(float tickDelta)
    {
        return resolve(tickDelta) != null;
    }

    public static boolean canVanillaRenderHands()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.player != null
            && mc.interactionManager != null
            && mc.options.getPerspective().isFirstPerson()
            && !mc.options.hudHidden
            && mc.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR
            && !(mc.getCameraEntity() instanceof LivingEntity living && living.isSleeping());
    }

    public static boolean shouldRenderHand(Hand hand)
    {
        if (!isRendering() || hand == Hand.MAIN_HAND)
        {
            return true;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        return player != null && !player.getOffHandStack().isEmpty();
    }

    public static boolean shouldRenderExtraOffhandArm(Hand hand, ItemStack stack)
    {
        return isRendering() && hand == Hand.OFF_HAND && (stack == null || stack.isEmpty()) && shouldRenderHand(hand);
    }

    public static boolean shouldForceRenderBothHands(ClientPlayerEntity player)
    {
        return isRendering() && player != null && !player.getOffHandStack().isEmpty();
    }

    public static Arm getArm(Hand hand)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Arm mainArm = player == null ? Arm.RIGHT : player.getMainArm();

        return hand == Hand.MAIN_HAND ? mainArm : mainArm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
    }

    /**
     * Applies the animated item-only pose after Minecraft has positioned the
     * first-person item. The arm is rendered before this point, so a talking
     * item can move and scale without dragging the replay's arm with it.
     */
    public static void applyItemTransform(MatrixStack matrices, Hand hand)
    {
        if (active == null)
        {
            return;
        }

        Transform transform = hand == Hand.MAIN_HAND
            ? active.replay.keyframes.rightHandPose.interpolate(active.tick)
            : active.replay.keyframes.leftHandPose.interpolate(active.tick);

        if (transform != null)
        {
            MatrixStackUtils.applyTransform(matrices, transform);
        }
    }

    private static Active resolve(float tickDelta)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard) || !(dashboard.getPanels().panel instanceof UIFilmPanel panel))
        {
            return null;
        }

        UIFilmController controller = panel.getController();

        if (controller.getPovMode() != UIFilmController.CAMERA_MODE_HANDS_ON_FIRST_PERSON || panel.replayEditor == null)
        {
            return null;
        }

        Replay replay = panel.replayEditor.getReplay();
        IEntity entity = controller.getCurrentEntity();

        if (replay == null || entity == null || !replay.enabled.get())
        {
            return null;
        }

        float transition = panel.getRunner().isRunning() ? tickDelta : 0F;

        return new Active(replay, entity, replay.getTick(panel.getCursor()) + transition);
    }

    private static void apply(ClientPlayerEntity player, Active active)
    {
        Morph morph = Morph.getMorph(player);
        Form form = active.entity.getForm();

        if (morph != null && form != null)
        {
            morph.setFormForRender(form);
        }

        ItemStack mainHand = getStack(active.replay.keyframes.mainHand, active.tick, active.entity.getEquipmentStack(EquipmentSlot.MAINHAND));
        ItemStack offHand = getStack(active.replay.keyframes.offHand, active.tick, active.entity.getEquipmentStack(EquipmentSlot.OFFHAND));
        int selectedSlot = active.replay.keyframes.selectedSlot.isEmpty()
            ? active.entity.getSelectedSlot()
            : active.replay.keyframes.selectedSlot.interpolate(active.tick, active.entity.getSelectedSlot());

        player.getInventory().selectedSlot = Math.max(0, Math.min(8, selectedSlot));
        player.equipStack(EquipmentSlot.MAINHAND, mainHand);
        player.equipStack(EquipmentSlot.OFFHAND, offHand);

        HeldItemRendererAccessor renderer = getHeldItemRenderer();

        if (renderer != null)
        {
            renderer.bbs$setMainHand(mainHand);
            renderer.bbs$setOffHand(offHand);
            renderer.bbs$setEquipProgressMainHand(1F);
            renderer.bbs$setPrevEquipProgressMainHand(1F);
            renderer.bbs$setEquipProgressOffHand(1F);
            renderer.bbs$setPrevEquipProgressOffHand(1F);
        }
    }

    private static ItemStack getStack(mchorse.bbs_mod.utils.keyframes.KeyframeChannel<ItemStack> channel, float tick, ItemStack fallback)
    {
        ItemStack stack = channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);

        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static HeldItemRendererAccessor getHeldItemRenderer()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.gameRenderer == null)
        {
            return null;
        }

        HeldItemRenderer renderer = mc.gameRenderer.firstPersonRenderer;

        return renderer instanceof HeldItemRendererAccessor accessor ? accessor : null;
    }

    private record Active(Replay replay, IEntity entity, float tick) {}

    private static class Snapshot
    {
        private final ClientPlayerEntity player;
        private final Morph morph;
        private final Form form;
        private final ItemStack mainHand;
        private final ItemStack offHand;
        private final int selectedSlot;
        private final HeldItemRendererAccessor renderer;
        private final ItemStack rendererMainHand;
        private final ItemStack rendererOffHand;
        private final float equipMain;
        private final float prevEquipMain;
        private final float equipOff;
        private final float prevEquipOff;

        private Snapshot(ClientPlayerEntity player)
        {
            this.player = player;
            this.morph = Morph.getMorph(player);
            this.form = this.morph == null ? null : this.morph.getForm();
            this.mainHand = player.getMainHandStack().copy();
            this.offHand = player.getOffHandStack().copy();
            this.selectedSlot = player.getInventory().selectedSlot;
            this.renderer = getHeldItemRenderer();

            if (this.renderer == null)
            {
                this.rendererMainHand = ItemStack.EMPTY;
                this.rendererOffHand = ItemStack.EMPTY;
                this.equipMain = this.prevEquipMain = this.equipOff = this.prevEquipOff = 0F;
            }
            else
            {
                this.rendererMainHand = this.renderer.bbs$getMainHand().copy();
                this.rendererOffHand = this.renderer.bbs$getOffHand().copy();
                this.equipMain = this.renderer.bbs$getEquipProgressMainHand();
                this.prevEquipMain = this.renderer.bbs$getPrevEquipProgressMainHand();
                this.equipOff = this.renderer.bbs$getEquipProgressOffHand();
                this.prevEquipOff = this.renderer.bbs$getPrevEquipProgressOffHand();
            }
        }

        private void restore()
        {
            if (this.morph != null)
            {
                this.morph.setFormForRender(this.form);
            }

            this.player.getInventory().selectedSlot = this.selectedSlot;
            this.player.equipStack(EquipmentSlot.MAINHAND, this.mainHand);
            this.player.equipStack(EquipmentSlot.OFFHAND, this.offHand);

            if (this.renderer != null)
            {
                this.renderer.bbs$setMainHand(this.rendererMainHand);
                this.renderer.bbs$setOffHand(this.rendererOffHand);
                this.renderer.bbs$setEquipProgressMainHand(this.equipMain);
                this.renderer.bbs$setPrevEquipProgressMainHand(this.prevEquipMain);
                this.renderer.bbs$setEquipProgressOffHand(this.equipOff);
                this.renderer.bbs$setPrevEquipProgressOffHand(this.prevEquipOff);
            }
        }
    }
}
