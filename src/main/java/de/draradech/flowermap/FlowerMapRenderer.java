package de.draradech.flowermap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
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
    ResourceLocation textureLocation;
    ResourceLocation pointerLocation;
    
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
        colorMap.put(Blocks.WILDFLOWERS, color(0, 127, 0));
        
        errorMap.put(Blocks.GRAY_WOOL, color(127, 127, 127)); // no flower can grow
        errorMap.put(Blocks.GREEN_WOOL, color(127, 255, 127)); // unknown flower
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
    
    Block getRandomFlowerAt(BlockPos pos, RandomSource randomSource)
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
                    return Blocks.GRAY_WOOL;
                } else {
					// get a random flower from the list of possible flowers at this position
					int k = randomSource.nextInt(list.size());
                    RandomPatchConfiguration config = (RandomPatchConfiguration) list.get(k).config();
                    SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) config.feature().value().feature().value().config();
                    return flowerMap.toPlace().getState(rand_render, pos).getBlock();
                }
            } else {
                // couldn't get vanilla biome
                return Blocks.YELLOW_WOOL;
            }
        } else {
            // couldn't get biome key
            return Blocks.RED_WOOL;
        }
    }
    
    public void renderPossibleFlowerName(GuiGraphics guiGraphics, Block block, int w, int i)
    {
        Component flowerName = getFlowerName(block);
        guiGraphics.drawString(minecraft.font, flowerName, w - 5 - 256 + 12, 256 + 5 + 5 + 12 * (i + 3), 0xffffffff);
    }
    
    public void renderPossibleFlowerNamesAt(BlockPos pos, RandomSource randomSource, int w, GuiGraphics gui)
    {
        // The client-side biomes don't know their generation settings.
        // As a workaround, assume biomes haven't been modified via datapack and get the biome from the builtin registry.
        if(vanillaBiomes == null)
        {
            loadVanillaBiomes();
        }
        
        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(pos);
        ResourceKey<Biome> biomeKey = biomeEntry.unwrapKey().orElse(null);
		int k = 0;
        if (biomeKey != null) {
            Reference<Biome> vanillaBiomeRef = vanillaBiomes.get(biomeKey).orElse(null);
            if (vanillaBiomeRef != null)
            {
                Biome vanillaBiome = vanillaBiomeRef.value();
                List<ConfiguredFeature<?, ?>> list = vanillaBiome.getGenerationSettings().getFlowerFeatures();
                if (list.isEmpty())
                {
                    // no flowers can grow here
                	renderPossibleFlowerName(gui, Blocks.GRAY_WOOL, w, k++);
                } else {
					// get a random flower from the list of possible flowers at this position
					for(ConfiguredFeature<?, ?> feature : list)
					{
	                    RandomPatchConfiguration config = (RandomPatchConfiguration) feature.config();
	                    SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) config.feature().value().feature().value().config();
	                    BlockStateProvider bsp = flowerMap.toPlace();
	                    if (  (bsp instanceof NoiseProvider)
	                       || (bsp instanceof SimpleStateProvider)
	                       )
	                    {
	                        // these have no randomness, so we can just query them directly
	                        renderPossibleFlowerName(gui, bsp.getState(rand_text, pos).getBlock(), w, k++);
	                    }
	                    else if (bsp instanceof WeightedStateProvider)
	                    {
	                    	Block b = bsp.getState(rand_text, pos).getBlock();
	                    	// birch forest
	                    	if (b == Blocks.WILDFLOWERS)
	                    	{
	                    		renderPossibleFlowerName(gui, Blocks.WILDFLOWERS, w, k++);
	                    	}
	                        // cherry
	                    	else if (b == Blocks.PINK_PETALS)
	                        {
	                            renderPossibleFlowerName(gui, Blocks.PINK_PETALS, w, k++);
	                        }
	                    	// default
	                        else
	                        {
	                            renderPossibleFlowerName(gui, Blocks.POPPY, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.DANDELION, w, k++);
	                        }
	                    }
	                    else if (bsp instanceof NoiseThresholdProvider)
	                    {
	                        // plains
	                        double t = noise.getValue((double)pos.getX() * 0.005F, (double)pos.getY() * 0.005F, (double)pos.getZ() * 0.005F);
	                        if (t < (double)-0.8F) {
	                            renderPossibleFlowerName(gui, Blocks.ORANGE_TULIP, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.RED_TULIP, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.PINK_TULIP, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.WHITE_TULIP, w, k++);
	                        } else {
	                            renderPossibleFlowerName(gui, Blocks.DANDELION, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.POPPY, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.AZURE_BLUET, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.OXEYE_DAISY, w, k++);
	                            renderPossibleFlowerName(gui, Blocks.CORNFLOWER, w, k++);
	                        }
	                    }
					}
                }
            } else {
                // couldn't get vanilla biome
                renderPossibleFlowerName(gui, Blocks.YELLOW_WOOL, w, k++);
            }
        } else {
            // couldn't get biome key
            renderPossibleFlowerName(gui, Blocks.RED_WOOL, w, k++);
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
                        Block block = getRandomFlowerAt(pos, rand_render);
                        texture.getPixels().setPixel(x, z, colorMap.getOrDefault(block, errorMap.getOrDefault(block, errorMap.get(Blocks.GREEN_WOOL))));
                    }
                }
                textureRendering = false;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
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
    
    public void render(GuiGraphics guiGraphics)
    {
        if (!FlowerMapMain.config.enabled) return;
        
        // there should be nothing in the buffers, just to be safe, we flush before changing render config.
        guiGraphics.flush();

        Profiler.get().push("flowermap");
        
        // SETUP
        if (texture == null) {
            texture = new DynamicTexture((String)null, 256, 256, false);
            texture.setFilter(false, false);
            pointerLocation = ResourceLocation.fromNamespaceAndPath("flowermap", "pointer.png");
            textureLocation = ResourceLocation.fromNamespaceAndPath("flowermap", "dynamic_map");
            minecraft.getTextureManager().register(textureLocation, texture);
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
        guiGraphics.blit(RenderType::guiTextured, textureLocation, (int)width - 256 - 5, 5, 0.0F, 0.0F, 256, 256, 256, 256);
        
        // Y LEVEL AND BIOME
        if (FlowerMapMain.config.dynamic) {
            guiGraphics.drawString(minecraft.font, String.format("Position (xzy): %d, %d, %d (player)", minecraft.player.getBlockX(), minecraft.player.getBlockZ(), minecraft.player.getBlockY()), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
        }
        else
        {
            guiGraphics.drawString(minecraft.font, String.format("Position (xzy): %d, %d, %d (fixed y)", minecraft.player.getBlockX(), minecraft.player.getBlockZ(), FlowerMapMain.config.fixedY), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
        }

        BlockPos.MutableBlockPos pos = minecraft.player.blockPosition().mutable();
        if (!FlowerMapMain.config.dynamic) pos.setY(FlowerMapMain.config.fixedY);

        Holder<Biome> biomeEntry = minecraft.player.level().getBiome(pos);
        MutableComponent biomeName = Component.translatable(Util.makeDescriptionId("biome", biomeEntry.unwrapKey().get().location()));
        guiGraphics.drawString(minecraft.font, Component.literal("Biome: ").append(biomeName), (int)width - 5 - 256, 256 + 5 + 5, 0xffffffff);

        // POSSIBLE FLOWERS
        Component desc = Component.literal("Possible flowers at this location:");
        guiGraphics.drawString(minecraft.font, desc, (int)width - 256 - 5, 256 + 5 + 5 + 24, 0xffffffff);
        
        renderPossibleFlowerNamesAt(pos, rand_text, (int)width, guiGraphics);
        
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
        guiGraphics.blit(RenderType::guiTextured, pointerLocation, -8, -9, 0.0F, 0.0F, 16, 16, 16, 16);
        
        guiGraphics.flush();
        guiGraphics.pose().popPose();
        RenderSystem.restoreProjectionMatrix();
        matrixStack.popMatrix();
        Profiler.get().pop();
    }
}
