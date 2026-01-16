
package com.adesi.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TestCommand extends AbstractPlayerCommand {

  protected TestCommand(String name, String description, boolean requiresConfirmation) {
    super(name, description, requiresConfirmation);
  }

  @Override
  protected void execute(CommandContext arg0, Store<EntityStore> arg1, Ref<EntityStore> arg2, PlayerRef arg3,
      World arg4) {
    arg0.sendMessage(Message.raw("YO2222"));
  }
}
