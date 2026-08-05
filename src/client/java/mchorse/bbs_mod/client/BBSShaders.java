package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.util.Optional;

public class BBSShaders
{
    /** Bones per model the skinned program can hold — mirrors {@code BoneMats[128]} in model_skinned.vsh. */
    public static final int MAX_SKINNED_BONES = 128;

    private static ShaderProgram model;
    private static ShaderProgram modelSkinned;
    private static ShaderProgram multiLink;
    private static ShaderProgram subtitles;
    private static ShaderProgram selection;

    private static ShaderProgram pickerPreview;
    private static ShaderProgram pickerBillboard;
    private static ShaderProgram pickerBillboardNoShading;
    private static ShaderProgram pickerParticles;
    private static ShaderProgram pickerModels;

    static
    {
        setup();
    }

    public static void setup()
    {
        if (model != null) model.close();
        if (modelSkinned != null) modelSkinned.close();
        if (subtitles != null) subtitles.close();
        if (selection != null) selection.close();

        if (pickerPreview != null) pickerPreview.close();
        if (pickerBillboard != null) pickerBillboard.close();
        if (pickerBillboardNoShading != null) pickerBillboardNoShading.close();
        if (pickerParticles != null) pickerParticles.close();
        if (pickerModels != null) pickerModels.close();

        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            model = new ShaderProgram(factory, "model", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            multiLink = new ShaderProgram(factory, "multilink", VertexFormats.POSITION_TEXTURE_COLOR);
            subtitles = new ShaderProgram(factory, "subtitles", VertexFormats.POSITION_TEXTURE_COLOR);
            selection = new ShaderProgram(factory, "selection", VertexFormats.POSITION_TEXTURE_COLOR);

            pickerPreview = new ShaderProgram(factory, "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR);
            pickerBillboard = new ShaderProgram(factory, "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            pickerBillboardNoShading = new ShaderProgram(factory, "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
            pickerParticles = new ShaderProgram(factory, "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pickerModels = new ShaderProgram(factory, "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        setupSkinned();
    }

    /**
     * The GPU-skinned twin of the model program, loaded separately: it is an optimization, so a
     * driver that can't hold the bone palette (or a compile that fails on it) has to leave the
     * rest of the mod running on the ordinary program rather than take the frame down with it.
     */
    private static void setupSkinned()
    {
        modelSkinned = null;

        /* The palette is a plain uniform array, and the spec's floor for vertex uniform storage
         * (1024 components) is a quarter of what it needs. Every GPU that runs the game today is
         * far past that, but asking is cheap and a link failure here is not. */
        int components = GL20.glGetInteger(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS);

        if (components < MAX_SKINNED_BONES * 16 + 128)
        {
            return;
        }

        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            modelSkinned = new ShaderProgram(factory, "model_skinned", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        catch (Exception e)
        {
            modelSkinned = null;

            e.printStackTrace();
        }
    }

    public static ShaderProgram getModel()
    {
        return model;
    }

    /** The GPU-skinned model program, or {@code null} when this machine couldn't load it. */
    public static ShaderProgram getModelSkinned()
    {
        return modelSkinned;
    }

    public static ShaderProgram getMultilinkProgram()
    {
        return multiLink;
    }

    public static ShaderProgram getSubtitlesProgram()
    {
        return subtitles;
    }

    public static ShaderProgram getSelectionProgram()
    {
        return selection;
    }

    public static ShaderProgram getPickerPreviewProgram()
    {
        return pickerPreview;
    }

    public static ShaderProgram getPickerBillboardProgram()
    {
        return pickerBillboard;
    }

    public static ShaderProgram getPickerBillboardNoShadingProgram()
    {
        return pickerBillboardNoShading;
    }

    public static ShaderProgram getPickerParticlesProgram()
    {
        return pickerParticles;
    }

    public static ShaderProgram getPickerModelsProgram()
    {
        return pickerModels;
    }

    private static class ProxyResourceFactory implements ResourceFactory
    {
        private ResourceManager manager;

        public ProxyResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(new Identifier(BBSMod.MOD_ID, id.getPath()));
            }

            return this.manager.getResource(id);
        }
    }
}
