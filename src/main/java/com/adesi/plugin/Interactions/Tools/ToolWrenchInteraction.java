package com.adesi.plugin.Interactions.Tools;

import com.adesi.plugin.MSPlugin;
import com.adesi.plugin.components.pipes.PipeComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.corecomponents.world.SensorCanPlace.Direction;
import com.hypixel.hytale.server.spawning.util.FloodFillPositionSelector.Debug;

public class ToolWrenchInteraction extends SimpleBlockInteraction {
  public static final BuilderCodec<ToolWrenchInteraction> CODEC = BuilderCodec.builder(
      ToolWrenchInteraction.class, ToolWrenchInteraction::new, SimpleBlockInteraction.CODEC)
      .documentation("Attempts to wrench the target block, executing interactions on it if any.")
      .build();

  private Vector3i SnapToAxis(Vector3d d) {
    double dx = Math.abs(d.x);
    double dy = Math.abs(d.y);
    double dz = Math.abs(d.z);

    if (dx >= dy && dx >= dz)
      return new Vector3i(d.x > 0 ? 1 : -1, 0, 0);
    else if (dy >= dx && dy >= dz)
      return new Vector3i(0, d.y > 0 ? 1 : -1, 0);
    else
      return new Vector3i(0, 0, d.z > 0 ? 1 : -1);
  }

  public final class RayHit {
    public Box box;
    public double t;
    public final Vector3d position = new Vector3d();
    public final Vector3d normal = new Vector3d();
  }

  public final class RayAABB {

    private static final double EPS = 1e-9;

    public static boolean intersectBox(
        Vector3d origin,
        Vector3d dir,
        Box box,
        double maxDistance,
        RayHit out) {
      double tMin = 0.0;
      double tMax = maxDistance;

      int hitAxis = -1;
      int hitSign = 0;

      // X axis
      if (Math.abs(dir.x) < EPS) {
        if (origin.x < box.min.x || origin.x > box.max.x)
          return false;
      } else {
        double inv = 1.0 / dir.x;
        double t1 = (box.min.x - origin.x) * inv;
        double t2 = (box.max.x - origin.x) * inv;

        int sign = t1 < t2 ? -1 : 1;
        if (t1 > t2) {
          double tmp = t1;
          t1 = t2;
          t2 = tmp;
        }

        if (t1 > tMin) {
          tMin = t1;
          hitAxis = 0;
          hitSign = sign;
        }
        tMax = Math.min(tMax, t2);
        if (tMin > tMax)
          return false;
      }

      // Y axis
      if (Math.abs(dir.y) < EPS) {
        if (origin.y < box.min.y || origin.y > box.max.y)
          return false;
      } else {
        double inv = 1.0 / dir.y;
        double t1 = (box.min.y - origin.y) * inv;
        double t2 = (box.max.y - origin.y) * inv;

        int sign = t1 < t2 ? -1 : 1;
        if (t1 > t2) {
          double tmp = t1;
          t1 = t2;
          t2 = tmp;
        }

        if (t1 > tMin) {
          tMin = t1;
          hitAxis = 1;
          hitSign = sign;
        }
        tMax = Math.min(tMax, t2);
        if (tMin > tMax)
          return false;
      }

      // Z axis
      if (Math.abs(dir.z) < EPS) {
        if (origin.z < box.min.z || origin.z > box.max.z)
          return false;
      } else {
        double inv = 1.0 / dir.z;
        double t1 = (box.min.z - origin.z) * inv;
        double t2 = (box.max.z - origin.z) * inv;

        int sign = t1 < t2 ? -1 : 1;
        if (t1 > t2) {
          double tmp = t1;
          t1 = t2;
          t2 = tmp;
        }

        if (t1 > tMin) {
          tMin = t1;
          hitAxis = 2;
          hitSign = sign;
        }
        tMax = Math.min(tMax, t2);
        if (tMin > tMax)
          return false;
      }

      if (tMin < 0.0)
        return false;

      // Fill result
      out.box = box;
      out.t = tMin;

      out.position.assign(
          origin.x + dir.x * tMin,
          origin.y + dir.y * tMin,
          origin.z + dir.z * tMin);

      out.normal.assign(0, 0, 0);
      if (hitAxis == 0)
        out.normal.x = hitSign;
      else if (hitAxis == 1)
        out.normal.y = hitSign;
      else
        out.normal.z = hitSign;

      return true;
    }
  }

  public boolean intersectBoxes(
      Vector3d origin,
      Vector3d direction,
      Box[] boxes,
      double maxDistance,
      RayHit out) {
    boolean hit = false;
    double closest = maxDistance;

    RayHit temp = new RayHit();

    for (Box box : boxes) {
      if (RayAABB.intersectBox(origin, direction, box, closest, temp)) {
        closest = temp.t;
        hit = true;

        out.box = temp.box;
        out.t = temp.t;
        out.position.assign(temp.position);
        out.normal.assign(temp.normal);
      }
    }

    return hit;
  }

  @Override
  protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBufferEntityStore,
      InteractionType interactionType,
      InteractionContext interactionContext, ItemStack itemStack, Vector3i targetBlock,
      CooldownHandler cooldownHandler) {
    if (!itemStack.getItemId().toLowerCase().contains("wrench")) {
      // MSPlugin.getLog().log("Item is not a wrench!");
      interactionContext.getState().state = InteractionState.Failed;
      return;
    }

    // TODO: Implement a more generic way to do wrench interactions...
    var worldchunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
    var entity = worldchunk
        .getBlockComponentEntity(targetBlock.x, targetBlock.y, targetBlock.z);
    if (entity == null) {
      // MSPlugin.getLog().log("Block has no componentholder!");
      interactionContext.getState().state = InteractionState.Failed;
      return;
    }
    var entitystore = entity.getStore();
    // MSPlugin.getLog().log("Wrenching on block: " + targetBlock.x + ", " +
    // targetBlock.y + ", " + targetBlock.z);

    var pipecomponent = entitystore.getComponent(entity, PipeComponent.getComponentType());
    if (pipecomponent == null) {
      // MSPlugin.getLog().log("No pipe component found!");
      interactionContext.getState().state = InteractionState.Skip;
      return;
    }

    var ref = interactionContext.getEntity();

    var ttt = TargetUtil.getLook(ref, ref.getStore());
    var bbb = BlockBoundingBoxes.getAssetMap().getAsset(world.getBlockType(targetBlock).getHitboxType());
    var rotation = RotationTuple.get(world.getBlockRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z));
    RayHit hit = new RayHit();
    if (intersectBoxes(ttt.getPosition().subtract(targetBlock), ttt.getDirection().normalize(),
        bbb.get(rotation.yaw(), rotation.pitch(), rotation.roll()).getDetailBoxes(), 100, hit)) {

      var dir = SnapToAxis(hit.position.clone().subtract(0.5).normalize()).negate();
      pipecomponent.toggleBlockedDirection(dir);
      entitystore.putComponent(entity, PipeComponent.getComponentType(),
          pipecomponent);
      var dd = dir.clone().negate();
      var neighbourpipe = worldchunk.getBlockComponentEntity(targetBlock.x + dd.x, targetBlock.y + dd.y,
          targetBlock.z + dd.z);
      if (neighbourpipe != null) {
        var neighbourpipecomp = neighbourpipe.getStore().getComponent(neighbourpipe,
            PipeComponent.getComponentType());
        if (neighbourpipecomp != null) {
          neighbourpipecomp.setBlockedDirection(dd.clone().negate(), !pipecomponent.getBlockedState(dd));
          neighbourpipe.getStore().putComponent(neighbourpipe,
              PipeComponent.getComponentType(), neighbourpipecomp);
          DebugUtils.addCube(world,
              new Vector3d(targetBlock.x + dd.x, targetBlock.y + dd.y, targetBlock.z +
                  dd.z),
              new Vector3f(0.3f, 0.3f, 0.3f), 0.6, 3);
        }
      }

    }

  }

  @Override
  protected void simulateInteractWithBlock(InteractionType interactionType, InteractionContext interactionContext,
      ItemStack itemStack, World world,
      Vector3i targetBlock) {
  }

}
