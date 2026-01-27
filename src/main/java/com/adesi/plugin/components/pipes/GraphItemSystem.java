package com.adesi.plugin.components.pipes;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Vector3i;
import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;

public class GraphItemSystem extends EntityTickingSystem<EntityStore> {
  private ComponentType<EntityStore, TransportPacket> componentType;
  private Query<EntityStore> query;

  public GraphItemSystem(ComponentType<EntityStore, TransportPacket> componentType) {
    this.componentType = componentType;
    query = Query.and(componentType);
  }

  @Override
  public Query<EntityStore> getQuery() {
    return query;
  }

  @Override
  public void tick(float dt, int index, ArchetypeChunk<EntityStore> archetypeChunkEntityStore,
      Store<EntityStore> storeEntityStore,
      CommandBuffer<EntityStore> commandBufferEntityStore) {

    var entity = archetypeChunkEntityStore.getReferenceTo(index);
    TransportPacket transportPacket = archetypeChunkEntityStore.getComponent(index, componentType);
    assert (transportPacket != null); // This should never happen.

    var transformComponent = archetypeChunkEntityStore.getComponent(index, TransformComponent.getComponentType());
    assert (transformComponent != null); // This should never happen.

    var position = transformComponent.getPosition();
    position.add(transportPacket.getPipeDirection().toVector3d().scale(dt));

    var currentChunk = entity.getStore().getExternalData().getWorld()
        .getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(Math.round(position.x), Math.round(position.z)));

    if (currentChunk == null) {
      return;
    }

    var graphChunkComponent = currentChunk.getReference().getStore().getComponent(currentChunk.getReference(),
        GraphChunkController.getGraphChunkControllerType());

    if (graphChunkComponent == null) {
      return;
    }
    var atJunction = graphChunkComponent.atJunction(transportPacket, position);
    if (atJunction == null) {
      if (graphChunkComponent.isOnPipe(position)) {

        transformComponent.setPosition(position);
      } else {
        transportPacket.setPipeDirection(transportPacket.getPipeDirection().clone().negate());
      }
    } else {
      transportPacket.setPipeID(atJunction.NewPipeID);
      transportPacket.setPipeDirection(atJunction.JunctionDirection);
      transformComponent.setPosition(position.add(atJunction.JunctionDirection.toVector3d().scale(dt)));
    }
  }
}
