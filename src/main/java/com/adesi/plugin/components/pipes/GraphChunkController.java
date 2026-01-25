package com.adesi.plugin.components.pipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;

import com.hypixel.hytale.math.vector.Vector3i;
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

  public void AddNode(Vector3i position, int OccupiedMask) {
    String currentPositionStr = VtoString(position);
    // figure out if we need to create a new node vs extending a existing one.
    for (Vector3i sides : Vector3i.BLOCK_SIDES) {
      Vector3i targetLocation = position.clone().add(sides);
      String targetLocationStr = VtoString(targetLocation);
      if (graphMap.containsKey(targetLocationStr) && (OccupiedMask & (1 << PipeComponent.getBitIndex(sides))) != 0) {
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
          AddJunctionsAround(position, OccupiedMask);
          return;
        }
      }
    }
    RegisterNewNode(position);
    AddJunctionsAround(position, OccupiedMask);
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

  private void AddJunctionsAround(Vector3i position, int OccupiedMask) {
    var targetLocationStr = VtoString(position);
    var ownNode = graphMap.get(targetLocationStr);
    var junctions = new ArrayList<Vector3i>();
    for (var dir : Vector3i.BLOCK_SIDES) {
      var sidesposition = position.clone().add(dir);
      var sidespositionStr = VtoString(sidesposition);
      if (graphMap.containsKey(sidespositionStr) && !graphMap.get(sidespositionStr).equals(ownNode)
          && (OccupiedMask * (1 << PipeComponent.getBitIndex(dir))) != 0) {
        junctions.add(dir);
        if (graphJunctionMap.containsKey(sidespositionStr)) {
          var junctionsAtNeighbour = graphJunctionMap.get(sidespositionStr);
          var newNeighbourJunction = Arrays.copyOf(junctionsAtNeighbour, junctionsAtNeighbour.length + 1);
          newNeighbourJunction[newNeighbourJunction.length - 1] = dir.clone().negate();
          graphJunctionMap.put(sidespositionStr, newNeighbourJunction);
        } else {
          graphJunctionMap.put(sidespositionStr, new Vector3i[] { dir.clone().negate() });
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
}
