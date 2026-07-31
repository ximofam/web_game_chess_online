package com.ximofam.graduation_project.common.uuidv7;

import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UUIDv7Test {

    @Test
    @DisplayName("Should generate valid UUIDv7")
    void shouldGenerateValidUUIDv7() {
        UUID uuid = UuidCreator.getTimeOrderedEpoch();

        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7); // UUID version 7
        assertThat(uuid.variant()).isEqualTo(2); // IETF RFC 4122 variant
    }

    @Test
    @DisplayName("Generated UUIDv7 should be time-ordered")
    void generatedUUIDv7ShouldBeTimeOrdered() throws InterruptedException {
        UUID uuid1 = UuidCreator.getTimeOrderedEpoch();
        
        // Small delay to ensure the timestamp advances 
        // (though UUIDv7 handles sub-millisecond sequencing, it's safer for the test)
        Thread.sleep(2);
        
        UUID uuid2 = UuidCreator.getTimeOrderedEpoch();

        // Compare as strings or use standard UUID comparison.
        // UUIDv7 is lexicographically sortable as strings.
        assertThat(uuid1.toString()).isLessThan(uuid2.toString());
        
        // Standard UUID comparison also works since version 7 puts timestamp in the most significant bits
        assertThat(uuid1.compareTo(uuid2)).isLessThan(0);
    }
    
    @Test
    @DisplayName("Generated UUIDv7s should be unique")
    void generatedUUIDv7ShouldBeUnique() {
        int count = 10000;
        java.util.Set<UUID> uuids = new java.util.HashSet<>();
        
        for (int i = 0; i < count; i++) {
            uuids.add(UuidCreator.getTimeOrderedEpoch());
        }
        
        assertThat(uuids).hasSize(count);
    }
}
