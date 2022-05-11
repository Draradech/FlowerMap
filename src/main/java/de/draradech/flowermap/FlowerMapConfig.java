package de.draradech.flowermap;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "flowermap")
public class FlowerMapConfig implements ConfigData {
    @ConfigEntry.Category(value = "main")
    public float scale = 2.0f;
}
