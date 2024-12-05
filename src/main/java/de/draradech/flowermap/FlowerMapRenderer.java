package de.draradech.flowermap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;


public class FlowerMapRenderer {
    final RandomSource rand_render = RandomSource.create();
    final RandomSource rand_text = RandomSource.create();
    final Minecraft minecraft;

    DynamicTexture texture = null;
    AbstractTexture pointer;
    
    Map<Block, Integer> colorMap = new LinkedHashMap<Block, Integer>();
    Map<Block, Integer> errorMap = new LinkedHashMap<Block, Integer>();
    Thread renderThread;
    boolean textureRendering;
    boolean enabled;
    
    RegistryLookup<Biome> vanillaBiomes = null;
    NormalNoise noise;
    
    int color(int r, int g, int b) {
        return 0xff << 24 | r << 16 | g << 8 | b;
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
        colorMap.put(Blocks.PINK_PETALS, color(255, 65, 191));
        colorMap.put(Blocks.CLOSED_EYEBLOSSOM, color(127, 63, 0));
        
        errorMap.put(Blocks.GRAY_WOOL, color(127, 127, 127)); // no flower can grow
        errorMap.put(Blocks.GREEN_WOOL, color(0, 127, 0)); // unknown flower
        errorMap.put(Blocks.YELLOW_WOOL, color(0, 255, 0)); // can't get biome
        errorMap.put(Blocks.RED_WOOL, color(255, 0, 255)); // can't get biome key
        
        noise = NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(2345L)), new NoiseParameters(0, 1.0));
        
        renderThread = new Thread(new Runnable() { public void run() { renderTexture(); } } );
        textureRendering = false;
        renderThread.start();
    }
    
    Component getFlowerName(Block block)
    {
        if(colorMap.containsKey(block)) return block.getName();
        if(block == Blocks.GRAY_WOOL) return Component.literal("None");
        if(block == Blocks.YELLOW_WOOL) return Component.literal("Error: can't get biome");
        if(block == Blocks.RED_WOOL) return Component.literal("Error: can't get biome key");
        return Component.literal("Error: unknown flower: ").append(block.getName());
    }
    
    void loadVanillaBiomes()
    {
        HolderLookup.Provider vanillaRegistries = VanillaRegistries.createLookup();
        vanillaBiomes = vanillaRegistries.lookupOrThrow(Registries.BIOME);
    }
    
    BlockStateProvider getFlowersAt(BlockPos pos)
    {
        // The client-side biomes don't know their generation settings.
        // As a workaround, assume biomes haven't been modified via datapack and get the biome from the builtin registry.
        if(vanillaBiomes == null)
        {
            loadVanillaBiomes();
        }
        
        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(pos);
        ResourceKey<Biome> biomeKey = biomeEntry.unwrapKey().orElse(null);
        if (biomeKey != null) {
            Reference<Biome> vanillaBiomeRef = vanillaBiomes.get(biomeKey).orElse(null);
            if (vanillaBiomeRef != null)
            {
                Biome vanillaBiome = vanillaBiomeRef.value();
                List<ConfiguredFeature<?, ?>> list = vanillaBiome.getGenerationSettings().getFlowerFeatures();
                if (list.isEmpty())
                {
                    // no flowers can grow here
                    return BlockStateProvider.simple(Blocks.GRAY_WOOL);
                } else {
                    // get a random flower from the list of possible flowers at this position
                    RandomPatchConfiguration config = (RandomPatchConfiguration) list.get(0).config();
                    SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) config.feature().value().feature().value().config();
                    return flowerMap.toPlace();
                }
            } else {
                // couldn't get vanilla biome
                return BlockStateProvider.simple(Blocks.YELLOW_WOOL);
            }
        } else {
            // couldn't get biome key
            return BlockStateProvider.simple(Blocks.RED_WOOL);
        }
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
                    for (int z = 0; z < 256; ++z)
                    {
                        pos.setX(px + x - 128);
                        pos.setZ(pz + z - 128);
                        BlockStateProvider bsp = getFlowersAt(pos);
                        Block block = bsp.getState(rand_render, pos).getBlock();
                        texture.getPixels().setPixel(x, z, colorMap.getOrDefault(block, errorMap.getOrDefault(block, errorMap.get(Blocks.GREEN_WOOL))));
                    }
                }
                textureRendering = false;
            }
            else
            {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
    private void drawTexture(GuiGraphics guiGraphics, int texid, int x, int y, int w, int h)
    {
    	RenderSystem.setShader(CoreShaders.POSITION_TEX);
    	RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    	RenderSystem.setShaderTexture(0, texid);
        Matrix4f matrix4f3 = guiGraphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.addVertex(matrix4f3, x, y, 0.0f).setUv(0.0f, 0.0f);
        bufferBuilder.addVertex(matrix4f3, x, y + h, 0.0f).setUv(0.0f, 1.0f);
        bufferBuilder.addVertex(matrix4f3, x + w, y + h, 0.0f).setUv(1.0f, 1.0f);
        bufferBuilder.addVertex(matrix4f3, x + w, y, 0.0f).setUv(1.0f, 0.0f);
        BufferUploader.drawWithShader(bufferBuilder.build());
    }
    
    public void renderPossibleFlowerName(GuiGraphics guiGraphics, Block block, int w, int i)
    {
        Component flowerName = getFlowerName(block);
        guiGraphics.drawString(minecraft.font, flowerName, w - 5 - 256 + 12, 256 + 5 + 5 + 12 * (i + 3), 0xffffffff);
    }
    
	public void render(GuiGraphics guiGraphics)
	{
        if (!FlowerMapMain.config.enabled) return;
        
        // there should be nothing in the buffers, just to be safe, we flush before changing render config.
        guiGraphics.flush();

        Profiler.get().push("flowermap");
        
        // SETUP
        if (texture == null) {
            texture = new DynamicTexture(256, 256, false);
            GlStateManager._bindTexture(texture.getId());
            GlStateManager._texParameter(3553, 10241, 9728); // GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST
            GlStateManager._texParameter(3553, 10240, 9728); // GL_TEXTURE_2D, GL_TEXTURE_MAX_FILTER, GL_NEAREST
            GlStateManager._bindTexture(0);
            pointer = minecraft.getTextureManager().getTexture(ResourceLocation.fromNamespaceAndPath("flowermap", "pointer.png"));
        }
        Window window = minecraft.getWindow();
        float width = window.getWidth() / FlowerMapMain.config.scale;
        float height = window.getHeight() / FlowerMapMain.config.scale;
        RenderSystem.backupProjectionMatrix();
        
        Matrix4f noguiscale = new Matrix4f().setOrtho(0.0f, width, height, 0.0f, 1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(noguiscale, ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.pushMatrix();
        matrixStack.identity();
        matrixStack.translate(0.0f, 0.0f, -11000.0f);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().setIdentity();
        
        // RENDER THREAD CONTROL, TEXTURE UPLOAD
        if (textureRendering == false) {
            Profiler.get().push("upload");
            texture.upload();
            Profiler.get().pop();
            textureRendering = true;
        }
        
        // FLOWER GRADIENT TEXTURE
        drawTexture(guiGraphics, texture.getId(), (int)width - 256 - 5, 5, 256, 256);
        
        // Y LEVEL AND BIOME
        if (FlowerMapMain.config.dynamic) {
        	guiGraphics.drawString(minecraft.font, String.format("Position (xzy): %d, %d, %d (player)", minecraft.player.getBlockX(), minecraft.player.getBlockZ(), minecraft.player.getBlockY()), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
        }
        else
        {
        	guiGraphics.drawString(minecraft.font, String.format("Position (xzy): %d, %d, %d (fixed y)", minecraft.player.getBlockX(), minecraft.player.getBlockZ(), FlowerMapMain.config.fixedY), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
        }

        int px = minecraft.player.getBlockX();
        int py = minecraft.player.getBlockY();
        int pz = minecraft.player.getBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(px, FlowerMapMain.config.fixedY, pz);
        if (FlowerMapMain.config.dynamic) pos.setY(py);

        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(pos);
        MutableComponent biomeName = Component.translatable(Util.makeDescriptionId("biome", biomeEntry.unwrapKey().get().location()));
        guiGraphics.drawString(minecraft.font, Component.literal("Biome: ").append(biomeName), (int)width - 5 - 256, 256 + 5 + 5, 0xffffffff);

        // POSSIBLE FLOWERS
        Component desc = Component.literal("Possible flowers at this location:");
        guiGraphics.drawString(minecraft.font, desc, (int)width - 256 - 5, 256 + 5 + 5 + 24, 0xffffffff);
        BlockStateProvider bsp = getFlowersAt(pos);
        if (  (bsp instanceof NoiseProvider)
           || (bsp instanceof SimpleStateProvider)
           )
        {
            // these have no randomness, so we can just query them directly
            renderPossibleFlowerName(guiGraphics, bsp.getState(rand_text, pos).getBlock(), (int)width, 0);
        }
        else if (bsp instanceof WeightedStateProvider)
        {
            // cherry or default
            if (bsp.getState(rand_text, pos).getBlock() == Blocks.PINK_PETALS)
            {
                renderPossibleFlowerName(guiGraphics, Blocks.PINK_PETALS, (int)width, 0);
            }
            else
            {
                renderPossibleFlowerName(guiGraphics, Blocks.POPPY, (int)width, 0);
                renderPossibleFlowerName(guiGraphics, Blocks.DANDELION, (int)width, 1);
            }
        }
        else if (bsp instanceof NoiseThresholdProvider)
        {
            // plains
            double t = noise.getValue((double)pos.getX() * 0.005F, (double)pos.getY() * 0.005F, (double)pos.getZ() * 0.005F);
            if (t < (double)-0.8F) {
                renderPossibleFlowerName(guiGraphics, Blocks.ORANGE_TULIP, (int)width, 0);
                renderPossibleFlowerName(guiGraphics, Blocks.RED_TULIP, (int)width, 1);
                renderPossibleFlowerName(guiGraphics, Blocks.PINK_TULIP, (int)width, 2);
                renderPossibleFlowerName(guiGraphics, Blocks.WHITE_TULIP, (int)width, 3);
            } else {
                renderPossibleFlowerName(guiGraphics, Blocks.DANDELION, (int)width, 0);
                renderPossibleFlowerName(guiGraphics, Blocks.POPPY, (int)width, 1);
                renderPossibleFlowerName(guiGraphics, Blocks.AZURE_BLUET, (int)width, 2);
                renderPossibleFlowerName(guiGraphics, Blocks.OXEYE_DAISY, (int)width, 3);
                renderPossibleFlowerName(guiGraphics, Blocks.CORNFLOWER, (int)width, 4);
            }
        }
        
        // LEGEND
        if (FlowerMapMain.config.legend) {
            Profiler.get().push("legend");
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, FlowerMapMain.config.legendScale / FlowerMapMain.config.scale, 1.0f);
            int i = 0;
            for (Map.Entry<Block, Integer> e : colorMap.entrySet())
            {
                guiGraphics.fill(5, 5 + i * 12, 15, 15 + i * 12, e.getValue());
                guiGraphics.drawString(minecraft.font, getFlowerName(e.getKey()), 17, 7 + i++ * 12, 0xffffffff);
            }
            guiGraphics.pose().popPose();
            Profiler.get().pop();
        }
        
        // PLAYER POSITION MARKER
        guiGraphics.pose().translate((int)width - 256 - 5 + 128, 5 + 128, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(minecraft.player.getYRot() + 180.0f));
        drawTexture(guiGraphics, pointer.getId(), -8, -9, 16, 16);
        
        guiGraphics.flush();
        guiGraphics.pose().popPose();
        RenderSystem.restoreProjectionMatrix();
        matrixStack.popMatrix();
        Profiler.get().pop();
    }
}
