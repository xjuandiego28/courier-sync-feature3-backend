package com.ep18.couriersync.backend.common.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditableEntityTest {

    @Test
    void lifecycleCallbacksSetTimestampsInUtc() {
        TestAuditable entity = new TestAuditable();

        entity.create();
        var createdAt = entity.getCreatedAt();
        var updatedAt = entity.getUpdatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isEqualTo(createdAt);
        assertThat(createdAt.getOffset().getTotalSeconds()).isZero();

        entity.update();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
        assertThat(entity.getUpdatedAt().getOffset().getTotalSeconds()).isZero();
    }

    private static final class TestAuditable extends AuditableEntity {
        void create() {
            onCreate();
        }

        void update() {
            onUpdate();
        }
    }
}
