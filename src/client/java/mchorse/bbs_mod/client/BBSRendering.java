package mchorse.bbs_mod.client;

import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.MathUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.sodium.SodiumUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class BBSRendering
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean canRender;

    public static boolean renderingWorld;
    public static int lastAction;

    private static boolean customSize;
    private static boolean iris;
    private static boolean sodium;
    private static boolean optifine;

    private static int width;
    private static int height;

    /* Orbit distance for the orthographic projection; negative = perspective.
     * Re-armed every frame by the film editor's orbit camera (which is set up
     * from Camera#update, between renderWorld's HEAD and its projection use),
     * so it can never go stale when another controller takes over. */
    private static float orthoDistance = -1F;

    private static boolean toggleFramebuffer;
    private static Framebuffer framebuffer;
    private static Framebuffer clientFramebuffer;
    private static Texture texture;
    private static mchorse.bbs_mod.graphics.Framebuffer /* NECESSARY */ exportFramebuffer;

    private static Runnable pendingExportResolutionAction;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoFrameRate.get(), getMotionBlurFactor());
    }

    public static int getMotionBlur(double fps, int target)
    {
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    public static int getMotionBlurFactor()
    {
        return getMotionBlurFactor(BBSSettings.videoMotionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        return width == 0 ? BBSSettings.videoWidth.get() : width;
    }

    public static int getVideoHeight()
    {
        return height == 0 ? BBSSettings.videoHeight.get() : height;
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoFrameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File exportPath = new File(BBSSettings.videoExportPath.get());

        if (exportPath.isDirectory())
        {
            movies = exportPath;
        }

        movies.mkdirs();

        return movies;
    }

    public static boolean canReplaceFramebuffer()
    {
        /* The world always renders at the export size. The interface (HUD) is drawn after the
         * world but still into our export framebuffer — toggleFramebuffer stays on until the blit —
         * so it must use the export size too. Otherwise it renders at the real window size and, when
         * the window can't physically reach the requested resolution, comes out stretched in the
         * file. Excluded while a BBS editor is open so the film panel's own UI keeps rendering at the
         * real window size. */
        return customSize && (renderingWorld || (toggleFramebuffer && UIScreen.getCurrentMenu() == null));
    }

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static boolean matchesCustomSize(int w, int h)
    {
        return customSize && width == w && height == h;
    }

    public static void setCustomSize(boolean customSize)
    {
        setCustomSize(customSize, customSize ? getVideoWidth() : 0, customSize ? getVideoHeight() : 0);
    }

    public static void setCustomSize(boolean customSize, int w, int h)
    {
        int newWidth = !customSize ? 0 : w;
        int newHeight = !customSize ? 0 : h;

        /* No-op when nothing actually changes. A redundant setCustomSize(false)
         * — e.g. a film panel disappearing while custom size is already off, which
         * happens when the dashboard is first lazily created by the teleport/record
         * keybinds — must NOT resize the vanilla framebuffers: that stalls the GPU
         * and freezes the screen for a frame even though the state didn't change. */
        if (BBSRendering.customSize == customSize && width == newWidth && height == newHeight)
        {
            return;
        }

        LOGGER.debug("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
            customSize, w, h, customSize ? w + "/" + h : "0/0");
        BBSRendering.customSize = customSize;

        width = newWidth;
        height = newHeight;

        if (!customSize)
        {
            resizeExtraFramebuffers();
        }
    }

    public static Texture getTexture()
    {
        if (texture == null)
        {
            texture = new Texture();
            texture.setFormat(TextureFormat.RGB_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        iris = FabricLoader.getInstance().isModLoaded("iris");
        sodium = FabricLoader.getInstance().isModLoaded("sodium");
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });

        if (!iris)
        {
            return;
        }

        IrisUtils.setup();
    }

    /* Framebuffers */

    public static Framebuffer getFramebuffer()
    {
        return framebuffer;
    }

    public static void setupFramebuffer()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        framebuffer = new WindowFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight());
    }

    public static void resizeExtraFramebuffers()
    {
        Set<Framebuffer> buffers = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        buffers.add(mc.worldRenderer.getEntityOutlinesFramebuffer());
        buffers.add(mc.worldRenderer.getTranslucentFramebuffer());
        buffers.add(mc.worldRenderer.getEntityFramebuffer());
        buffers.add(mc.worldRenderer.getParticlesFramebuffer());
        buffers.add(mc.worldRenderer.getWeatherFramebuffer());
        buffers.add(mc.worldRenderer.getCloudsFramebuffer());

        for (Framebuffer buffer : buffers)
        {
            resizeFramebuffer(buffer);
        }
    }

    public static void resizeFramebuffer(Framebuffer framebuffer)
    {
        if (framebuffer == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();

        if (framebuffer.textureWidth == w && framebuffer.textureHeight == h)
        {
            return;
        }

        framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
    }

    public static void toggleFramebuffer(boolean toggleFramebuffer)
    {
        if (toggleFramebuffer == BBSRendering.toggleFramebuffer)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();

        BBSRendering.toggleFramebuffer = toggleFramebuffer;

        if (toggleFramebuffer)
        {
            int w = mc.getWindow().getFramebufferWidth();
            int h = mc.getWindow().getFramebufferHeight();

            resizeExtraFramebuffers();

            if (framebuffer.textureWidth != w || framebuffer.textureHeight != h)
            {
                framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            }

            clientFramebuffer = mc.getFramebuffer();

            reassignFramebuffer(framebuffer);

            framebuffer.beginWrite(true);
        }
        else
        {
            int drawW = window.getFramebufferWidth();
            int drawH = window.getFramebufferHeight();
            reassignFramebuffer(clientFramebuffer);

            mc.getFramebuffer().beginWrite(true);

            if (width != 0)
            {
                /* When the film panel is open, the UI draws the preview texture in its block; do not
                 * blit our framebuffer to the full window or the preview would stretch to full screen. */
                UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                boolean filmPanelShowing = currentMenu instanceof UIDashboard dashboard
                    && dashboard.getPanels().panel instanceof UIFilmPanel;
                if (!filmPanelShowing)
                {
                    framebuffer.draw(drawW, drawH);
                }
            }
        }
    }

    /**
     * Last-resort handoff before a BBS screen renders. The normal HUD hook restores the client
     * framebuffer after capturing the world, but screen changes can skip that hook for a frame.
     * Never let editor widgets draw into the preview framebuffer in that gap.
     */
    public static void prepareForScreenRender()
    {
        if (toggleFramebuffer)
        {
            toggleFramebuffer(false);
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        RenderSystem.disableScissor();
    }

    private static void reassignFramebuffer(Framebuffer framebuffer)
    {
        MinecraftClient.getInstance().framebuffer = framebuffer;
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {
        if (orthoDistance > 0F)
        {
            /* Give back the culling disabled for the previous ortho frame
             * (see setOrthoDistance); re-armed by the orbit if still on. */
            MinecraftClient.getInstance().chunkCullingEnabled = true;

            if (sodium)
            {
                SodiumUtils.restorePointCameraCulling();
            }
        }

        orthoDistance = -1F;

        MinecraftClient mc = MinecraftClient.getInstance();
        BBSModClient.getFilms().startRenderFrame(mc.getTickDelta());

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getTickDelta());
        }

        renderingWorld = true;

        if (!customSize)
        {
            return;
        }

        toggleFramebuffer(true);
    }

    public static void onWorldRenderEnd()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
            Batcher2D batcher = new Batcher2D(drawContext);

            UISubtitleRenderer.renderSubtitles(batcher.getContext().getMatrices(), batcher, SubtitleClip.getSubtitles(controller.getContext()));
        }

        if (!customSize)
        {
            renderingWorld = false;

            return;
        }

        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

        if (currentMenu instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                UISubtitleRenderer.renderSubtitles(currentMenu.context.batcher.getContext().getMatrices(), currentMenu.context.batcher, SubtitleClip.getSubtitles(panel.getRunner().getContext()));
            }
        }

        renderingWorld = false;
    }

    public static void onRenderBeforeScreen()
    {
        Texture texture = getTexture();
        int targetWidth = getVideoWidth();
        int targetHeight = getVideoHeight();

        if (texture.width != targetWidth || texture.height != targetHeight)
        {
            texture.bind();
            texture.setSize(targetWidth, targetHeight);
            texture.unbind();
        }

        if (exportFramebuffer == null)
        {
            exportFramebuffer = new mchorse.bbs_mod.graphics.Framebuffer();
            exportFramebuffer.attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            exportFramebuffer.unbind();
        }

        /* The export framebuffer is rendered at the display's native resolution, which on
         * HiDPI screens (such as Retina) is larger than the requested video size. Downscale
         * it into the export texture with a linear blit so the recording stays at the
         * resolution the user asked for (and gains free supersampling). A plain
         * glCopyTexSubImage2D can't rescale, so it would copy the full-size frame and
         * overflow the video recorder's frame buffer (macOS crashes in storeVecColor_BGR_UB). */
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, exportFramebuffer.id);
        GL30.glBlitFramebuffer(
            0, 0, framebuffer.textureWidth, framebuffer.textureHeight,
            0, 0, targetWidth, targetHeight,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);

        renderRecordingOverlay();

        toggleFramebuffer(false);

        if (pendingExportResolutionAction != null)
        {
            Runnable action = pendingExportResolutionAction;
            pendingExportResolutionAction = null;
            MinecraftClient.getInstance().execute(action);
        }
    }

    public static void scheduleAfterNextExportFrame(Runnable action)
    {
        pendingExportResolutionAction = action;
    }

    public static void onRenderChunkLayer(MatrixStack stack)
    {
        WorldRenderContextImpl worldRenderContext = new WorldRenderContextImpl();
        MinecraftClient mc = MinecraftClient.getInstance();

        worldRenderContext.prepare(
            mc.worldRenderer, stack, mc.getTickDelta(), mc.getRenderTime(), false,
            mc.gameRenderer.getCamera(), mc.gameRenderer, mc.gameRenderer.getLightmapTextureManager(),
            RenderSystem.getProjectionMatrix(), mc.getBufferBuilders().getEntityVertexConsumers(), null, false, mc.world
        );

        if (isIrisShadersEnabled())
        {
            renderCoolStuff(worldRenderContext);
        }
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);
    }

    /**
     * Draw the recording countdown / frame-counter overlay. This is operator UI: it is drawn from
     * {@link #onRenderBeforeScreen()} after the export blit but before the buffer is copied to the
     * screen, so it shows up on screen but is never captured into the file.
     */
    private static void renderRecordingOverlay()
    {
        if (!BBSSettings.recordingOverlays.get() || UIScreen.getCurrentMenu() != null)
        {
            return;
        }

        String label;

        if (BBSModClient.isVideoExportDelayPending())
        {
            int countdown = Math.max(0, (int) Math.ceil(BBSModClient.getVideoExportDelayRemainingMs() / 50D));

            label = String.valueOf(countdown / 20F);
        }
        else if (BBSModClient.getVideoRecorder().isRecording())
        {
            int count = BBSModClient.getVideoRecorder().getCounter();

            label = UIKeys.FILM_VIDEO_RECORDING.format(
                count,
                BBSModClient.getKeyRecordVideo().getBoundKeyLocalizedText().getString()
            ).get();
        }
        else
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());

        renderRecordingTimerOverlay(new Batcher2D(drawContext), label);

        drawContext.draw();
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label)
    {
        renderRecordingTimerOverlay(batcher2D, label, 5, 5);
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label, int x, int y)
    {
        int iconX = x + 16;

        batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, iconX, y, 1F, 0F);
        batcher2D.textCard(label, iconX + 3, y + 4, Colors.WHITE, Colors.A50);
    }

    public static void renderCoolStuff(WorldRenderContext worldRenderContext)
    {
        /* Before anything the film draws, so the colour covers the world and the replays sit on
         * top of it. Here rather than at the event, because with Iris loaded this is reached from
         * a different call site and the colour has to go in front of the same things either way. */
        renderChromaSkyOverlay(worldRenderContext);

        if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
        {
            screen.renderInWorld(worldRenderContext);
        }

        BBSModClient.getFilms().render(worldRenderContext);
    }

    public static boolean isOptifinePresent()
    {
        return optifine;
    }

    public static boolean isRenderingWorld()
    {
        return renderingWorld;
    }

    /**
     * Arm the orthographic projection for the current frame. Pass the orbit
     * camera's distance to the pivot; negative disables. The value is reset
     * at the beginning of every world render, so the caller must re-arm it
     * each frame for as long as ortho should stay on.
     */
    public static void setOrthoDistance(float distance)
    {
        orthoDistance = distance;

        if (distance > 0F)
        {
            /* The chunk occlusion culling walks sections outward from the
             * camera POINT, which is only sound for a perspective projection —
             * under ortho's parallel sightlines it over-culls sections near
             * the screen edges. Disable it for the frame (Sodium honours the
             * same flag); the frustum and render distance still cull. Sodium's
             * own point-camera heuristics get the same treatment. */
            MinecraftClient.getInstance().chunkCullingEnabled = false;

            if (sodium)
            {
                SodiumUtils.disablePointCameraCulling();
            }
        }
    }

    public static boolean isOrthoActive()
    {
        return orthoDistance > 0F;
    }

    /**
     * Build the orthographic projection replacing the given perspective one
     * (returns the input untouched when ortho is not armed). FOV and aspect are
     * derived from the perspective matrix itself, so the ortho frame height
     * matches the perspective frame height at the orbit pivot's distance: the
     * subject keeps its size when toggling projections, and the scroll zoom
     * keeps working through the orbit distance.
     *
     * @param minHalfHeight a lower bound on the frame's half height, and the
     *        slack behind the camera plane the near plane is given; the frustum
     *        culling matrix is built with a loose bound on both, so culling
     *        stays conservative when zoomed all the way in.
     */
    public static Matrix4f getOrthoProjection(GameRenderer renderer, Matrix4f perspective, float minHalfHeight)
    {
        if (orthoDistance <= 0F)
        {
            return perspective;
        }

        float tanHalfFov = 1F / perspective.m11();
        float aspect = perspective.m11() / perspective.m00();
        float halfHeight = Math.max(minHalfHeight, orthoDistance * tanHalfFov);
        float halfWidth = halfHeight * aspect;

        /* The near plane sits exactly at the camera, the way a perspective one
         * effectively does: under ortho's parallel sightlines everything BEHIND
         * the camera projects into the frame as well, so a hillside the camera
         * stands in paints itself over the subject, and no amount of orbiting
         * gets past it. Clipping at the camera plane drops precisely what the
         * eye has already passed and nothing the eye still faces — pushing the
         * plane any further in would slice the ground in front of the camera
         * and leave a hole where it was. Zooming in walks the camera towards
         * the pivot, so the zoom doubles as the control over how much of an
         * obstacle in front gets cut.
         *
         * The far plane is the one vanilla builds its perspective with, which
         * already bounds everything the game draws; together with the near
         * plane it keeps the box tight enough for the frustum to cull with,
         * which matters here because chunk occlusion culling is off (see
         * setOrthoDistance). */
        float near = -minHalfHeight;
        float far = renderer.getFarPlaneDistance();

        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
    }

    public static boolean isIrisShadersEnabled()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShaderPackEnabled();
    }

    /** Whether Iris is installed at all - i.e. whether asking it to turn shaders on can do anything. */
    public static boolean isIrisAvailable()
    {
        return iris;
    }

    /**
     * Switch Iris shaders on or off, as the shader selection screen's toggle does. Iris rebuilds
     * its pipeline and reloads the world renderer, so the picture takes a moment to settle.
     */
    public static void setIrisShadersEnabled(boolean enabled)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.setShadersEnabled(enabled);
    }

    public static boolean isIrisShadowPass()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShadowPass();
    }

    /**
     * Iris considers a vanilla core program applied during world rendering a stray
     * draw into its G-buffers and masks its color/depth writes. Reporting that the
     * main framebuffer isn't bound (like vanilla render targets do via bindWrite)
     * turns both core shader overrides and that masking off.
     */
    public static void setIrisMainBound(boolean bound)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.setMainBound(bound);
    }

    public static void trackTexture(Texture texture)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.trackTexture(texture);
    }

    public static float[] calculateTangents(float[] t, float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return t;
        }

        return IrisUtils.calculateTangents(t, v, n, u);
    }

    public static float[] calculateTangents(float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return v;
        }

        return IrisUtils.calculateTangents(v, n, u);
    }

    public static void addUniforms(List<CachedUniform> list, Map<String, ShaderCurves.ShaderVariable> variableMap)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.addUniforms(list, variableMap);
    }

    public static List<String> getShadersSliderOptions()
    {
        if (!iris)
        {
            return Collections.emptyList();
        }

        return IrisUtils.getSliderProperties();
    }

    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (!iris)
        {
            return Collections.emptyMap();
        }

        return IrisUtils.getShadersLanguageMap(language);
    }

    /* Curves */

    public static Long getTimeOfDay()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.SUN_ROTATION) : null;

            if (v != null)
            {
                /* The curve is in the game's own time now - 0 dawn, 6000 noon, 18000 midnight -
                 * rather than the thousands of ticks it used to be, where every number an
                 * animator already knew had to be divided by a thousand first. */
                return (long) (double) v;
            }
        }

        return null;
    }

    public static Double getBrightness()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.BRIGHTNESS) : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    public static Double getWeather()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.WEATHER) : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    /**
     * How hard it is raining, from the weather curve, or null when nothing says.
     *
     * <p>The states run into each other rather than switching: 0 is clear, 1 is rain, 2 is a
     * thunderstorm, and a keyframe moving from one to the next brings the weather in over those
     * ticks instead of snapping. Rain fills in over the first half of that range and the storm
     * over the second, which is also the order they arrive in on their own.</p>
     */
    public static Double getWeatherRain()
    {
        Double state = getWeatherState();

        /* Spelled out rather than written as one conditional. With a boxed Double on one side and
         * a primitive on the other, the conditional's type is the primitive, so both sides get
         * unboxed - including the null that means "nothing is driving the weather". Which is
         * every call from the server thread, and so every world load. */
        if (state == null)
        {
            return getWeather();
        }

        return MathUtils.clamp(state, 0D, 1D);
    }

    /** The storm half of the weather curve - dark sky and lightning, on top of the rain. */
    public static Double getWeatherThunder()
    {
        Double state = getWeatherState();

        if (state == null)
        {
            return null;
        }

        return MathUtils.clamp(state - 1D, 0D, 1D);
    }

    private static Double getWeatherState()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());

            return values == null ? null : values.get(CurveClip.WEATHER_STATE);
        }

        return null;
    }

    /**
     * How far round the compass the sun rises, in degrees, or null when nothing says.
     *
     * <p>Turns the whole celestial sphere rather than the clock, so the time of day - and the
     * light level and the colour of the sky that come with it - stays exactly where it was put.
     * The existing sun curve could only move the sun by moving the time, which is the wrong
     * control when the shot needs the light coming from behind a building at the same hour.</p>
     *
     * <p>Vanilla's sky only. A shader pack works the sun's position out for itself from the world
     * time and will not follow this, so with Iris loaded the sun and moon move and the shading
     * does not.</p>
     */
    public static Double getSunDirection()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());

            return values == null ? null : values.get(CurveClip.SUN_DIRECTION);
        }

        return null;
    }

    /**
     * Whether the world should be replaced by a flat colour right now.
     *
     * <p>The curve wins over the setting when a clip has one, so a film can drop the world away
     * for a few seconds without the setting being switched by hand and left on afterwards. With
     * no curve, the setting is still the answer.</p>
     */
    public static boolean isChromaSkyActive()
    {
        return getChromaSkyStrength() > 0D;
    }

    /**
     * How far the chroma colour has come in, 0 to 1.
     *
     * <p>The curve used to be read as a switch at the halfway mark, so a keyframe from 0 to 1
     * spent its whole length doing nothing and then swapped the world for a colour in a single
     * frame. It is the amount of colour instead, which is what makes it possible to fade one in.
     * Without a curve the setting still answers, as fully on or fully off.</p>
     */
    public static double getChromaSkyStrength()
    {
        Double curve = getChromaSky();

        if (curve == null)
        {
            return BBSSettings.chromaSkyEnabled.get() ? 1D : 0D;
        }

        return MathUtils.clamp(curve, 0D, 1D);
    }

    /**
     * Whether the colour is solid enough to stand in for the world entirely - the point at which
     * the sky and the terrain can be skipped outright rather than covered over. Below it they
     * have to be drawn, or there would be nothing to fade out of.
     */
    public static boolean isChromaSkyOpaque()
    {
        return getChromaSkyStrength() >= 1D;
    }

    /**
     * Lay the chroma colour over the whole frame, at whatever strength the curve is at.
     *
     * <p>Drawn without depth testing after the world and its entities and before the film's own
     * forms, so it covers blocks, mobs, particles and everything else the world drew, and the
     * replays land on top of it.</p>
     */
    public static void renderChromaSkyOverlay(WorldRenderContext context)
    {
        double strength = getChromaSkyStrength();

        if (strength <= 0D)
        {
            return;
        }

        Integer fromCurve = getChromaSkyColorArgb();
        Color color = Colors.COLOR.set(fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get());
        MatrixStack stack = context.matrixStack();

        stack.push();

        MatrixStack.Entry peek = stack.peek();

        /* Identity, so the quad is in front of the camera rather than somewhere in the world. */
        peek.getPositionMatrix().identity();
        peek.getNormalMatrix().identity();
        stack.translate(0F, 0F, -1F);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* Comfortably past the corners of any field of view - this is a curtain, not a shape. */
        float d = 8F;

        Draw.fillQuad(builder, stack,
            -d, -d, 0,
            d, -d, 0,
            d, d, 0,
            -d, d, 0,
            color.r, color.g, color.b, (float) strength
        );

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.disableBlend();

        stack.pop();
    }

    /**
     * Whether the terrain is still drawn under a chroma sky.
     *
     * <p>Never, when a curve is driving it: asking for the world to go and getting the sky alone
     * is not what the curve is for.</p>
     */
    public static boolean isChromaSkyTerrainVisible()
    {
        return getChromaSky() == null && BBSSettings.chromaSkyTerrain.get();
    }

    private static Double getChromaSky()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());

            return values == null ? null : values.get(CurveClip.CHROMA_SKY);
        }

        return null;
    }

    public static Integer getChromaSkyColorArgb()
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Integer> values = CurveClip.getColorValues(controller.getContext());

            if (values != null)
            {
                return values.get(CurveClip.CHROMA_SKY_COLOR);
            }
        }

        return null;
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color)
    {
        if (sodium)
        {
            return (b) -> SodiumUtils.createVertexBuffer(b, color);
        }

        return (b) -> new RecolorVertexConsumer(b, color);
    }
}
