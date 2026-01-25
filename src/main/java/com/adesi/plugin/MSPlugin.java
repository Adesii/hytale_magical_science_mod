package com.adesi.plugin;

import java.util.logging.Level;

import com.adesi.plugin.Interactions.Tools.ToolWrenchInteraction;
import com.adesi.plugin.components.pipes.GraphChunkController;
import com.adesi.plugin.components.pipes.GraphChunkSystem;
import com.adesi.plugin.components.pipes.PipeComponent;
import com.adesi.plugin.components.pipes.PipeSystem;
import com.adesi.plugin.components.pipes.UpdatePipeComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class MSPlugin extends JavaPlugin {

  private static MSPlugin instance;
  public static HytaleLogger LOGGER;

  private ComponentType<ChunkStore, PipeComponent> pipeType;
  private ComponentType<ChunkStore, GraphChunkController> graphChunkControllerType;
  private ComponentType<ChunkStore, UpdatePipeComponent> pipeUpdateType;

  public MSPlugin(JavaPluginInit init) {
    super(init);
    instance = this;
    LOGGER = getLogger();
  }

  public static MSPlugin get() {
    return instance;
  }

  @Override
  protected void setup() {
    this.pipeType = this.getChunkStoreRegistry().registerComponent(PipeComponent.class, "Pipe", PipeComponent.CODEC);
    this.pipeUpdateType = this.getChunkStoreRegistry().registerComponent(UpdatePipeComponent.class,
        UpdatePipeComponent::new);
    this.graphChunkControllerType = this.getChunkStoreRegistry().registerComponent(GraphChunkController.class,
        "GraphChunkController", GraphChunkController.CODEC);
    this.getChunkStoreRegistry().registerSystem(new GraphChunkSystem(this.graphChunkControllerType));
    this.getChunkStoreRegistry().registerSystem(new PipeSystem.PipeRefSystem());
    this.getChunkStoreRegistry().registerSystem(new PipeSystem.PipeChangeUpdater(this.pipeUpdateType, this.pipeType));
    this.getChunkStoreRegistry().registerSystem(new PipeSystem.PipeChangeSystem());
    this.getLogger().at(Level.INFO).log("Registed Magical Science Plugin!");
    Interaction.CODEC.register("UseWrench", ToolWrenchInteraction.class, ToolWrenchInteraction.CODEC);
  }

  public static Api getLog() {
    return LOGGER.atInfo();
  }

  public ComponentType<ChunkStore, PipeComponent> getPipeType() {
    return this.pipeType;
  }

  public ComponentType<ChunkStore, UpdatePipeComponent> getPipeUpdateType() {
    return this.pipeUpdateType;
  }

  public ComponentType<ChunkStore, GraphChunkController> getGraphChunkControllerType() {
    return this.graphChunkControllerType;
  }

}
