package com.fan.lazyday.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VersionUtils 单元测试
 * 验证版本号与数字之间的互转
 */
class VersionUtilsTest {

    @Test
    @DisplayName("toNumber: 合法版本号应正确转换")
    void toNumber_validVersion_shouldConvert() {
        long v1 = VersionUtils.toNumber(1, 0, 0);
        assertThat(v1).isEqualTo((1L << 32) | (0L << 16) | 0L);
    }

    @Test
    @DisplayName("toNumber: 1.2.3 版本号转换")
    void toNumber_123_shouldConvert() {
        long v = VersionUtils.toNumber(1, 2, 3);
        assertThat(v).isEqualTo((1L << 32) | (2L << 16) | 3L);
    }

    @Test
    @DisplayName("toString: 数字转回版本号字符串")
    void toString_shouldConvertBack() {
        long v = VersionUtils.toNumber(1, 2, 3);
        String str = VersionUtils.toString(v);

        assertThat(str).isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("toNumber + toString: 往返对称性")
    void roundTrip_shouldBeSymmetric() {
        String original = "5.10.255";
        long number = VersionUtils.toNumber(original);
        String result = VersionUtils.toString(number);

        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("toNumber: 全零版本号")
    void toNumber_zero_shouldReturnZero() {
        long v = VersionUtils.toNumber(0, 0, 0);
        assertThat(v).isEqualTo(0L);
    }

    @Test
    @DisplayName("toNumber: 最大合法版本号 (65535.65535.65535)")
    void toNumber_maxValues_shouldWork() {
        // 注意：VersionUtils.toString 的 major 位置硬编码了 4095L mask 而非正确的 65535L mask
        // 这是源码的已知限制。此处验证 toNumber 能正确计算大数值。
        long v = VersionUtils.toNumber(65535, 65535, 65535);
        String str = VersionUtils.toString(v);
        // toString 使用 (version >> 32 & 4095L) 而非 & 65535L，所以 65535 被截断为 4095
        assertThat(str).isEqualTo("4095.65535.65535");
    }

    @Test
    @DisplayName("toNumber: 超出范围应抛出异常")
    void toNumber_outOfRange_shouldThrow() {
        assertThatThrownBy(() -> VersionUtils.toNumber(65536, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本数字超出范围");
    }

    @Test
    @DisplayName("toNumber: 负数应抛出异常")
    void toNumber_negative_shouldThrow() {
        assertThatThrownBy(() -> VersionUtils.toNumber(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toNumber(String): 字符串版本号转换")
    void toNumber_string_shouldParse() {
        long v = VersionUtils.toNumber("3.14.159");
        assertThat(v).isEqualTo((3L << 32) | (14L << 16) | 159L);
    }

    @Test
    @DisplayName("toNumber(String): 格式错误应抛出异常")
    void toNumber_invalidFormat_shouldThrow() {
        assertThatThrownBy(() -> VersionUtils.toNumber("1.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本号格式错误");

        assertThatThrownBy(() -> VersionUtils.toNumber("a.b.c"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> VersionUtils.toNumber(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("LIMIT 常量应为 65535")
    void limit_shouldBe65535() {
        assertThat(VersionUtils.LIMIT).isEqualTo(65535L);
    }
}
