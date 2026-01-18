package com.adesi.plugin.components.pipes;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class PipeComponent implements Component<ChunkStore> {

  @Nonnull
  public static final BuilderCodec<PipeComponent> CODEC = BuilderCodec.builder(PipeComponent.class, PipeComponent::new)
      .append(new KeyedCodec<>("PipeState", Codec.INTEGER), (o, state) -> o.pipeState = state, o -> o.pipeState)
      .addValidator(Validators.greaterThanOrEqual((int) 0))
      .add().build();
  // bitmask for pipe state for each direction including up and down, 6 bits for
  // current state and 6 bits for masking so the player can remove certain
  // connections.
  private int pipeState;

  public PipeComponent() {
    this(0); // default to no connections.
  }

  public PipeComponent(PipeComponent other) {
    pipeState = other.pipeState; // copy the state.
  }

  public PipeComponent(int pipeState) {
    this.pipeState = pipeState;
  }

  public int getPipeState() {
    return this.pipeState;
  }

  public void setPipeState(int pipeState) {
    this.pipeState = pipeState;
  }

  public void setDirectionalConnection(Vector3i direction, boolean connected) {
    var getIndex = -1;
    for (int i = 0; i < Vector3i.BLOCK_SIDES.length; i++) {
      if (Vector3i.BLOCK_SIDES[i] == direction) {
        getIndex = i;
        break;
      }
    }
    if (getIndex == -1) {
      return;
    }
    if (connected) {
      this.pipeState |= 0b111 << getIndex;
    } else {
      this.pipeState &= ~(0b111 << getIndex);
    }
  }

  public void setDirectionalConnectionIndex(int index, boolean connected) {
    if (connected) {
      this.pipeState |= 1 << index;
    } else {
      this.pipeState &= ~(1 << index);
    }
  }

  public void setDirectionalState(int mask) {
    // only replace the starting part of the mask
    this.pipeState = mask; // only replace the starting part of the mask
  }

  public boolean isMatchingMask(int occupancymask) {
    return (this.pipeState & occupancymask) == occupancymask; // check if all bits in the mask are set in
                                                              // pipeState even if pipestate has more bits
  }

  public boolean hasDirectionalConnection(Vector3i direction) {
    var getIndex = -1;
    for (int i = 0; i < Vector3i.BLOCK_SIDES.length; i++) {
      if (Vector3i.BLOCK_SIDES[i] == direction) {
        getIndex = i;
        break;
      }
    }
    if (getIndex == -1) {
      return false;
    }
    return ((this.pipeState >> getIndex) & 1) == 1; // check the bit at the index position
  }

  public boolean hasDirectionalConnectionIndex(int index) {
    return ((this.pipeState >> index) & 1) == 1; // check the bit at the index position
  }

  @NullableDecl
  @Override
  public Component<ChunkStore> clone() {
    return new PipeComponent(this);
  }

  public static ComponentType<ChunkStore, PipeComponent> getComponentType() {
    return MSPlugin.get().getPipeType();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (var direction : Vector3i.BLOCK_SIDES) {
      if (hasDirectionalConnection(direction)) {
        sb.append(direction).append(",");
      }
    }
    return "PipeComponent{" +
        "pipeState=" + Integer.toBinaryString(pipeState) +
        ", connections=" + sb.toString() +
        '}';
  }

}
