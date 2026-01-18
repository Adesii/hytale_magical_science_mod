package com.adesi.plugin.components.pipes;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class UpdatePipeComponent implements Component<ChunkStore> {

  public UpdatePipeComponent() {
  }

  public UpdatePipeComponent(UpdatePipeComponent other) {
  }

  @NullableDecl
  @Override
  public Component<ChunkStore> clone() {
    return new UpdatePipeComponent(this);
  }

  public static ComponentType<ChunkStore, UpdatePipeComponent> getComponentType() {
    return MSPlugin.get().getPipeUpdateType();
  }

}
