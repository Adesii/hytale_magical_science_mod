package com.adesi.plugin.components.pipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;


public class PipeSystem {
  public record PipeArrangement(RotationTuple rotation, String state) {
  }

  static final Vector3i[] CANONICAL_ONE_WAY = {
      Vector3i.FORWARD
  };

  static final Vector3i[] CANONICAL_STRAIGHT = {
      Vector3i.FORWARD, Vector3i.BACKWARD
  };

  static final Vector3i[] CANONICAL_ELBOW = {
      Vector3i.FORWARD, Vector3i.RIGHT
  };

  static final Vector3i[] CANONICAL_T = {
      Vector3i.LEFT, Vector3i.RIGHT, Vector3i.FORWARD
  };

  static final Vector3i[] CANONICAL_CORNERS = {
      Vector3i.LEFT, Vector3i.FORWARD, Vector3i.UP
  };

  static final Vector3i[] CANONICAL_CROSS = {
      Vector3i.FORWARD, Vector3i.RIGHT, Vector3i.BACKWARD, Vector3i.LEFT
  };
  static final Vector3i[] CANONICAL_EXTRA_CROSS = {
      Vector3i.FORWARD, Vector3i.RIGHT, Vector3i.BACKWARD, Vector3i.LEFT, Vector3i.UP
  };

  static final Vector3i[] CANONICAL_SPECIAL_CORNER = {
      Vector3i.LEFT, Vector3i.FORWARD, Vector3i.RIGHT, Vector3i.UP
  };

  static Axis axisOf(Vector3i v) {
    if (v.equals(Vector3i.UP) || v.equals(Vector3i.DOWN))
      return Axis.Y;
    if (v.equals(Vector3i.LEFT) || v.equals(Vector3i.RIGHT))
      return Axis.X;
    return Axis.Z;
  }

  static Set<Vector3i> directionsFromState(int pipestate) {
    Set<Vector3i> set = HashSet.newHashSet(Vector3i.BLOCK_SIDES.length);

    for (int i = 0; i < Vector3i.BLOCK_SIDES.length; i++) {
      if ((pipestate & (1 << i)) != 0) {
        set.add(new Vector3i(Vector3i.BLOCK_SIDES[i]));
      }
    }
    return set;
  }

  static final Map<Set<Vector3i>, RotationTuple> manualOverride = new HashMap<>();

  static {
    // T Overrides
    manualOverride.put(Set.of(Vector3i.UP, Vector3i.RIGHT, Vector3i.DOWN),
        RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.Ninety));
    manualOverride.put(Set.of(Vector3i.UP, Vector3i.LEFT, Vector3i.DOWN),
        RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.Ninety));
    // SpecialCorner Overrides
    manualOverride.put(Set.of(Vector3i.UP, Vector3i.RIGHT, Vector3i.DOWN, Vector3i.BACKWARD),
        RotationTuple.of(Rotation.None, Rotation.OneEighty, Rotation.TwoSeventy));
    manualOverride.put(Set.of(Vector3i.UP, Vector3i.LEFT, Vector3i.DOWN, Vector3i.BACKWARD),
        RotationTuple.of(Rotation.TwoSeventy, Rotation.OneEighty, Rotation.TwoSeventy));
  }

  static boolean isValidRotation(RotationTuple rotation, Vector3i[] canonical, Set<Vector3i> actual) {
    Set<Vector3i> rotated = new HashSet<>();
    for (Vector3i v : canonical) {
      rotated.add(Rotation.rotate(new Vector3i(v), rotation.yaw(), rotation.pitch(), rotation.roll()));
    }

    // Quick check: vector sets match
    if (!rotated.equals(actual))
      return false;

    // Manual override check: ensure arms align physically
    // For example:
    for (Vector3i arm : rotated) {
      if (!actual.contains(arm))
        return false;

    }
    // Optional: check orientation of specific arms (like the "stem" of T)
    // e.g., primary forward must map to primary forward in the world
    // This handles ambiguous rotations
    return true;
  }

  static RotationTuple findRotation(Vector3i[] canonical, Set<Vector3i> actual) {
    if (manualOverride.containsKey(actual)) {
      return manualOverride.get(actual);
    }
    for (Rotation yaw : Rotation.VALUES) {
      for (Rotation pitch : Rotation.VALUES) {
        Rotation roll = Rotation.None;
        RotationTuple candidate = RotationTuple.of(yaw, pitch, roll);
        if (isValidRotation(candidate, canonical, actual)) {
          return candidate;
        }
      }
    }
    for (Rotation yaw : Rotation.VALUES) {
      for (Rotation roll : Rotation.VALUES) {
        Rotation pitch = Rotation.None;
        RotationTuple candidate = RotationTuple.of(yaw, pitch, roll);
        if (isValidRotation(candidate, canonical, actual)) {
          return candidate;
        }
      }
    }
    for (Rotation pitch : Rotation.VALUES) {
      for (Rotation roll : Rotation.VALUES) {
        Rotation yaw = Rotation.None;
        RotationTuple candidate = RotationTuple.of(yaw, pitch, roll);
        if (isValidRotation(candidate, canonical, actual)) {
          return candidate;
        }
      }
    }
    return null;
  }

  static boolean isElbow(Set<Vector3i> dirs) {
    Iterator<Vector3i> it = dirs.iterator();
    Vector3i a = it.next();
    Vector3i b = it.next();

    if (a.equals(new Vector3i(b).negate())) {
      return false;
    }
    return axisOf(a) != axisOf(b);
  }

  static boolean isT(Set<Vector3i> dirs) {
    if (dirs.size() != 3)
      return false;

    boolean x = dirs.stream().anyMatch(d -> axisOf(d) == Axis.X);
    boolean y = dirs.stream().anyMatch(d -> axisOf(d) == Axis.Y);
    boolean z = dirs.stream().anyMatch(d -> axisOf(d) == Axis.Z);

    return (x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0) == 2;
  }

  static boolean isCorner(Set<Vector3i> dirs) {
    if (dirs.size() != 3)
      return false;
    Iterator<Vector3i> it = dirs.iterator();
    Vector3i a = it.next();
    Vector3i b = it.next();
    Vector3i c = it.next();

    return axisOf(a) != axisOf(b) && axisOf(b) != axisOf(c) && axisOf(a) != axisOf(c);
  }

  static boolean isSpecialCorner(Set<Vector3i> dirs) {
    int[] axes_count = new int[3];
    for (Vector3i dir : dirs) {
      Axis axis = axisOf(dir);
      axes_count[axis.ordinal()]++;
    }
    boolean has_two_common = false;
    boolean has_atleast_one = false;
    for (int count : axes_count) {
      if (count == 2)
        has_two_common = true;
      else if (count >= 1)
        has_atleast_one = true;
      else {
        return false;
      }
    }
    return has_two_common && has_atleast_one;
  }

  static boolean isStraight(Set<Vector3i> dirs) {
    Iterator<Vector3i> it = dirs.iterator();
    Vector3i a = it.next();
    Vector3i b = it.next();

    // Must be opposite directions
    if (!a.equals(new Vector3i(b).negate()))
      return false;

    // Must share the same axis
    return axisOf(a) == axisOf(b);
  }

  static boolean isCross(Set<Vector3i> dirs) {
    int[] axes_count = new int[3];
    for (Vector3i dir : dirs) {
      Axis axis = axisOf(dir);
      axes_count[axis.ordinal()]++;
    }
    for (int count : axes_count) {
      if (count == 0) {
        return true;
      }
    }
    return false;
  }

  public static PipeArrangement getPipeArrangement(int pipestate) {

    Set<Vector3i> dirs = directionsFromState(pipestate);
    // MSPlugin.get().getLogger().atInfo().log("Directions: " +
    // Arrays.toString(dirs.toArray()));
    int count = dirs.size();

    if (count == 0) {
      return new PipeArrangement(RotationTuple.NONE,
          "default");
    }

    if (count == 1) {
      return new PipeArrangement(findRotation(CANONICAL_ONE_WAY, dirs), "One_Way");
    }

    if (count == 2) {
      if (isStraight(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_STRAIGHT, dirs), "Straight");
      }
      if (isElbow(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_ELBOW, dirs), "Corner_Left");
      }
      return invalid();
    }

    if (count == 3) {
      if (isT(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_T, dirs), "T");
      }
      if (isCorner(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_CORNERS, dirs), "Corner_Full");

      }
      return invalid();
    }

    if (count == 4) {
      if (isCross(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_CROSS, dirs), "Cross");
      }
      if (isSpecialCorner(dirs)) {
        return new PipeArrangement(findRotation(CANONICAL_SPECIAL_CORNER, dirs), "Corner_Special");
      }
    }

    if (count == 5) {
      return new PipeArrangement(findRotation(CANONICAL_EXTRA_CROSS, dirs), "Cross_Extra");
    }

    // 4+ connections: symmetric
    return new PipeArrangement(
        RotationTuple.of(Rotation.None, Rotation.None, Rotation.None),
        "Full");
  }

  public static PipeArrangement invalid() {
    return new PipeArrangement(RotationTuple.NONE, "Debug");
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
      this.query = Query.and(this.componentType, this.pipeComponent);
    }

    @Override
    public Query<ChunkStore> getQuery() {
      return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunkChunkStore,
        Store<ChunkStore> storeChunkStore,
        CommandBuffer<ChunkStore> commandBufferChunkStore) {

      PipeComponent pipe = archetypeChunkChunkStore.getComponent(index, PipeComponent.getComponentType());
      var ref = archetypeChunkChunkStore.getReferenceTo(index);
      MSPlugin.get().getLogger().at(Level.INFO).log("Added ChunkStore to Entity: "
          + ref.toString());

      var blockstate = storeChunkStore.getComponent(ref,
          BlockStateInfo.getComponentType());
      var blockchunk = storeChunkStore.getComponent(blockstate.getChunkRef(),
          BlockChunk.getComponentType());
      var world = storeChunkStore.getExternalData().getWorld();
      int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
          ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
      int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
      int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
          ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
      int iterationIndex = -1;
      int occupiedMask = 0;
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
            var neighbourPipe = storeChunkStore.getComponent(entity, PipeComponent.getComponentType());
            if (neighbourPipe != null && neighbourPipe.canConnectTo(dir)) {
              occupiedMask |= 1 << iterationIndex;
            }
          }
        }
      }

      // MSPlugin.get().getLogger().atInfo().log("Test Component mask vs calculated
      // mask "
      // + Integer.toBinaryString(occupiedMask) + " == " +
      // Integer.toBinaryString(pipe.getPipeState()));
      if (pipe.isMatchingMask(occupiedMask) && occupiedMask != 0) {
        commandBufferChunkStore.removeComponent(ref, UpdatePipeComponent.getComponentType());
        return;
      }
      var oldstate = getPipeArrangement(pipe.getPipeState());
      pipe.setDirectionalState(
          occupiedMask);
      MSPlugin.getLog().log("bleh" + pipe.toString());
      PipeArrangement pa = getPipeArrangement(pipe.getPipeState());

      MSPlugin.getLog().log(pa.state + " " + oldstate.state);
      BlockType blockType = BlockType.getAssetMap()
          .getAsset(blockchunk.getBlock(ChunkUtil.xFromBlockInColumn(blockstate.getIndex()),
              y,
              ChunkUtil.zFromBlockInColumn(blockstate.getIndex())));
      var currentstate = blockType.getState().getStateForBlock(blockType.getId());
      MSPlugin.getLog().log(currentstate + " " + pa.state);
      if (oldstate.state == pa.state && oldstate.rotation.equals(pa.rotation)
          && currentstate == pa.state) {
        commandBufferChunkStore.removeComponent(ref, UpdatePipeComponent.getComponentType());
        return;
      }

      // MSPlugin.get().getLogger().at(Level.INFO).log("Setting block update at: " + x
      // + "," + y + "," + z + ", " +
      // pa.rotation);
      commandBufferChunkStore.run(_store -> {
        var updatecomp = _store.getComponent(ref, UpdatePipeComponent.getComponentType());
        updatecomp.setHasUpdated(true);
        WorldChunk wc2 = _store.getComponent(blockstate.getChunkRef(),
            WorldChunk.getComponentType());
        var newblockstate = blockType.getBlockForState(pa.state);
        if (newblockstate == null) {
          newblockstate = blockType;
        }
        MSPlugin.getLog()
            .log("Setting block update at: " + x + y + z + ", " + pa.rotation + " to state:" + newblockstate.getId()
                + "  " + pipe.toString());
        var oldstatess = wc2.getBlockType(x, y, z).getId();
        if (oldstatess == newblockstate.getId()) {
          MSPlugin.getLog()
              .log("No change in state: " + oldstatess + " to " + newblockstate.getId());
          _store.removeComponent(ref, UpdatePipeComponent.getComponentType());
          return;
        }
        wc2.setBlock(x, y, z,
            BlockType.getAssetMap().getIndex(newblockstate.getId()), newblockstate,
            pa.rotation.index(), 0, 2);
        // wc2.setBlockInteractionState(x, y, z, blockType, pa.state, true);
        var entity = wc2.getBlockComponentEntity(x, y, z);
        if (entity == null) {
          MSPlugin.getLog().log("Block has no componentholder!");
          return;
        }
        var entitystore = entity.getStore();
        var npipe = entitystore.getComponent(entity, PipeComponent.getComponentType());
        npipe.updateFrom(pipe);
        MSPlugin.getLog().log("       END OF UPDATE    " + npipe.toString());

      });
    }

  }

  public static class PipeChangeSystem extends RefChangeSystem<ChunkStore, PipeComponent> {
    public static final ComponentType<ChunkStore, PipeComponent> COMPONENT_TYPE = PipeComponent.getComponentType();

    @Override
    public Query<ChunkStore> getQuery() {
      return Query.and(COMPONENT_TYPE);
    }

    @Override
    public ComponentType<ChunkStore, PipeComponent> componentType() {
      return COMPONENT_TYPE;
    }

    @Override
    public void onComponentAdded(Ref<ChunkStore> arg0, PipeComponent arg1, Store<ChunkStore> arg2,
        CommandBuffer<ChunkStore> arg3) {

    }

    @Override
    public void onComponentRemoved(Ref<ChunkStore> arg0, PipeComponent arg1, Store<ChunkStore> arg2,
        CommandBuffer<ChunkStore> arg3) {

    }

    @Override
    public void onComponentSet(Ref<ChunkStore> refChunkStore, PipeComponent pipeComponent, PipeComponent pipeComponent2,
        Store<ChunkStore> storeChunkStore,
        CommandBuffer<ChunkStore> commandBufferChunkStore) {

      MSPlugin.getLog().log("Setting component " + pipeComponent.toString() + " TO" + pipeComponent2.toString());

      var blockstate = storeChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
      var chunkref = blockstate.getChunkRef();
      if (chunkref == null || !chunkref.isValid()) {
        return;
      }
      var blockchunk = storeChunkStore.getComponent(chunkref, BlockChunk.getComponentType());
      int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
          ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
      int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
      int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
          ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));

      var test = getPipeArrangement(pipeComponent2.getPipeState());
      BlockType blockType = BlockType.getAssetMap()
          .getAsset(blockchunk.getBlock(ChunkUtil.xFromBlockInColumn(blockstate.getIndex()), y,
              ChunkUtil.zFromBlockInColumn(blockstate.getIndex())));

      if (blockType != null) {
        commandBufferChunkStore.run(_store -> {
          WorldChunk wc2 = _store.getComponent(chunkref, WorldChunk.getComponentType());
          PipeArrangement pa = test;
          var newblockstate = blockType.getBlockForState(pa.state);
          MSPlugin.getLog()
              .log("Setting bloc at: " + x + y + z + ", " + pa.rotation + " to state:"
                  + newblockstate.getId() + "  " + pipeComponent.toString());
          wc2.setBlock(x, y, z,
              BlockType.getAssetMap().getIndex(newblockstate.getId()), newblockstate,
              pa.rotation.index(), 0, 2);
        });
        var world = storeChunkStore.getExternalData().getWorld();
        for (var dir : Vector3i.BLOCK_SIDES) {
          var currentX = x + dir.x;
          var currentY = y + dir.y;
          var currentZ = z + dir.z;
          var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
          var chunkrefforblock = world.getChunk(chunkfor);
          if (chunkrefforblock != null) {
            var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
            var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
            if (holder != null) {
              var neighbourPipe = storeChunkStore.getComponent(entity, PipeComponent.getComponentType());
              if (neighbourPipe != null) {
                commandBufferChunkStore.ensureComponent(entity, UpdatePipeComponent.getComponentType());
              }
            }
          }
        }
      }

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

          var blockstate = commandBufferChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
          var chunkref = blockstate.getChunkRef();
          if (chunkref != null && chunkref.isValid()) {
            var blockchunk = commandBufferChunkStore.getComponent(chunkref, BlockChunk.getComponentType());
            int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
            int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
            int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
            // MSPlugin.get().getLogger().at(Level.INFO).log("Pipe is at:" + " " + x + " " +
            // y + " " + z);
            int occupiedMask = 0;
            var world = commandBufferChunkStore.getExternalData().getWorld();
            int iterationIndex = -1;
            var pipeComponent = storeChunkStore.getComponent(refChunkStore, PipeComponent.getComponentType());

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
                  if (neighbourPipe != null && neighbourPipe.canConnectTo(dir)) {

                    occupiedMask |= 1 << iterationIndex;
                    neighbours.add(dir);
                  }
                }
              }
            }
            if (pipeComponent != null && !pipeComponent.isMatchingMask(occupiedMask)) {
              MSPlugin.getLog().log(" before set" + pipeComponent.toString());
              pipeComponent.setDirectionalState(occupiedMask);
              pipeComponent.setPipeState(occupiedMask);
              var test = getPipeArrangement(pipeComponent.getPipeState());
              MSPlugin.getLog().log(" after set" + pipeComponent.toString() + "  new state" + test.state);
              BlockType blockType = BlockType.getAssetMap()
                  .getAsset(blockchunk.getBlock(ChunkUtil.xFromBlockInColumn(blockstate.getIndex()), y,
                      ChunkUtil.zFromBlockInColumn(blockstate.getIndex())));

              if (blockType != null) {
                commandBufferChunkStore.run(_store -> {
                  WorldChunk wc2 = _store.getComponent(chunkref, WorldChunk.getComponentType());
                  PipeArrangement pa = test;
                  var newblockstate = blockType.getBlockForState(pa.state);

                  for (Vector3i dir : neighbours) {
                    if (dir == null || (dir.x == 0 && dir.z == 0 && dir.y == 0))
                      continue;
                    var currentX = x + dir.x;
                    var currentY = y + dir.y;
                    var currentZ = z + dir.z;
                    // MSPlugin.get().getLogger().at(Level.INFO).log("neighbour Pipe is at:" + " " +
                    // currentX + " " +
                    // currentY + " " + currentZ);

                    var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
                    var chunkre = world.getChunk(chunkfor);
                    var chunkrefforblock = _store.getComponent(chunkre.getReference(), WorldChunk.getComponentType());
                    if (chunkrefforblock != null) {
                      var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
                      if (holder != null) {
                        var neighbourPipe = holder.getComponent(PipeComponent.getComponentType());
                        // MSPlugin.get().getLogger().at(Level.INFO)
                        // .log("Found pipe at: " + currentX + ", " + currentY + ", " + currentZ + ",."
                        // + neighbourPipe);
                        var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
                        if (neighbourPipe != null) {
                          _store.ensureComponent(entity, UpdatePipeComponent.getComponentType());
                        }
                      }
                    }
                  }
                  MSPlugin.getLog()
                      .log("Setting bloc at: " + x + y + z + ", " + pa.rotation + " to state:"
                          + newblockstate.getId() + "  " + pipeComponent.toString());
                  wc2.setBlock(x, y, z,
                      BlockType.getAssetMap().getIndex(newblockstate.getId()), newblockstate,
                      pa.rotation.index(), 0, 2);
                });
              }

            }
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
      if (removeReason != RemoveReason.REMOVE) {
        return;
      }
      if (storeChunkStore.getComponent(refChunkStore, UpdatePipeComponent.getComponentType()) != null
          || commandBufferChunkStore.getComponent(refChunkStore, UpdatePipeComponent.getComponentType()) != null) {
        return;
      }

      var blockstate = storeChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
      var chunkref = blockstate.getChunkRef();
      if (chunkref != null && chunkref.isValid()) {
        var blockchunk = storeChunkStore.getComponent(chunkref, BlockChunk.getComponentType());
        int x = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getX(),
            ChunkUtil.xFromBlockInColumn(blockstate.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(blockstate.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockchunk.getZ(),
            ChunkUtil.zFromBlockInColumn(blockstate.getIndex()));
        var world = storeChunkStore.getExternalData().getWorld();

        for (var dir : Vector3i.BLOCK_SIDES) {
          var currentX = x + dir.x;
          var currentY = y + dir.y;
          var currentZ = z + dir.z;
          var chunkfor = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
          var chunkrefforblock = world.getChunk(chunkfor);
          if (chunkrefforblock != null) {
            var holder = chunkrefforblock.getBlockComponentHolder(currentX, currentY, currentZ);
            var entity = chunkrefforblock.getBlockComponentEntity(currentX, currentY, currentZ);
            if (holder != null) {
              var neighbourPipe = storeChunkStore.getComponent(entity, PipeComponent.getComponentType());
              if (neighbourPipe != null && neighbourPipe.canConnectTo(dir)) {
                commandBufferChunkStore.run(_store -> {
                  _store.ensureComponent(entity, UpdatePipeComponent.getComponentType());
                });
              }
            }
          }
        }
      }

    }
  }

}
