package com.smarttrafficflow.backend.domain.streets.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Street entity mapping tests")
class StreetTests {

    @Test
    @DisplayName("does not declare the street name as unique, because the schema has no such constraint")
    void doesNotDeclareTheNameAsUnique() throws NoSuchFieldException {
        Column column = Street.class.getDeclaredField("name").getAnnotation(Column.class);

        assertThat(column.unique()).isFalse();
    }

    @Test
    @DisplayName("keeps the osm way id as unique, as in the schema")
    void keepsTheOsmWayIdUnique() throws NoSuchFieldException {
        Column column = Street.class.getDeclaredField("osmWayId").getAnnotation(Column.class);

        assertThat(column.unique()).isTrue();
    }
}
