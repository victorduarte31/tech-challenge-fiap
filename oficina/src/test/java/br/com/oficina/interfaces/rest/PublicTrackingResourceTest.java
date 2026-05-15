package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicTrackingResourceTest {

    private static final String CLIENT_CPF = "111.222.333-96";
    private static final String WRONG_CPF = "356.492.810-33";

    private static Long clientId;
    private static Long vehicleId;
    private static String orderNumber;

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
                    "clientType": "PF"
                }
                """, CLIENT_CPF))
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

        // Avança o status: RECEIVED → IN_DIAGNOSIS → AWAITING_APPROVAL
        given().when().patch("/admin/work-orders/number/" + orderNumber);
        Long woId = given().when()
            .get("/admin/work-orders/number/" + orderNumber)
            .then().statusCode(200).extract().<Integer>path("id").longValue();

        given().when().patch("/admin/work-orders/" + woId + "/start-diagnosis").then().statusCode(200);
        given().when().patch("/admin/work-orders/" + woId + "/send-for-approval").then().statusCode(200);
    }

    @Test
    @Order(4)
    void getStatus_publicEndpoint_shouldReturn200() {
        given()
            .when().get("/public/work-orders/" + orderNumber + "/status")
            .then()
            .statusCode(200)
            .body("orderNumber", equalTo(orderNumber))
            .body("status", equalTo("AWAITING_APPROVAL"));
    }

    @Test
    @Order(5)
    void getStatus_unknownOrder_shouldReturn404() {
        given()
            .when().get("/public/work-orders/OS-999999/status")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(6)
    void approve_withEmptyBody_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(7)
    void approve_withBlankCpfCnpj_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"clientCpfCnpj\": \"\"}")
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(8)
    void approve_withWrongCpfCnpj_shouldReturn404() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\"}", WRONG_CPF))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(9)
    void approve_withMatchingCpfCnpj_shouldReturn200AndChangeStatus() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"clientCpfCnpj\": \"%s\"}", CLIENT_CPF))
            .when().post("/public/work-orders/" + orderNumber + "/approve")
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_EXECUTION"))
            .body("orderNumber", equalTo(orderNumber));
    }

    @Test
    @Order(10)
    void publicStatus_doesNotExposeClientPersonalData() {
        // Confere que o JSON público NÃO inclui campos sensíveis (clientName, clientCpfCnpj)
        given()
            .when().get("/public/work-orders/" + orderNumber + "/status")
            .then()
            .statusCode(200)
            .body("$", not(hasKey("clientName")))
            .body("$", not(hasKey("clientCpfCnpj")));
    }
}
