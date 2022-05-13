package de.draradech.flowermap;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;


public class FlowerMapMain implements ModInitializer{
    public static FlowerMapConfig config;
    static ConfigHolder<FlowerMapConfig> configHolder;
    static FlowerMapRenderer renderer;
    static KeyMapping keyToggle;
    static KeyMapping keyToggleDynamic;
    static KeyMapping keyIncreaseY;
    static KeyMapping keyDecreaseY;
    static KeyMapping keySetY;
    
    @SuppressWarnings("resource")
    @Override
    public void onInitialize() {
        renderer = new FlowerMapRenderer();
        
        configHolder = AutoConfig.register(FlowerMapConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(FlowerMapConfig.class).getConfig();
        
        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.toggle", GLFW.GLFW_KEY_F8, "key.category.flowermap"));
        keyToggleDynamic = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.toggleDynamic", GLFW.GLFW_KEY_KP_MULTIPLY, "key.category.flowermap"));
        keyIncreaseY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.increaseY", GLFW.GLFW_KEY_KP_ADD, "key.category.flowermap"));
        keyDecreaseY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.decreaseY", GLFW.GLFW_KEY_KP_SUBTRACT, "key.category.flowermap"));
        keySetY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.setY", GLFW.GLFW_KEY_KP_0, "key.category.flowermap"));
        
        HudRenderCallback.EVENT.register((poseStack, delta) -> {
            if (keyIncreaseY.consumeClick()     && config.enabled && !config.dynamic) {config.fixedY = Mth.clamp(config.fixedY + 1, -63, 319);     configHolder.save();}
            if (keyDecreaseY.consumeClick()     && config.enabled && !config.dynamic) {config.fixedY = Mth.clamp(config.fixedY - 1, -63, 319);     configHolder.save();}
            if (keySetY.consumeClick()          && config.enabled && !config.dynamic) {config.fixedY = Minecraft.getInstance().player.getBlockY(); configHolder.save();}
            if (keyToggleDynamic.consumeClick() && config.enabled)                    {config.dynamic = !config.dynamic;                           configHolder.save();}
            if (keyToggle.consumeClick())                                             {config.enabled = !config.enabled;                           configHolder.save();}
            renderer.render();
        });
    }
}
