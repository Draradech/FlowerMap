package de.draradech.flowermap;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.lwjgl.glfw.GLFW;


public class FlowerMapMain {
    public static final String MODID = "flowermap";

    public static FlowerMapConfig config;
    public static ConfigHolder<FlowerMapConfig> configHolder;
    public static FlowerMapRenderer renderer;
    public static KeyMapping keyToggle;
    public static KeyMapping keyToggleDynamic;
    public static KeyMapping keyIncreaseY;
    public static KeyMapping keyDecreaseY;
    public static KeyMapping keySetY;
    
    public static void init() {
        renderer = new FlowerMapRenderer();

        keyToggle = new KeyMapping("key.flowermap.toggle", GLFW.GLFW_KEY_F8, "key.category.flowermap");
        keyToggleDynamic = new KeyMapping("key.flowermap.toggleDynamic", GLFW.GLFW_KEY_KP_MULTIPLY, "key.category.flowermap");
        keyIncreaseY = new KeyMapping("key.flowermap.increaseY", GLFW.GLFW_KEY_KP_ADD, "key.category.flowermap");
        keyDecreaseY = new KeyMapping("key.flowermap.decreaseY", GLFW.GLFW_KEY_KP_SUBTRACT, "key.category.flowermap");
        keySetY = new KeyMapping("key.flowermap.setY", GLFW.GLFW_KEY_KP_0, "key.category.flowermap");

        configHolder = AutoConfig.register(FlowerMapConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(FlowerMapConfig.class).getConfig();
    }
    
    public static void render(GuiGraphics guiGraphics)
    {
        if (keyIncreaseY.consumeClick()     && config.enabled && !config.dynamic) {config.fixedY = Mth.clamp(config.fixedY + 1, -63, 319);     configHolder.save();}
        if (keyDecreaseY.consumeClick()     && config.enabled && !config.dynamic) {config.fixedY = Mth.clamp(config.fixedY - 1, -63, 319);     configHolder.save();}
        if (keySetY.consumeClick()          && config.enabled && !config.dynamic) {config.fixedY = Minecraft.getInstance().player.getBlockY(); configHolder.save();}
        if (keyToggleDynamic.consumeClick() && config.enabled)                    {config.dynamic = !config.dynamic;                           configHolder.save();}
        if (keyToggle.consumeClick())                                             {config.enabled = !config.enabled;                           configHolder.save();}
        renderer.render(guiGraphics);
    }
}
