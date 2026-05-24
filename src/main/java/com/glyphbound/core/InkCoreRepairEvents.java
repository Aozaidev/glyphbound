package com.glyphbound.core;

import com.aozainkmc.api.InkStaffTier;
import com.aozainkmc.api.InkStaffs;
import com.glyphbound.Glyphbound;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = Glyphbound.MOD_ID)
public final class InkCoreRepairEvents {
    private static final int MAX_CORES_PER_REPAIR = 4;

    private InkCoreRepairEvents() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack staff = event.getLeft();
        ItemStack material = event.getRight();
        InkStaffTier tier = InkStaffs.tier(staff).orElse(null);
        if (tier == null || !staff.isDamageableItem() || !material.is(GlyphboundItems.INK_CORE.get())) {
            return;
        }

        int damage = staff.getDamageValue();
        if (damage <= 0) {
            return;
        }

        int repairPerCore = Math.max(1, staff.getMaxDamage() / 4);
        int coresNeeded = Math.max(1, (damage + repairPerCore - 1) / repairPerCore);
        int coresUsed = Math.min(Math.min(material.getCount(), MAX_CORES_PER_REPAIR), coresNeeded);
        if (coresUsed <= 0) {
            return;
        }

        ItemStack output = staff.copy();
        output.setDamageValue(Math.max(0, damage - repairPerCore * coresUsed));
        event.setOutput(output);
        event.setMaterialCost(coresUsed);
        event.setCost(Math.max(1, tier.ordinal() + 1 + coresUsed));
    }
}
