package com.fan.lazyday.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SuggestUtils 单元测试
 * 验证名称去重建议逻辑
 */
class SuggestUtilsTest {

    @Test
    @DisplayName("suggest: 名称不存在时直接返回原名称")
    void suggest_nameNotExists_shouldReturnOriginal() {
        AtomicLong seq = new AtomicLong(1);
        Set<String> existing = Set.of("other", "names");

        String result = SuggestUtils.suggest("newname", existing, seq::getAndIncrement);

        assertThat(result).isEqualTo("newname");
    }

    @Test
    @DisplayName("suggest: 名称已存在时追加序号")
    void suggest_nameExists_shouldAppendSequence() {
        AtomicLong seq = new AtomicLong(1);
        Set<String> existing = Set.of("myapp", "myapp1");

        String result = SuggestUtils.suggest("myapp", existing, seq::getAndIncrement);

        // "myapp" 已存在，seq 从 1 开始尝试 "myapp1"(也存在)，然后 "myapp2"
        assertThat(result).isEqualTo("myapp2");
    }

    @Test
    @DisplayName("suggest2: 名称不存在时直接返回原名称")
    void suggest2_nameNotExists_shouldReturnOriginal() {
        AtomicLong seq = new AtomicLong(1);
        Set<String> existing = Set.of("other");

        String result = SuggestUtils.suggest2("newname", existing, seq::getAndIncrement);

        assertThat(result).isEqualTo("newname");
    }

    @Test
    @DisplayName("suggest2: 名称已存在时追加括号序号")
    void suggest2_nameExists_shouldAppendParenthesizedSequence() {
        AtomicLong seq = new AtomicLong(1);
        Set<String> existing = Set.of("myapp", "myapp(1)");

        String result = SuggestUtils.suggest2("myapp", existing, seq::getAndIncrement);

        // 前缀 "myapp" 已存在，尝试 "myapp(1)"(也存在)，然后 "myapp(2)"
        assertThat(result).isEqualTo("myapp(2)");
    }

    @Test
    @DisplayName("suggest3: 名称不存在时直接返回原名称")
    void suggest3_nameNotExists_shouldReturnOriginal() {
        Set<String> existing = Set.of("other");

        String result = SuggestUtils.suggest3("newname", existing);

        assertThat(result).isEqualTo("newname");
    }

    @Test
    @DisplayName("suggest3: 名称已存在时自增序号")
    void suggest3_nameExists_shouldAutoIncrement() {
        Set<String> existing = Set.of("doc", "doc(1)", "doc(2)");

        String result = SuggestUtils.suggest3("doc", existing);

        assertThat(result).isEqualTo("doc(3)");
    }

    @Test
    @DisplayName("suggest3: 带有序号的名称应从现有序号递增")
    void suggest3_existingWithNumber_shouldIncrementFromExisting() {
        Set<String> existing = Set.of("report(3)", "report(4)");

        String result = SuggestUtils.suggest3("report(3)", existing);

        // 前缀 "report"，当前序号 3，先尝试 4(存在)，再尝试 5
        assertThat(result).isEqualTo("report(5)");
    }

    @Test
    @DisplayName("suggestPrefix: 正常名称直接返回")
    void suggestPrefix_normalName_shouldReturnAsIs() {
        String result = SuggestUtils.suggestPrefix("myapp");

        assertThat(result).isEqualTo("myapp");
    }

    @Test
    @DisplayName("suggestPrefix: 带括号序号的名称应提取前缀")
    void suggestPrefix_withNumber_shouldExtractPrefix() {
        String result = SuggestUtils.suggestPrefix("myapp(3)");

        assertThat(result).isEqualTo("myapp");
    }

    @Test
    @DisplayName("suggestPrefix: 空括号时返回带空括号的完整名称")
    void suggestPrefix_emptyParentheses_shouldReturnWithParentheses() {
        // 正则 "^(.+?)(\\((\\d+)\\))?$" 中 (\\d+) 不匹配空内容
        // 所以 "myapp()" 整体匹配 group(1)，返回 "myapp()"
        String result = SuggestUtils.suggestPrefix("myapp()");

        assertThat(result).isEqualTo("myapp()");
    }
}
