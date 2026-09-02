package de.draradech.flowermap;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;


public class FlowerMapMain {
    public static final String MODID = "flowermap";

    private static final int KEY_TOGGLE = InputConstants.KEY_F8;
    private static final int KEY_TOGGLE_MODE = InputConstants.KEY_MULTIPLY;
    private static final int KEY_INCREASE_Y = InputConstants.KEY_ADD;
    private static final int KEY_DECREASE_Y = 86; // SDL_SCANCODE_KP_MINUS (no InputConstant available)
    private static final int KEY_SET_Y = InputConstants.KEY_NUMPAD0;

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

        keyToggle = new KeyMapping("key.flowermap.toggle", KEY_TOGGLE, keyCategory);
        keyToggleMode = new KeyMapping("key.flowermap.toggleMode", KEY_TOGGLE_MODE, keyCategory);
        keyIncreaseY = new KeyMapping("key.flowermap.increaseY", KEY_INCREASE_Y, keyCategory);
        keyDecreaseY = new KeyMapping("key.flowermap.decreaseY", KEY_DECREASE_Y, keyCategory);
        keySetY = new KeyMapping("key.flowermap.setY", KEY_SET_Y, keyCategory);

        configHolder = AutoConfig.register(FlowerMapConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(FlowerMapConfig.class).getConfig();
    }

    public static void render(GuiGraphicsExtractor guiGraphics)
    {
        if (keyIncreaseY.consumeClick()  && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Mth.clamp(config.fixedY + 1, -63, 319); configHolder.save();}
        if (keyDecreaseY.consumeClick()  && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Mth.clamp(config.fixedY - 1, -63, 319); configHolder.save();}
        if (keySetY.consumeClick()       && config.enabled && config.mode == FlowerMapConfig.EMode.FIXED) {config.fixedY = Minecraft.getInstance().player.getBlockY();             configHolder.save();}
        if (keyToggleMode.consumeClick() && config.enabled) {config.mode = FlowerMapConfig.EMode.values()[(config.mode.ordinal() + 1) % (FlowerMapConfig.EMode.values().length)];  configHolder.save();}
        if (keyToggle.consumeClick()) {config.enabled = !config.enabled; configHolder.save();}
        renderer.render(guiGraphics);
    }
}
