package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.crowd.CrowdWalk;
import mchorse.bbs_mod.actions.crowd.CrowdTexture;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSource;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdFormTest
{
    @BeforeAll
    public static void bootstrapFormRuntime()
    {
        BBSSettings.recordingPoseTransformOverlays = new ValueInt("recording_pose_transform_overlays", 0);
    }

    @Test
    public void stableMemberIdsAreRepeatableAndUnique()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.seed.set(42);

        assertEquals(crowd.getStableMemberId(100), crowd.getStableMemberId(100));
        assertNotEquals(crowd.getStableMemberId(100), crowd.getStableMemberId(101));
    }

    @Test
    public void stableTextureSelectionIsRepeatableAndBounded()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.seed.set(991);

        for (int i = 0; i < 10_000; i++)
        {
            int texture = crowd.getStableTextureIndex(i, 37);

            assertEquals(texture, crowd.getStableTextureIndex(i, 37));
            assertTrue(texture >= 0 && texture < 37);
        }

        assertEquals(-1, crowd.getStableTextureIndex(0, 0));
    }

    @Test
    public void staticSetupValuesStayOffTheKeyframeTimeline()
    {
        CrowdForm crowd = new CrowdForm();

        assertEquals(6, CrowdForm.CURRENT_SCHEMA);
        assertFalse(crowd.sources.isVisible());
        assertFalse(crowd.count.isVisible());
        assertFalse(crowd.formation.isVisible());
        assertFalse(crowd.perBlock.isVisible());
        assertFalse(crowd.spacing.isVisible());
        assertFalse(crowd.radius.isVisible());
        assertFalse(crowd.seed.isVisible());
        assertFalse(crowd.variation.isVisible());
        assertFalse(crowd.textureFolder.isVisible());
        assertFalse(crowd.recursiveTextures.isVisible());
        assertFalse(crowd.health.isVisible());
        assertFalse(crowd.behaviorEnabled.isVisible());
        assertFalse(crowd.volume.isVisible());
    }

    @Test
    public void weightedSelectionIsRepeatable()
    {
        CrowdForm crowd = new CrowdForm();
        CrowdMemberSource first = crowd.sources.addSource(new LabelForm());
        CrowdMemberSource second = crowd.sources.addSource(new LabelForm());

        first.weight.set(1);
        second.weight.set(4);

        int firstSelections = 0;

        for (int i = 0; i < 10_000; i++)
        {
            long id = crowd.getStableMemberId(i);
            int selected = crowd.sources.selectSource(id);

            assertEquals(selected, crowd.sources.selectSource(id));

            if (selected == 0)
            {
                firstSelections += 1;
            }
        }

        assertTrue(firstSelections > 1_800 && firstSelections < 2_200);
    }

    @Test
    public void sourceSettingsRoundTrip()
    {
        CrowdMemberSource source = new CrowdMemberSource("0");

        source.weight.set(7);
        source.minimumScale.set(0.8F);
        source.maximumScale.set(1.2F);

        BaseType data = source.toData();
        CrowdMemberSource loaded = new CrowdMemberSource("0");

        loaded.fromData(data);

        assertEquals(7, loaded.weight.get());
        assertEquals(0.8F, loaded.minimumScale.get());
        assertEquals(1.2F, loaded.maximumScale.get());
    }

    @Test
    public void validationRepairsUnsafeValues()
    {
        CrowdForm crowd = new CrowdForm();
        CrowdMemberSource source = crowd.sources.addSource(new LabelForm());

        crowd.count.set(-100);
        source.weight.set(0);
        source.minimumScale.set(-2F);

        crowd.validateCrowd();

        assertEquals(1, crowd.count.get());
        assertEquals(1, source.weight.get());
        assertEquals(0.01F, source.minimumScale.get());
    }

    @Test
    public void theCrowdKeepsItsWorldScaleRadiusAtTheLiveMemberCeiling()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.count.set(CrowdForm.MAX_MEMBERS);
        crowd.radius.set(CrowdForm.MAX_RADIUS);
        crowd.validateCrowd();

        assertEquals(CrowdForm.MAX_MEMBERS, crowd.count.get());
        assertEquals(CrowdForm.MAX_RADIUS, crowd.radius.get());

        CrowdSpawnActionClip spawn = new CrowdSpawnActionClip();

        spawn.radius.set(CrowdForm.MAX_RADIUS);
        assertEquals(CrowdForm.MAX_RADIUS, spawn.radius.get());
    }

    @Test
    public void fakeGeometryCrowdsAreClampedToWhatCanActuallySpawn()
    {
        MapType old = new MapType();

        old.putInt("crowd_schema", 4);
        old.putInt("count", 250_000);
        old.putInt("render_budget", 4096);

        CrowdForm crowd = new CrowdForm();
        crowd.fromData(old);

        assertEquals(CrowdForm.MAX_MEMBERS, crowd.count.get(),
            "a quarter-million fake members cannot become a quarter-million real actors");
    }

    @Test
    public void everyBehaviourTheOldActionClipsExposedIsAuthorableOnTheForm()
    {
        CrowdForm crowd = new CrowdForm();
        java.util.Set<String> formIds = new java.util.HashSet<>();

        for (BaseValue value : crowd.getAll())
        {
            formIds.add(value.getId());
        }

        /* Folding the two crowd action clips into one form was only worth doing if nothing
         * was lost on the way. Every knob the behaviour clip authored is listed here, so
         * dropping one from CrowdForm fails loudly instead of quietly disappearing from the
         * editor. The clip itself cannot be constructed in a unit test: its projectile item
         * stack pulls in Minecraft's item registry. */
        java.util.List<String> missing = new java.util.ArrayList<>();

        for (String id : new String[] {
            "behavior_mode", "behavior_pause", "behavior_speed", "behavior_sprint",
            "behavior_move_ease", "behavior_stop_distance", "behavior_target_spread",
            "behavior_disperse_radius", "behavior_wander_interval", "behavior_look_around_ticks",
            "behavior_area_x", "behavior_area_y", "behavior_area_z", "behavior_separation",
            "behavior_max_step_height", "behavior_crouch", "behavior_zig_zag",
            "behavior_random_jump", "behavior_jump_rate", "behavior_arm_swing",
            "behavior_arm_swing_rate", "behavior_head_motion", "behavior_energy",
            "behavior_look_at_target", "behavior_look_ease", "behavior_head_yaw_limit",
            "behavior_look_body_yaw", "behavior_look_head_yaw", "behavior_look_head_pitch",
            "behavior_enemy_group", "behavior_fight_damage", "behavior_attack_rate",
            "behavior_engagement_distance", "behavior_fight_radius", "behavior_retarget_ticks",
            "behavior_fight_randomness", "behavior_shoot", "behavior_shoot_rate",
            "behavior_projectile_model", "behavior_projectile_speed", "behavior_projectile_life_span",
            "behavior_impact_model", "behavior_impact_bounces", "behavior_impact_bounce_damping",
            "behavior_impact_vanish", "behavior_impact_damage", "behavior_impact_knockback",
            "behavior_impact_collide_blocks", "behavior_impact_collide_entities"
        })
        {
            if (!formIds.contains(id))
            {
                missing.add(id);
            }
        }

        assertTrue(missing.isEmpty(), "CrowdForm cannot author: " + missing);
    }

    @Test
    public void fightingCrowdsFindEachOtherByGroupName()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.groupName.set("reds");
        crowd.behaviorEnemyGroup.set("blues");
        crowd.behaviorMode.set(CrowdBehaviorMode.FIGHT.ordinal());
        crowd.validateCrowd();

        assertEquals("reds", crowd.groupName.get());
        assertEquals("blues", crowd.behaviorEnemyGroup.get());
        assertEquals(CrowdBehaviorMode.FIGHT, CrowdBehaviorMode.get(crowd.behaviorMode.get()));
    }

    @Test
    public void schemaTwoCrowdsMigrateTheirFormation()
    {
        MapType old = new MapType();
        old.putInt("crowd_schema", 2);
        old.putInt("formation", 0);

        CrowdForm loaded = new CrowdForm();
        loaded.fromData(old);

        assertEquals(CrowdForm.CURRENT_SCHEMA, loaded.crowdSchema.get());
        assertEquals(CrowdFormation.GRID.ordinal(), loaded.formation.get());
    }

    @Test
    public void schemaThreeFilledCircleMigratesToZeroHole()
    {
        MapType old = new MapType();

        old.putInt("crowd_schema", 3);
        old.putInt("formation", CrowdFormation.CIRCLE.ordinal());
        old.putFloat("hollow", 0.45F);

        CrowdForm loaded = new CrowdForm();
        loaded.fromData(old);

        assertEquals(CrowdFormation.CIRCLE.ordinal(), loaded.formation.get());
        assertEquals(0F, loaded.hollow.get());
    }

    @Test
    public void legacyHollowCircleBecomesCircleAndKeepsItsHole()
    {
        MapType old = new MapType();

        old.putInt("crowd_schema", 3);
        old.putInt("formation", CrowdFormation.HOLLOW_CIRCLE.ordinal());
        old.putFloat("hollow", 0.7F);

        CrowdForm loaded = new CrowdForm();
        loaded.fromData(old);

        assertEquals(CrowdFormation.CIRCLE.ordinal(), loaded.formation.get());
        assertEquals(0.7F, loaded.hollow.get());
        assertFalse(java.util.Arrays.asList(CrowdFormation.selectableValues()).contains(CrowdFormation.HOLLOW_CIRCLE));
    }

    @Test
    public void legacyPrimarySourceFeedsTheLiveMemberForm()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.sources.addSource(new LabelForm());
        crowd.validateCrowd();

        assertTrue(crowd.getMemberForm() instanceof LabelForm);
        assertTrue(crowd.memberForm.get() instanceof LabelForm);
    }

    @Test
    public void walkPointsRoundTripWithoutLosingBehaviorFlags()
    {
        CrowdWalk point = new CrowdWalk();

        point.x = 12.5F;
        point.y = 4F;
        point.z = -3F;
        point.run = false;
        point.faceTravel = false;
        point.terrainFollow = false;
        point.ease = 0.4F;
        point.stagger = 0.65F;
        point.spread = 0.35F;

        CrowdWalk loaded = CrowdWalk.fromData(point.toData());

        assertEquals(point.x, loaded.x);
        assertEquals(point.y, loaded.y);
        assertEquals(point.z, loaded.z);
        assertFalse(loaded.run);
        assertFalse(loaded.faceTravel);
        assertFalse(loaded.terrainFollow);
        assertEquals(point.ease, loaded.ease);
        assertEquals(point.stagger, loaded.stagger);
        assertEquals(point.spread, loaded.spread);
    }

    @Test
    public void fullDensityPacksMembersCloserThanTheyAreWide()
    {
        CrowdForm crowd = new CrowdForm();

        crowd.formation.set(CrowdFormation.CIRCLE.ordinal());
        crowd.radius.set(10F);
        crowd.density.set(CrowdForm.MAX_DENSITY);
        crowd.validateCrowd();

        double area = Math.PI * 10D * 10D;
        double spacing = Math.sqrt(area / crowd.count.get());

        assertTrue(spacing < 0.6D, "at density 100 neighbours must be closer than a villager is wide, was " + spacing);
    }

    @Test
    public void densityScalesWithTheAuthoredRadiusInsteadOfChangingIt()
    {
        CrowdForm small = new CrowdForm();
        CrowdForm large = new CrowdForm();

        for (CrowdForm crowd : new CrowdForm[] {small, large})
        {
            crowd.formation.set(CrowdFormation.CIRCLE.ordinal());
            crowd.density.set(50F);
        }

        small.radius.set(5F);
        large.radius.set(10F);
        small.validateCrowd();
        large.validateCrowd();

        assertEquals(5F, small.radius.get(), "density must never move the radius");
        assertEquals(10F, large.radius.get());
        assertEquals(4D, large.count.get() / (double) small.count.get(), 0.05D,
            "four times the ground must hold four times the members");
    }

    @Test
    public void crowdTexturesRoundTripInChosenAndRandomModes()
    {
        CrowdTexture chosen = new CrowdTexture();
        CrowdTexture random = new CrowdTexture();

        chosen.texture = Link.create("bbs:textures/chosen.png");
        random.random = true;
        random.folder = Link.create("bbs:textures/crowd");
        random.recursive = true;

        CrowdTexture loadedChosen = CrowdTexture.fromData(chosen.toData());
        CrowdTexture loadedRandom = CrowdTexture.fromData(random.toData());

        assertEquals(chosen.texture, loadedChosen.texture);
        assertFalse(loadedChosen.random);
        assertEquals(random.folder, loadedRandom.folder);
        assertTrue(loadedRandom.random);
        assertTrue(loadedRandom.recursive);
    }
}
