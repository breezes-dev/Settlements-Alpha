package dev.breezes.settlements.domain.entities;

/**
 * The vanilla lantern variant a villager owns for carrying.
 */
public enum OwnedLanternType {

    NONE,
    LANTERN,
    SOUL_LANTERN,
    ;

    private static final OwnedLanternType[] VALUES = values();

    public byte toNetworkByte() {
        return (byte) this.ordinal();
    }

    /**
     * Decodes a lantern variant, returning NONE for an unknown byte.
     */
    public static OwnedLanternType fromNetworkByte(byte encoded) {
        int index = Byte.toUnsignedInt(encoded);
        if (index >= VALUES.length) {
            return NONE;
        }

        return VALUES[index];
    }

}
