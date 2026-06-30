package com.ximofam.graduation_project.integration.redis;

import com.ximofam.graduation_project.integration.base.AbstractSpringBootTest;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis Template Integration Tests")
class RedisTemplateTest extends AbstractSpringBootTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_STRING_KEY = "test:string:key";
    private static final String TEST_JSON_KEY = "test:json:key";

    @Data
    public static class TestUser {
        private Long id;
        private String name;
        private String email;

        public TestUser() {
        }

        public TestUser(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    @Test
    @DisplayName("Lưu và lấy string value từ Redis")
    void saveAndRetrieveStringValue() {
        String testValue = "Hello Redis!";

        redisTemplate.opsForValue().set(TEST_STRING_KEY, testValue);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_STRING_KEY);

        assertThat(retrievedValue).isNotNull();
        assertThat(retrievedValue).isEqualTo(testValue);
    }

    @Test
    @DisplayName("Lưu và lấy JSON object từ Redis")
    void saveAndRetrieveJsonObject() throws Exception {
        TestUser testUser = new TestUser(1L, "John Doe", "john@example.com");

        redisTemplate.opsForValue().set(TEST_JSON_KEY, testUser);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_JSON_KEY);

        assertThat(retrievedValue).isNotNull();
        assertThat(retrievedValue).isInstanceOf(TestUser.class);

        TestUser retrievedUser = (TestUser) retrievedValue;
        assertThat(retrievedUser.getId()).isEqualTo(1L);
        assertThat(retrievedUser.getName()).isEqualTo("John Doe");
        assertThat(retrievedUser.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Increment value trong Redis")
    void incrementValue() {
        String counterKey = "test:counter";
        redisTemplate.opsForValue().set(counterKey, 0L);

        redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.opsForValue().increment(counterKey);
        Long value = redisTemplate.opsForValue().increment(counterKey);

        assertThat(value).isEqualTo(3L);
    }

    @Test
    @DisplayName("Increment value với số lượng chỉ định")
    void incrementValueByAmount() {
        String counterKey = "test:counter:amount";
        redisTemplate.opsForValue().set(counterKey, 10L);

        Long value = redisTemplate.opsForValue().increment(counterKey, 5);

        assertThat(value).isEqualTo(15L);
    }

    @Test
    @DisplayName("Delete key từ Redis")
    void deleteKey() {
        redisTemplate.opsForValue().set(TEST_STRING_KEY, "test value");

        redisTemplate.delete(TEST_STRING_KEY);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_STRING_KEY);

        assertThat(retrievedValue).isNull();
    }

    @Test
    @DisplayName("Check key tồn tại trong Redis")
    void checkKeyExists() {
        redisTemplate.opsForValue().set(TEST_STRING_KEY, "test value");

        Boolean exists = redisTemplate.hasKey(TEST_STRING_KEY);
        Boolean notExists = redisTemplate.hasKey("non-existent-key");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Lưu multiple JSON objects")
    void saveMultipleJsonObjects() throws Exception {
        TestUser user1 = new TestUser(1L, "Alice", "alice@example.com");
        TestUser user2 = new TestUser(2L, "Bob", "bob@example.com");
        TestUser user3 = new TestUser(3L, "Charlie", "charlie@example.com");

        redisTemplate.opsForValue().set("user:1", user1);
        redisTemplate.opsForValue().set("user:2", user2);
        redisTemplate.opsForValue().set("user:3", user3);

        TestUser retrievedUser1 = (TestUser) redisTemplate.opsForValue().get("user:1");
        TestUser retrievedUser2 = (TestUser) redisTemplate.opsForValue().get("user:2");
        TestUser retrievedUser3 = (TestUser) redisTemplate.opsForValue().get("user:3");

        assertThat(retrievedUser1.getName()).isEqualTo("Alice");
        assertThat(retrievedUser2.getName()).isEqualTo("Bob");
        assertThat(retrievedUser3.getName()).isEqualTo("Charlie");
    }
}
