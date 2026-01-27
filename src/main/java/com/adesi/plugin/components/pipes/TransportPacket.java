package com.adesi.plugin.components.pipes;

import java.util.logging.Level;

import com.adesi.plugin.MSPlugin;
import com.hypixel.hytale.builtin.model.commands.ModelCommand;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TransportPacket implements Component<EntityStore> {
  public static final BuilderCodec<TransportPacket> CODEC = BuilderCodec
      .builder(TransportPacket.class, TransportPacket::new)
      .append(new KeyedCodec<>("PacketStack", ItemStack.CODEC),
          TransportPacket::setPacketStack, (o) -> o.packetStack)
      .add()
      .append(new KeyedCodec<>("PipeID", Codec.STRING), TransportPacket::setPipeID, (o) -> o.pipeID).add()
      .append(new KeyedCodec<>("PipeDirection", Vector3i.CODEC), TransportPacket::setPipeDirection,
          (o) -> o.pipeDirection)
      .add()
      .build();

  private ItemStack packetStack;

  public ItemStack getPacketStack() {
    return packetStack;
  }

  public void setPacketStack(ItemStack packetStack) {
    this.packetStack = packetStack;
  }

  private String pipeID;

  public String getPipeID() {
    return pipeID;
  }

  public void setPipeID(String pipeID) {
    this.pipeID = pipeID;
  }

  private Vector3i pipeDirection = Vector3i.FORWARD;

  public Vector3i getPipeDirection() {
    return pipeDirection;
  }

  public void setPipeDirection(Vector3i pipeDirection) {
    this.pipeDirection = pipeDirection;
  }

  public TransportPacket() {
  }

  public TransportPacket(ItemStack packetStack, String pipeID, Vector3i StartDirection) {
    this.packetStack = packetStack;
    this.pipeID = pipeID;
    this.pipeDirection = StartDirection;
  }

  public TransportPacket(TransportPacket other) {
    this.packetStack = other.packetStack;
    this.pipeID = other.pipeID;
    this.pipeDirection = other.pipeDirection;
  }

  @Override
  public Component<EntityStore> clone() {
    return new TransportPacket(this);
  }

  public static ComponentType<EntityStore, TransportPacket> getComponentType() {
    return MSPlugin.get().getTransportPacketType();
  }

  public static Holder<EntityStore> GenerateTransportPacket(ItemStack itemStack, String pipeID, Vector3i StartDirection,
      Vector3d StartPosition, Vector3f StartRotation) {
    if (itemStack != null && !itemStack.isEmpty() && itemStack.isValid()) {
      Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
      TransportPacket itemComponent = new TransportPacket(itemStack, pipeID, StartDirection);
      holder.addComponent(getComponentType(), itemComponent);
      holder.addComponent(TransformComponent.getComponentType(),
          new TransformComponent(StartPosition, StartRotation));
      holder.ensureComponent(UUIDComponent.getComponentType());
      holder.ensureComponent(Intangible.getComponentType());
      holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(0.7f));
      holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(itemStack));
      holder.ensureComponent(PreventItemMerging.getComponentType());
      holder.ensureComponent(PreventPickup.getComponentType());
      return holder;
    } else {
      MSPlugin.LOGGER.at(Level.WARNING).log("Attempted to Pipe invalid item %s at %s", itemStack, StartPosition);
      return null;
    }
  }
}
