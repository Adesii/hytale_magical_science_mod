package com.adesi.plugin.Interactions.Tools;

import java.nio.channels.Pipe;
import java.sql.Ref;
import java.time.ZoneId;

import com.adesi.plugin.MSPlugin;
import com.adesi.plugin.components.pipes.PipeComponent;
import com.adesi.plugin.components.pipes.UpdatePipeComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ToolWrenchInteraction extends SimpleBlockInteraction {
  public static final BuilderCodec<ToolWrenchInteraction> CODEC = BuilderCodec.builder(
      ToolWrenchInteraction.class, ToolWrenchInteraction::new, SimpleBlockInteraction.CODEC)
      .documentation("Attempts to wrench the target block, executing interactions on it if any.")
      .build();

  @Override
  protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBufferEntityStore,
      InteractionType interactionType,
      InteractionContext interactionContext, ItemStack itemStack, Vector3i targetBlock,
      CooldownHandler cooldownHandler) {
    if (!itemStack.getItemId().toLowerCase().contains("wrench")) {
      MSPlugin.getLog().log("Item is not a wrench!");
      interactionContext.getState().state = InteractionState.Failed;
      return;
    }

    // TODO: Implement a more generic way to do wrench interactions...
    var entity = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z))
        .getBlockComponentEntity(targetBlock.x, targetBlock.y, targetBlock.z);
    if (entity == null) {
      MSPlugin.getLog().log("Block has no componentholder!");
      interactionContext.getState().state = InteractionState.Failed;
      return;
    }
    var entitystore = entity.getStore();
    MSPlugin.getLog().log("Wrenching on block: " + targetBlock.x + ", " + targetBlock.y + ", " + targetBlock.z);

    // world.execute(() -> {
    var pipecomponent = entitystore.getComponent(entity, PipeComponent.getComponentType());
    if (pipecomponent == null) {
      MSPlugin.getLog().log("No pipe component found!");
      interactionContext.getState().state = InteractionState.Skip;
      return;
    }
    pipecomponent.setBlockedDirection(Vector3i.UP, false);
    pipecomponent.setBlockedDirection(Vector3i.RIGHT, false);
    MSPlugin.getLog().log(pipecomponent.toString());

    entitystore.putComponent(entity, PipeComponent.getComponentType(),
        pipecomponent);
    // entitystore.addComponent(entity, UpdatePipeComponent.getComponentType(), new
    // UpdatePipeComponent());
    MSPlugin.getLog().log("Wrenching complete! Pipe direction unblocked. Updating pipe component...");

    // });
    //
    // var entitystorething =
    // interactionContext.getCommandBuffer().getExternalData().getWorld()
    // .getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x,
    // targetBlock.z));
    // MSPlugin.getLog().log(entitystorething.toString());
    // var blockcomponententity =
    // entitystorething.getBlockComponentEntity(targetBlock.x, targetBlock.y,
    // targetBlock.z);
    // MSPlugin.getLog().log(blockcomponententity.toString());
    // var chunkref =
    // world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(targetBlock.x,
    // targetBlock.y));
    // var chunkstore = chunkref.getStore();
    // BlockComponentChunk blockComponentChunk = chunkstore.getComponent(chunkref,
    // BlockComponentChunk.getComponentType());
    //
    // assert blockComponentChunk != null;
    //
    // int blockIndex = ChunkUtil.indexBlockInColumn(targetBlock.x, targetBlock.y,
    // targetBlock.z);
    // var blockRef = blockComponentChunk.getEntityHolder(blockIndex);
    // var pipecomponent = blockRef.getComponent(
    // PipeComponent.getComponentType());
    // pipecomponent.setBlockedDirection(Vector3i.UP, false);
    // world.getChunkStore().getStore().replaceComponent(blockcomponententity,
    // PipeComponent.getComponentType(),
    // pipecomponent);
    // world.getChunkStore().getStore().addComponent(blockcomponententity,
    // UpdatePipeComponent.getComponentType());
    //
  }

  @Override
  protected void simulateInteractWithBlock(InteractionType interactionType, InteractionContext interactionContext,
      ItemStack itemStack, World world,
      Vector3i targetBlock) {
  }

}
