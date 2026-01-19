package com.adesi.plugin;

import java.util.logging.Level;

import com.adesi.plugin.components.pipes.PipeComponent;
import com.adesi.plugin.components.pipes.PipeSystem;
import com.adesi.plugin.components.pipes.UpdatePipeComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class MSPlugin extends JavaPlugin {

  private static MSPlugin instance;

  private ComponentType<ChunkStore, PipeComponent> pipeType;
  private ComponentType<ChunkStore, UpdatePipeComponent> pipeUpdateType;

  public MSPlugin(JavaPluginInit init) {
    super(init);
    instance = this;
  }

  public static MSPlugin get() {
    return instance;
  }

  @Override
  protected void setup() {
    super.setup();
    this.getCommandRegistry().registerCommand(new TestCommand("plus", "zooooo", false));
    this.pipeType = this.getChunkStoreRegistry().registerComponent(PipeComponent.class, "Pipe", PipeComponent.CODEC);
    this.pipeUpdateType = this.getChunkStoreRegistry().registerComponent(UpdatePipeComponent.class,
        UpdatePipeComponent::new);
    this.getChunkStoreRegistry().registerSystem(new PipeSystem.PipeRefSystem());
    this.getChunkStoreRegistry().registerSystem(new PipeSystem.PipeChangeUpdater(this.pipeUpdateType, this.pipeType));
    this.getLogger().at(Level.INFO).log("Registed Magical Science Plugin!");
  }

  public ComponentType<ChunkStore, PipeComponent> getPipeType() {
    return this.pipeType;
  }

  public ComponentType<ChunkStore, UpdatePipeComponent> getPipeUpdateType() {
    return pipeUpdateType;
  }

  public static Api log() {
    return get().getLogger().atInfo();
  }

}
