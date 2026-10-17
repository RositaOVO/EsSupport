package org.essupport.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record SupportAction(
        Type type, long session, UUID id, int ammo, int materials, String note) {
    public enum Type {
        VIEW,
        SUPPLY,
        CLAIM,
        RELEASE,
        CANCEL
    }

    public static final UUID NONE = new UUID(0, 0);

    public static SupportAction simple(Type type, long session, UUID id) {
        return new SupportAction(type, session, id == null ? NONE : id, 0, 0, "");
    }

    public static SupportAction read(FriendlyByteBuf b) {
        return new SupportAction(
                b.readEnum(Type.class),
                b.readLong(),
                b.readUUID(),
                b.readVarInt(),
                b.readVarInt(),
                b.readUtf(96));
    }

    public void write(FriendlyByteBuf b) {
        b.writeEnum(type);
        b.writeLong(session);
        b.writeUUID(id);
        b.writeVarInt(ammo);
        b.writeVarInt(materials);
        b.writeUtf(note, 96);
    }
}
