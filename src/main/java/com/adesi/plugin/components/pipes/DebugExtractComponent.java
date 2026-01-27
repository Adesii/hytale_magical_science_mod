package com.adesi.plugin.components.pipes;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class DebugExtractComponent implements Component<ChunkStore> {
  public static final BuilderCodec<DebugExtractComponent> CODEC = BuilderCodec
      .builder(DebugExtractComponent.class, DebugExtractComponent::new)
      .build();

  public DebugExtractComponent() {
  }

  public DebugExtractComponent(DebugExtractComponent other) {
  }

  @Override
  public Component<ChunkStore> clone() {
    return new DebugExtractComponent(this);
  }

  public static ComponentType<ChunkStore, DebugExtractComponent> getComponentType() {
    return MSPlugin.get().getDebugExtractComponentType();
  }
}
