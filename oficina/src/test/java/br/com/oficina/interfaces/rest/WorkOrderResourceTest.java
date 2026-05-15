package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkOrderResourceTest {

    private static Long clientId;
    private static Long vehicleId;
    private static Long workOrderId;
    private static String workOrderNumber;
    private static Long serviceItemId;
    private static Long partId;

    @Test
    @Order(1)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createClient() {
        clientId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Pedro Test WO",
                    "cpfCnpj": "356.492.810-33",
                    "clientType": "PF",
                    "email": "pedro@test.com"
                }
                """)
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
                    "licensePlate": "XYZ9B88",
                    "brand": "Honda",
                    "model": "Civic",
                    "productionYear": 2021,
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
    void setup_createServiceItem() {
        serviceItemId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Troca de Óleo Test",
                    "description": "Teste",
                    "basePrice": 120.00,
                    "estimatedDurationMinutes": 30
                }
                """)
            .when().post("/admin/services")
            .then()
            .statusCode(201)
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(4)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void setup_createPart() {
        partId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Óleo 5W30 Test",
                    "description": "1L",
                    "unitPrice": 45.90,
                    "stockQuantity": 50,
                    "unit": "L"
                }
                """)
            .when().post("/admin/parts")
            .then()
            .statusCode(201)
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(5)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void createWorkOrder_shouldReturn201() {
        var response = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "clientCpfCnpj": "356.492.810-33",
                    "vehicleId": %d,
                    "notes": "Revisão geral"
                }
                """, vehicleId))
            .when().post("/admin/work-orders")
            .then()
            .statusCode(201)
            .body("status", equalTo("RECEIVED"))
            .body("orderNumber", startsWith("OS-"))
            .extract().response();
        workOrderId = response.<Integer>path("id").longValue();
        workOrderNumber = response.path("orderNumber");
    }

    @Test
    @Order(6)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void addService_toWorkOrder_shouldReturn200() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {"serviceItemId": %d, "notes": "Troca com filtro"}
                """, serviceItemId))
            .when().post("/admin/work-orders/" + workOrderId + "/services")
            .then()
            .statusCode(200)
            .body("services", hasSize(greaterThan(0)));
    }

    @Test
    @Order(7)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void addPart_toWorkOrder_shouldDecreaseStock() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {"partId": %d, "quantity": 2}
                """, partId))
            .when().post("/admin/work-orders/" + workOrderId + "/parts")
            .then()
            .statusCode(200)
            .body("parts", hasSize(greaterThan(0)));
    }

    @Test
    @Order(8)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void startDiagnosis_shouldChangeStatus() {
        given()
            .when().patch("/admin/work-orders/" + workOrderId + "/start-diagnosis")
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_DIAGNOSIS"));
    }

    @Test
    @Order(9)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void sendForApproval_shouldChangeStatus() {
        given()
            .when().patch("/admin/work-orders/" + workOrderId + "/send-for-approval")
            .then()
            .statusCode(200)
            .body("status", equalTo("AWAITING_APPROVAL"))
            .body("totalCost", greaterThan(0f));
    }

    @Test
    @Order(10)
    void publicTracking_shouldReturnStatus() {
        given()
            .when().get("/public/work-orders/" + workOrderNumber + "/status")
            .then()
            .statusCode(200)
            .body("orderNumber", equalTo(workOrderNumber));
    }

    @Test
    @Order(11)
    void publicApprove_withWrongCpfCnpj_shouldReturn404() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"clientCpfCnpj\": \"529.982.247-25\"}")
            .when().post("/public/work-orders/" + workOrderNumber + "/approve")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(12)
    void publicApprove_withMatchingCpfCnpj_shouldChangeStatusToInExecution() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"clientCpfCnpj\": \"356.492.810-33\"}")
            .when().post("/public/work-orders/" + workOrderNumber + "/approve")
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_EXECUTION"))
            .body("orderNumber", equalTo(workOrderNumber));
    }

    @Test
    @Order(13)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void complete_shouldChangeStatus() {
        given()
            .when().patch("/admin/work-orders/" + workOrderId + "/complete")
            .then()
            .statusCode(200)
            .body("status", equalTo("FINISHED"));
    }

    @Test
    @Order(14)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void deliver_shouldChangeStatus() {
        given()
            .when().patch("/admin/work-orders/" + workOrderId + "/deliver")
            .then()
            .statusCode(200)
            .body("status", equalTo("DELIVERED"));
    }

    @Test
    @Order(15)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void listAll_shouldIncludeCreatedOrder() {
        given()
            .when().get("/admin/work-orders")
            .then()
            .statusCode(200)
            .body("$", hasSize(greaterThan(0)));
    }

    @Test
    @Order(16)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void getMetrics_shouldReturnValidData() {
        given()
            .when().get("/admin/metrics")
            .then()
            .statusCode(200)
            .body("totalWorkOrders", greaterThanOrEqualTo(1));
    }
}
