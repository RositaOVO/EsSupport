package org.essupport.mixin;

import net.minecraft.server.level.ServerPlayer;

import org.espetro.network.VehicleSupplyActionPacket;
import org.espetro.network.VehicleSupplyActionPacket.Interaction;
import org.essupport.core.SupportTypes.Resource;
import org.essupport.server.SupportServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VehicleSupplyActionPacket.class, remap = false)
public abstract class SupplyTransferMixin {
    @Unique private static final ThreadLocal<Integer> essupport$before = new ThreadLocal<>();

    @Inject(
            method = {"loadAmmo", "unloadAmmo"},
            at = @At("HEAD"))
    private static void essupport$ammoBefore(
            ServerPlayer p, Interaction i, int chunk, CallbackInfo ci) {
        essupport$before.set(i.supply().getAmmo());
    }

    @Inject(
            method = {"loadConstruction", "unloadConstruction"},
            at = @At("HEAD"))
    private static void essupport$materialsBefore(
            ServerPlayer p, Interaction i, int chunk, CallbackInfo ci) {
        essupport$before.set(i.supply().getConstruction());
    }

    @Inject(
            method = {"loadAmmo", "unloadAmmo"},
            at = @At("RETURN"))
    private static void essupport$ammoAfter(
            ServerPlayer p, Interaction i, int chunk, CallbackInfo ci) {
        essupport$record(p, i, Resource.AMMO, i.supply().getAmmo());
    }

    @Inject(
            method = {"loadConstruction", "unloadConstruction"},
            at = @At("RETURN"))
    private static void essupport$materialsAfter(
            ServerPlayer p, Interaction i, int chunk, CallbackInfo ci) {
        essupport$record(p, i, Resource.MATERIALS, i.supply().getConstruction());
    }

    @Unique
    private static void essupport$record(ServerPlayer p, Interaction i, Resource r, int after) {
        Integer before = essupport$before.get();
        essupport$before.remove();
        if (before == null || before == after) return;
        SupportServer.transfer(
                p,
                i.vehicleId(),
                i.mainBase() ? null : i.radio() == null ? null : i.radio().getBastionId(),
                r,
                after > before,
                Math.abs(after - before),
                before);
    }
}
