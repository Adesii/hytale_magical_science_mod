package com.adesi.plugin.components.pipes;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;

import java.nio.channels.Pipe;
import java.util.ArrayList;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;

public class DebugExtractSystem extends DelayedEntitySystem<ChunkStore> {
  public DebugExtractSystem() {
    super(0.2f);
  }

  @Override
  public Query<ChunkStore> getQuery() {
    return Query.and(DebugExtractComponent.getComponentType());
  }

  @Override
  public void tick(float delta, int index, ArchetypeChunk<ChunkStore> archetypeChunkChunkStore,
      Store<ChunkStore> storeChunkStore,
      CommandBuffer<ChunkStore> commandBufferChunkStore) {
    var ref = archetypeChunkChunkStore.getReferenceTo(index);
    ItemContainerState chest = archetypeChunkChunkStore.getComponent(index,
        BlockStateModule.get().getComponentType(ItemContainerState.class));
    // MSPlugin.get().getLogger().at(Level.INFO).log("Added ChunkStore to Entity: "
    // + ref.toString());

    var blockstate = storeChunkStore.getComponent(ref,
        BlockStateInfo.getComponentType());
    var blockchunk = storeChunkStore.getComponent(blockstate.getChunkRef(),
        BlockChunk.getComponentType());
    var world = storeChunkStore.getExternalData().getWorld();
    int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
        ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
    int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
    int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
        ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
    ArrayList<Vector3i> pipedirections = new ArrayList<>();
    for (var dir : Vector3i.BLOCK_SIDES) {
      var currentX = x + dir.x;
      var currentY = y + dir.y;
      var currentZ = z + dir.z;
      var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
      var chunkrefforblock = world.getChunk(chunkfor);
      if (chunkrefforblock != null) {
        var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
        var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
        if (holder != null) {
          var neighbourPipe = storeChunkStore.getComponent(entity, PipeComponent.getComponentType());
          if (neighbourPipe != null) {
            if (neighbourPipe.canConnectTo(dir)) {
              pipedirections.add(dir);
            }
          }
        }
      }
    }
    var controller = storeChunkStore.getComponent(blockstate.getChunkRef(),
        GraphChunkController.getGraphChunkControllerType());
    controller.addItemToPipe(world, new Vector3i(x, y, z).add(pipedirections.getFirst()),
        chest.getItemContainer().getItemStack((short) 0));

  }
}
