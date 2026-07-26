package com.ensitech.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MoneyTransferApiTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private String baseUrl;

    @BeforeEach
    void setBaseUrl() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void transfersMoneyAndReplaysAnIdempotentRequestOnlyOnce() throws Exception {
        JsonNode source = createAccount("Alice", "100.00");
        JsonNode destination = createAccount("Bob", "25.00");
        String body = transferBody(source, destination, "30.50");

        HttpResponse<String> first = post("/transfers", body, "transfer-001");
        assertEquals(201, first.statusCode());
        JsonNode firstTransfer = json.readTree(first.body());
        assertEquals("COMPLETED", firstTransfer.get("state").asText());
        assertEquals(3, firstTransfer.get("transitions").size());
        assertEquals("PENDING", firstTransfer.get("transitions").get(0).get("state").asText());
        assertEquals("PROCESSING", firstTransfer.get("transitions").get(1).get("state").asText());
        assertEquals("COMPLETED", firstTransfer.get("transitions").get(2).get("state").asText());

        HttpResponse<String> replay = post("/transfers", body, "transfer-001");
        assertEquals(200, replay.statusCode());
        assertEquals("true", replay.headers().firstValue("Idempotent-Replayed").orElseThrow());
        JsonNode replayedTransfer = json.readTree(replay.body());
        assertEquals(firstTransfer.get("id").asText(), replayedTransfer.get("id").asText());

        JsonNode ledger = bodyOf(get("/transfers/" + firstTransfer.get("id").asText() + "/ledger"));
        assertEquals(2, ledger.size());
        assertEquals("DEBIT", ledger.get(0).get("type").asText());
        assertEquals("CREDIT", ledger.get(1).get("type").asText());
        assertMoney("30.50", ledger.get(0).get("amount").decimalValue());
        assertMoney("30.50", ledger.get(1).get("amount").decimalValue());
        assertEquals(source.get("id").asText(), ledger.get(0).get("accountId").asText());
        assertEquals(destination.get("id").asText(), ledger.get(1).get("accountId").asText());
        assertMoney("69.50", ledger.get(0).get("balanceAfter").decimalValue());
        assertMoney("55.50", ledger.get(1).get("balanceAfter").decimalValue());

        JsonNode globalLedger = bodyOf(get("/ledger"));
        assertEquals(2, globalLedger.size());
        BigDecimal signedTotal = BigDecimal.ZERO;
        for (JsonNode entry : globalLedger) {
            BigDecimal amount = entry.get("amount").decimalValue();
            signedTotal = "DEBIT".equals(entry.get("type").asText())
                    ? signedTotal.subtract(amount)
                    : signedTotal.add(amount);
        }
        assertMoney("0.00", signedTotal);

        assertEquals(1, bodyOf(get("/accounts/" + source.get("id").asText() + "/ledger")).size());
        assertMoney("69.50", getAccount(source.get("id").asText()).get("balance").decimalValue());
        assertMoney("55.50", getAccount(destination.get("id").asText()).get("balance").decimalValue());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyForDifferentInput() throws Exception {
        JsonNode source = createAccount("Alice", "100.00");
        JsonNode destination = createAccount("Bob", "0.00");

        assertEquals(201, post("/transfers", transferBody(source, destination, "10.00"), "same-key").statusCode());

        HttpResponse<String> conflict =
                post("/transfers", transferBody(source, destination, "11.00"), "same-key");
        assertEquals(409, conflict.statusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", bodyOf(conflict).get("code").asText());
    }

    @Test
    void recordsARejectedTransferWithoutChangingBalancesOrLedger() throws Exception {
        JsonNode source = createAccount("Alice", "5.00");
        JsonNode destination = createAccount("Bob", "1.00");

        HttpResponse<String> response =
                post("/transfers", transferBody(source, destination, "10.00"), UUID.randomUUID().toString());
        assertEquals(422, response.statusCode());
        JsonNode failed = bodyOf(response);
        assertEquals("FAILED", failed.get("state").asText());
        assertEquals("Insufficient funds", failed.get("failureReason").asText());

        HttpResponse<String> lookup = get("/transfers/" + failed.get("id").asText());
        assertEquals(200, lookup.statusCode());
        assertEquals("FAILED", bodyOf(lookup).get("state").asText());
        assertTrue(bodyOf(get("/transfers/" + failed.get("id").asText() + "/ledger")).isEmpty());
        assertTrue(bodyOf(get("/ledger")).isEmpty());
        assertMoney("5.00", getAccount(source.get("id").asText()).get("balance").decimalValue());
        assertMoney("1.00", getAccount(destination.get("id").asText()).get("balance").decimalValue());
    }

    @Test
    void validatesRequestsAndExposesHealth() throws Exception {
        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("UP", bodyOf(health).get("status").asText());

        JsonNode source = createAccount("Alice", "1.00");
        JsonNode destination = createAccount("Bob", "0.00");
        HttpResponse<String> missingKey =
                post("/transfers", transferBody(source, destination, "1.00"), null);
        assertEquals(400, missingKey.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", bodyOf(missingKey).get("code").asText());

        HttpResponse<String> malformed = post("/accounts", "{not-json", null);
        assertEquals(400, malformed.statusCode());
        assertEquals("INVALID_JSON", bodyOf(malformed).get("code").asText());

        HttpResponse<String> unknownRoute = get("/unknown");
        assertEquals(404, unknownRoute.statusCode());
        assertEquals("NOT_FOUND", bodyOf(unknownRoute).get("code").asText());
    }

    private JsonNode createAccount(String owner, String balance) throws Exception {
        String body = """
                {"owner":"%s","initialBalance":%s}
                """.formatted(owner, balance);
        HttpResponse<String> response = post("/accounts", body, null);
        assertEquals(201, response.statusCode(), response.body());
        JsonNode account = bodyOf(response);
        assertNotNull(account.get("id"));
        return account;
    }

    private JsonNode getAccount(String id) throws Exception {
        HttpResponse<String> response = get("/accounts/" + id);
        assertEquals(200, response.statusCode());
        return bodyOf(response);
    }

    private String transferBody(JsonNode source, JsonNode destination, String amount) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":%s}
                """.formatted(source.get("id").asText(), destination.get("id").asText(), amount);
    }

    private HttpResponse<String> post(String path, String body, String idempotencyKey)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode bodyOf(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
