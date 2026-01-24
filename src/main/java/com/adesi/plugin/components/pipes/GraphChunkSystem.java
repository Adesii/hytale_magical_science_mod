package com.adesi.plugin.components.pipes;

import java.util.Set;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class GraphChunkSystem extends EntityTickingSystem<ChunkStore> {

  private ComponentType<ChunkStore, GraphChunkController> componentType;

  private Query<ChunkStore> query;

  public GraphChunkSystem(ComponentType<ChunkStore, GraphChunkController> componentType) {
    this.componentType = componentType;
    query = Query.and(componentType);
  }

  @Override
  public Query<ChunkStore> getQuery() {
    return query;
  }

  @Override
  public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunkChunkStore,
      Store<ChunkStore> storeChunkStore,
      CommandBuffer<ChunkStore> commandBufferChunkStore) {
    // MSPlugin.getLog().log("GraphChunkSystem.tick +" + index);

    var graphController = archetypeChunkChunkStore.getComponent(index,
        componentType);
    var world = storeChunkStore.getExternalData().getWorld();
    if (graphController != null) {
      for (var node : graphController.getNodes()) {
        Vector3d nodeStart = node.startPosition.toVector3d();
        Vector3d nodeDirection = node.extendDirection.toVector3d();
        Vector3d nodeEnd = nodeStart.clone().add(nodeDirection.clone().scale(node.extendLength));
        // MSPlugin.getLog().log(" node " + node);
        DebugUtils.addSphere(world, nodeStart, new Vector3f(0,
            1, 0), 0.4f, dt + 0.2f);
        // DebugUtils.addArrow(world, nodeStart,
        // nodeDirection,
        // new Vector3f(1, 1, 1), dt, true);
        DebugUtils.addSphere(world,
            nodeEnd,
            new Vector3f(1, 0, 0), 0.4f, dt + 0.2f);
      }
      for (var junctions : graphController.getJunctions()) {
        var splitname = junctions.getKey().split(",");
        var center = new Vector3d(Double.parseDouble(splitname[0]), Double.parseDouble(splitname[1]),
            Double.parseDouble(splitname[2]));
        DebugUtils.addSphere(world, center, new Vector3f(0, 0, 1), 0.5, dt + 0.2f);
        for (var juncdirection : junctions.getValue()) {
          DebugUtils.addArrow(world, center, juncdirection.toVector3d(), new Vector3f(0, 0, 1), dt + 0.2f, false);

        }
      }
    }

  }

  // @Override
  // public Set<Dependency<ChunkStore>> getDependencies() {
  // return Set.of(
  // new SystemDependency<>(Order.BEFORE, PipeSystem.PipeChangeUpdater.class)
  // // new SystemDependency<>(Order.AFTER, PipeSystem.PipeRefSystem.class),
  // // new SystemDependency<>(Order.AFTER, PipeSystem.PipeChangeSystem.class)
  // );
  // }

}
