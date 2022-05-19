package de.draradech.flowermap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;


public class FlowerMapRenderer extends GuiComponent {
    final RandomSource random = RandomSource.create();
    final Minecraft minecraft;
    DynamicTexture texture = null;
    AbstractTexture pointer;
    
    Map<BlockState, Integer> colorMap = new LinkedHashMap<BlockState, Integer>();
    Thread renderThread;
    boolean textureRendering;
    boolean enabled;
    
    int color(int r, int g, int b) {
        return 255 << 24 | b << 16 | g << 8 | r;
    }
    
    public FlowerMapRenderer() {
        minecraft = Minecraft.getInstance();
        colorMap.put(Blocks.DANDELION.defaultBlockState(), color(255, 255, 0));
        colorMap.put(Blocks.POPPY.defaultBlockState(), color(255, 0, 0));
        colorMap.put(Blocks.ALLIUM.defaultBlockState(), color(153, 0, 255));
        colorMap.put(Blocks.AZURE_BLUET.defaultBlockState(), color(255, 253, 221));
        colorMap.put(Blocks.RED_TULIP.defaultBlockState(), color(255, 77, 98));
        colorMap.put(Blocks.ORANGE_TULIP.defaultBlockState(), color(255, 181, 90));
        colorMap.put(Blocks.WHITE_TULIP.defaultBlockState(), color(221, 255, 255));
        colorMap.put(Blocks.PINK_TULIP.defaultBlockState(), color(245, 180, 255));
        colorMap.put(Blocks.OXEYE_DAISY.defaultBlockState(), color(255, 238, 221));
        colorMap.put(Blocks.CORNFLOWER.defaultBlockState(), color(65, 0, 255));
        colorMap.put(Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), color(255, 255, 255));
        colorMap.put(Blocks.BLUE_ORCHID.defaultBlockState(), color(0, 191, 255));
        renderThread = new Thread(new Runnable() { public void run() { renderTexture(); } } );
        textureRendering = false;
        renderThread.start();
    }
    
    void renderTexture()
    {
        for(;;)
        {
            if(textureRendering == true)
            {
                int px = minecraft.player.getBlockX();
                int py = minecraft.player.getBlockY();
                int pz = minecraft.player.getBlockZ();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, FlowerMapMain.config.fixedY, 0);
                if (FlowerMapMain.config.dynamic) pos.setY(py);
                for (int x = 0; x < 256; ++x)
                {
                    for (int z = 0; z < 256; ++z) {
                        pos.setX(px + x - 128);
                        pos.setZ(pz + z - 128);
                        Holder<Biome> biomeEntry = minecraft.player.level.getBiome(pos);
                        Biome biome;
                        if (minecraft.isLocalServer()) {
                            // If this is a local server (singleplayer or opened to LAN), we can get the biome directly, it will know its generation settings.
                            biome = biomeEntry.value();
                        }
                        else
                        {
                            // If the server is remote (multiplayer client), the biomes don't know their generation settings.
                            // As a workaround, assume biomes haven't been modified via datapack and get the biome from the builtin registry.
                            biome = BuiltinRegistries.BIOME.get(biomeEntry.unwrapKey().get());
                        }
                        List<ConfiguredFeature<?, ?>> list = biome.getGenerationSettings().getFlowerFeatures();
                        if (list.isEmpty()) {
                            texture.getPixels().setPixelRGBA(x, z, 0xff7f7f7f);
                        } else {
                            RandomPatchConfiguration config = (RandomPatchConfiguration) list.get(0).config();
                            SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) config.feature().value().feature().value().config();
                            BlockState state = flowerMap.toPlace().getState(random, pos);
                            texture.getPixels().setPixelRGBA(x, z, colorMap.getOrDefault(state, 0xff007f00));
                        }
                    }
                }
                textureRendering = false;
            }
            else
            {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
    public void render()
    {
        if (!FlowerMapMain.config.enabled) return;
        
        minecraft.getProfiler().push("flowermap");
        
        // SETUP
        if (texture == null) {
            texture = new DynamicTexture(256, 256, false);
            pointer = minecraft.getTextureManager().getTexture(new ResourceLocation("flowermap:pointer.png"));
        }
        Window window = minecraft.getWindow();
        float width = window.getWidth() / FlowerMapMain.config.scale;
        float height = window.getHeight() / FlowerMapMain.config.scale;
        Matrix4f before = RenderSystem.getProjectionMatrix();
        Matrix4f noguiscale = Matrix4f.orthographic(0.0f, width, 0.0f, height, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(noguiscale);
        PoseStack poseStack = new PoseStack();
        
        // RENDER THREAD CONTROL
        if (textureRendering == false) {
            minecraft.getProfiler().push("upload");
            texture.upload();
            minecraft.getProfiler().pop();
            textureRendering = true;
        }
        
        // FLOWER GRADIENT TEXTURE
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        blit(poseStack, (int)width - 256 - 5, 5, 0, 0, 256, 256);
        
        // Y LEVEL AND BIOME
        RenderSystem.disableBlend();
        if (FlowerMapMain.config.dynamic) {
            minecraft.font.drawShadow(poseStack, String.format("y: %d (player)", minecraft.player.getBlockY()), (int)width - 256 - 5, 256 + 5 + 5, 0xffffffff);
        }
        else
        {
            minecraft.font.drawShadow(poseStack, String.format("y: %d (fixed)", FlowerMapMain.config.fixedY), (int)width - 256 - 5, 256 + 5 + 5, 0xffffffff);
        }
        Holder<Biome> biomeEntry = minecraft.player.level.getBiome(minecraft.player.blockPosition());
        MutableComponent biomeName = Component.translatable(Util.makeDescriptionId("biome", biomeEntry.unwrapKey().get().location()));
        int nameLength = minecraft.font.width(biomeName);
        minecraft.font.drawShadow(poseStack, biomeName, (int)width - 5 - nameLength, 256 + 5 + 5, 0xffffffff);
        
        // LEGEND
        if (FlowerMapMain.config.legend) {
            minecraft.getProfiler().push("legend");
            poseStack.pushPose();
            poseStack.scale(FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, 1.0f);
            int i = 0;
            for (Map.Entry<BlockState, Integer> e : colorMap.entrySet())
            {
                int col = e.getValue();
                int r = col & 0xff;
                int g = (col >> 8) & 0xff;
                int b = (col >> 16) & 0xff;
                fill(poseStack, 5, 5 + i * 12, 15, 15 + i * 12, 0xff000000 | r << 16 | g << 8 | b);
                minecraft.font.drawShadow(poseStack, e.getKey().getBlock().getName(), 17, 7 + i++ * 12, 0xffffffff);
            }
            poseStack.popPose();
            minecraft.getProfiler().pop();
        }
        
        // PLAYER POSITION MARKER
        poseStack.translate((int)width - 256 - 5 + 128, 5 + 128, 0);
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(minecraft.player.getYRot() + 180.0f));
        RenderSystem.setShaderTexture(0, pointer.getId());
        blit(poseStack, -8, -9, 0, 0, 16, 16, 16, 16);
        
        RenderSystem.setProjectionMatrix(before);
        minecraft.getProfiler().pop();
    }
}
