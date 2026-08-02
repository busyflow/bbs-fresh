package mchorse.bbs_mod;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ColorPresetTest
{
    @Test
    public void appliesCopperCharcoalAndDetectsCustomColors()
    {
        ValueInt previousPrimary = BBSSettings.primaryColor;
        ValueInt previousSurface = BBSSettings.interfaceSurfaceColor;
        ValueInt previousPreset = BBSSettings.colorPreset;

        try
        {
            BBSSettings.primaryColor = new ValueInt("primary_color", 0).color();
            BBSSettings.interfaceSurfaceColor = new ValueInt("interface_surface_color", 0).color();
            BBSSettings.colorPreset = new ValueInt("color_preset", 0, 0, BBSSettings.getColorPresetCount() - 1);

            BBSSettings.applyColorPreset(0);

            assertEquals("Copper Charcoal", BBSSettings.getColorPresetName(0));
            assertEquals(0xda6c48, BBSSettings.primaryColor.get());
            assertEquals(0x171616, BBSSettings.interfaceSurfaceColor.get());
            assertEquals(0, BBSSettings.detectColorPreset());

            BBSSettings.primaryColor.set(0x123456);
            BBSSettings.syncColorPreset();

            assertEquals(BBSSettings.getColorPresetCount() - 1, BBSSettings.colorPreset.get());
            assertEquals("Custom", BBSSettings.getColorPresetName(BBSSettings.colorPreset.get()));
        }
        finally
        {
            BBSSettings.primaryColor = previousPrimary;
            BBSSettings.interfaceSurfaceColor = previousSurface;
            BBSSettings.colorPreset = previousPreset;
        }
    }
}
