package com.adesi.plugin.components.pipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class GraphChunkController implements Component<ChunkStore> {
  private static final ArrayCodec<Vector3i> JunctionCodec = new ArrayCodec<>(Vector3i.CODEC, (o) -> {
    return new Vector3i[o];
  });
  public static final BuilderCodec<GraphChunkController> CODEC = BuilderCodec
      .builder(GraphChunkController.class, GraphChunkController::new)
      .append(
          new KeyedCodec<>("GraphNodeMap",
              new MapCodec<>(GraphNode.CODEC, HashMap::new)),
          (o, i) -> {
            o.graphNodeMap = new HashMap<>(i);
          }, (o) -> {
            return o.graphNodeMap;
          })
      .add()
      .append(new KeyedCodec<>("GraphMap", new MapCodec<>(Codec.STRING, HashMap::new)), (o, i) -> {
        o.graphMap = new HashMap<>(i);
      }, (o) -> {
        return o.graphMap;
      })
      .add()
      .append(new KeyedCodec<>("GraphJunctions",
          new MapCodec<>(JunctionCodec, HashMap::new)),
          (o, i) -> {
            o.graphJunctionMap = new HashMap<>(i);
          }, (o) -> {
            return o.graphJunctionMap;
          })
      .add()
      .build();

  private Map<String, String> graphMap; // Position,UUID
  private Map<String, GraphNode> graphNodeMap; // UUID,GraphNode
  private Map<String, Vector3i[]> graphJunctionMap; // Position,Junctions directions

  private String VtoString(Vector3i pos) {
    return pos.x + "," + pos.y + "," + pos.z;
  }

  public GraphNode RegisterNewNode(Vector3i position) {
    GraphNode newNode = new GraphNode(position, UUID.randomUUID());
    graphMap.put(VtoString(position), newNode.graphid.toString());
    graphNodeMap.put(newNode.graphid.toString(), newNode);
    return newNode;
  }

  public void AddNode(Vector3i position, ArrayList<Vector3i> neighbours) {
    String currentPositionStr = VtoString(position);
    // figure out if we need to create a new node vs extending a existing one.
    for (Vector3i sides : neighbours) {
      Vector3i targetLocation = position.clone().add(sides);
      String targetLocationStr = VtoString(targetLocation);
      if (graphMap.containsKey(targetLocationStr)) {
        GraphNode existingNode = graphNodeMap.get(graphMap.get(targetLocationStr));
        if (existingNode.extendDirection.equals(Vector3i.ALL_ONES)) {
          existingNode.extendDirection = sides;
        }
        if (PipeSystem.axisOf(sides).equals(PipeSystem.axisOf(existingNode.extendDirection))) {
          if (existingNode.extendDirection.equals(sides)) {
            // TODO: add update logic for ItemTravel so they don't teleport back a block
            existingNode.startPosition = position;
          }
          existingNode.extendLength++;
          graphMap.put(currentPositionStr, existingNode.graphid.toString());
          MSPlugin.getLog().log("Extending Node at " + targetLocationStr);
          AddJunctionsAround(position, neighbours);
          return;
        }
      }
    }
    RegisterNewNode(position);
    AddJunctionsAround(position, neighbours);
  }

  public void RemoveNode(Vector3i position) {
    // figure out if we need to retact a node or split a existing one.
    String targetLocationStr = VtoString(position);
    if (!graphMap.containsKey(targetLocationStr)) {
      MSPlugin.get().getLogger().atWarning()
          .log("A pipe was deleted that wasn't part of the Graph System! " + targetLocationStr);
      return;
    }
    GraphNode existingNode = graphNodeMap.get(graphMap.get(targetLocationStr));
    if (existingNode.extendLength > 1) {
      // TODO: Split the node into two if the removed location is in the middle.
      existingNode.extendLength--;
      if (existingNode.startPosition.equals(position)) {
        existingNode.startPosition.add(existingNode.extendDirection);
      }

      // graphMap.put(VtoString(position), existingNode.graphid.toString());
    } else {
      graphNodeMap.remove(existingNode.graphid.toString());
    }
    graphMap.remove(targetLocationStr);
    RemoveJunctionsAround(position);
  }

  private void AddJunctionsAround(Vector3i position, ArrayList<Vector3i> neighbours) {
    var targetLocationStr = VtoString(position);
    var ownNode = graphMap.get(targetLocationStr);
    var junctions = new ArrayList<Vector3i>();
    for (var dir : neighbours) {
      var sidesposition = position.clone().add(dir);
      var sidespositionStr = VtoString(sidesposition);
      if (graphMap.containsKey(sidespositionStr) && !graphMap.get(sidespositionStr).equals(ownNode)) {
        junctions.add(dir.clone());
        if (graphJunctionMap.containsKey(sidespositionStr)) {
          var junctionsAtNeighbour = graphJunctionMap.get(sidespositionStr);
          var newNeighbourJunction = Arrays.copyOf(junctionsAtNeighbour,
              junctionsAtNeighbour.length + 1);
          newNeighbourJunction[newNeighbourJunction.length - 1] = dir.clone().negate();
          graphJunctionMap.put(sidespositionStr, newNeighbourJunction);
        } else {
          var neighbourPipe = graphNodeMap.get(graphMap.get(sidespositionStr));
          graphJunctionMap.put(sidespositionStr, new Vector3i[] { dir.clone().negate(),
              neighbourPipe.extendDirection.clone(),
              neighbourPipe.extendDirection.clone().negate() });
        }
      }
    }
    if (!junctions.isEmpty()) {
      var junctionsAround = new Vector3i[junctions.size()];
      graphJunctionMap.put(targetLocationStr, junctions.toArray(junctionsAround));
    }

  }

  private void RemoveJunctionsAround(Vector3i position) {
    var targetLocationStr = VtoString(position);
    if (graphJunctionMap.containsKey(targetLocationStr)) {
      graphJunctionMap.remove(targetLocationStr);
    }
    for (var dir : Vector3i.BLOCK_SIDES) {
      var sidespositions = position.clone().add(dir);
      var sidespositionstr = VtoString(sidespositions);
      if (graphJunctionMap.containsKey(sidespositionstr)) {
        var oldarray = graphJunctionMap.get(sidespositionstr);
        if (oldarray.length == 1 && oldarray[0].equals(dir.clone().negate())) {
          graphJunctionMap.remove(sidespositionstr);
          continue;
        }
        var newArray = new ArrayList<Vector3i>();
        for (var entryDirection : oldarray) {
          if (!entryDirection.equals(dir.clone().negate())) {
            newArray.add(entryDirection);
          }
        }
        if (newArray.size() == 0) {
          graphJunctionMap.remove(sidespositionstr);
        } else {
          var newArrayArr = new Vector3i[newArray.size()];
          graphJunctionMap.put(sidespositionstr, newArray.toArray(newArrayArr));
        }
      }
    }

  }

  public Collection<GraphNode> getNodes() {
    return graphNodeMap.values();
  }

  public Set<Entry<String, Vector3i[]>> getJunctions() {
    return graphJunctionMap.entrySet();
  }

  public GraphChunkController() {
    graphNodeMap = new HashMap<>();
    graphMap = new HashMap<>();
    graphJunctionMap = new HashMap<>();
  }

  public GraphChunkController(GraphChunkController other) {
    this.graphNodeMap = new HashMap<>(other.graphNodeMap);
    this.graphMap = new HashMap<>(other.graphMap);
    this.graphJunctionMap = new HashMap<>(other.graphJunctionMap);
  }

  @Override
  public Component<ChunkStore> clone() {
    return new GraphChunkController(this);
  }

  public static ComponentType<ChunkStore, GraphChunkController> getGraphChunkControllerType() {
    return MSPlugin.get().getGraphChunkControllerType();
  }

  public void addItemToPipe(World world, Vector3i vector3i, ItemStack itemStack) {
    if (itemStack == null || !graphMap.containsKey(VtoString(vector3i)))
      return;
    var pipeid = graphMap.get(VtoString(vector3i));
    var pipenode = graphNodeMap.get(pipeid);
    var reverse = false;
    if (pipenode.startPosition.distanceSquaredTo(vector3i) > pipenode.startPosition.clone()
        .add(pipenode.extendDirection.clone().scale(pipenode.extendLength)).distanceSquaredTo(vector3i)) {
      reverse = true;
    }
    var transportPacket = TransportPacket.GenerateTransportPacket(itemStack, pipeid,
        pipenode.extendDirection.clone().scale(reverse ? -1.0 : 1.0), vector3i.toVector3d(),
        new Vector3f());
    // find if the end is closer or the start
    world.execute(() -> {
      world.getEntityStore().getStore().addEntity(transportPacket, AddReason.SPAWN);
    });
  }

  public class JunctionResult {
    public Vector3i JunctionDirection;
    public String NewPipeID;
  }

  public JunctionResult atJunction(TransportPacket packet, Vector3d position) {
    var rounded_position = new Vector3i((int) Math.round(position.x), (int) Math.round(position.y),
        (int) Math.round(position.z));
    if (rounded_position.toVector3d().distanceSquaredTo(position) > 0.01f) {
      return null;
    }
    var pipe = graphNodeMap.get(packet.getPipeID());
    if (pipe == null) {
      return null;
    }

    var junction = graphJunctionMap.get(VtoString(rounded_position));
    if (junction == null) {
      return null;
    }
    var take_it = ThreadLocalRandom.current().nextBoolean();
    if (rounded_position.equals(pipe.startPosition) || rounded_position
        .equals(pipe.startPosition.clone().add(pipe.extendDirection.clone().scale(pipe.extendLength)))) {
      take_it = true;
    }
    if (!take_it) {
      return null;
    }
    var direction = packet.getPipeDirection();
    var pickedJunction = junction[ThreadLocalRandom.current().nextInt(junction.length)];
    if (direction.equals(pickedJunction.clone().negate())) {
      return null;
    }
    var newjunction = new JunctionResult();
    newjunction.JunctionDirection = pickedJunction;
    newjunction.NewPipeID = graphMap.get(VtoString(rounded_position.clone().add(pickedJunction)));

    return newjunction;
  }

  public boolean isOnPipe(Vector3d position) {
    var rounded_position = new Vector3i(
        (int) Math.round(position.x),
        (int) Math.round(position.y),
        (int) Math.round(position.z));
    return graphMap.containsKey(VtoString(rounded_position));
  }
}
