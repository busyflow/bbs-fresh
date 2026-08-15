package mchorse.bbs_mod;

import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueStringList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

public class BBSSettings {

	public static ValueBoolean killExcludeSelf;
	public static ValueBoolean highBoneModelOptimization;
	public static ValueInt crowdPreviewCount;
	public static ValueBoolean creativeShowHearts;
	public static ValueBoolean creativeShowHunger;
	public static ValueBoolean pauseHealthRegen;
	public static ValueBoolean pauseHunger;
	public static ValueBoolean creativeShowXpBar;
	public static ValueBoolean uiFont;
	public static ValueFloat uiFontScale;

	public static final String DEFAULT_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p %NAME%.mp4";
	public static final String DEFAULT_AUDIO_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -i %AUDIO_TRACK% -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest %NAME%.mp4";
	public static final String DEFAULT_MUX_FFMPEG_ARGUMENTS = "-y -i %VIDEO% -i %AUDIO_TRACK% -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -shortest %NAME%.mp4";

	public static ValueColors favoriteColors;
	public static ValueColors recentColors;
	public static ValueStringKeys disabledSheets;
	public static ValueStringKeys disabledMorphFormCategories;
	/** Quick-access folders pinned in the texture picker (Link strings). */
	public static ValueStringList pinnedTextureFolders;
	/** Cell size the file pickers were last zoomed to. */
	public static ValueInt texturePickerThumbnailSize;
	public static ValueLanguage language;
	public static ValueInt primaryColor;
	public static ValueInt interfaceSurfaceColor;
	public static ValueInt colorPreset;
	public static ValueInt stencilHighlightColor;
	public static ValueBoolean originalBBSTheme;
	public static ValueBoolean enableTrackpadIncrements;
	public static ValueBoolean enableTrackpadScrolling;
	public static ValueFloat userIntefaceScale;
	public static ValueInt theme;
	public static ValueFloat fov;
	public static ValueBoolean hsvColorPicker;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean listModelPreview;
	public static ValueBoolean morphingFocusSearch;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean axesKeepScreenSize;
	public static ValueBoolean rotate3dSphere;
	public static ValueInt rotate3dSphereMode;
	public static ValueBoolean rotateHideRings;
	public static ValueBoolean hideInactiveHandles;
	public static ValueFloat snapTranslate;
	public static ValueFloat snapRotate;
	public static ValueFloat snapScale;
	public static ValueInt gizmoHoverTolerance;
	public static ValueFloat gizmoOpacity;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueBoolean defaultLocalTransform;
	public static ValueInt transformSpace;
	public static ValueBoolean transformHotkeys3dRay;
	public static ValueBoolean poseMirrorEdit;
	public static ValueBoolean poseAlternateInvert;
	public static ValueBoolean poseShowDisabledBones;
	public static ValueOrder translateHotkeyOrder;
	public static ValueOrder scaleHotkeyOrder;
	public static ValueOrder rotateHotkeyOrder;
	public static ValueFloat trackballSensitivity;

	public static ValueBoolean enableCursorRendering;
	public static ValueBoolean enableMouseButtonRendering;
	public static ValueBoolean enableKeystrokeRendering;
	public static ValueInt keystrokeOffset;
	public static ValueInt keystrokeMode;

	public static ValueLink backgroundImage;
	public static ValueInt backgroundColor;

	public static ValueBoolean chromaSkyEnabled;
	public static ValueInt chromaSkyColor;
	public static ValueBoolean chromaSkyTerrain;
	public static ValueFloat chromaSkyBillboard;

	public static ValueInt scrollbarWidth;
	public static ValueFloat scrollingSensitivity;
	public static ValueFloat scrollingSensitivityHorizontal;
	public static ValueBoolean scrollingSmoothness;
	public static ValueBoolean scrollingDisableSmoothnessInEditors;

	public static ValueBoolean multiskinMultiThreaded;

	public static ValueString videoEncoderPath;
	public static ValueBoolean videoEncoderLog;
	public static ValueBoolean worldExportResizeWindow;
	public static ValueInt videoWidth;
	public static ValueInt videoHeight;
	public static ValueInt videoFrameRate;
	public static ValueBoolean videoLimitFrameRate;
	public static ValueString videoExportPath;
	public static ValueString videoExportFilenameFormat;
	public static ValueBoolean videoExportAudio;
	public static ValueBoolean videoExportMinecraftSounds;
	public static ValueBoolean videoMuteAudioWhileRender;
	public static ValueInt videoMotionBlur;
	public static ValueInt videoHeldFrames;
	public static ValueFloat videoDelay;
	public static ValueBoolean videoExportShaders;
	public static ValueBoolean videoOpenFolderAfterExport;
	public static ValueBoolean videoPlaySoundAfterExport;
	public static ValueBoolean videoHardwareEncoder;
	public static ValueString videoArguments;
	public static ValueString videoArgumentsAudio;
	public static ValueString videoArgumentsMux;

	public static ValueFloat editorCameraSpeed;
	public static ValueFloat editorCameraAngleSpeed;
	public static ValueInt duration;
	public static ValueBoolean editorLoop;
	public static ValueInt editorJump;
	public static ValueInt editorGuidesColor;
	public static ValueBoolean editorRuleOfThirds;
	public static ValueBoolean editorCenterLines;
	public static ValueBoolean editorCrosshair;
	public static ValueBoolean editorSeconds;
	public static ValueBoolean editorTimelineMajorLines;
	public static ValueBoolean editorTimelineMinorLines;
	public static ValueBoolean editorColoredKeyframeLines;
	/** Camera keyframe clip: properties under the sheet at full width instead of a side column. */
	public static ValueBoolean editorKeyframePropertiesBelow;
	/**
	 * Pixels added to the radius every keyframe is drawn at, and to the radius one can be grabbed
	 * by. Whole pixels and the same for every shape, so they stay the size of each other and land
	 * on the pixel grid; nothing below the stock size, which is as small as the shapes read.
	 */
	public static ValueFloat keyframeSize;
	/** Draw the line of a channel with no keyframes in one chosen colour, not the channel's own. */
	public static ValueBoolean keyframeHideChannelLines;
	public static ValueBoolean keyframeUnusedTint;
	public static ValueInt keyframeUnusedColor;

	/**
	 * The colour an empty channel's line is drawn in - the chosen tint when it is on, otherwise
	 * the channel's own colour as before.
	 */
	public static int keyframeBaseColor(int trackColor)
	{
		if (keyframeUnusedTint != null && keyframeUnusedTint.get())
		{
			return keyframeUnusedColor.get() | Colors.A100;
		}

		return trackColor;
	}
	/** Height of the keyframe properties panel, wherever it is shown under the keyframes. */
	public static ValueInt keyframePropertiesHeight;

	/** Radius, in pixels, a keyframe is grabbed by before {@link #keyframeSize} widens it. */
	public static final int KEYFRAME_GRAB_RADIUS = 5;

	/** Squared grab radius, widened by the keyframe size setting. */
	public static double keyframeGrabRadiusSq()
	{
		float r = KEYFRAME_GRAB_RADIUS + (keyframeSize == null ? 0F : keyframeSize.get());

		return r * r;
	}
	public static ValueBoolean editorShowAllReplayTracks;
	public static ValueInt editorPeriodicSave;
	public static ValueBoolean editorHorizontalFlight;
	public static ValueBoolean editorOrbitMovementRequiresFlight;
	public static ValueBoolean editorOrbitCenterMarker;
	public static ValueBoolean editorOrbitGizmo;
	public static ValueFloat editorOrbitGizmoScale;
	public static ValueBoolean editorOrbitAxisOrtho;
	public static ValueMotionPath editorMotionPath;
	public static ValueBoolean editorOrbitTeleportOnSwitch;
	public static ValueFloat editorCameraSmoothness;
	public static ValueInt editorCameraMode;
	public static ValueBoolean editorPlayerFollowsCamera;
	public static ValueEditorLayout editorLayoutSettings;
	public static ValueOnionSkin editorOnionSkin;
	public static ValueIKDebug ikDebug;
	public static ValuePhysicsDebug physicsDebug;
	public static ValueBoolean editorSnapToMarkers;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueBoolean editorResizablePanels;
	public static ValueInt editorTrackWidth;
	public static ValueInt keyframeDefaultShape;
	public static ValueString keyframeDefaultInterpolation;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;
	public static ValueBoolean editorKeepFrameOnExit;

	public static ValueFloat recordingCountdown;
	public static ValueBoolean recordingSwipeDamage;
	public static ValueBoolean recordingOverlays;
	public static ValueInt recordingPoseTransformOverlays;
	public static ValueBoolean recordingCameraPreview;

	public static ValueBoolean renderAllModelBlocks;
	public static ValueBoolean clickModelBlocks;

	public static ValueString entitySelectorsPropertyWhitelist;

	public static ValueBoolean damageControl;

	public static ValueFloat backgroundBrightness;
	public static ValueBoolean interfaceShadows;

	public static ValueBoolean shaderCurvesEnabled;

	public static ValueBoolean audioWaveformVisibleInPreview;
	public static ValueBoolean audioWaveformVisibleInKeyframes;
	public static ValueInt audioWaveformDensity;
	public static ValueFloat audioWaveformWidth;
	public static ValueInt audioWaveformHeight;
	public static ValueBoolean audioWaveformFilename;
	public static ValueBoolean audioWaveformTime;
	public static ValueBoolean audioWaveformPreviewCombined;

	public static ValueString cdnUrl;
	public static ValueString cdnToken;

	private static final int LIGHT_THEME = 0;
	private static final int DARK_THEME = 1;
	private static final int DEFAULT_THEME = DARK_THEME;
	private static final float DEFAULT_BACKGROUND_BRIGHTNESS = 1F;
	private static final float MIN_BACKGROUND_BRIGHTNESS = 0.5F;
	private static final float MAX_BACKGROUND_BRIGHTNESS = 1.5F;
	private static final float IDENTITY_BRIGHTNESS = 1F;
	private static final float BRIGHTNESS_EPSILON = 0.001F;
	private static final int DEFAULT_PRIMARY_COLOR = 0xff3242;
	private static final int COLOR_PRESET_CUSTOM = 6;
	private static final String[] COLOR_PRESET_NAMES = {
		"Copper Charcoal",
		"Ember Noir",
		"Ocean Slate",
		"Forest Ink",
		"Golden Charcoal",
		"Studio Default",
		"Custom"
	};
	private static final int[][] COLOR_PRESETS = {
		{0xda6c48, 0x171616},
		{0xff3242, 0x0d0600},
		{0x42b8d8, 0x101922},
		{0x63c174, 0x101812},
		{0xe7ad45, 0x19160f},
		{0xff3242, 0x171a1f}
	};
	private static final int LIGHT_CHROME_SURFACE = 0xffe6e9ef;
	private static final int DARK_CHROME_SURFACE = 0xff111316;
	private static final int LIGHT_BASE_SURFACE = 0xfff1f4f8;
	private static final int DARK_BASE_SURFACE = 0xff171a1f;
	private static final int LIGHT_RAISED_SURFACE = 0xfff8fafd;
	private static final int DARK_RAISED_SURFACE = 0xff1d2127;
	private static final int LIGHT_DEEP_SURFACE = 0xffdee4ed;
	private static final int DARK_DEEP_SURFACE = 0xff0f1217;
	private static final int LIGHT_DIVIDER_COLOR = 0xffc2cbd8;
	private static final int DARK_DIVIDER_COLOR = 0xff30353d;

	public static int getDefaultInterfaceSurfaceColor()
	{
		return DARK_BASE_SURFACE;
	}

	public static int getColorPresetCount()
	{
		return COLOR_PRESET_NAMES.length;
	}

	public static String getColorPresetName(int preset)
	{
		return COLOR_PRESET_NAMES[MathUtils.clamp(preset, 0, COLOR_PRESET_NAMES.length - 1)];
	}

	public static int detectColorPreset()
	{
		if (primaryColor == null || interfaceSurfaceColor == null)
		{
			return COLOR_PRESET_CUSTOM;
		}

		int primary = primaryColor.get() & Colors.RGB;
		int surface = interfaceSurfaceColor.get() & Colors.RGB;

		for (int i = 0; i < COLOR_PRESETS.length; i++)
		{
			if (COLOR_PRESETS[i][0] == primary && COLOR_PRESETS[i][1] == surface)
			{
				return i;
			}
		}

		return COLOR_PRESET_CUSTOM;
	}

	public static void syncColorPreset()
	{
		if (colorPreset != null)
		{
			colorPreset.set(detectColorPreset());
		}
	}

	public static void applyColorPreset(int preset)
	{
		if (preset >= 0 && preset < COLOR_PRESETS.length)
		{
			primaryColor.set(COLOR_PRESETS[preset][0]);
			interfaceSurfaceColor.set(COLOR_PRESETS[preset][1]);
		}

		if (colorPreset != null)
		{
			colorPreset.set(preset >= 0 && preset < COLOR_PRESET_NAMES.length ? preset : COLOR_PRESET_CUSTOM);
		}
	}

	public static int primaryColor()
	{
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha)
	{
		return withAlpha(accentColor(), alpha);
	}

	public static int accentColor()
	{
		return isOriginalBBSTheme() ? Colors.ACTIVE : primaryColor.get();
	}

	public static boolean isOriginalBBSTheme()
	{
		return originalBBSTheme != null && originalBBSTheme.get();
	}

	public static boolean isLightTheme()
	{
		return !isOriginalBBSTheme() && theme != null && theme.get() == LIGHT_THEME;
	}

	private static int withAlpha(int color, int alpha)
	{
		return (color & Colors.RGB) | alpha;
	}

	private static int getThemeColor(int lightColor, int darkColor)
	{
		return isLightTheme() ? lightColor : darkColor;
	}

	private static float getBackgroundBrightnessFactor()
	{
		return backgroundBrightness == null ? DEFAULT_BACKGROUND_BRIGHTNESS : backgroundBrightness.get();
	}

	private static int applyBackgroundBrightness(int color)
	{
		float brightness = MathUtils.clamp(getBackgroundBrightnessFactor(), MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS);

		if (Math.abs(brightness - IDENTITY_BRIGHTNESS) < BRIGHTNESS_EPSILON)
		{
			return color;
		}

		int a = color & 0xff000000;
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		if (brightness < 1F)
		{
			r = Math.round(r * brightness);
			g = Math.round(g * brightness);
			b = Math.round(b * brightness);
		}
		else
		{
			float factor = brightness - 1F;

			r += Math.round((255 - r) * factor);
			g += Math.round((255 - g) * factor);
			b += Math.round((255 - b) * factor);
		}

		r = MathUtils.clamp(r, 0, 255);
		g = MathUtils.clamp(g, 0, 255);
		b = MathUtils.clamp(b, 0, 255);

		return a | (r << 16) | (g << 8) | b;
	}

	private static int getThemeSurface(int lightColor, int darkColor)
	{
		return applyBackgroundBrightness(getThemeColor(lightColor, darkColor));
	}

	private static boolean usesCustomDarkSurface()
	{
		return !isLightTheme() && interfaceSurfaceColor != null && (interfaceSurfaceColor.get() & Colors.RGB) != (DARK_BASE_SURFACE & Colors.RGB);
	}

	private static int tintSurface(int color, float factor)
	{
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		if (factor <= 1F)
		{
			r = Math.round(r * factor);
			g = Math.round(g * factor);
			b = Math.round(b * factor);
		}
        else
        {
            /* Preserve the selected hue instead of washing raised fields toward white. */
            r = Math.round(r * factor);
            g = Math.round(g * factor);
            b = Math.round(b * factor);
        }

		return Colors.A100 | (MathUtils.clamp(r, 0, 255) << 16) | (MathUtils.clamp(g, 0, 255) << 8) | MathUtils.clamp(b, 0, 255);
	}

	private static int customDarkSurface(float factor)
	{
		return applyBackgroundBrightness(tintSurface(interfaceSurfaceColor.get(), factor));
	}

	public static int chromeSurface()
	{
		if (isOriginalBBSTheme())
		{
			return Colors.CONTROL_BAR;
		}

		return usesCustomDarkSurface() ? customDarkSurface(0.68F) : getThemeSurface(LIGHT_CHROME_SURFACE, DARK_CHROME_SURFACE);
	}

	public static int baseSurface()
	{
		if (isOriginalBBSTheme())
		{
			return Colors.A75;
		}

		return usesCustomDarkSurface() ? customDarkSurface(1F) : getThemeSurface(LIGHT_BASE_SURFACE, DARK_BASE_SURFACE);
	}

	public static int raisedSurface()
	{
		if (isOriginalBBSTheme())
		{
			return Colors.A50;
		}

		return usesCustomDarkSurface() ? customDarkSurface(1.18F) : getThemeSurface(LIGHT_RAISED_SURFACE, DARK_RAISED_SURFACE);
	}

	public static int deepSurface()
	{
		if (isOriginalBBSTheme())
		{
			return Colors.A50;
		}

		return usesCustomDarkSurface() ? customDarkSurface(0.62F) : getThemeSurface(LIGHT_DEEP_SURFACE, DARK_DEEP_SURFACE);
	}

	public static int dividerColor()
	{
		if (isOriginalBBSTheme())
		{
			return Colors.setA(Colors.WHITE, 0.25F);
		}

		return usesCustomDarkSurface() ? tintSurface(interfaceSurfaceColor.get(), 1.45F) : getThemeColor(LIGHT_DIVIDER_COLOR, DARK_DIVIDER_COLOR);
	}

	public static int color(int color, int alpha)
	{
		return withAlpha(color, alpha);
	}

	public static int accentOverlay(int alpha)
	{
		return primaryColor(alpha);
	}

	/**
	 * Render-scoped: the film editor sets this so its inputs stay light on its dark panels.
	 */
	public static boolean lightInputs = false;

	public static int inputSurface()
	{
		return lightInputs ? raisedSurface() : deepSurface();
	}

	public static int panelShadowOpaqueColor()
	{
		return Colors.A25 | accentColor();
	}

	public static int panelShadowTransparentColor()
	{
		return Colors.setA(accentColor(), 0F);
	}

	public static int getDefaultDuration()
	{
		return duration == null ? 30 : duration.get();
	}

	public static float getFov()
	{
		return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
	}

	public static float getAxesDistanceScale(float distance)
	{
		return getAxesDistanceScale(distance, getFov());
	}

	public static float getAxesDistanceScale(float distance, float fov)
	{
		if (axesKeepScreenSize != null && axesKeepScreenSize.get())
		{
			float tanFov = (float) Math.tan(fov / 2.0);
			// 0.4663F is roughly tan(50 degrees / 2)
			float scale = (distance / 5F) * (tanFov / 0.4663F);

			return Math.max(scale, 0.0001F);
		}

		return 1F;
	}

	public static boolean isHorizontalClipEditorEffective()
	{
		return editorHorizontalClipEditor.get();
	}

	/**
	 * Returns the user-configured default shape for newly created keyframes. Falls back to
	 * {@link KeyframeShape#SQUARE} before settings are registered or if the stored ordinal
	 * is out of range (e.g. after the enum shrinks in a future version).
	 */
	public static KeyframeShape getDefaultKeyframeShape()
	{
		if (keyframeDefaultShape == null)
		{
			return KeyframeShape.SQUARE;
		}

		int index = keyframeDefaultShape.get();
		KeyframeShape[] values = KeyframeShape.values();

		return index >= 0 && index < values.length ? values[index] : KeyframeShape.SQUARE;
	}

	/**
	 * The interpolation given to a hand-created keyframe when it has no neighbour to inherit
	 * from (see {@code IUIKeyframeGraph#addKeyframeManually}) - i.e. the replacement for the
	 * hardcoded linear that used to apply in that "empty spot" case. Keyframes that do inherit
	 * from a neighbour keep the neighbour's interpolation, and recorded/baked keyframes never
	 * consult this. Falls back to linear before settings are registered or on an unknown key.
	 */
	public static IInterp getDefaultKeyframeInterpolation()
	{
		if (keyframeDefaultInterpolation == null)
		{
			return Interpolations.LINEAR;
		}

		IInterp interp = Interpolations.MAP.get(keyframeDefaultInterpolation.get());

		return interp == null ? Interpolations.LINEAR : interp;
	}

	public static boolean migrateLegacySettings(MapType root)
	{
		MapType appearance = root.getMap("appearance");
		MapType editor = root.getMap("editor");
		MapType personalization = root.getMap("personalization");
		boolean migrated = false;

		migrated |= migrateLegacyValue(appearance, personalization, "primary_color");
		migrated |= migrateLegacyValue(appearance, personalization, "tooltip_style", "theme");
		migrated |= migrateLegacyValue(appearance, personalization, "track_width");
		migrated |= migrateLegacyValue(appearance, personalization, "keyframe_default_shape");
		migrated |= migrateLegacyValue(editor, personalization, "timeline_grid", "timeline_major_lines");
		migrated |= migrateLegacyValue(editor, personalization, "timeline_grid", "timeline_minor_lines");

		if (migrated)
		{
			root.put("personalization", personalization);
		}

		return migrated;
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String key)
	{
		return migrateLegacyValue(oldCategory, newCategory, key, key);
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String oldKey, String newKey)
	{
		if (newCategory.has(newKey) || !oldCategory.has(oldKey))
		{
			return false;
		}

		newCategory.put(newKey, oldCategory.get(oldKey).copy());

		return true;
	}

	public static void register(SettingsBuilder builder)
	{
		HashSet<String> defaultFilters = new HashSet<>();

		defaultFilters.add("item_off_hand");
		defaultFilters.add("item_head");
		defaultFilters.add("item_chest");
		defaultFilters.add("item_legs");
		defaultFilters.add("item_feet");
		defaultFilters.add("vX");
		defaultFilters.add("vY");
		defaultFilters.add("vZ");
		defaultFilters.add("grounded");
		defaultFilters.add("stick_rx");
		defaultFilters.add("stick_ry");
		defaultFilters.add("trigger_l");
		defaultFilters.add("trigger_r");
		defaultFilters.add("extra1_x");
		defaultFilters.add("extra1_y");
		defaultFilters.add("extra2_x");
		defaultFilters.add("extra2_y");

		builder.category("appearance", Icons.LAYOUT);
		builder.register(language = new ValueLanguage("language"));
		/* Keep this high in Settings because it changes the replay editor's everyday layout. */
		editorShowAllReplayTracks = builder.getBoolean("show_all_replay_tracks", false);
		enableTrackpadIncrements = builder.getBoolean("trackpad_increments", false);
		enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", false);
		userIntefaceScale = builder.getFloat("ui_scale", 2F, 0F, 4F).slider();
		fov = builder.getFloat("fov", 40, 0, 180).slider();
		hsvColorPicker = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		freezeModels = builder.getBoolean("freeze_models", false);
		listModelPreview = builder.getBoolean("list_model_preview", true);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
		uniformScale = builder.getBoolean("uniform_scale", false);
		clickSound = builder.getBoolean("click_sound", false);
		favoriteColors = new ValueColors("favorite_colors");
		recentColors = new ValueColors("recent_colors").limit(33);
		disabledSheets = new ValueStringKeys("disabled_sheets");
		disabledSheets.set(defaultFilters);
		builder.register(favoriteColors);
		builder.register(recentColors);
		builder.register(disabledSheets);
		disabledMorphFormCategories = new ValueStringKeys("disabled_morph_form_categories");
		builder.register(disabledMorphFormCategories);
		pinnedTextureFolders = new ValueStringList("pinned_texture_folders");
		builder.register(pinnedTextureFolders);
		texturePickerThumbnailSize = builder.getInt("texture_picker_thumbnail_size", 16, 16, 128);
		editorClipAutoName = builder.getBoolean("clip_auto_name", true);

		builder.category("personalization", Icons.COLOR);
		backgroundBrightness = builder.getFloat("background_brightness", DEFAULT_BACKGROUND_BRIGHTNESS, MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS).slider();
		interfaceShadows = builder.getBoolean("interface_shadows", true);
		colorPreset = builder.getInt("color_preset", 0, 0, COLOR_PRESET_NAMES.length - 1).modes(
			IKey.constant(COLOR_PRESET_NAMES[0]),
			IKey.constant(COLOR_PRESET_NAMES[1]),
			IKey.constant(COLOR_PRESET_NAMES[2]),
			IKey.constant(COLOR_PRESET_NAMES[3]),
			IKey.constant(COLOR_PRESET_NAMES[4]),
			IKey.constant(COLOR_PRESET_NAMES[5]),
			IKey.constant(COLOR_PRESET_NAMES[6])
		);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		interfaceSurfaceColor = builder.getInt("interface_surface_color", DARK_BASE_SURFACE).color();
		stencilHighlightColor = builder.getInt("stencil_highlight_color", 0x2EFFFFFF).colorAlpha();
		originalBBSTheme = builder.getBoolean("original_bbs_theme", false);
		editorTimelineMajorLines = builder.getBoolean("timeline_major_lines", true);
		editorTimelineMinorLines = builder.getBoolean("timeline_minor_lines", true);
		theme = builder.getInt("theme", DEFAULT_THEME);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10).slider();
		keyframeDefaultShape = builder.getInt("keyframe_default_shape", 0, 0, KeyframeShape.values().length - 1);

		builder.category("transformation", Icons.SCALE);
		gizmos = builder.getBoolean("gizmos", true);
		axesScale = builder.getFloat("axes_scale", 2F, 0F, 10F).slider();
		axesThickness = builder.getFloat("axes_thickness", 0.35F, 0.25F, 3F).slider();
		axesKeepScreenSize = builder.getBoolean("axes_keep_screen_size", true);
		rotate3dSphere = builder.getBoolean("rotate_3d_sphere", true);
		rotate3dSphereMode = builder.getInt("rotate_3d_sphere_mode", 0);
		rotateHideRings = builder.getBoolean("rotate_hide_rings", false);
		hideInactiveHandles = builder.getBoolean("hide_inactive_handles", true);
		snapTranslate = builder.getFloat("snap_translate", 1F, 0.001F, 100F);
		snapRotate = builder.getFloat("snap_rotate", 5F, 0.001F, 90F);
		snapScale = builder.getFloat("snap_scale", 0.1F, 0.001F, 10F);
		gizmoHoverTolerance = builder.getInt("gizmo_hover_tolerance", 8, 0, 40).slider();
		gizmoOpacity = builder.getFloat("gizmo_opacity", 1F, 0.05F, 1F).slider();
		defaultLocalTransform = builder.getBoolean("default_local", false);
		transformSpace = builder.getInt("transform_space", defaultLocalTransform.get() ? 0 : 3);
		transformSpace.invisible();
		transformHotkeys3dRay = builder.getBoolean("hotkeys_3d_ray", true);
		poseMirrorEdit = builder.getBoolean("pose_mirror_edit", false);
		poseMirrorEdit.invisible();
		poseAlternateInvert = builder.getBoolean("pose_alternate_invert", false);
		poseAlternateInvert.invisible();
		poseShowDisabledBones = builder.getBoolean("pose_show_disabled_bones", false);
		translateHotkeyOrder = new ValueOrder("translate_hotkey_order", "screen", "x", "y", "z");
		builder.register(translateHotkeyOrder);
		scaleHotkeyOrder = new ValueOrder("scale_hotkey_order", "all", "x", "y", "z");
		builder.register(scaleHotkeyOrder);
		rotateHotkeyOrder = new ValueOrder("rotate_hotkey_order", "view", "sphere", "x", "y", "z");
		builder.register(rotateHotkeyOrder);
		trackballSensitivity = builder.getFloat("trackball_sensitivity", 1F, 0.05F, 2F).slider();

		builder.category("tutorials", Icons.HELP);
		enableCursorRendering = builder.getBoolean("cursor", false);
		enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
		enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
		keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20).slider();
		keystrokeMode = builder.getInt("keystrokes_position", 1);

		builder.category("background", Icons.IMAGE);
		backgroundImage = builder.getRL("image", null);
		backgroundColor = builder.getInt("color", 0x7b000000).colorAlpha();

		builder.category("chroma_sky", Icons.GLOBE);
		chromaSkyEnabled = builder.getBoolean("enabled", false);
		chromaSkyColor = builder.getInt("color", Colors.A75).color();
		chromaSkyTerrain = builder.getBoolean("terrain", true);
		chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

		builder.category("scrollbars", Icons.VERTICAL);
		scrollbarWidth = builder.getInt("width", 4, 2, 10).slider();
		scrollingSensitivity = builder.getFloat("sensitivity", 3F, 0F, 10F).slider();
		scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 3F, 0F, 10F).slider();
		scrollingSmoothness = builder.getBoolean("smoothness", true);
		scrollingDisableSmoothnessInEditors = builder.getBoolean("disable_smoothness_in_editors", false);

		builder.category("multiskin", Icons.USER);
		multiskinMultiThreaded = builder.getBoolean("multithreaded", true);

		builder.category("video", Icons.VIDEO_CAMERA);
		videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
		videoEncoderLog = builder.getBoolean("log", true);
		worldExportResizeWindow = builder.getBoolean("world_export_resize_window", false);
		videoWidth = builder.getInt("width", 1280, 2, 8096);
		videoHeight = builder.getInt("height", 720, 2, 8096);
		videoFrameRate = builder.getInt("frame_rate", 60, 10, 1000);
		videoLimitFrameRate = builder.getBoolean("limit_frame_rate", false);
		videoExportPath = builder.getString("export_path", "");
		videoExportFilenameFormat = builder.getString("filename_format", "{datetime}");
		videoExportAudio = builder.getBoolean("audio", false);
		videoExportMinecraftSounds = builder.getBoolean("minecraft_sounds", false);
		videoMuteAudioWhileRender = builder.getBoolean("mute_audio_while_render", false);
		videoMotionBlur = builder.getInt("motion_blur", 0, 0, 6);
		videoHeldFrames = builder.getInt("held_frames", 1, 1, 1000);
		videoDelay = builder.getFloat("delay", 0.5F, 0F, 30F);
		videoOpenFolderAfterExport = builder.getBoolean("open_folder_after_export", false);
		videoPlaySoundAfterExport = builder.getBoolean("play_sound_after_export", true);
		videoHardwareEncoder = builder.getBoolean("hardware_encoder", false);
		videoArguments = builder.getString("arguments", DEFAULT_FFMPEG_ARGUMENTS);
		videoArgumentsAudio = builder.getString("arguments_audio", DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
		videoArgumentsMux = builder.getString("arguments_mux", DEFAULT_MUX_FFMPEG_ARGUMENTS);

		/* Camera editor */
		builder.category("editor", Icons.EDITOR);
		editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
		editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
		duration = builder.getInt("duration", 30, 1, 1000);
		editorJump = builder.getInt("jump", 5, 1, 1000);
		editorLoop = builder.getBoolean("loop", false);
		editorGuidesColor = builder.getInt("guides_color", 0xcccc0000).colorAlpha();
		editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
		editorCenterLines = builder.getBoolean("center_lines", false);
		editorCrosshair = builder.getBoolean("crosshair", false);
		editorSeconds = builder.getBoolean("seconds", false);
		editorColoredKeyframeLines = builder.getBoolean("colored_keyframe_lines", true);
		keyframeDefaultInterpolation = builder.getString("keyframe_default_interpolation", Interpolations.LINEAR.getKey());
		editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
		editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
		editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
		editorOrbitCenterMarker = builder.getBoolean("orbit_center_marker", false);
		editorOrbitGizmo = builder.getBoolean("orbit_gizmo", true);
		editorOrbitGizmoScale = builder.getFloat("orbit_gizmo_scale", 1F, 0.5F, 2F).slider();
		editorOrbitAxisOrtho = builder.getBoolean("orbit_axis_ortho", true);
		editorOrbitTeleportOnSwitch = builder.getBoolean("orbit_teleport_on_switch", true);
		editorCameraSmoothness = builder.getFloat("camera_smoothness", 0.1F, 0F, 0.95F).slider();
		editorCameraMode = builder.getInt("camera_mode", 0, 0, 5);
		editorCameraMode.invisible();
		editorPlayerFollowsCamera = builder.getBoolean("player_follows_camera", false);
		builder.register(editorLayoutSettings = new ValueEditorLayout("layout"));
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		builder.register(editorMotionPath = new ValueMotionPath("motion_path"));
		builder.register(ikDebug = new ValueIKDebug("ik_debug"));
		builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorClipPreview = builder.getBoolean("clip_preview", true);
		editorRewind = builder.getBoolean("rewind", true);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", true);
		editorMinutesBackup = builder.getBoolean("minutes_backup", true);
		editorResizablePanels = builder.getBoolean("resizable_panels", true);
		editorPreviewSizeMode = builder.getInt("preview_size_mode", 0, 0, 2);
		editorPreviewCustomWidth = builder.getInt("preview_custom_width", 1280, 2, 16384);
		editorPreviewCustomHeight = builder.getInt("preview_custom_height", 720, 2, 16384);
		editorPreviewResolutionScale = builder.getFloat("preview_resolution_scale", 2F, 1F, 3F).slider();
		editorKeepFrameOnExit = builder.getBoolean("keep_frame_on_exit", false);


		builder.category("recording", Icons.FILM);
		recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
		recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
		recordingOverlays = builder.getBoolean("overlays", true);
		recordingPoseTransformOverlays = builder.getInt("pose_transform_overlays", 0, 0, 42);
		recordingCameraPreview = builder.getBoolean("camera_preview", true);

		builder.category("model_blocks", Icons.BLOCK);
		renderAllModelBlocks = builder.getBoolean("render_all", true);
		clickModelBlocks = builder.getBoolean("click", true);

		builder.category("entity_selectors", Icons.POINTER);
		entitySelectorsPropertyWhitelist = builder.getString("whitelist", "CustomName,Name");

		builder.category("dc", Icons.EXCLAMATION);
		damageControl = builder.getBoolean("enabled", true);

		builder.category("shader_curves", Icons.CURVES);
		shaderCurvesEnabled = builder.getBoolean("enabled", true);

		builder.category("audio", Icons.SOUND);
		audioWaveformVisibleInPreview = builder.getBoolean("waveform_visible_preview", true);
		audioWaveformVisibleInKeyframes = builder.getBoolean("waveform_visible_keyframes", true);
		audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100).slider();
		audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F).slider();
		audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40).slider();
		audioWaveformFilename = builder.getBoolean("waveform_filename", false);
		audioWaveformTime = builder.getBoolean("waveform_time", false);
		audioWaveformPreviewCombined = builder.getBoolean("waveform_preview_combined", false);

		builder.category("cdn", Icons.SERVER);
		cdnUrl = builder.getString("url", "");
		cdnToken = builder.getString("token", "");

		builder.category("fresh", Icons.CONSOLE);
		killExcludeSelf = builder.getBoolean("kill_exclude_self", true);
		highBoneModelOptimization = builder.getBoolean("high_bone_model_optimization", false);
		crowdPreviewCount = builder.getInt("crowd_preview_count", 500, 0, 100000);
		creativeShowHearts = builder.getBoolean("creative_show_hearts", false);
		creativeShowHunger = builder.getBoolean("creative_show_hunger", false);
		pauseHealthRegen = builder.getBoolean("pause_health_regen", false);
		pauseHunger = builder.getBoolean("pause_hunger", false);
		creativeShowXpBar = builder.getBoolean("creative_show_xp_bar", false);
		uiFont = builder.getBoolean("ui_font", true);
		uiFontScale = builder.getFloat("ui_font_scale", 1F, 0.5F, 4F);
		videoExportShaders = builder.getBoolean("export_with_shaders", false);
		editorKeyframePropertiesBelow = builder.getBoolean("keyframe_properties_below", false);
		keyframeSize = builder.getFloat("keyframe_size", 0F, 0F, 2F);
		keyframeHideChannelLines = builder.getBoolean("keyframe_hide_channel_lines", false);
		keyframeUnusedTint = builder.getBoolean("keyframe_unused_tint", false);
		keyframeUnusedColor = builder.getInt("keyframe_unused_color", 0xffaaaaaa).color();
		keyframePropertiesHeight = builder.getInt("keyframe_properties_height", 160, 60, 500);
	}
}
