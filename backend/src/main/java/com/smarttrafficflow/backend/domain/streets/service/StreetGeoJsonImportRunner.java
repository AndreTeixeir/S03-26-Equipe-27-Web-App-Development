package com.smarttrafficflow.backend.domain.streets.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.streets.import.enabled", havingValue = "true")
public class StreetGeoJsonImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StreetGeoJsonImportRunner.class);
    private static final int MAX_NAME_LENGTH = 180;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String geoJsonPath;

    public StreetGeoJsonImportRunner(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            @Value("${app.streets.import.geojson-path}") String geoJsonPath
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.geoJsonPath = geoJsonPath;
    }

    @Override
    public void run(String... args) throws IOException {
        log.info(
                "Street import config: enabled=true, geojsonPath={}",
                (geoJsonPath == null || geoJsonPath.isBlank()) ? "<empty>" : geoJsonPath
        );

        Integer streetCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM streets", Integer.class);
        if (streetCount != null && streetCount > 0) {
            log.info("Street import skipped because {} streets already exist in database", streetCount);
            return;
        }

        log.info("Street table is empty. Starting GeoJSON import.");

        if (geoJsonPath == null || geoJsonPath.isBlank()) {
            throw new IllegalArgumentException("APP_STREETS_IMPORT_GEOJSON_PATH nao configurado");
        }

        Path path = Paths.get(geoJsonPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Arquivo GeoJSON nao encontrado: " + geoJsonPath);
        }

        // A single transaction makes the import all-or-nothing: if it fails midway, no street is kept,
        // the table stays empty and the next start tries the whole import again.
        ImportResult result;
        try {
            result = transactionTemplate.execute(status -> {
                try {
                    return importFeatures(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            log.error("Street GeoJSON import failed and was rolled back; no street was saved: {}", ex.getMessage());
            throw ex.getCause();
        } catch (RuntimeException ex) {
            log.error("Street GeoJSON import failed and was rolled back; no street was saved: {}", ex.getMessage());
            throw ex;
        }

        Integer finalStreetCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM streets", Integer.class);
        log.info(
                "Street GeoJSON import complete: imported={}, skipped={}, finalStreetCount={}, path={}",
                result.imported(),
                result.skipped(),
                finalStreetCount == null ? 0 : finalStreetCount,
                geoJsonPath
        );
    }

    private ImportResult importFeatures(Path path) throws IOException {
        int imported = 0;
        int skipped = 0;

        try (JsonParser parser = objectMapper.createParser(path.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("GeoJSON invalido: objeto raiz nao encontrado");
            }

            boolean featuresFound = false;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                if (fieldName == null) {
                    continue;
                }

                JsonToken fieldValueToken = parser.nextToken();
                if (!"features".equals(fieldName)) {
                    parser.skipChildren();
                    continue;
                }

                if (fieldValueToken != JsonToken.START_ARRAY) {
                    throw new IllegalArgumentException("GeoJSON invalido: campo 'features' nao encontrado");
                }

                featuresFound = true;
                log.info("GeoJSON features array found. Starting streaming import.");

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode feature = objectMapper.readTree(parser);
                    if (feature == null || feature.isNull()) {
                        skipped++;
                        continue;
                    }

                    JsonNode geometry = feature.get("geometry");
                    JsonNode properties = feature.get("properties");
                    if (geometry == null || properties == null) {
                        skipped++;
                        continue;
                    }

                    String geometryType = geometry.path("type").asText("");
                    if (!"LineString".equals(geometryType)) {
                        skipped++;
                        continue;
                    }

                    long osmWayId = extractOsmWayId(properties, feature);
                    if (osmWayId <= 0) {
                        skipped++;
                        continue;
                    }

                    // PostGIS stores a one-point LineString as an invalid geometry and turns non-numeric
                    // coordinates into 0, so malformed lines are rejected here instead of saved corrupted.
                    if (!hasValidLineCoordinates(geometry.get("coordinates"))) {
                        log.warn("Skipping OSM way {}: LineString needs at least 2 positions with numeric coordinates",
                                osmWayId);
                        skipped++;
                        continue;
                    }

                    String streetName = properties.path("name").asText("");
                    if (streetName.isBlank()) {
                        streetName = "OSM WAY " + osmWayId;
                    }
                    if (streetName.codePointCount(0, streetName.length()) > MAX_NAME_LENGTH) {
                        log.warn("Skipping OSM way {}: name longer than {} characters", osmWayId, MAX_NAME_LENGTH);
                        skipped++;
                        continue;
                    }

                    UUID streetId = UUID.nameUUIDFromBytes(("osm-way-" + osmWayId).getBytes(StandardCharsets.UTF_8));
                    String geometryJson = objectMapper.writeValueAsString(geometry);

                    jdbcTemplate.update(
                            """
                            INSERT INTO streets (id, osm_way_id, name, geom)
                            VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                            ON CONFLICT (osm_way_id) DO UPDATE
                            SET name = EXCLUDED.name,
                                geom = EXCLUDED.geom
                            """,
                            streetId,
                            osmWayId,
                            streetName,
                            geometryJson
                    );
                    imported++;
                }

                break;
            }

            if (!featuresFound) {
                throw new IllegalArgumentException("GeoJSON invalido: campo 'features' nao encontrado");
            }
        }

        return new ImportResult(imported, skipped);
    }

    private boolean hasValidLineCoordinates(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray() || coordinates.size() < 2) {
            return false;
        }
        for (JsonNode position : coordinates) {
            if (!position.isArray() || position.size() < 2) {
                return false;
            }
            for (int axis = 0; axis < 2; axis++) {
                JsonNode value = position.get(axis);
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    return false;
                }
            }
        }
        return true;
    }

    private long extractOsmWayId(JsonNode properties, JsonNode feature) {
        JsonNode explicitOsmWayId = properties.get("osm_way_id");
        if (explicitOsmWayId != null && explicitOsmWayId.canConvertToLong()) {
            return explicitOsmWayId.asLong();
        }

        JsonNode osmId = properties.get("osm_id");
        if (osmId != null && osmId.canConvertToLong()) {
            return osmId.asLong();
        }

        String id = feature.path("id").asText("");
        if (id.startsWith("way/")) {
            try {
                return Long.parseLong(id.substring("way/".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        return -1;
    }

    private record ImportResult(int imported, int skipped) {
    }
}
