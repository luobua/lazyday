package com.fan.lazyday.infrastructure.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JsonUtils 单元测试
 * 验证序列化/反序列化的往返对称性
 */
class JsonUtilsTest {

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class TestPojo {
        private String name;
        private int age;
        private Boolean active;

        public TestPojo() {}

        public TestPojo(String name, int age, Boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
    }

    @Test
    @DisplayName("toJSONString + parse: 对象序列化/反序列化往返")
    void roundTrip_object_shouldBeSymmetric() {
        TestPojo original = new TestPojo("Alice", 30, true);

        String json = JsonUtils.toJSONString(original);
        TestPojo parsed = JsonUtils.parse(json, TestPojo.class);

        assertThat(parsed.getName()).isEqualTo("Alice");
        assertThat(parsed.getAge()).isEqualTo(30);
        assertThat(parsed.getActive()).isTrue();
    }

    @Test
    @DisplayName("toJSONString + parse: 中文字符正常处理")
    void roundTrip_chineseCharacters_shouldWork() {
        TestPojo original = new TestPojo("张三", 25, false);

        String json = JsonUtils.toJSONString(original);
        TestPojo parsed = JsonUtils.parse(json, TestPojo.class);

        assertThat(parsed.getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("parse: 未知属性不报错（FAIL_ON_UNKNOWN_PROPERTIES = false）")
    void parse_unknownProperties_shouldNotThrow() {
        String json = "{\"name\":\"Bob\",\"age\":40,\"active\":true,\"unknown\":\"value\"}";

        TestPojo parsed = JsonUtils.parse(json, TestPojo.class);

        assertThat(parsed.getName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("parseAsList: JSON 数组反序列化为 List")
    void parseAsList_shouldDeserializeArray() {
        String json = "[{\"name\":\"A\",\"age\":1,\"active\":true},{\"name\":\"B\",\"age\":2,\"active\":false}]";

        List<TestPojo> list = JsonUtils.parseAsList(json, TestPojo.class);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo("A");
        assertThat(list.get(1).getName()).isEqualTo("B");
    }

    @Test
    @DisplayName("parseAsMap: JSON 对象反序列化为 Map")
    void parseAsMap_shouldDeserializeObject() {
        String json = "{\"key1\":\"value1\",\"key2\":123}";

        Map<String, Object> map = JsonUtils.parseAsMap(json);

        assertThat(map).containsEntry("key1", "value1").containsEntry("key2", 123);
    }

    @Test
    @DisplayName("clone: 深拷贝对象")
    void clone_shouldDeepCopy() {
        TestPojo original = new TestPojo("Clone", 99, true);

        TestPojo cloned = JsonUtils.clone(original, TestPojo.class);

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getName()).isEqualTo("Clone");
        assertThat(cloned.getAge()).isEqualTo(99);
    }

    @Test
    @DisplayName("toJSONString + parse: byte[] 往返")
    void roundTrip_bytes_shouldBeSymmetric() {
        TestPojo original = new TestPojo("Bytes", 50, false);

        byte[] jsonBytes = JsonUtils.toJSONBytes(original);
        TestPojo parsed = JsonUtils.parse(jsonBytes, TestPojo.class);

        assertThat(parsed.getName()).isEqualTo("Bytes");
    }

    @Test
    @DisplayName("readerForUpdating: 部分更新对象")
    void readerForUpdating_shouldMerge() {
        TestPojo original = new TestPojo("Original", 30, true);

        String patch = "{\"age\":31}";
        TestPojo updated = JsonUtils.readerForUpdating(original, patch);

        assertThat(updated.getName()).isEqualTo("Original");
        assertThat(updated.getAge()).isEqualTo(31);
    }

    @Test
    @DisplayName("readerForUpdatingExcludeEmpty: null 字段不覆盖原值")
    void readerForUpdatingExcludeEmpty_shouldNotOverrideNullFields() {
        TestPojo original = new TestPojo("Keep", 30, true);

        String patch = "{\"age\":31,\"name\":null}";
        TestPojo updated = JsonUtils.readerForUpdating(original, patch, true);

        assertThat(updated.getName()).isEqualTo("Keep"); // null 不覆盖
        assertThat(updated.getAge()).isEqualTo(31);
    }

    @Test
    @DisplayName("parse: 非法 JSON 应抛出异常")
    void parse_invalidJson_shouldThrow() {
        assertThatThrownBy(() -> JsonUtils.parse("{invalid}", TestPojo.class))
                .isInstanceOf(JsonUtils.JsonException.class);
    }

    @Test
    @DisplayName("toJSONString: null 处理")
    void toJSONString_null_shouldSerialize() {
        String json = JsonUtils.toJSONString(null);
        assertThat(json).isEqualTo("null");
    }
}
