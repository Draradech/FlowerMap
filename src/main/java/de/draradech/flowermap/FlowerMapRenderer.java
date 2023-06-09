package de.draradech.flowermap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;


public class FlowerMapRenderer {
    final RandomSource random = RandomSource.create();
    final Minecraft minecraft;
    DynamicTexture texture = null;
    AbstractTexture pointer;
    
    Map<Block, Integer> colorMap = new LinkedHashMap<Block, Integer>();
    Thread renderThread;
    boolean textureRendering;
    boolean enabled;
    
    RegistryLookup<Biome> vanillaBiomes = null;
    
    int color(int r, int g, int b) {
        return 255 << 24 | b << 16 | g << 8 | r;
    }
    
    public FlowerMapRenderer() {
        minecraft = Minecraft.getInstance();
        colorMap.put(Blocks.DANDELION, color(255, 255, 0));
        colorMap.put(Blocks.POPPY, color(255, 0, 0));
        colorMap.put(Blocks.ALLIUM, color(153, 0, 255));
        colorMap.put(Blocks.AZURE_BLUET, color(255, 253, 221));
        colorMap.put(Blocks.RED_TULIP, color(255, 77, 98));
        colorMap.put(Blocks.ORANGE_TULIP, color(255, 181, 90));
        colorMap.put(Blocks.WHITE_TULIP, color(221, 255, 255));
        colorMap.put(Blocks.PINK_TULIP, color(245, 180, 255));
        colorMap.put(Blocks.OXEYE_DAISY, color(255, 238, 221));
        colorMap.put(Blocks.CORNFLOWER, color(65, 0, 255));
        colorMap.put(Blocks.LILY_OF_THE_VALLEY, color(255, 255, 255));
        colorMap.put(Blocks.BLUE_ORCHID, color(0, 191, 255));
        colorMap.put(Blocks.PINK_PETALS, color(255, 0, 191));
        renderThread = new Thread(new Runnable() { public void run() { renderTexture(); } } );
        textureRendering = false;
        renderThread.start();
    }
    
    void loadVanillaBiomes()
    {
        HolderLookup.Provider vanillaRegistries = VanillaRegistries.createLookup();
        vanillaBiomes = vanillaRegistries.lookupOrThrow(Registries.BIOME);
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
                        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(pos);
                        Biome biome;
                        if (minecraft.isLocalServer()) {
                            // If this is a local server (singleplayer or opened to LAN), we can get the biome directly, it will know its generation settings.
                            biome = biomeEntry.value();
                        }
                        else
                        {
                            // If the server is remote (multiplayer client), the biomes don't know their generation settings.
                            // As a workaround, assume biomes haven't been modified via datapack and get the biome from the builtin registry.
                            if(vanillaBiomes == null)
                            {
                                loadVanillaBiomes();
                            }
                            biome = vanillaBiomes.get(biomeEntry.unwrapKey().get()).get().value();
                        }
                        List<ConfiguredFeature<?, ?>> list = biome.getGenerationSettings().getFlowerFeatures();
                        if (list.isEmpty()) {
                            texture.getPixels().setPixelRGBA(x, z, 0xff7f7f7f);
                        } else {
                            RandomPatchConfiguration config = (RandomPatchConfiguration) list.get(0).config();
                            SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) config.feature().value().feature().value().config();
                            Block block = flowerMap.toPlace().getState(random, pos).getBlock();
                            texture.getPixels().setPixelRGBA(x, z, colorMap.getOrDefault(block, 0xff007f00));
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
    
    private void blit(GuiGraphics guiGraphics, int texid, int x, int y, int w, int h)
    {
    	BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
    	RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texid);
        Matrix4f matrix4f3 = guiGraphics.pose().last().pose();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix4f3, x, y, 0.0f).uv(0.0f, 0.0f).endVertex();
        bufferBuilder.vertex(matrix4f3, x, y + h, 0.0f).uv(0.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix4f3, x + w, y + h, 0.0f).uv(1.0f, 1.0f).endVertex();
        bufferBuilder.vertex(matrix4f3, x + w, y, 0.0f).uv(1.0f, 0.0f).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }
    
	public void render(GuiGraphics guiGraphics)
	{
        if (!FlowerMapMain.config.enabled) return;
        
        // there should be nothing in the buffers, just to be safe, we flush before changing render config.
        guiGraphics.flush();

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
        VertexSorting vsBefore = RenderSystem.getVertexSorting();
        Matrix4f noguiscale = new Matrix4f().setOrtho(0.0f, width, height, 0.0f, 1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(noguiscale, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
		guiGraphics.pose().pushPose();
		guiGraphics.pose().setIdentity();
        
        // RENDER THREAD CONTROL
        if (textureRendering == false) {
            minecraft.getProfiler().push("upload");
            texture.upload();
            minecraft.getProfiler().pop();
            textureRendering = true;
        }
        
        // FLOWER GRADIENT TEXTURE
        blit(guiGraphics, texture.getId(), (int)width - 256 - 5, 5, 256, 256);
        
        // Y LEVEL AND BIOME
        if (FlowerMapMain.config.dynamic) {
        	guiGraphics.drawString(minecraft.font, String.format("y: %d (player)", minecraft.player.getBlockY()), (int)width - 256 - 5, 256 + 5 + 5, 0xffffffff);
        }
        else
        {
        	guiGraphics.drawString(minecraft.font, String.format("y: %d (fixed)", FlowerMapMain.config.fixedY), (int)width - 256 - 5, 256 + 5 + 5, 0xffffffff);
        }
        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(minecraft.player.blockPosition());
        MutableComponent biomeName = Component.translatable(Util.makeDescriptionId("biome", biomeEntry.unwrapKey().get().location()));
        int nameLength = minecraft.font.width(biomeName);
        guiGraphics.drawString(minecraft.font, biomeName, (int)width - 5 - nameLength, 256 + 5 + 5, 0xffffffff);
        
        // LEGEND
        if (FlowerMapMain.config.legend) {
            minecraft.getProfiler().push("legend");
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, 1.0f);
            int i = 0;
            for (Map.Entry<Block, Integer> e : colorMap.entrySet())
            {
                int col = e.getValue();
                int r = col & 0xff;
                int g = (col >> 8) & 0xff;
                int b = (col >> 16) & 0xff;
                guiGraphics.fill(5, 5 + i * 12, 15, 15 + i * 12, 0xff000000 | r << 16 | g << 8 | b);
                guiGraphics.drawString(minecraft.font, e.getKey().getName(), 17, 7 + i++ * 12, 0xffffffff);
            }
            guiGraphics.pose().popPose();
            minecraft.getProfiler().pop();
        }
        
        // PLAYER POSITION MARKER
        guiGraphics.pose().translate((int)width - 256 - 5 + 128, 5 + 128, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(minecraft.player.getYRot() + 180.0f));
        blit(guiGraphics, pointer.getId(), -8, -9, 16, 16);
        
        guiGraphics.flush();
        guiGraphics.pose().popPose();
        RenderSystem.setProjectionMatrix(before, vsBefore);
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        minecraft.getProfiler().pop();
    }
}
