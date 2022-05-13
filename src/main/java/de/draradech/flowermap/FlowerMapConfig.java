package de.draradech.flowermap;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;


@Config(name = "flowermap")
public class FlowerMapConfig implements ConfigData {
    public boolean enabled = true;
    public float scale = 2.0f;
    public boolean legend = true;
    public float legendScale = 2.0f;
    public boolean dynamic = true;
    @ConfigEntry.BoundedDiscrete(min = -63, max = 319)
    public int fixedY = 64;
}
