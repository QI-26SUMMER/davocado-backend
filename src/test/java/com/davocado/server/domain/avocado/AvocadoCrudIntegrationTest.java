package com.davocado.server.domain.avocado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davocado.server.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests for {@code /avocados} CRUD. */
class AvocadoCrudIntegrationTest extends IntegrationTest {

    private String authHeader(String token) {
        return "Bearer " + token;
    }

    private long createAvocado(String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/avocados")
                        .header("Authorization", authHeader(token))
                        .contentType("application/json")
                        .content(asJson(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    @Nested
    @DisplayName("POST /avocados")
    class Create {

        @Test
        @DisplayName("purpose=weekend maps to target_stage 4")
        void purposeWeekendMapsToStage4() throws Exception {
            String token = signupAndLogin("avo_create_1", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "room");
            body.put("purpose", "weekend");

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.target_stage").value(4))
                    .andExpect(jsonPath("$.data.current_stage").doesNotExist())
                    .andExpect(jsonPath("$.data.status").value("tracking"));
        }

        @Test
        @DisplayName("purpose=eat_now maps to target_stage 3")
        void purposeEatNowMapsToStage3() throws Exception {
            String token = signupAndLogin("avo_create_2", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.target_stage").value(3));
        }

        @Test
        @DisplayName("explicit target_stage=3 is accepted as-is")
        void explicitTargetStage3() throws Exception {
            String token = signupAndLogin("avo_create_3", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("target_stage", 3);

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.target_stage").value(3));
        }

        @Test
        @DisplayName("target_stage=5 is rejected with 422 VALIDATION_FAILED")
        void invalidTargetStage() throws Exception {
            String token = signupAndLogin("avo_create_4", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("target_stage", 5);

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("neither purpose nor target_stage is rejected with 422 VALIDATION_FAILED")
        void missingPurposeAndTargetStage() throws Exception {
            String token = signupAndLogin("avo_create_5", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("invalid storage_condition is rejected with 422 VALIDATION_FAILED")
        void invalidStorageCondition() throws Exception {
            String token = signupAndLogin("avo_create_6", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "freezer");
            body.put("purpose", "eat_now");

            mockMvc.perform(post("/avocados")
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");

            mockMvc.perform(post("/avocados")
                            .contentType("application/json")
                            .content(asJson(body)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /avocados")
    class List {

        @Test
        @DisplayName("returns only the caller's avocados")
        void isolatedPerUser() throws Exception {
            String tokenA = signupAndLogin("avo_list_userA", "password123");
            String tokenB = signupAndLogin("avo_list_userB", "password123");

            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            createAvocado(tokenA, body);
            createAvocado(tokenB, body);

            mockMvc.perform(get("/avocados").header("Authorization", authHeader(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1));
        }

        @Test
        @DisplayName("supports filtering by status")
        void filterByStatus() throws Exception {
            String token = signupAndLogin("avo_list_status", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long trackingId = createAvocado(token, body);
            long consumedId = createAvocado(token, body);

            Map<String, Object> consumeUpdate = Map.of("status", "consumed");
            mockMvc.perform(patch("/avocados/{id}", consumedId)
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(consumeUpdate)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/avocados")
                            .header("Authorization", authHeader(token))
                            .param("status", "tracking"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(trackingId));

            mockMvc.perform(get("/avocados")
                            .header("Authorization", authHeader(token))
                            .param("status", "consumed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(consumedId));
        }

        @Test
        @DisplayName("cursor pagination pages through all results")
        void cursorPagination() throws Exception {
            String token = signupAndLogin("avo_list_cursor", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");

            int total = 5;
            for (int i = 0; i < total; i++) {
                createAvocado(token, body);
            }

            MvcResult firstPage = mockMvc.perform(get("/avocados")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.next_cursor").isNotEmpty())
                    .andReturn();

            JsonNode firstPageJson = objectMapper.readTree(firstPage.getResponse().getContentAsString());
            long cursor = firstPageJson.path("data").path("next_cursor").asLong();

            mockMvc.perform(get("/avocados")
                            .header("Authorization", authHeader(token))
                            .param("limit", "3")
                            .param("cursor", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.next_cursor").doesNotExist());
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/avocados")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /avocados/{id}")
    class Get {

        @Test
        @DisplayName("returns the avocado when owned by the caller")
        void getOwned() throws Exception {
            String token = signupAndLogin("avo_get_owner", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(token, body);

            mockMvc.perform(get("/avocados/{id}", id).header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id));
        }

        @Test
        @DisplayName("another user's avocado returns 403 FORBIDDEN")
        void anotherUsersAvocado() throws Exception {
            String tokenA = signupAndLogin("avo_get_userA", "password123");
            String tokenB = signupAndLogin("avo_get_userB", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(tokenA, body);

            mockMvc.perform(get("/avocados/{id}", id).header("Authorization", authHeader(tokenB)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("missing id returns 404 NOT_FOUND")
        void missingId() throws Exception {
            String token = signupAndLogin("avo_get_missing", "password123");

            mockMvc.perform(get("/avocados/{id}", 999_999L).header("Authorization", authHeader(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/avocados/{id}", 1L)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /avocados/{id}")
    class Update {

        @Test
        @DisplayName("updates fields, verified via a follow-up GET")
        void updateFields() throws Exception {
            String token = signupAndLogin("avo_update_1", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            body.put("nickname", "original");
            long id = createAvocado(token, body);

            Map<String, Object> update = new HashMap<>();
            update.put("nickname", "renamed");
            update.put("storage_condition", "room");

            mockMvc.perform(patch("/avocados/{id}", id)
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("renamed"))
                    .andExpect(jsonPath("$.data.storage_condition").value("room"));

            mockMvc.perform(get("/avocados/{id}", id).header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("renamed"))
                    .andExpect(jsonPath("$.data.storage_condition").value("room"));
        }

        @Test
        @DisplayName("status change is persisted")
        void statusChange() throws Exception {
            String token = signupAndLogin("avo_update_2", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(token, body);

            Map<String, Object> update = Map.of("status", "discarded");
            mockMvc.perform(patch("/avocados/{id}", id)
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("discarded"));
        }

        @Test
        @DisplayName("invalid value returns 422 VALIDATION_FAILED")
        void invalidValue() throws Exception {
            String token = signupAndLogin("avo_update_3", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(token, body);

            Map<String, Object> update = Map.of("target_stage", 1);
            mockMvc.perform(patch("/avocados/{id}", id)
                            .header("Authorization", authHeader(token))
                            .contentType("application/json")
                            .content(asJson(update)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("another user's avocado returns 403 FORBIDDEN")
        void anotherUsersAvocado() throws Exception {
            String tokenA = signupAndLogin("avo_update_userA", "password123");
            String tokenB = signupAndLogin("avo_update_userB", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(tokenA, body);

            Map<String, Object> update = Map.of("nickname", "hijacked");
            mockMvc.perform(patch("/avocados/{id}", id)
                            .header("Authorization", authHeader(tokenB))
                            .contentType("application/json")
                            .content(asJson(update)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            Map<String, Object> update = Map.of("nickname", "x");
            mockMvc.perform(patch("/avocados/{id}", 1L)
                            .contentType("application/json")
                            .content(asJson(update)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /avocados/{id}")
    class Delete {

        @Test
        @DisplayName("deletes the avocado; a follow-up GET returns 404")
        void deleteThenGetNotFound() throws Exception {
            String token = signupAndLogin("avo_delete_1", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(token, body);

            mockMvc.perform(delete("/avocados/{id}", id).header("Authorization", authHeader(token)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/avocados/{id}", id).header("Authorization", authHeader(token)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user's avocado returns 403 FORBIDDEN")
        void anotherUsersAvocado() throws Exception {
            String tokenA = signupAndLogin("avo_delete_userA", "password123");
            String tokenB = signupAndLogin("avo_delete_userB", "password123");
            Map<String, Object> body = new HashMap<>();
            body.put("storage_condition", "fridge");
            body.put("purpose", "eat_now");
            long id = createAvocado(tokenA, body);

            mockMvc.perform(delete("/avocados/{id}", id).header("Authorization", authHeader(tokenB)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("without a token returns 401 UNAUTHORIZED")
        void withoutToken() throws Exception {
            mockMvc.perform(delete("/avocados/{id}", 1L)).andExpect(status().isUnauthorized());
        }
    }
}
