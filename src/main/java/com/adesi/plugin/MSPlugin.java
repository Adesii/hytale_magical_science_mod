package com.adesi.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class MSPlugin extends JavaPlugin {

  public MSPlugin(JavaPluginInit init) {
    super(init);
  }

  @Override
  protected void setup() {
    super.setup();
    this.getCommandRegistry().registerCommand(new TestCommand("hello", "zooooo", false));
  }

}
