package de.draradech.flowermap;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderSet;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseThresholdProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;


public class FlowerMapRenderer
{
    final RandomSource rand_render = RandomSource.create();
    final RandomSource rand_text = RandomSource.create();
    final Minecraft minecraft;

    DynamicTexture texture = null;
    Identifier textureLocation;
    Identifier pointerLocation;

    Map<Block, Integer> colorMap = new LinkedHashMap<>();
    Map<Block, Integer> errorMap = new LinkedHashMap<>();
    Map<Biome, List<ConfiguredFeature<?,?>>> biomeFeatureCache = new LinkedHashMap<>();
    Thread renderThread;
    boolean textureRendering;
    
    RegistryLookup<Biome> vanillaBiomes = null;
    ArrayList<ResourceKey<ConfiguredFeature<?, ?>>> canSpawnFromBonemealList = new ArrayList<>(10);

    NormalNoise noise;
    
    int color(int r, int g, int b)
    {
        return 0xff << 24 | r << 16 | g << 8 | b;
    }
    
    public FlowerMapRenderer()
    {
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

        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_DEFAULT);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_FLOWER_FOREST);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_SWAMP);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_PLAIN);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_MEADOW);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_CHERRY);
        canSpawnFromBonemealList.add(VegetationFeatures.WILDFLOWER);
        canSpawnFromBonemealList.add(VegetationFeatures.FLOWER_PALE_GARDEN);

        noise = NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(2345L)), new NoiseParameters(0, 1.0));

        renderThread = new Thread(this::renderTexture);
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
        // The client-side biomes don't know their generation settings.
        // As a workaround, assume biomes haven't been modified via datapack and get the biome from the builtin registry.
        HolderLookup.Provider vanillaRegistries = VanillaRegistries.createLookup();
        vanillaBiomes = vanillaRegistries.lookupOrThrow(Registries.BIOME);
    }

    List<ConfiguredFeature<?,?>> getBonemealFeatures(Biome biome)
    {
        // the biomes created from the builtin registry are missing tags
        // with the new can_spawn_from_bonemeal tag for vegetation features we can no longer just call getFlowerFeatures (now called getBonemealFeatures)
        // iterate through the feature stream and collect matching features manually
        if (!biomeFeatureCache.containsKey(biome))
        {
            biomeFeatureCache.put(biome,
                    biome.getGenerationSettings().features().stream()
                            .flatMap(HolderSet::stream)
                            .flatMap(feature -> ((PlacedFeature)feature.value()).getFeatures())
                            .filter(feature -> {
                                Optional<ResourceKey<ConfiguredFeature<?, ?>>> key = feature.unwrapKey();
                                return key.isPresent() && canSpawnFromBonemealList.contains(key.get());})
                            .map(Holder::value)
                            .collect(ImmutableList.toImmutableList()));
        }

        return biomeFeatureCache.get(biome);
    }

    Block getRandomFlowerAt(Level level, BlockPos pos, RandomSource randomSource)
    {
        if(vanillaBiomes == null)
        {
            loadVanillaBiomes();
        }
        
        Holder<Biome> biomeEntry = level.getBiome(pos);
        ResourceKey<Biome> biomeKey = biomeEntry.unwrapKey().orElse(null);
        if (biomeKey == null)
        {
            // couldn't get biome key
            return Blocks.RED_WOOL;
        }
        else
        {
            Reference<Biome> vanillaBiomeRef = vanillaBiomes.get(biomeKey).orElse(null);
            if (vanillaBiomeRef == null)
            {
                // couldn't get vanilla biome
                return Blocks.YELLOW_WOOL;
            }
            else
            {
                Biome vanillaBiome = vanillaBiomeRef.value();
                List<ConfiguredFeature<?, ?>> list = getBonemealFeatures(vanillaBiome);
                if (list.isEmpty())
                {
                    // no flowers can grow here
                    return Blocks.GRAY_WOOL;
                }
                else
                {
                    // get a random flower from a random state provider at this position
                    int k = randomSource.nextInt(list.size());
                    SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) list.get(k).config();
                    return flowerMap.toPlace().getState(null, rand_render, pos).getBlock();
                }
            }
        }
    }
    
    public void renderPossibleFlowerName(GuiGraphicsExtractor guiGraphics, Block block, int w, int i)
    {
        Component flowerName = getFlowerName(block);
        guiGraphics.text(minecraft.font, flowerName, w - 5 - 256 + 12, 256 + 5 + 5 + 12 * (i + 3), 0xffffffff);
    }
    
    public void renderPossibleFlowerNamesAt(Level level, BlockPos pos, int w, GuiGraphicsExtractor gui)
    {
        if(vanillaBiomes == null)
        {
            loadVanillaBiomes();
        }

        int k = 0;
        Holder<Biome> biomeEntry = level.getBiome(pos);
        ResourceKey<Biome> biomeKey = biomeEntry.unwrapKey().orElse(null);
        if (biomeKey == null)
        {
            // couldn't get biome key
            renderPossibleFlowerName(gui, Blocks.RED_WOOL, w, k++);
        }
        else
        {
            Reference<Biome> vanillaBiomeRef = vanillaBiomes.get(biomeKey).orElse(null);
            if (vanillaBiomeRef == null)
            {
                // couldn't get vanilla biome
                renderPossibleFlowerName(gui, Blocks.YELLOW_WOOL, w, k++);
            }
            else
            {
                Biome vanillaBiome = vanillaBiomeRef.value();
                List<ConfiguredFeature<?, ?>> list = getBonemealFeatures(vanillaBiome);
                if (list.isEmpty()) {
                    // no flowers can grow here
                    renderPossibleFlowerName(gui, Blocks.GRAY_WOOL, w, k++);
                }
                else
                {
                    // go through the list of state providers, with custom handling by provider type
                    for(ConfiguredFeature<?, ?> feature : list)
                    {
                        SimpleBlockConfiguration flowerMap = (SimpleBlockConfiguration) feature.config();
                        BlockStateProvider bsp = flowerMap.toPlace();
                        if (  (bsp instanceof NoiseProvider)
                           || (bsp instanceof SimpleStateProvider)
                           )
                        {
                            // these have no randomness, so we can just query them directly
                            renderPossibleFlowerName(gui, bsp.getState(null, rand_text, pos).getBlock(), w, k++);
                        }
                        else if (bsp instanceof WeightedStateProvider)
                        {
                            // this can be default, cherry or wild flower overlay
                            Block b = bsp.getState(null, rand_text, pos).getBlock();
                            if (b == Blocks.WILDFLOWERS)
                            {
                                // wild flower overlay (only wildflowers)
                                renderPossibleFlowerName(gui, Blocks.WILDFLOWERS, w, k++);
                            }
                            else if (b == Blocks.PINK_PETALS)
                            {
                                // cherry blossom (only pink petals)
                                renderPossibleFlowerName(gui, Blocks.PINK_PETALS, w, k++);
                            }
                            else
                            {
                                // assume default (poppies and dandelions)
                                renderPossibleFlowerName(gui, Blocks.POPPY, w, k++);
                                renderPossibleFlowerName(gui, Blocks.DANDELION, w, k++);
                            }
                        }
                        else if (bsp instanceof NoiseThresholdProvider)
                        {
                            // plains and dripstone caves have 2 different distributions depending on noise value
                            double t = noise.getValue((double)pos.getX() * 0.005F, (double)pos.getY() * 0.005F, (double)pos.getZ() * 0.005F);
                            if (t < (double)-0.8F)
                            {
                                renderPossibleFlowerName(gui, Blocks.ORANGE_TULIP, w, k++);
                                renderPossibleFlowerName(gui, Blocks.RED_TULIP, w, k++);
                                renderPossibleFlowerName(gui, Blocks.PINK_TULIP, w, k++);
                                renderPossibleFlowerName(gui, Blocks.WHITE_TULIP, w, k++);
                            }
                            else
                            {
                                renderPossibleFlowerName(gui, Blocks.DANDELION, w, k++);
                                renderPossibleFlowerName(gui, Blocks.POPPY, w, k++);
                                renderPossibleFlowerName(gui, Blocks.AZURE_BLUET, w, k++);
                                renderPossibleFlowerName(gui, Blocks.OXEYE_DAISY, w, k++);
                                renderPossibleFlowerName(gui, Blocks.CORNFLOWER, w, k++);
                            }
                        }
                    }
                }
            }
        }
    }
    
    void renderTexture()
    {
        for(;;)
        {
            if(textureRendering == true)
            {
                LocalPlayer player = minecraft.player;
                if (player != null) {
                    Level level = player.level();
                    int px = player.getBlockX();
                    int py = player.getBlockY();
                    int pz = player.getBlockZ();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, FlowerMapMain.config.fixedY, 0);
                    if (FlowerMapMain.config.mode == FlowerMapConfig.EMode.PLAYER) pos.setY(py);

                    for (int x = 0; x < 256; ++x) {
                        for (int z = 0; z < 256; ++z) {
                            pos.setX(px + x - 128);
                            if (FlowerMapMain.config.mode == FlowerMapConfig.EMode.SURFACE) {
                                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, px + x - 128, pz + z - 128);
                                pos.setY(y);
                            }
                            pos.setZ(pz + z - 128);
                            Block block = getRandomFlowerAt(level, pos, rand_render);
                            texture.getPixels().setPixel(x, z, colorMap.getOrDefault(block, errorMap.getOrDefault(block, errorMap.get(Blocks.GREEN_WOOL))));
                        }
                    }
                    textureRendering = false;
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
            else
            {
                try { Thread.sleep(1); } catch (InterruptedException e) {}
            }
        }
    }
    
    public void render(GuiGraphicsExtractor guiGraphics)
    {
        if (!FlowerMapMain.config.enabled) return;
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        Level level = player.level();
        
        Profiler.get().push(FlowerMapMain.MODID);
        
        // SETUP
        if (texture == null)
        {
            texture = new DynamicTexture((String)null, 256, 256, false);
            pointerLocation = Identifier.fromNamespaceAndPath(FlowerMapMain.MODID, "pointer.png");
            textureLocation = Identifier.fromNamespaceAndPath(FlowerMapMain.MODID, "dynamic_map");
            minecraft.getTextureManager().register(textureLocation, texture);
        }

        Window window = minecraft.getWindow();
        float width = window.getWidth() / FlowerMapMain.config.scale;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().identity();
        guiGraphics.pose().scale(FlowerMapMain.config.scale / window.getGuiScale());
        
        // RENDER THREAD CONTROL, TEXTURE UPLOAD
        if (textureRendering == false)
        {
            Profiler.get().push("upload");
            texture.upload();
            Profiler.get().pop();
            textureRendering = true;
        }
        
        // FLOWER GRADIENT TEXTURE
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, textureLocation, (int)width - 256 - 5, 5, 0.0F, 0.0F, 256, 256, 256, 256);
        
        // Y LEVEL AND BIOME
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable();

        if (FlowerMapMain.config.mode == FlowerMapConfig.EMode.PLAYER)
        {
            guiGraphics.text(minecraft.font, String.format("Position (xzy): %d, %d, %d (player)", player.getBlockX(), player.getBlockZ(), player.getBlockY()), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
        }
        else if (FlowerMapMain.config.mode == FlowerMapConfig.EMode.FIXED)
        {
            guiGraphics.text(minecraft.font, String.format("Position (xzy): %d, %d, %d (fixed y)", player.getBlockX(), player.getBlockZ(), FlowerMapMain.config.fixedY), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
            pos.setY(FlowerMapMain.config.fixedY);
        }
        else if (FlowerMapMain.config.mode == FlowerMapConfig.EMode.SURFACE)
        {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, player.getBlockX(), player.getBlockZ());
            guiGraphics.text(minecraft.font, String.format("Position (xzy): %d, %d, %d (surface)", player.getBlockX(), player.getBlockZ(), y), (int)width - 256 - 5, 256 + 5 + 5 + 12, 0xffffffff);
            pos.setY(y);
        }

        Holder<Biome> biomeEntry = level.getBiome(pos);
        MutableComponent biomeName = Component.translatable(Util.makeDescriptionId("biome", biomeEntry.unwrapKey().get().identifier()));
        guiGraphics.text(minecraft.font, Component.literal("Biome: ").append(biomeName), (int)width - 5 - 256, 256 + 5 + 5, 0xffffffff);

        // POSSIBLE FLOWERS
        Component desc = Component.literal("Possible flowers at this location:");
        guiGraphics.text(minecraft.font, desc, (int)width - 256 - 5, 256 + 5 + 5 + 24, 0xffffffff);
        
        renderPossibleFlowerNamesAt(level, pos, (int)width, guiGraphics);
        
        // LEGEND
        if (FlowerMapMain.config.legend)
        {
            Profiler.get().push("legend");
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(FlowerMapMain.config.legendScale / FlowerMapMain.config.scale);
            int i = 0;
            for (Map.Entry<Block, Integer> e : colorMap.entrySet())
            {
                guiGraphics.fill(5, 5 + i * 12, 15, 15 + i * 12, e.getValue());
                guiGraphics.text(minecraft.font, getFlowerName(e.getKey()), 17, 7 + i++ * 12, 0xffffffff);
            }
            guiGraphics.pose().popMatrix();
            Profiler.get().pop();
        }
        
        // PLAYER POSITION MARKER
        guiGraphics.pose().translate((int)width - 256 - 5 + 128, 5 + 128);
        guiGraphics.pose().rotate((float)(Math.PI / 180.0) * (player.getYRot() + 180.0f));
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, pointerLocation, -8, -9, 0.0F, 0.0F, 16, 16, 16, 16);
        
        guiGraphics.pose().popMatrix();
        Profiler.get().pop();
    }
}
