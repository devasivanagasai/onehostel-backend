package com.onehostel.common.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PrivTokensTest {

    @Test
    void buildsOneCharacterPerBit() {
        assertThat(PrivTokens.of(List.of(1, 3), 4)).isEqualTo("1010");
        assertThat(PrivTokens.of(List.of(), 3)).isEqualTo("000");
    }

    @Test
    void ignoresBitsOutsideTheToken() {
        assertThat(PrivTokens.of(Arrays.asList(0, -1, 5, null), 4)).isEqualTo("0000");
    }

    @Test
    void zeroBitDenies() {
        assertThat(PrivTokens.hasBit("1010", 2)).isFalse();
        assertThat(PrivTokens.hasBit("1010", 4)).isFalse();
    }

    @Test
    void oneBitAllows() {
        assertThat(PrivTokens.hasBit("1010", 1)).isTrue();
        assertThat(PrivTokens.hasBit("1010", 3)).isTrue();
    }

    @Test
    void missingTokenPositionOrBitDenies() {
        assertThat(PrivTokens.hasBit(null, 1)).isFalse();
        assertThat(PrivTokens.hasBit("", 1)).isFalse();
        assertThat(PrivTokens.hasBit("11", 3)).isFalse();
        assertThat(PrivTokens.hasBit("11", 0)).isFalse();
        assertThat(PrivTokens.hasBit("11", -1)).isFalse();
    }

    @Test
    void anythingOtherThanOneDenies() {
        assertThat(PrivTokens.hasBit("x1", 1)).isFalse();
    }

    @Test
    void unionKeepsEveryRolesBits() {
        assertThat(PrivTokens.union(List.of("100", "00101"))).isEqualTo("10101");
        assertThat(PrivTokens.union(List.of())).isEmpty();
    }
}
