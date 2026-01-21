package com.adesi.plugin.components.pipes;

import java.nio.channels.Pipe;
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
      .add()
      .append(new KeyedCodec<>("BlockedPipeState", Codec.INTEGER), (o, state) -> o.blockPipeState = state,
          o -> o.blockPipeState)
      .addValidator(Validators.greaterThanOrEqual((int) 0))
      .add().build();
  // bitmask for pipe state for each direction including up and down, 6 bits for
  // current state and 6 bits for masking so the player can remove certain
  // connections.
  private int pipeState;
  private int blockPipeState; // bitmask for directions that are manually blocked off. like when using a
                              // wrench.

  public PipeComponent() {
    this(0, 0); // default to no connections.
  }

  public PipeComponent(PipeComponent other) {
    pipeState = other.pipeState; // copy the state.
    blockPipeState = other.blockPipeState; // copy the blocked state.
  }

  public PipeComponent(int pipeState, int blockedState) {
    this.pipeState = pipeState;
    blockPipeState = blockedState; // no directions blocked by default.
  }

  public int getPipeState() {
    return this.pipeState & ~blockPipeState; // mask out blocked directions.
  }

  public void updateFrom(PipeComponent pp) {
    this.pipeState = pp.pipeState;
    this.blockPipeState = pp.blockPipeState;
  }

  public void setPipeState(int pipeState) {
    this.pipeState = pipeState;
  }

  public static int getBitIndex(Vector3i direction) {
    var getIndex = -1;
    for (int i = 0; i < Vector3i.BLOCK_SIDES.length; i++) {
      if (Vector3i.BLOCK_SIDES[i].equals(direction)) {
        getIndex = i;
        break;
      }
    }
    return getIndex;
  }

  public void setBlockedDirection(Vector3i direction, boolean canConnect) {
    int getIndex = getBitIndex(direction);
    if (getIndex == -1) {
      return;
    }
    if (canConnect) {
      blockPipeState &= ~(1 << getIndex);
    } else {
      blockPipeState |= 1 << getIndex;
    }
  }

  public void toggleBlockedDirection(Vector3i direction) {
    int getIndex = getBitIndex(direction.clone().negate());
    if (getIndex == -1) {
      return;
    }
    blockPipeState ^= 1 << getIndex;
  }

  public boolean getBlockedState(Vector3i dir) {
    return (blockPipeState & (1 << getBitIndex(dir))) != 0;
  }

  public boolean canConnectTo(Vector3i direction) {
    int getIndex = getBitIndex(direction.clone().negate());
    if (getIndex == -1) {
      return false;
    }

    return (blockPipeState & (1 << getIndex)) == 0;
  }

  public void setDirectionalConnection(Vector3i direction, boolean connected) {
    int getIndex = getBitIndex(direction);
    if (getIndex == -1) {
      return;
    }
    setDirectionalConnectionIndex(getIndex, connected);
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
    return getPipeState() == occupancymask;
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
    return ((getPipeState() >> getIndex) & 1) == 1; // check the bit at the index position
  }

  public boolean hasDirectionalConnectionIndex(int index) {
    return ((getPipeState() >> index) & 1) == 1; // check the bit at the index position
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
        ", blocked directions=" + Integer.toBinaryString(blockPipeState) +
        '}';
  }

}
