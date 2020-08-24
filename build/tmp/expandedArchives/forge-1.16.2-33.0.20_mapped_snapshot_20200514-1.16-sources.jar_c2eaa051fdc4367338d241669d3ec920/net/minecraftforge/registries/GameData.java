/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.registries;

import com.google.common.collect.*;
import com.mojang.serialization.Lifecycle;

import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.ai.brain.schedule.Activity;
import net.minecraft.entity.ai.brain.schedule.Schedule;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.item.PaintingType;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.network.datasync.IDataSerializer;
import net.minecraft.particles.ParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.Potion;
import net.minecraft.state.StateContainer;
import net.minecraft.stats.StatType;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.registry.DefaultedRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.DebugChunkGenerator;
import net.minecraft.world.gen.blockplacer.BlockPlacerType;
import net.minecraft.world.gen.blockstateprovider.BlockStateProviderType;
import net.minecraft.world.gen.carver.WorldCarver;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.foliageplacer.FoliagePlacerType;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;
import net.minecraft.world.gen.treedecorator.TreeDecoratorType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.RegistryEvent.MissingMappings;
import net.minecraftforge.fml.LifecycleEventProvider;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.EnhancedRuntimeException;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import net.minecraftforge.fml.event.lifecycle.FMLModIdMappingEvent;
import net.minecraftforge.fml.loading.AdvancedLogMessageAdapter;

import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraftforge.registries.ForgeRegistry.REGISTRIES;
import static net.minecraftforge.registries.ForgeRegistries.Keys.*;

import net.minecraft.block.AbstractBlock;
import net.minecraftforge.fml.common.EnhancedRuntimeException.WrappedPrintStream;

/**
 * INTERNAL ONLY
 * MODDERS SHOULD HAVE NO REASON TO USE THIS CLASS
 */
public class GameData
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_VARINT = Integer.MAX_VALUE - 1; //We were told it is their intention to have everything in a reg be unlimited, so assume that until we find cases where it isnt.

    private static final ResourceLocation BLOCK_TO_ITEM = new ResourceLocation("minecraft:blocktoitemmap");
    private static final ResourceLocation BLOCKSTATE_TO_ID = new ResourceLocation("minecraft:blockstatetoid");
    private static final ResourceLocation SERIALIZER_TO_ENTRY = new ResourceLocation("forge:serializer_to_entry");
    private static final ResourceLocation STRUCTURES = new ResourceLocation("minecraft:structures");

    private static boolean hasInit = false;
    private static final boolean DISABLE_VANILLA_REGISTRIES = Boolean.parseBoolean(System.getProperty("forge.disableVanillaGameData", "false")); // Use for unit tests/debugging
    private static final BiConsumer<ResourceLocation, ForgeRegistry<?>> LOCK_VANILLA = (name, reg) -> reg.slaves.values().stream().filter(o -> o instanceof ILockableRegistry).forEach(o -> ((ILockableRegistry)o).lock());

    static {
        init();
    }

    public static void init()
    {
        if (DISABLE_VANILLA_REGISTRIES)
        {
            LOGGER.warn(REGISTRIES, "DISABLING VANILLA REGISTRY CREATION AS PER SYSTEM VARIABLE SETTING! forge.disableVanillaGameData");
            return;
        }
        if (hasInit)
            return;
        hasInit = true;

        // Game objects
        makeRegistry(BLOCKS, Block.class, "air").addCallback(BlockCallbacks.INSTANCE).legacyName("blocks").create();
        makeRegistry(FLUIDS, Fluid.class, "empty").create();
        makeRegistry(ITEMS, Item.class, "air").addCallback(ItemCallbacks.INSTANCE).legacyName("items").create();
        makeRegistry(EFFECTS, Effect.class ).legacyName("potions").create();
        //makeRegistry(BIOMES, Biome.class).legacyName("biomes").create();
        makeRegistry(SOUND_EVENTS, SoundEvent.class).legacyName("soundevents").create();
        makeRegistry(POTIONS, Potion.class, "empty").legacyName("potiontypes").create();
        makeRegistry(ENCHANTMENTS, Enchantment.class).legacyName("enchantments").create();
        makeRegistry(ENTITY_TYPES, c(EntityType.class), "pig").legacyName("entities").create();
        makeRegistry(TILE_ENTITY_TYPES, c(TileEntityType.class)).disableSaving().legacyName("tileentities").create();
        makeRegistry(PARTICLE_TYPES, c(ParticleType.class)).disableSaving().create();
        makeRegistry(CONTAINER_TYPES, c(ContainerType.class)).disableSaving().create();
        makeRegistry(PAINTING_TYPES, PaintingType.class, "kebab").create();
        makeRegistry(RECIPE_SERIALIZERS, c(IRecipeSerializer.class)).disableSaving().create();
        makeRegistry(ATTRIBUTES, Attribute.class).onValidate(AttributeCallbacks.INSTANCE).disableSaving().disableSync().create();
        makeRegistry(STAT_TYPES, c(StatType.class)).create();

        // Villagers
        makeRegistry(VILLAGER_PROFESSIONS, VillagerProfession.class, "none").create();
        makeRegistry(POI_TYPES, PointOfInterestType.class, "unemployed").disableSync().create();
        makeRegistry(MEMORY_MODULE_TYPES, c(MemoryModuleType.class), "dummy").disableSync().create();
        makeRegistry(SENSOR_TYPES, c(SensorType.class), "dummy").disableSaving().disableSync().create();
        makeRegistry(SCHEDULES, Schedule.class).disableSaving().disableSync().create();
        makeRegistry(ACTIVITIES, Activity.class).disableSaving().disableSync().create();

        // Worldgen
        makeRegistry(WORLD_CARVERS, c(WorldCarver.class)).disableSaving().disableSync().create();
        makeRegistry(SURFACE_BUILDERS, c(SurfaceBuilder.class)).disableSaving().disableSync().create();
        makeRegistry(FEATURES, c(Feature.class)).addCallback(FeatureCallbacks.INSTANCE).disableSaving().create();
        makeRegistry(DECORATORS, c(Placement.class)).disableSaving().disableSync().create();
        makeRegistry(CHUNK_STATUS, ChunkStatus.class, "empty").disableSaving().disableSync().create();
        makeRegistry(STRUCTURE_FEATURES, c(Structure.class)).disableSaving().disableSync().create();
        makeRegistry(BLOCK_STATE_PROVIDER_TYPES, c(BlockStateProviderType.class)).disableSaving().disableSync().create();
        makeRegistry(BLOCK_PLACER_TYPES, c(BlockPlacerType.class)).disableSaving().disableSync().create();
        makeRegistry(FOLIAGE_PLACER_TYPES, c(FoliagePlacerType.class)).disableSaving().disableSync().create();
        makeRegistry(TREE_DECORATOR_TYPES, c(TreeDecoratorType.class)).disableSaving().disableSync().create();

        // Custom forge registries
        makeRegistry(DATA_SERIALIZERS, DataSerializerEntry.class, 256 /*vanilla space*/, MAX_VARINT).disableSaving().disableOverrides().addCallback(SerializerCallbacks.INSTANCE).create();
        makeRegistry(LOOT_MODIFIER_SERIALIZERS, c(GlobalLootModifierSerializer.class)).disableSaving().disableSync().create();
    }
    @SuppressWarnings("unchecked") //Ugly hack to let us pass in a typed Class object. Remove when we remove type specific references.
    private static <T> Class<T> c(Class<?> cls) { return (Class<T>)cls; }

    private static <T extends IForgeRegistryEntry<T>> RegistryBuilder<T> makeRegistry(RegistryKey<? extends Registry<T>> key, Class<T> type)
    {
        return new RegistryBuilder<T>().setName(key.func_240901_a_()).setType(type).setMaxID(MAX_VARINT).addCallback(new NamespacedWrapper.Factory<T>());
    }
    private static <T extends IForgeRegistryEntry<T>> RegistryBuilder<T> makeRegistry(RegistryKey<? extends Registry<T>> key, Class<T> type, int min, int max)
    {
        return new RegistryBuilder<T>().setName(key.func_240901_a_()).setType(type).setIDRange(min, max).addCallback(new NamespacedWrapper.Factory<T>());
    }
    private static <T extends IForgeRegistryEntry<T>> RegistryBuilder<T> makeRegistry(RegistryKey<? extends Registry<T>> key, Class<T> type, String _default)
    {
        return new RegistryBuilder<T>().setName(key.func_240901_a_()).setType(type).setMaxID(MAX_VARINT).addCallback(new NamespacedDefaultedWrapper.Factory<T>()).setDefaultKey(new ResourceLocation(_default));
    }

    public static <T extends IForgeRegistryEntry<T>> SimpleRegistry<T> getWrapper(RegistryKey<? extends Registry<T>> key, Lifecycle lifecycle)
    {
        IForgeRegistry<T> reg = RegistryManager.ACTIVE.getRegistry(key);
        Validate.notNull(reg, "Attempted to get vanilla wrapper for unknown registry: " + key.toString());
        @SuppressWarnings("unchecked")
        SimpleRegistry<T> ret = reg.getSlaveMap(NamespacedWrapper.Factory.ID, NamespacedWrapper.class);
        Validate.notNull(ret, "Attempted to get vanilla wrapper for registry created incorrectly: " + key.toString());
        return ret;
    }

    public static <T extends IForgeRegistryEntry<T>> DefaultedRegistry<T> getWrapper(RegistryKey<? extends Registry<T>> key, Lifecycle lifecycle, String defKey)
    {
        IForgeRegistry<T> reg = RegistryManager.ACTIVE.getRegistry(key);
        Validate.notNull(reg, "Attempted to get vanilla wrapper for unknown registry: " + key.toString());
        @SuppressWarnings("unchecked")
        DefaultedRegistry<T> ret = reg.getSlaveMap(NamespacedDefaultedWrapper.Factory.ID, NamespacedDefaultedWrapper.class);
        Validate.notNull(ret, "Attempted to get vanilla wrapper for registry created incorrectly: " + key.toString());
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static Map<Block,Item> getBlockItemMap()
    {
        return RegistryManager.ACTIVE.getRegistry(Item.class).getSlaveMap(BLOCK_TO_ITEM, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static ObjectIntIdentityMap<BlockState> getBlockStateIDMap()
    {
        return RegistryManager.ACTIVE.getRegistry(Block.class).getSlaveMap(BLOCKSTATE_TO_ID, ObjectIntIdentityMap.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<IDataSerializer<?>, DataSerializerEntry> getSerializerMap()
    {
        return RegistryManager.ACTIVE.getRegistry(DataSerializerEntry.class).getSlaveMap(SERIALIZER_TO_ENTRY, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static BiMap<String, Structure<?>> getStructureMap()
    {
        return (BiMap<String, Structure<?>>) RegistryManager.ACTIVE.getRegistry(Feature.class).getSlaveMap(STRUCTURES, BiMap.class);
    }

    public static <K extends IForgeRegistryEntry<K>> K register_impl(K value)
    {
        Validate.notNull(value, "Attempted to register a null object");
        Validate.notNull(value.getRegistryName(), String.format("Attempt to register object without having set a registry name %s (type %s)", value, value.getClass().getName()));
        final IForgeRegistry<K> registry = RegistryManager.ACTIVE.getRegistry(value.getRegistryType());
        Validate.notNull(registry, "Attempted to registry object without creating registry first: " + value.getRegistryType().getName());
        registry.register(value);
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void vanillaSnapshot()
    {
        LOGGER.debug(REGISTRIES, "Creating vanilla freeze snapshot");
        for (Map.Entry<ResourceLocation, ForgeRegistry<? extends IForgeRegistryEntry<?>>> r : RegistryManager.ACTIVE.registries.entrySet())
        {
            final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(r.getKey());
            loadRegistry(r.getKey(), RegistryManager.ACTIVE, RegistryManager.VANILLA, clazz, true);
        }
        RegistryManager.VANILLA.registries.forEach((name, reg) ->
        {
            reg.validateContent(name);
            reg.freeze();
        });
        RegistryManager.VANILLA.registries.forEach(LOCK_VANILLA);
        RegistryManager.ACTIVE.registries.forEach(LOCK_VANILLA);
        LOGGER.debug(REGISTRIES, "Vanilla freeze snapshot created");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void freezeData()
    {
        LOGGER.debug(REGISTRIES, "Freezing registries");
        for (Map.Entry<ResourceLocation, ForgeRegistry<? extends IForgeRegistryEntry<?>>> r : RegistryManager.ACTIVE.registries.entrySet())
        {
            final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(r.getKey());
            loadRegistry(r.getKey(), RegistryManager.ACTIVE, RegistryManager.FROZEN, clazz, true);
        }
        RegistryManager.FROZEN.registries.forEach((name, reg) ->
        {
            reg.validateContent(name);
            reg.freeze();
        });
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> {
            reg.freeze();
            reg.bake();
            reg.dump(name);
        });

        // the id mapping is finalized, no ids actually changed but this is a good place to tell everyone to 'bake' their stuff.
        fireRemapEvent(ImmutableMap.of(), true);

        LOGGER.debug(REGISTRIES, "All registries frozen");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void revertToFrozen()
    {
        if (RegistryManager.FROZEN.registries.isEmpty())
        {
            LOGGER.warn(REGISTRIES, "Can't revert to frozen GameData state without freezing first.");
            return;
        }
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> reg.resetDelegates());

        LOGGER.debug(REGISTRIES, "Reverting to frozen data state.");
        for (Map.Entry<ResourceLocation, ForgeRegistry<? extends IForgeRegistryEntry<?>>> r : RegistryManager.ACTIVE.registries.entrySet())
        {
            final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(r.getKey());
            loadRegistry(r.getKey(), RegistryManager.FROZEN, RegistryManager.ACTIVE, clazz, true);
        }
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> reg.bake());
        // the id mapping has reverted, fire remap events for those that care about id changes
        fireRemapEvent(ImmutableMap.of(), true);

        ObjectHolderRegistry.applyObjectHolders();
        // the id mapping has reverted, ensure we sync up the object holders
        LOGGER.debug(REGISTRIES, "Frozen state restored.");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void revert(RegistryManager state, ResourceLocation registry, boolean lock)
    {
        LOGGER.debug(REGISTRIES, "Reverting {} to {}", registry, state.getName());
        final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(registry);
        loadRegistry(registry, state, RegistryManager.ACTIVE, clazz, lock);
        LOGGER.debug(REGISTRIES, "Reverting complete");
    }

    //Lets us clear the map so we can rebuild it.
    private static class ClearableObjectIntIdentityMap<I> extends ObjectIntIdentityMap<I>
    {
        void clear()
        {
            this.identityMap.clear();
            this.objectList.clear();
            this.nextId = 0;
        }

        void remove(I key)
        {
            Integer prev = this.identityMap.remove(key);
            if (prev != null)
            {
                this.objectList.set(prev, null);
            }
        }
    }

    private static class BlockCallbacks implements IForgeRegistry.AddCallback<Block>, IForgeRegistry.ClearCallback<Block>, IForgeRegistry.BakeCallback<Block>, IForgeRegistry.CreateCallback<Block>, IForgeRegistry.DummyFactory<Block>
    {
        static final BlockCallbacks INSTANCE = new BlockCallbacks();

        @Override
        public void onAdd(IForgeRegistryInternal<Block> owner, RegistryManager stage, int id, Block block, @Nullable Block oldBlock)
        {
            if (oldBlock != null)
            {
                StateContainer<Block, BlockState> oldContainer = oldBlock.getStateContainer();
                StateContainer<Block, BlockState> newContainer = block.getStateContainer();

                // Test vanilla blockstates, if the number matches, make sure they also match in their string representations
                if (block.getRegistryName().getNamespace().equals("minecraft") && !oldContainer.getProperties().equals(newContainer.getProperties()))
                {
                    String oldSequence = oldContainer.getProperties().stream()
                            .map(s -> String.format("%s={%s}", s.getName(),
                                    s.getAllowedValues().stream().map(Object::toString).collect(Collectors.joining( "," ))))
                            .collect(Collectors.joining(";"));
                    String newSequence = newContainer.getProperties().stream()
                            .map(s -> String.format("%s={%s}", s.getName(),
                                    s.getAllowedValues().stream().map(Object::toString).collect(Collectors.joining( "," ))))
                            .collect(Collectors.joining(";"));

                    LOGGER.error(REGISTRIES,()-> new AdvancedLogMessageAdapter(sb-> {
                        sb.append("Registry replacements for vanilla block '").append(block.getRegistryName()).
                                append("' must not change the number or order of blockstates.\n");
                        sb.append("\tOld: ").append(oldSequence).append('\n');
                        sb.append("\tNew: ").append(newSequence);
                    }));
                    throw new RuntimeException("Invalid vanilla replacement. See log for details.");
                }
            }
        }

        @Override
        public void onClear(IForgeRegistryInternal<Block> owner, RegistryManager stage)
        {
            owner.getSlaveMap(BLOCKSTATE_TO_ID, ClearableObjectIntIdentityMap.class).clear();
        }

        @Override
        public void onCreate(IForgeRegistryInternal<Block> owner, RegistryManager stage)
        {
            final ClearableObjectIntIdentityMap<BlockState> idMap = new ClearableObjectIntIdentityMap<BlockState>()
            {
                @Override
                public int getId(BlockState key)
                {
                    Integer integer = (Integer)this.identityMap.get(key);
                    // There are some cases where this map is queried to serialize a state that is valid,
                    //but somehow not in this list, so attempt to get real metadata. Doing this hear saves us 7 patches
                    //if (integer == null && key != null)
                    //    integer = this.identityMap.get(key.getBlock().getStateFromMeta(key.getBlock().getMetaFromState(key)));
                    return integer == null ? -1 : integer.intValue();
                }
            };
            owner.setSlaveMap(BLOCKSTATE_TO_ID, idMap);
            owner.setSlaveMap(BLOCK_TO_ITEM, Maps.newHashMap());
        }

        @Override
        public Block createDummy(ResourceLocation key)
        {
            Block ret = new BlockDummyAir(AbstractBlock.Properties.create(Material.AIR));
            GameData.forceRegistryName(ret, key);
            return ret;
        }

        @Override
        public void onBake(IForgeRegistryInternal<Block> owner, RegistryManager stage)
        {
            @SuppressWarnings("unchecked")
            ClearableObjectIntIdentityMap<BlockState> blockstateMap = owner.getSlaveMap(BLOCKSTATE_TO_ID, ClearableObjectIntIdentityMap.class);

            for (Block block : owner)
            {
                for (BlockState state : block.getStateContainer().getValidStates())
                {
                    blockstateMap.add(state);
                    state.cacheState();
                }

                block.getLootTable();
            }
            DebugChunkGenerator.initValidStates();
        }

        private static class BlockDummyAir extends AirBlock //A named class so DummyBlockReplacementTest can detect if its a dummy
        {
            private BlockDummyAir(AbstractBlock.Properties properties)
            {
                super(properties);
            }

            @Override
            public String getTranslationKey()
            {
                return "block.minecraft.air";
            }
        }
    }

    private static class ItemCallbacks implements IForgeRegistry.AddCallback<Item>, IForgeRegistry.ClearCallback<Item>, IForgeRegistry.CreateCallback<Item>
    {
        static final ItemCallbacks INSTANCE = new ItemCallbacks();

        @Override
        public void onAdd(IForgeRegistryInternal<Item> owner, RegistryManager stage, int id, Item item, @Nullable Item oldItem)
        {
            if (oldItem instanceof BlockItem)
            {
                @SuppressWarnings("unchecked")
                Map<Block, Item> blockToItem = owner.getSlaveMap(BLOCK_TO_ITEM, Map.class);
                ((BlockItem)oldItem).removeFromBlockToItemMap(blockToItem, item);
            }
            if (item instanceof BlockItem)
            {
                @SuppressWarnings("unchecked")
                Map<Block, Item> blockToItem = owner.getSlaveMap(BLOCK_TO_ITEM, Map.class);
                ((BlockItem)item).addToBlockToItemMap(blockToItem, item);
            }
        }

        @Override
        public void onClear(IForgeRegistryInternal<Item> owner, RegistryManager stage)
        {
            owner.getSlaveMap(BLOCK_TO_ITEM, Map.class).clear();
        }

        @Override
        public void onCreate(IForgeRegistryInternal<Item> owner, RegistryManager stage)
        {
            // We share the blockItem map between items and blocks registries
            Map<?, ?> map = stage.getRegistry(BLOCKS).getSlaveMap(BLOCK_TO_ITEM, Map.class);
            owner.setSlaveMap(BLOCK_TO_ITEM, map);
        }
    }

    private static class AttributeCallbacks implements IForgeRegistry.ValidateCallback<Attribute> {

        static final AttributeCallbacks INSTANCE = new AttributeCallbacks();

        @Override
        public void onValidate(IForgeRegistryInternal<Attribute> owner, RegistryManager stage, int id, ResourceLocation key, Attribute obj)
        {
            GlobalEntityTypeAttributes.func_233834_a_();
        }
    }

    private static class SerializerCallbacks implements IForgeRegistry.AddCallback<DataSerializerEntry>, IForgeRegistry.ClearCallback<DataSerializerEntry>, IForgeRegistry.CreateCallback<DataSerializerEntry>
    {
        static final SerializerCallbacks INSTANCE = new SerializerCallbacks();

        @Override
        public void onAdd(IForgeRegistryInternal<DataSerializerEntry> owner, RegistryManager stage, int id, DataSerializerEntry entry, @Nullable DataSerializerEntry oldEntry)
        {
            @SuppressWarnings("unchecked")
            Map<IDataSerializer<?>, DataSerializerEntry> map = owner.getSlaveMap(SERIALIZER_TO_ENTRY, Map.class);
            if (oldEntry != null) map.remove(oldEntry.getSerializer());
            map.put(entry.getSerializer(), entry);
        }

        @Override
        public void onClear(IForgeRegistryInternal<DataSerializerEntry> owner, RegistryManager stage)
        {
            owner.getSlaveMap(SERIALIZER_TO_ENTRY, Map.class).clear();
        }

        @Override
        public void onCreate(IForgeRegistryInternal<DataSerializerEntry> owner, RegistryManager stage)
        {
            owner.setSlaveMap(SERIALIZER_TO_ENTRY, new IdentityHashMap<>());
        }
    }

    private static class FeatureCallbacks implements IForgeRegistry.ClearCallback<Feature<?>>, IForgeRegistry.CreateCallback<Feature<?>>
    {
        static final FeatureCallbacks INSTANCE = new FeatureCallbacks();

        @Override
        public void onClear(IForgeRegistryInternal<Feature<?>> owner, RegistryManager stage)
        {
            owner.getSlaveMap(STRUCTURES, BiMap.class).clear();
        }

        @Override
        public void onCreate(IForgeRegistryInternal<Feature<?>> owner, RegistryManager stage)
        {
            owner.setSlaveMap(STRUCTURES, HashBiMap.create());
        }
    }

    private static <T extends IForgeRegistryEntry<T>> void loadRegistry(final ResourceLocation registryName, final RegistryManager from, final RegistryManager to, final Class<T> regType, boolean freeze)
    {
        ForgeRegistry<T> fromRegistry = from.getRegistry(registryName);
        if (fromRegistry == null)
        {
            ForgeRegistry<T> toRegistry = to.getRegistry(registryName);
            if (toRegistry == null)
            {
                throw new EnhancedRuntimeException("Could not find registry to load: " + registryName){
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected void printStackTrace(WrappedPrintStream stream)
                    {
                        stream.println("Looking For: " + registryName);
                        stream.println("Found From:");
                        for (ResourceLocation name : from.registries.keySet())
                            stream.println("  " + name);
                        stream.println("Found To:");
                        for (ResourceLocation name : to.registries.keySet())
                            stream.println("  " + name);
                    }
                };
            }
            // We found it in to, so lets trust to's state...
            // This happens when connecting to a server that doesn't have this registry.
            // Such as a 1.8.0 Forge server with 1.8.8+ Forge.
            // We must however, re-fire the callbacks as some internal data may be corrupted {potions}
            //TODO: With my rework of how registries add callbacks are done.. I don't think this is necessary.
            //fire addCallback for each entry
        }
        else
        {
            ForgeRegistry<T> toRegistry = to.getRegistry(registryName, from);
            toRegistry.sync(registryName, fromRegistry);
            if (freeze)
                toRegistry.isFrozen = true;
        }
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Multimap<ResourceLocation, ResourceLocation> injectSnapshot(Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot, boolean injectFrozenData, boolean isLocalWorld)
    {
        LOGGER.info(REGISTRIES, "Injecting existing registry data into this {} instance", EffectiveSide.get());
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> reg.validateContent(name));
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> reg.dump(name));
        RegistryManager.ACTIVE.registries.forEach((name, reg) -> reg.resetDelegates());

        // Update legacy names
        snapshot = snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // FIXME Registries need dependency ordering, this makes sure blocks are done before items (for ItemCallbacks) but it's lazy as hell
                .collect(Collectors.toMap(e -> RegistryManager.ACTIVE.updateLegacyName(e.getKey()), Map.Entry::getValue, (k1, k2) -> k1, LinkedHashMap::new));

        if (isLocalWorld)
        {
            List<ResourceLocation> missingRegs = snapshot.keySet().stream().filter(name -> !RegistryManager.ACTIVE.registries.containsKey(name)).collect(Collectors.toList());
            if (missingRegs.size() > 0)
            {
                String header = "Forge Mod Loader detected missing/unknown registrie(s).\n\n" +
                        "There are " + missingRegs.size() + " missing registries in this save.\n" +
                        "If you continue the missing registries will get removed.\n" +
                        "This may cause issues, it is advised that you create a world backup before continuing.\n\n";

                StringBuilder text = new StringBuilder("Missing Registries:\n");

                for (ResourceLocation s : missingRegs)
                    text.append(s).append("\n");
            }
        }

        RegistryManager STAGING = new RegistryManager("STAGING");

        final Map<ResourceLocation, Map<ResourceLocation, Integer[]>> remaps = Maps.newHashMap();
        final LinkedHashMap<ResourceLocation, Map<ResourceLocation, Integer>> missing = Maps.newLinkedHashMap();
        // Load the snapshot into the "STAGING" registry
        snapshot.forEach((key, value) ->
        {
            final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(key);
            remaps.put(key, Maps.newLinkedHashMap());
            missing.put(key, Maps.newHashMap());
            loadPersistentDataToStagingRegistry(RegistryManager.ACTIVE, STAGING, remaps.get(key), missing.get(key), key, value, clazz);
        });

        snapshot.forEach((key, value) ->
        {
            value.dummied.forEach(dummy ->
            {
                Map<ResourceLocation, Integer> m = missing.get(key);
                ForgeRegistry<?> reg = STAGING.getRegistry(key);

                // Currently missing locally, we just inject and carry on
                if (m.containsKey(dummy))
                {
                    if (reg.markDummy(dummy, m.get(dummy)))
                        m.remove(dummy);
                }
                else if (isLocalWorld)
                {
                   LOGGER.debug(REGISTRIES,"Registry {}: Resuscitating dummy entry {}", key, dummy);
                }
                else
                {
                    // The server believes this is a dummy block identity, but we seem to have one locally. This is likely a conflict
                    // in mod setup - Mark this entry as a dummy
                    int id = reg.getID(dummy);
                    LOGGER.warn(REGISTRIES, "Registry {}: The ID {} @ {} is currently locally mapped - it will be replaced with a dummy for this session", dummy, key, id);
                    reg.markDummy(dummy, id);
                }
            });
        });

        int count = missing.values().stream().mapToInt(Map::size).sum();
        if (count > 0)
        {
            LOGGER.debug(REGISTRIES,"There are {} mappings missing - attempting a mod remap", count);
            Multimap<ResourceLocation, ResourceLocation> defaulted = ArrayListMultimap.create();
            Multimap<ResourceLocation, ResourceLocation> failed = ArrayListMultimap.create();

            missing.entrySet().stream().filter(e -> e.getValue().size() > 0).forEach(m ->
            {
                ResourceLocation name = m.getKey();
                ForgeRegistry<?> reg = STAGING.getRegistry(name);
                RegistryEvent.MissingMappings<?> event = reg.getMissingEvent(name, m.getValue());
                MinecraftForge.EVENT_BUS.post(event);

                List<MissingMappings.Mapping<?>> lst = event.getAllMappings().stream().filter(e -> e.getAction() == MissingMappings.Action.DEFAULT).sorted((a, b) -> a.toString().compareTo(b.toString())).collect(Collectors.toList());
                if (!lst.isEmpty())
                {
                    LOGGER.error(REGISTRIES,()->new AdvancedLogMessageAdapter(sb->{
                       sb.append("Unidentified mapping from registry ").append(name).append('\n');
                       lst.forEach(map->sb.append('\t').append(map.key).append(": ").append(map.id).append('\n'));
                    }));
                }
                event.getAllMappings().stream().filter(e -> e.getAction() == MissingMappings.Action.FAIL).forEach(fail -> failed.put(name, fail.key));

                final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(name);
                processMissing(clazz, name, STAGING, event, m.getValue(), remaps.get(name), defaulted.get(name), failed.get(name), !isLocalWorld);
            });

            if (!defaulted.isEmpty() && !isLocalWorld)
                return defaulted;

            if (!defaulted.isEmpty())
            {
                String header = "Forge Mod Loader detected missing registry entries.\n\n" +
                   "There are " + defaulted.size() + " missing entries in this save.\n" +
                   "If you continue the missing entries will get removed.\n" +
                   "A world backup will be automatically created in your saves directory.\n\n";

                StringBuilder buf = new StringBuilder();
                defaulted.asMap().forEach((name, entries) ->
                {
                    buf.append("Missing ").append(name).append(":\n");
                    entries.forEach(rl -> buf.append("    ").append(rl).append("\n"));
                    buf.append("\n");
                });
            }

            if (!defaulted.isEmpty())
            {
                if (isLocalWorld)
                    LOGGER.error(REGISTRIES, "There are unidentified mappings in this world - we are going to attempt to process anyway");
            }

        }

        if (injectFrozenData)
        {
            // If we're loading from disk, we can actually substitute air in the block map for anything that is otherwise "missing". This keeps the reference in the map, in case
            // the block comes back later
            missing.forEach((name, m) ->
            {
                ForgeRegistry<?> reg = STAGING.getRegistry(name);
                m.forEach((rl, id) -> reg.markDummy(rl, id));
            });


            // If we're loading up the world from disk, we want to add in the new data that might have been provisioned by mods
            // So we load it from the frozen persistent registry
            RegistryManager.ACTIVE.registries.forEach((name, reg) ->
            {
                final Class<? extends IForgeRegistryEntry> clazz = RegistryManager.ACTIVE.getSuperType(name);
                loadFrozenDataToStagingRegistry(STAGING, name, remaps.get(name), clazz);
            });
        }

        // Validate that all the STAGING data is good
        STAGING.registries.forEach((name, reg) -> reg.validateContent(name));

        // Load the STAGING registry into the ACTIVE registry
        //for (Map.Entry<ResourceLocation, IForgeRegistry<? extends IForgeRegistryEntry<?>>> r : RegistryManager.ACTIVE.registries.entrySet())
        RegistryManager.ACTIVE.registries.forEach((key, value) ->
        {
            final Class<? extends IForgeRegistryEntry> registrySuperType = RegistryManager.ACTIVE.getSuperType(key);
            loadRegistry(key, STAGING, RegistryManager.ACTIVE, registrySuperType, true);
        });

        RegistryManager.ACTIVE.registries.forEach((name, reg) -> {
            reg.bake();

            // Dump the active registry
            reg.dump(name);
        });

        // Tell mods that the ids have changed
        fireRemapEvent(remaps, false);

        // The id map changed, ensure we apply object holders
        ObjectHolderRegistry.applyObjectHolders();

        // Return an empty list, because we're good
        return ArrayListMultimap.create();
    }

    private static void fireRemapEvent(final Map<ResourceLocation, Map<ResourceLocation, Integer[]>> remaps, final boolean isFreezing) {
        StartupMessageManager.modLoaderConsumer().ifPresent(s->s.accept("Remapping mod data"));
        MinecraftForge.EVENT_BUS.post(new FMLModIdMappingEvent(remaps, isFreezing));
        StartupMessageManager.modLoaderConsumer().ifPresent(s->s.accept("Remap complete"));
    }

    //Has to be split because of generics, Yay!
    private static <T extends IForgeRegistryEntry<T>> void loadPersistentDataToStagingRegistry(RegistryManager pool, RegistryManager to, Map<ResourceLocation, Integer[]> remaps, Map<ResourceLocation, Integer> missing, ResourceLocation name, ForgeRegistry.Snapshot snap, Class<T> regType)
    {
        ForgeRegistry<T> active  = pool.getRegistry(name);
        if (active == null)
            return; // We've already asked the user if they wish to continue. So if the reg isnt found just assume the user knows and accepted it.
        ForgeRegistry<T> _new = to.getRegistry(name, RegistryManager.ACTIVE);
        snap.aliases.forEach(_new::addAlias);
        snap.blocked.forEach(_new::block);
        // Load current dummies BEFORE the snapshot is loaded so that add() will remove from the list.
        snap.dummied.forEach(_new::addDummy);
        _new.loadIds(snap.ids, snap.overrides, missing, remaps, active, name);
    }

    //Another bouncer for generic reasons
    @SuppressWarnings("unchecked")
    private static <T extends IForgeRegistryEntry<T>> void processMissing(Class<T> clazz, ResourceLocation name, RegistryManager STAGING, MissingMappings<?> e, Map<ResourceLocation, Integer> missing, Map<ResourceLocation, Integer[]> remaps, Collection<ResourceLocation> defaulted, Collection<ResourceLocation> failed, boolean injectNetworkDummies)
    {
        List<MissingMappings.Mapping<T>> mappings = ((MissingMappings<T>)e).getAllMappings();
        ForgeRegistry<T> active = RegistryManager.ACTIVE.getRegistry(name);
        ForgeRegistry<T> staging = STAGING.getRegistry(name);
        staging.processMissingEvent(name, active, mappings, missing, remaps, defaulted, failed, injectNetworkDummies);
    }

    private static <T extends IForgeRegistryEntry<T>> void loadFrozenDataToStagingRegistry(RegistryManager STAGING, ResourceLocation name, Map<ResourceLocation, Integer[]> remaps, Class<T> clazz)
    {
        ForgeRegistry<T> frozen = RegistryManager.FROZEN.getRegistry(name);
        ForgeRegistry<T> newRegistry = STAGING.getRegistry(name, RegistryManager.FROZEN);
        Map<ResourceLocation, Integer> _new = Maps.newHashMap();
        frozen.getKeys().stream().filter(key -> !newRegistry.containsKey(key)).forEach(key -> _new.put(key, frozen.getID(key)));
        newRegistry.loadIds(_new, frozen.getOverrideOwners(), Maps.newLinkedHashMap(), remaps, frozen, name);
    }

    public static void fireCreateRegistryEvents()
    {
        MinecraftForge.EVENT_BUS.post(new RegistryEvent.NewRegistry());
    }

    public static void fireCreateRegistryEvents(final LifecycleEventProvider lifecycleEventProvider, final Consumer<LifecycleEventProvider> eventDispatcher) {
        final RegistryEvent.NewRegistry newRegistryEvent = new RegistryEvent.NewRegistry();
        lifecycleEventProvider.setCustomEventSupplier(()->newRegistryEvent);
        eventDispatcher.accept(lifecycleEventProvider);
    }


    public static void fireRegistryEvents(Predicate<ResourceLocation> filter, final LifecycleEventProvider lifecycleEventProvider, final Consumer<LifecycleEventProvider> eventDispatcher)
    {
        List<ResourceLocation> keys = Lists.newArrayList(RegistryManager.ACTIVE.registries.keySet());
        keys.sort((o1, o2) -> String.valueOf(o1).compareToIgnoreCase(String.valueOf(o2)));

        //Move Blocks to first, and Items to second.
        keys.remove(BLOCKS.func_240901_a_());
        keys.remove(ITEMS.func_240901_a_());

        keys.add(0, BLOCKS.func_240901_a_());
        keys.add(1, ITEMS.func_240901_a_());
        for (int i = 0, keysSize = keys.size(); i < keysSize; i++) {
            final ResourceLocation rl = keys.get(i);
            if (!filter.test(rl)) continue;
            ForgeRegistry<?> reg = RegistryManager.ACTIVE.getRegistry(rl);
            reg.unfreeze();
            StartupMessageManager.modLoaderConsumer().ifPresent(s->s.accept("REGISTERING "+rl));
            final RegistryEvent.Register<?> registerEvent = reg.getRegisterEvent(rl);
            lifecycleEventProvider.setCustomEventSupplier(() -> registerEvent);
            lifecycleEventProvider.changeProgression(LifecycleEventProvider.LifecycleEvent.Progression.STAY);
            if (i==keysSize-1) lifecycleEventProvider.changeProgression(LifecycleEventProvider.LifecycleEvent.Progression.NEXT);
            eventDispatcher.accept(lifecycleEventProvider);
            reg.freeze();
            LOGGER.debug(REGISTRIES,"Applying holder lookups: {}", rl.toString());
            ObjectHolderRegistry.applyObjectHolders(rl::equals);
            LOGGER.debug(REGISTRIES,"Holder lookups applied: {}", rl.toString());
        }
    }

    /**
     * Check a name for a domain prefix, and if not present infer it from the
     * current active mod container.
     *
     * @param name          The name or resource location
     * @param warnOverrides If true, logs a warning if domain differs from that of
     *                      the currently currently active mod container
     *
     * @return The {@link ResourceLocation} with given or inferred domain
     */
    public static ResourceLocation checkPrefix(String name, boolean warnOverrides)
    {
        int index = name.lastIndexOf(':');
        String oldPrefix = index == -1 ? "" : name.substring(0, index).toLowerCase(Locale.ROOT);
        name = index == -1 ? name : name.substring(index + 1);
        String prefix = ModLoadingContext.get().getActiveNamespace();
        if (warnOverrides && !oldPrefix.equals(prefix) && oldPrefix.length() > 0)
        {
            LogManager.getLogger().info("Potentially Dangerous alternative prefix `{}` for name `{}`, expected `{}`. This could be a intended override, but in most cases indicates a broken mod.", oldPrefix, name, prefix);
            prefix = oldPrefix;
        }
        return new ResourceLocation(prefix, name);
    }

    private static Field regName;
    private static void forceRegistryName(IForgeRegistryEntry<?> entry, ResourceLocation name)
    {
        if (regName == null)
        {
            try
            {
                regName = ForgeRegistryEntry.class.getDeclaredField("registryName");
                regName.setAccessible(true);
            }
            catch (NoSuchFieldException | SecurityException e)
            {
                LOGGER.error(REGISTRIES, "Could not get `registryName` field from IForgeRegistryEntry.Impl", e);
                throw new RuntimeException(e);
            }
        }
        try
        {
            regName.set(entry, name);
        }
        catch (IllegalArgumentException | IllegalAccessException e)
        {
            LOGGER.error(REGISTRIES,"Could not set `registryName` field in IForgeRegistryEntry.Impl to `{}`", name.toString(), e);
            throw new RuntimeException(e);
        }

    }
}