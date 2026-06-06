package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OperationRequestResourceTest {

    private static Long partId;
    private static Long requestId;

    @Test
    @Order(1)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createPart() {
        partId = given()
            .contentType(ContentType.JSON)
            .body("""
                {"name": "Óleo 5W30 Req", "unitPrice": 40.0, "stockQuantity": 10, "unit": "L"}
                """)
            .when().post("/admin/parts")
            .then().statusCode(201)
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(2)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void requestStockAdjustment_asAttendant_shouldReturn201() {
        requestId = given()
            .contentType(ContentType.JSON)
            .body("""
                {"partId": %d, "adjustment": 15, "reason": "Compra de fornecedor"}
                """.formatted(partId))
            .when().post("/admin/requests/stock-adjustment")
            .then().statusCode(201)
            .body("status", equalTo("PENDING"))
            .body("type", equalTo("STOCK_ADJUSTMENT"))
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(3)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void requestStockAdjustment_withoutReason_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"partId": %d, "adjustment": 5}
                """.formatted(partId))
            .when().post("/admin/requests/stock-adjustment")
            .then().statusCode(400);
    }

    @Test
    @Order(4)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void list_asAttendant_shouldReturn403() {
        given()
            .when().get("/admin/requests?status=PENDING")
            .then().statusCode(403);
    }

    @Test
    @Order(5)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void list_asAdmin_shouldReturnPending() {
        given()
            .when().get("/admin/requests?status=PENDING")
            .then().statusCode(200)
            .body("$", hasSize(greaterThan(0)));
    }

    @Test
    @Order(6)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void approve_asAttendant_shouldReturn403() {
        given()
            .when().post("/admin/requests/" + requestId + "/approve")
            .then().statusCode(403);
    }

    @Test
    @Order(7)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void approve_asAdmin_shouldExecuteAndUpdateStock() {
        given()
            .when().post("/admin/requests/" + requestId + "/approve")
            .then().statusCode(200)
            .body("status", equalTo("APPROVED"))
            .body("decidedBy", equalTo("admin"));

        // Estoque foi de 10 para 25 (10 + 15)
        given()
            .when().get("/admin/parts/" + partId)
            .then().statusCode(200)
            .body("stockQuantity", equalTo(25));
    }

    @Test
    @Order(8)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void approve_again_shouldReturn422() {
        given()
            .when().post("/admin/requests/" + requestId + "/approve")
            .then().statusCode(422);
    }
}
