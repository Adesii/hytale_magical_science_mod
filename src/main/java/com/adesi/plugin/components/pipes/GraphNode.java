package com.adesi.plugin.components.pipes;

import java.util.UUID;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;

public class GraphNode {
  public static final BuilderCodec<GraphNode> CODEC = BuilderCodec.builder(GraphNode.class, GraphNode::new)
      .append(new KeyedCodec<>("StartPosition", Vector3i.CODEC), (o, i) -> {
        o.startPosition = i;
      }, (o) -> {
        return o.startPosition;
      }).add().append(new KeyedCodec<>("ExtendDirection", Vector3i.CODEC), (o, i) -> {
        o.extendDirection = i;
      }, (o) -> {
        return o.extendDirection;
      }).add().append(new KeyedCodec<>("ExtendLength", Codec.INTEGER), (o, i) -> {
        o.extendLength = i;
      }, (o) -> {
        return o.extendLength;
      }).add().append(new KeyedCodec<>("GraphID", Codec.UUID_STRING), (o, i) -> {
        o.graphid = i;
      }, (o) -> {
        return o.graphid;
      }).add().build();

  public Vector3i startPosition;
  public Vector3i extendDirection;
  public int extendLength;
  public UUID graphid;

  public GraphNode() {

  }

  public GraphNode(Vector3i start, UUID id) {
    this.startPosition = start;
    this.graphid = id;
    extendLength = 1;
    extendDirection = Vector3i.ALL_ONES;
  }

  @Override
  public String toString() {
    return "GraphNode{" +
        "startPosition=" + startPosition +
        ", extendDirection=" + extendDirection +
        ", extendLength=" + extendLength +
        ", graphid=" + graphid +
        '}';
  }
}
