package com.adesi.plugin.components.pipes;

import java.awt.color.CMMException;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentSystems.Update;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class PipeSystem {
  public record PipeArrangement(int rotation, String state) {
  }

  static final int UP = 0;
  static final int DOWN = 1;
  static final int FORWARD = 2;
  static final int BACKWARD = 3;
  static final int LEFT = 4;
  static final int RIGHT = 5;

  static final int[] ONE_WAY_ROTATION = new int[6];

  static Vector3i yaw(Vector3i v) {
    return new Vector3i(-v.getZ(), v.getY(), v.getX());
  }

  static Vector3i pitch(Vector3i v) {
    return new Vector3i(v.getX(), -v.getZ(), v.getY());
  }

  static Vector3i roll(Vector3i v) {
    return new Vector3i(-v.getY(), v.getX(), v.getZ());
  }

  static final int[] CANONICAL_STRAIGHT = { BACKWARD, FORWARD };
  static final int[] CANONICAL_ELBOW = { BACKWARD, LEFT };
  static final int[] CANONICAL_T = { LEFT, BACKWARD, RIGHT };

  static final Map<Integer, Integer> STRAIGHT_ROTATIONS = new HashMap<>();
  static final Map<Integer, Integer> ELBOW_ROTATIONS = new HashMap<>();
  static final Map<Integer, Integer> T_ROTATIONS = new HashMap<>();

  static {
    int straightBase = maskFromDirs(CANONICAL_STRAIGHT);
    int elbowBase = maskFromDirs(CANONICAL_ELBOW);
    int tBase = maskFromDirs(CANONICAL_T);

    for (int rot = 0; rot < 24; rot++) {
      var straight = rotateMask(straightBase, rot);
      if (!STRAIGHT_ROTATIONS.containsKey(straight)) {
        STRAIGHT_ROTATIONS.put(straight, rot);
      }
      var elbow = rotateMask(elbowBase, rot);
      if (!ELBOW_ROTATIONS.containsKey(elbow)) {

        ELBOW_ROTATIONS.put(elbow, rot);
      }
      var t = rotateMask(tBase, rot);
      if (!T_ROTATIONS.containsKey(t)) {

        T_ROTATIONS.put(t, rot);
      }
    }

    ELBOW_ROTATIONS.put(10, 8);
    ELBOW_ROTATIONS.put(6, 12);
  }

  static int maskFromDirs(int... dirs) {
    int mask = 0;
    for (int d : dirs) {
      mask |= 1 << d;
    }
    return mask;
  }

  static int rotateMask(int mask, int rotationIndex) {
    int result = 0;

    for (int d = 0; d < 6; d++) {
      if ((mask & (1 << d)) != 0) {
        Vector3i v = Vector3i.BLOCK_SIDES[d];
        Vector3i rotated = applyRotation(v, rotationIndex);
        result |= 1 << dirFromVector(rotated);
      }
    }

    return result;
  }

  static Vector3i applyRotation(Vector3i v, int index) {
    int yaw = index % 4;
    int pitch = (index / 4) % 3;

    Vector3i r = new Vector3i(v);
    for (int i = 0; i < yaw; i++)
      r = yaw(r);
    for (int i = 0; i < pitch; i++)
      r = pitch(r);
    return r;
  }

  static int dirFromVector(Vector3i v) {
    for (int i = 0; i < Vector3i.BLOCK_SIDES.length; i++) {
      if (Vector3i.BLOCK_SIDES[i].equals(v)) {
        return i;
      }
    }
    throw new IllegalStateException("Invalid vector: " + v);
  }

  private static boolean has(int state, int dir) {
    return (state & (1 << dir)) != 0;
  }

  private static boolean isOpposite(int a, int b) {
    return (a == UP && b == DOWN) ||
        (a == DOWN && b == UP) ||
        (a == FORWARD && b == BACKWARD) ||
        (a == BACKWARD && b == FORWARD) ||
        (a == RIGHT && b == LEFT) ||
        (a == LEFT && b == RIGHT);
  }

  static int rotationForDirection(int dir) {
    switch (dir) {
      case 0:
        return 12;
      case 1:
        return 6;
      case 2:
        return 2;
      case 3:
        return 0;
      case 4:
        return 3;
      case 5:
        return 1;
      default:
        throw new IllegalArgumentException("Invalid direction: " + dir);
    }
  }

  private static int axisOf(int dir) {
    return switch (dir) {
      case UP, DOWN -> 0;
      case LEFT, RIGHT -> 1;
      case FORWARD, BACKWARD -> 2;
      default -> -1;
    };
  }

  private static boolean isElbow(int pipestate) {
    int a = Integer.numberOfTrailingZeros(pipestate);
    int b = Integer.numberOfTrailingZeros(pipestate & ~(1 << a));

    // reject straight
    if (isOpposite(a, b))
      return false;

    // reject same axis
    return axisOf(a) != axisOf(b);
  }

  private static boolean hasAxis(int state, int a, int b) {
    return (state & (1 << a)) != 0 || (state & (1 << b)) != 0;
  }

  private static boolean isValidT(int pipestate) {
    if (Integer.bitCount(pipestate) != 3)
      return false;

    boolean x = hasAxis(pipestate, LEFT, RIGHT);
    boolean y = hasAxis(pipestate, UP, DOWN);
    boolean z = hasAxis(pipestate, FORWARD, BACKWARD);

    int axesPresent = (x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0);

    return axesPresent == 2;
  }

  private static boolean isStraight(int pipestate) {
    int first = Integer.numberOfTrailingZeros(pipestate);
    int second = Integer.numberOfTrailingZeros(pipestate & ~(1 << first));
    return isOpposite(first, second);
  }

  private static PipeArrangement resolveCrossOrComplex(int pipestate) {
    int connections = Integer.bitCount(pipestate);

    switch (connections) {
      case 4:
        return new PipeArrangement(0, "Cross");

      case 5:
        return new PipeArrangement(0, "Five_Way");

      case 6:
        return new PipeArrangement(0, "Six_Way");

      default:
        // Defensive fallback
        return new PipeArrangement(0, "Cross");
    }
  }

  public static PipeArrangement getPipeArrangement(int pipestate) {
    if (pipestate == 0) {
      return new PipeArrangement(0, "none");
    }

    int connections = Integer.bitCount(pipestate);

    switch (connections) {
      case 1: {
        return resolveOneWay(pipestate);
      }

      case 2: {
        if (isStraight(pipestate)) {
          return resolveStraight(pipestate);
        } else {
          return resolveElbow(pipestate);
        }
      }

      case 3:
        return resolveTJunction(pipestate);

      default:
        // return new PipeArrangement(0, "none");
        return resolveCrossOrComplex(pipestate);
    }
  }

  private static PipeArrangement resolveTJunction(int pipestate) {
    if (!isValidT(pipestate)) {
      return resolveInvalid(pipestate);
    }
    Integer rot = T_ROTATIONS.get(pipestate);
    if (rot == null) {
      System.out.println("Invalid T state: " + pipestate);
      return new PipeArrangement(0, "T");
    }
    return new PipeArrangement(rot, "T");
  }

  private static PipeArrangement resolveStraight(int pipestate) {
    Integer rot = STRAIGHT_ROTATIONS.get(pipestate);
    if (rot == null) {
      System.out.println("Invalid Straight state: " + pipestate);
      return new PipeArrangement(0, "Straight");
    }
    return new PipeArrangement(rot, "Straight");
  }

  private static PipeArrangement resolveOneWay(int pipestate) {
    int dir = Integer.numberOfTrailingZeros(pipestate);
    return new PipeArrangement(
        rotationForDirection(dir),
        "One_Way");
  }

  private static PipeArrangement resolveElbow(int pipestate) {
    if (!isElbow(pipestate)) {
      return resolveInvalid(pipestate);
    }
    Integer rot = ELBOW_ROTATIONS.get(pipestate);
    if (rot == null) {
      System.out.println("Invalid elbow state: " + pipestate + "  :::  " + ELBOW_ROTATIONS);
      return new PipeArrangement(0, "Corner_Left");
    }
    return new PipeArrangement(rot, "Corner_Left");
  }

  private static PipeArrangement resolveInvalid(int pipestate) {
    return new PipeArrangement(0, "Debug");
  }

  public static int PlaceAmount = 0;

  public static class PipeChangeUpdater extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, UpdatePipeComponent> componentType;
    private final ComponentType<ChunkStore, PipeComponent> pipeComponent;
    private final Query<ChunkStore> query;

    public PipeChangeUpdater(ComponentType<ChunkStore, UpdatePipeComponent> componentType,
        ComponentType<ChunkStore, PipeComponent> pipeComponent) {
      this.componentType = componentType;
      this.pipeComponent = pipeComponent;
      this.query = Query.and(this.componentType, pipeComponent);
    }

    @Override
    public Query<ChunkStore> getQuery() {
      return Query.and(componentType, pipeComponent);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunkChunkStore,
        Store<ChunkStore> storeChunkStore,
        CommandBuffer<ChunkStore> commandBufferChunkStore) {

      PipeComponent pipe = archetypeChunkChunkStore.getComponent(index, PipeComponent.getComponentType());
      var ref = archetypeChunkChunkStore.getReferenceTo(index);
      MSPlugin.get().getLogger().at(Level.INFO).log("Added ChunkStore to Entity: " + ref.toString());
      commandBufferChunkStore.run(_store -> {
        PipeComponent pp = _store.getComponent(ref,
            PipeComponent.getComponentType());
        PipeArrangement pa = getPipeArrangement(pp.getPipeState());
        var blockstate = _store.getComponent(ref,
            BlockStateInfo.getComponentType());
        var blockchunk = _store.getComponent(blockstate.getChunkRef(),
            BlockChunk.getComponentType());
        int x = ChunkUtil.xFromBlockInColumn(blockstate.getIndex());
        int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
        int z = ChunkUtil.zFromBlockInColumn(blockstate.getIndex());

        BlockType blockType = BlockType.getAssetMap()
            .getAsset(blockchunk.getBlock(ChunkUtil.xFromBlockInColumn(blockstate.getIndex()),
                y,
                ChunkUtil.zFromBlockInColumn(blockstate.getIndex())));

        MSPlugin.get().getLogger().at(Level.INFO).log("Setting block update at: " + x
            + "," + y + "," + z + ", " +
            pa.rotation);
        WorldChunk wc2 = _store.getComponent(blockstate.getChunkRef(),
            WorldChunk.getComponentType());
        wc2.setBlock(x, y, z,
            BlockType.getAssetMap().getIndex(blockType.getId()), blockType,
            pa.rotation(), 0, 0);
        wc2.setBlockInteractionState(x, y, z, blockType, pa.state, true);

      });

      commandBufferChunkStore.removeComponent(ref,
          UpdatePipeComponent.getComponentType());
    }

  }

  public static class PipeRefSystem extends RefSystem<ChunkStore> {

    public static final ComponentType<ChunkStore, PipeComponent> COMPONENT_TYPE = PipeComponent.getComponentType();

    @Override
    public Query<ChunkStore> getQuery() {
      return Query.and(COMPONENT_TYPE, Query.not(UpdatePipeComponent.getComponentType()));
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> refChunkStore, AddReason addReason, Store<ChunkStore> storeChunkStore,
        CommandBuffer<ChunkStore> commandBufferChunkStore) {

      switch (addReason) {
        case SPAWN:
          if (commandBufferChunkStore.getComponent(refChunkStore, UpdatePipeComponent.getComponentType()) != null) {
            return;
          }
          // TODO: do proper pipe validation and replacement. only on spawn since loading
          // should already have the right one
          // MSPlugin.get().getLogger().at(Level.INFO)
          // .log("Added Entity: " +
          // commandBufferChunkStore.getArchetype(refChunkStore).toString());
          var blockstate = commandBufferChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
          var chunkref = blockstate.getChunkRef();
          if (chunkref != null && chunkref.isValid()) {
            var blockchunk = commandBufferChunkStore.getComponent(chunkref, BlockChunk.getComponentType());
            int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
            int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
            int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
            MSPlugin.get().getLogger().at(Level.INFO).log("Pipe is at:" + " " + x + " " +
                y + " " + z);
            int occupiedMask = 0;
            var world = commandBufferChunkStore.getExternalData().getWorld();
            int iterationIndex = -1;
            var pipeComponent = commandBufferChunkStore.getComponent(refChunkStore, PipeComponent.getComponentType());
            var wc = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
            var localX = ChunkUtil.xFromBlockInColumn(blockstate.getIndex());
            var localZ = ChunkUtil.zFromBlockInColumn(blockstate.getIndex());
            var neighbours = new ArrayList<Vector3i>();
            for (var dir : Vector3i.BLOCK_SIDES) {
              var currentX = x + dir.x;
              var currentY = y + dir.y;
              var currentZ = z + dir.z;
              iterationIndex++;
              var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
              var chunkrefforblock = world.getChunk(chunkfor);
              if (chunkrefforblock != null) {
                var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
                var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
                if (holder != null) {
                  var neighbourPipe = commandBufferChunkStore.getComponent(entity, PipeComponent.getComponentType());
                  if (neighbourPipe != null) {

                    occupiedMask |= 1 << iterationIndex;
                    neighbourPipe.setDirectionalConnectionIndex(iterationIndex, true); // TODO: Check for more
                    // holder.addComponent(UpdatePipeComponent.getComponentType(), new
                    // UpdatePipeComponent());
                    // if (holder.getComponent(UpdatePipeComponent.getComponentType()) != null)
                    // continue;
                    // commandBufferChunkStore.run(_store -> {
                    neighbours.add(dir);
                    // if (holder.getComponent(UpdatePipeComponent.getComponentType()) == null) {
                    // holder.addComponent(
                    // UpdatePipeComponent.getComponentType(), new UpdatePipeComponent());
                    // });
                    // commandBufferChunkStore.ensureAndGetComponent(
                    // chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ),
                    // UpdatePipeComponent.getComponentType());
                    // MSPlugin.get().getLogger().at(Level.INFO).log("Found neighbour at: " + dir);
                  }
                }
              }
            }
            if (pipeComponent != null && !pipeComponent.isMatchingMask(occupiedMask)) {
              pipeComponent.setDirectionalState(occupiedMask);
              pipeComponent.setPipeState(occupiedMask);
              BlockType blockType = BlockType.getAssetMap()
                  .getAsset(blockchunk.getBlock(ChunkUtil.xFromBlockInColumn(blockstate.getIndex()), y,
                      ChunkUtil.zFromBlockInColumn(blockstate.getIndex())));

              if (blockType != null) {
                // MSPlugin.get().getLogger().at(Level.INFO).log("Found neighbour at: " +
                // differentState);
                commandBufferChunkStore.run(_store -> {
                  WorldChunk wc2 = _store.getComponent(chunkref, WorldChunk.getComponentType());
                  PipeArrangement pa = getPipeArrangement(pipeComponent.getPipeState());
                  // MSPlugin.get().getLogger().at(Level.INFO).log("Setting block at: " +
                  // pa.rotation);
                  wc2.setBlock(x, y, z,
                      BlockType.getAssetMap().getIndex(blockType.getId()), blockType,
                      pa.rotation, 0, 0);
                  wc2.setBlockInteractionState(x, y, z, blockType, pa.state, true);
                  for (Vector3i dir : neighbours) {
                    if (dir == null || (dir.x == 0 && dir.z == 0 && dir.y == 0))
                      continue;
                    var currentX = x + dir.x;
                    var currentY = y + dir.y;
                    var currentZ = z + dir.z;
                    MSPlugin.get().getLogger().at(Level.INFO).log("neighbour Pipe is at:" + " " + currentX + " " +
                        currentY + " " + currentZ);

                    var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
                    var chunkre = world.getChunk(chunkfor);
                    var chunkrefforblock = _store.getComponent(chunkre.getReference(), WorldChunk.getComponentType());
                    if (chunkrefforblock != null) {
                      var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
                      if (holder != null) {
                        var neighbourPipe = holder.getComponent(PipeComponent.getComponentType());
                        MSPlugin.get().getLogger().at(Level.INFO)
                            .log("Found pipe at: " + currentX + ", " + currentY + ", " + currentZ + ",."
                                + neighbourPipe);
                        var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
                        if (neighbourPipe != null) {
                          BlockType otherblockType = chunkrefforblock.getBlockType(currentX, currentY, currentZ);
                          // holder.addComponent(UpdatePipeComponent.getComponentType(), new
                          // UpdatePipeComponent());
                          if (_store.getComponent(entity, UpdatePipeComponent.getComponentType()) == null)
                            _store.addComponent(entity, UpdatePipeComponent.getComponentType());
                          // PipeArrangement pa2 = getPipeArrangement(neighbourPipe.getPipeState());
                          // // MSPlugin.get().getLogger().at(Level.INFO).log("Setting block at: " +
                          // // pa.rotation);
                          // chunkrefforblock.setBlock(currentX, currentY, currentZ,
                          // BlockType.getAssetMap().getIndex(otherblockType.getId()), otherblockType,
                          // pa2.rotation, 0, 0);
                          // chunkrefforblock.setBlockInteractionState(currentX, currentY, currentZ,
                          // otherblockType,
                          // pa2.state, true);

                        }
                      }
                    }
                  }
                });
              }

            }
            // MSPlugin.get().getLogger().at(Level.INFO)
            // .log("Current Pipe State is:" + pipeComponent + "Current PLace Amount" +
            // PlaceAmount);
          }

          break;

        default:
          break;

      }

    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> refChunkStore, RemoveReason removeReason,
        Store<ChunkStore> storeChunkStore,
        CommandBuffer<ChunkStore> commandBufferChunkStore) {

      MSPlugin.get().getLogger().at(Level.INFO).log("Removed Entity: " + removeReason.toString());
    }
  }

}
