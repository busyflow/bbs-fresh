package mchorse.bbs_mod.client;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bridges BBS replay data into vanilla's held-item feature renderer. Context is
 * scoped around a MobForm render, so real world mobs and players remain untouched.
 */
public class HeldItemPoseContext
{
    private static final ThreadLocal<Deque<IEntity>> SOURCES = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(IEntity source)
    {
        if (source != null)
        {
            SOURCES.get().push(source);
        }
    }

    public static void pop()
    {
        Deque<IEntity> sources = SOURCES.get();

        if (!sources.isEmpty())
        {
            sources.pop();
        }

        if (sources.isEmpty())
        {
            SOURCES.remove();
        }
    }

    public static boolean apply(MatrixStack matrices, LivingEntity renderedEntity, Arm arm)
    {
        Deque<IEntity> sources = SOURCES.get();

        if (sources.isEmpty())
        {
            return false;
        }

        IEntity source = sources.peek();
        EquipmentSlot slot = renderedEntity.getMainArm() == arm ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        Transform transform = source.getEquipmentTransform(slot);

        if (transform == null)
        {
            return false;
        }

        MatrixStackUtils.applyTransform(matrices, transform);

        return true;
    }
}
