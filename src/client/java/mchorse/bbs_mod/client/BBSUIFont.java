package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;

/**
 * The font BBS draws its own interface with.
 *
 * <p>Its own {@link TextRenderer} over a TrueType file, kept entirely separate from the one the
 * game draws with. Nothing here is ever handed to the world, the HUD or a vanilla screen - BBS
 * asks for a renderer in exactly one place, {@code Batcher2D}, and that is the only caller. A
 * font that looked right in the editor and then turned up on signs and nameplates would be worse
 * than no font at all.</p>
 *
 * <p>The file is read from the BBS config folder rather than bundled, so it stays the user's own
 * font: dropping a different one in swaps the interface over, and shipping this build to someone
 * without it simply falls back to the game's font.</p>
 */
public class BBSUIFont
{
    public static final String FONT_PATH = "fonts/ui.ttf";

    private static final Identifier ID = new Identifier("bbs", "ui_font");

    /**
     * The em size a scale of 1 asks the font for.
     *
     * <p>Ten rather than the nine pixels BBS lays a line out in, because those are not the same
     * measurement. Nine is the whole line, ascender and descender included; the game's own font
     * puts a capital letter seven pixels tall inside it. A typeface asked for nine draws its
     * capitals around six, which is a noticeably smaller and thinner word in the same space -
     * legible, but working against the reader. Ten puts the capitals back at seven.</p>
     */
    private static final float BASE_SIZE = 10F;

    private static TextRenderer renderer;
    private static FontStorage storage;

    /** What the current renderer was built for, so a change is noticed and nothing else is. */
    private static float builtScale = -1F;
    private static float builtOversample = -1F;
    private static boolean triedAndFailed;

    private BBSUIFont()
    {}

    public static File getFontFile()
    {
        return BBSMod.getSettingsPath(FONT_PATH);
    }

    /**
     * @return the interface font, or the game's own when it is switched off, missing or unreadable.
     */
    public static TextRenderer get()
    {
        TextRenderer vanilla = MinecraftClient.getInstance().textRenderer;

        if (!BBSSettings.uiFont.get())
        {
            return vanilla;
        }

        float scale = (float) MathUtils.clamp(BBSSettings.uiFontScale.get(), 0.5D, 4D);
        float oversample = oversample();

        if (renderer == null || builtScale != scale || builtOversample != oversample)
        {
            if (triedAndFailed && builtScale == scale && builtOversample == oversample)
            {
                return vanilla;
            }

            build(scale, oversample);
        }

        return renderer == null ? vanilla : renderer;
    }

    /**
     * How many real pixels the interface gets for each of the pixels it is laid out in.
     *
     * <p>This is what decides whether the text is sharp, and getting it wrong is what made the
     * first attempt at this font worse than the game's own. A glyph is rasterised at the drawn
     * size times this, and the atlas is sampled without any filtering - so at anything other than
     * the interface's real scale, the drawing is picking some texels and dropping others. Four
     * looked like the safe high-quality answer and was throwing away three pixels in every four.</p>
     *
     * <p>At the real scale each texel is one pixel on the screen, which is the whole of the
     * difference: the letters are rendered at the resolution they are actually shown at, the same
     * way everything outside the game is.</p>
     */
    private static float oversample()
    {
        return (float) MathUtils.clamp(BBSModClient.getGUIScale(), 1D, 8D);
    }

    private static void build(float scale, float oversample)
    {
        close();

        builtScale = scale;
        builtOversample = oversample;
        triedAndFailed = true;

        File file = getFontFile();

        if (!file.isFile())
        {
            return;
        }

        ByteBuffer buffer = null;
        STBTTFontinfo info = null;

        try
        {
            byte[] bytes = Files.readAllBytes(file.toPath());

            /* Off-heap and left allocated: stb reads the glyph outlines straight out of this for
             * as long as the font is alive, so it is the font that owns it and frees it. */
            buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            info = STBTTFontinfo.malloc();

            if (!STBTruetype.stbtt_InitFont(info, buffer))
            {
                info.free();
                MemoryUtil.memFree(buffer);

                return;
            }

            TrueTypeFont font = new TrueTypeFont(buffer, info, BASE_SIZE * scale, oversample, 0F, 0F, "");

            storage = new FontStorage(MinecraftClient.getInstance().getTextureManager(), ID);
            storage.setFonts(List.of(font));

            renderer = new TextRenderer((id) -> storage, false);
            triedAndFailed = false;
        }
        catch (Exception e)
        {
            /* A font that will not load is a reason to draw in the game's font, not a reason to
             * take the interface down with it. Reported once, then left alone - this is asked
             * for every frame. */
            System.err.println("BBS: failed to load the interface font at " + file + ": " + e);

            if (info != null)
            {
                info.free();
            }

            if (buffer != null)
            {
                MemoryUtil.memFree(buffer);
            }

            close();
        }
    }

    private static void close()
    {
        if (storage != null)
        {
            /* Closing the storage closes the font under it, which frees the glyph info and the
             * file buffer it was reading from. */
            storage.close();
        }

        storage = null;
        renderer = null;
    }
}
