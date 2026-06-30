package com.ximofam.graduation_project.integration.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
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
        // Arrange
        String testValue = "Hello Redis!";

        // Act
        redisTemplate.opsForValue().set(TEST_STRING_KEY, testValue);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_STRING_KEY);

        // Assert
        assertThat(retrievedValue).isNotNull();
        assertThat(retrievedValue).isEqualTo(testValue);
    }

    @Test
    @DisplayName("Lưu và lấy JSON object từ Redis")
    void saveAndRetrieveJsonObject() throws Exception {
        // Arrange
        TestUser testUser = new TestUser(1L, "John Doe", "john@example.com");

        // Act
        redisTemplate.opsForValue().set(TEST_JSON_KEY, testUser);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_JSON_KEY);

        // Assert
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
        // Arrange
        String counterKey = "test:counter";
        redisTemplate.opsForValue().set(counterKey, 0L);

        // Act
        redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.opsForValue().increment(counterKey);
        Long value = (Long) redisTemplate.opsForValue().get(counterKey);

        // Assert
        assertThat(value).isEqualTo(3L);
    }

    @Test
    @DisplayName("Increment value với số lượng chỉ định")
    void incrementValueByAmount() {
        // Arrange
        String counterKey = "test:counter:amount";
        redisTemplate.opsForValue().set(counterKey, 10L);

        // Act
        redisTemplate.opsForValue().increment(counterKey, 5);
        Long value = (Long) redisTemplate.opsForValue().get(counterKey);

        // Assert
        assertThat(value).isEqualTo(15L);
    }

    @Test
    @DisplayName("Delete key từ Redis")
    void deleteKey() {
        // Arrange
        redisTemplate.opsForValue().set(TEST_STRING_KEY, "test value");

        // Act
        redisTemplate.delete(TEST_STRING_KEY);
        Object retrievedValue = redisTemplate.opsForValue().get(TEST_STRING_KEY);

        // Assert
        assertThat(retrievedValue).isNull();
    }

    @Test
    @DisplayName("Check key tồn tại trong Redis")
    void checkKeyExists() {
        // Arrange
        redisTemplate.opsForValue().set(TEST_STRING_KEY, "test value");

        // Act
        Boolean exists = redisTemplate.hasKey(TEST_STRING_KEY);
        Boolean notExists = redisTemplate.hasKey("non-existent-key");

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Lưu multiple JSON objects")
    void saveMultipleJsonObjects() throws Exception {
        // Arrange
        TestUser user1 = new TestUser(1L, "Alice", "alice@example.com");
        TestUser user2 = new TestUser(2L, "Bob", "bob@example.com");
        TestUser user3 = new TestUser(3L, "Charlie", "charlie@example.com");

        // Act
        redisTemplate.opsForValue().set("user:1", user1);
        redisTemplate.opsForValue().set("user:2", user2);
        redisTemplate.opsForValue().set("user:3", user3);

        TestUser retrievedUser1 = (TestUser) redisTemplate.opsForValue().get("user:1");
        TestUser retrievedUser2 = (TestUser) redisTemplate.opsForValue().get("user:2");
        TestUser retrievedUser3 = (TestUser) redisTemplate.opsForValue().get("user:3");

        // Assert
        assertThat(retrievedUser1.getName()).isEqualTo("Alice");
        assertThat(retrievedUser2.getName()).isEqualTo("Bob");
        assertThat(retrievedUser3.getName()).isEqualTo("Charlie");
    }
}
