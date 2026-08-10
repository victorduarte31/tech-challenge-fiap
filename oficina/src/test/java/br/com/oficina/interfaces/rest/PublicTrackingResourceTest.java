package br.com.oficina.interfaces.rest;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicTrackingResourceTest {

    private static final String CLIENT_CPF = "111.222.333-96";
    private static final String WRONG_CPF = "356.492.810-33";
    private static final String CLIENT_EMAIL = "cliente.tracking@example.com";
    private static final Pattern TOKEN_IN_EMAIL =
        Pattern.compile("Código de autorização: (\\S+)");

    @Inject
    MockMailbox mailbox;

    private static Long clientId;
    private static Long vehicleId;
    private static String orderNumber;
    private static String approvalToken;

    @Test
    @Order(1)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createClient() {
        clientId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Cliente Public Tracking",
                    "cpfCnpj": "%s",
                    "clientType": "PF",
                    "email": "%s"
                }
                """, CLIENT_CPF, CLIENT_EMAIL))
            .when().post("/admin/clients")
            .then()
            .statusCode(201)
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(2)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createVehicle() {
        vehicleId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "licensePlate": "PUB-1A23",
                    "brand": "Fiat",
                    "model": "Mobi",
                    "productionYear": 2022,
                    "clientId": %d
                }
                """, clientId))
            .when().post("/admin/vehicles")
            .then()
            .statusCode(201)
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(3)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createWorkOrderAndAdvanceToAwaitingApproval() {
        mailbox.clear();

        orderNumber = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "clientCpfCnpj": "%s",
                    "vehicleId": %d,
                    "notes": "Teste public tracking"
                }
                """, CLIENT_CPF, vehicleId))
            .when().post("/admin/work-orders")
            .then()
            .statusCode(201)
            .extract().path("orderNumber");

        Long woId = given().when()
            .get("/admin/work-orders/number/" + orderNumber)
            .then().statusCode(200).extract().<Integer>path("id").longValue();

        // Avança o status: RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL
        given().when().patch("/admin/work-orders/" + woId + "/start-diagnosis").then().statusCode(200);
        given().when().patch("/admin/work-orders/" + woId + "/send-for-approval").then().statusCode(200);
    }

    /**
     * O código de autorização não é exposto em nenhuma resposta da API — nem para o
     * operador autenticado. O único canal é o e-mail, então é de lá que o teste o
     * lê, exatamente como o cliente faria.
     */
    @Test
    @Order(4)
    void approvalToken_shouldBeDeliveredOnlyByEmail() {
        var messages = mailbox.getMailMessagesSentTo(CLIENT_EMAIL);
        assertThat(messages).hasSize(1);

        Matcher matcher = TOKEN_IN_EMAIL.matcher(messages.getFirst().getText());
        assertThat(matcher.find()).as("token presente no corpo do e-mail").isTrue();
        approvalToken = matcher.group(1);
        assertThat(approvalToken).hasSize(43);
    }

    @Test
    @Order(5)
    void getStatus_publicEndpoint_shouldReturn200() {
        given()
            .when().get("/public/work-orders/" + orderNumber + "/status")
            .then()
            .statusCode(200)
            .body("orderNumber", equalTo(orderNumber))
            .body("status", equalTo("AWAITING_APPROVAL"));
    }

    @Test
    @Order(6)
    void getStatus_unknownOrder_shouldReturn404() {
        given()
            .when().get("/public/work-orders/OS-999999/status")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(7)
    void approve_withEmptyBody_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(8)
    void approve_withBlankCpfCnpj_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"\", \"approvalToken\": \"%s\"}", approvalToken))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(9)
    void approve_withoutApprovalToken_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\"}", CLIENT_CPF))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(10)
    void approve_withWrongCpfCnpj_shouldReturn404() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\", \"approvalToken\": \"%s\"}",
                WRONG_CPF, approvalToken))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(404);
    }

    /** Sem o token, conhecer OS + CPF/CNPJ não basta — este é o ponto da mudança. */
    @Test
    @Order(11)
    void approve_withWrongToken_shouldReturn404AndKeepStatus() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\", \"approvalToken\": \"token-chutado\"}",
                CLIENT_CPF))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(404);

        given()
            .when().get("/public/work-orders/" + orderNumber + "/status")
            .then().statusCode(200).body("status", equalTo("AWAITING_APPROVAL"));
    }

    @Test
    @Order(12)
    void approve_withMatchingCpfCnpjAndToken_shouldReturn200AndChangeStatus() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\", \"approvalToken\": \"%s\"}",
                CLIENT_CPF, approvalToken))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_EXECUTION"))
            .body("orderNumber", equalTo(orderNumber));
    }

    @Test
    @Order(13)
    void approve_reusingTheSameToken_shouldReturn422() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\", \"approvalToken\": \"%s\"}",
                CLIENT_CPF, approvalToken))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(422)
            .body("message", containsString("já foi utilizado"));
    }

    @Test
    @Order(14)
    void publicResponses_doNotExposeClientPersonalDataNorApprovalToken() {
        given()
            .when().get("/public/work-orders/" + orderNumber + "/status")
            .then()
            .statusCode(200)
            .body("$", not(hasKey("clientName")))
            .body("$", not(hasKey("clientCpfCnpj")))
            .body("$", not(hasKey("approvalToken")));
    }

    @Test
    @Order(15)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void adminResponse_doesNotExposeApprovalTokenEither() {
        given()
            .when().get("/admin/work-orders/number/" + orderNumber)
            .then()
            .statusCode(200)
            .body("$", not(hasKey("approvalToken")));
    }
}
