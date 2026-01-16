package com.adesi.plugin.fluid;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class PumpComponent implements Component<EntityStore> {

    public PumpComponent(PumpComponent other){
    }


    @NullableDecl
    @Override
    public Component<EntityStore> clone() {
        return new PumpComponent(this);
    }
}
