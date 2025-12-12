package de.draradech.flowermap;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
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
    public static KeyMapping keyToggleMode;
    public static KeyMapping keyIncreaseY;
    public static KeyMapping keyDecreaseY;
    public static KeyMapping keySetY;
    public static KeyMapping.Category keyCategory;
    
    public static void init() {
        renderer = new FlowerMapRenderer();

        keyCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FlowerMapMain.MODID, "keycategory"));

        keyToggle = new KeyMapping("key.flowermap.toggle", GLFW.GLFW_KEY_F8, keyCategory);
        keyToggleMode = new KeyMapping("key.flowermap.toggleMode", GLFW.GLFW_KEY_KP_MULTIPLY, keyCategory);
        keyIncreaseY = new KeyMapping("key.flowermap.increaseY", GLFW.GLFW_KEY_KP_ADD, keyCategory);
        keyDecreaseY = new KeyMapping("key.flowermap.decreaseY", GLFW.GLFW_KEY_KP_SUBTRACT, keyCategory);
        keySetY = new KeyMapping("key.flowermap.setY", GLFW.GLFW_KEY_KP_0, keyCategory);

        configHolder = AutoConfig.register(FlowerMapConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(FlowerMapConfig.class).getConfig();
    }
    
    public static void render(GuiGraphics guiGraphics)
    {
        if (keyIncreaseY.consumeClick()  && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Mth.clamp(config.fixedY + 1, -63, 319); configHolder.save();}
        if (keyDecreaseY.consumeClick()  && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Mth.clamp(config.fixedY - 1, -63, 319); configHolder.save();}
        if (keySetY.consumeClick()       && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Minecraft.getInstance().player.getBlockY();             configHolder.save();}
        if (keyToggleMode.consumeClick() && config.enabled) {config.mode = FlowerMapConfig.EMode.values()[(config.mode.ordinal() + 1) % (FlowerMapConfig.EMode.values().length)];  configHolder.save();}
        if (keyToggle.consumeClick()) {config.enabled = !config.enabled; configHolder.save();}
        renderer.render(guiGraphics);
    }
}
