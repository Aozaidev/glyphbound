package com.glyphbound.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.Holder;

public final class GlyphAttributes {
    private GlyphAttributes() {
    }

    public static void setTransient(
        LivingEntity entity,
        Holder<Attribute> attribute,
        ResourceLocation id,
        double amount,
        AttributeModifier.Operation operation,
        boolean active
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        boolean present = instance.getModifier(id) != null;
        if (active && !present) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        } else if (!active && present) {
            instance.removeModifier(id);
        }
    }

    public static void setTransientAmount(
        LivingEntity entity,
        Holder<Attribute> attribute,
        ResourceLocation id,
        double amount,
        AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.amount() == amount && existing.operation() == operation) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    public static void remove(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }
}
