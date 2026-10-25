package dev.breezes.settlements.domain.ai.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NavigationType {

    STROLL(0.4F),
    WALK(0.5F),
    RUN(0.7F),
    SPRINT(0.9F),
    PANIC(0.9F),
    ;

    private static final NavigationType[] VALUES = values();

    private final float baseModifier;

    public byte toNetworkByte() {
        return (byte) this.ordinal();
    }

    public static NavigationType fromNetworkByte(byte encoded) {
        int index = Byte.toUnsignedInt(encoded);
        if (index >= VALUES.length) {
            return WALK;
        }
        return VALUES[index];
    }

}
