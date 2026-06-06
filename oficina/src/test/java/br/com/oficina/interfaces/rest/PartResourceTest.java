package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PartResourceTest {

    private static Long partId;

    @Test
    @Order(1)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_shouldReturn201() {
        partId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Filtro de Ar Test",
                    "description": "Filtro para teste",
                    "unitPrice": 29.90,
                    "stockQuantity": 25,
                    "unit": "UN"
                }
                """)
            .when().post("/admin/parts")
            .then()
            .statusCode(201)
            .body("name", equalTo("Filtro de Ar Test"))
            .body("stockQuantity", equalTo(25))
            .extract().<Integer>path("id").longValue();
    }

    @Test
    @Order(2)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void create_asAttendant_shouldReturn201() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "X", "unitPrice": 1.0, "stockQuantity": 1, "unit": "UN"
                }
                """)
            .when().post("/admin/parts")
            .then()
            .statusCode(201);
    }

    @Test
    @Order(3)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void listAll_asAttendant_shouldReturn200() {
        given()
            .when().get("/admin/parts")
            .then()
            .statusCode(200)
            .body("$", hasSize(greaterThan(0)));
    }

    @Test
    @Order(4)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void findById_shouldReturnPart() {
        given()
            .when().get("/admin/parts/" + partId)
            .then()
            .statusCode(200)
            .body("id", equalTo(partId.intValue()));
    }

    @Test
    @Order(5)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void adjustStock_increase_shouldUpdateStock() {
        given()
            .when().patch("/admin/parts/" + partId + "/stock?adjustment=10")
            .then()
            .statusCode(200)
            .body("stockQuantity", equalTo(35));
    }

    @Test
    @Order(6)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void adjustStock_belowZero_shouldReturn422() {
        given()
            .when().patch("/admin/parts/" + partId + "/stock?adjustment=-9999")
            .then()
            .statusCode(422)
            .body("message", containsString("Estoque insuficiente"));
    }

    @Test
    @Order(7)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void adjustStock_directly_asAttendant_shouldReturn403() {
        // Atendente não ajusta estoque diretamente; deve abrir solicitação.
        given()
            .when().patch("/admin/parts/" + partId + "/stock?adjustment=5")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(8)
    void listAll_unauthenticated_shouldReturn401() {
        given()
            .when().get("/admin/parts")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(9)
    @TestSecurity(user = "atendente", roles = {"ATTENDANT"})
    void reactivate_asAttendant_shouldReturn403() {
        given()
            .when().patch("/admin/parts/" + partId + "/reactivate")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(11)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void adjustStock_withoutAdjustment_shouldReturn400() {
        given()
            .when().patch("/admin/parts/" + partId + "/stock")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(12)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void adjustStock_withZero_shouldReturn422() {
        given()
            .when().patch("/admin/parts/" + partId + "/stock?adjustment=0")
            .then()
            .statusCode(422);
    }

    @Test
    @Order(13)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withUnitPriceTooLarge_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Peça Cara",
                    "unitPrice": 999999999999.99,
                    "stockQuantity": 1,
                    "unit": "UN"
                }
                """)
            .when().post("/admin/parts")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(10)
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void softDeleteThenReactivate_cycle() {
        // Soft-delete: desativa a peça
        given()
            .when().delete("/admin/parts/" + partId)
            .then()
            .statusCode(204);

        // Ainda existe (admin consegue ver), porém inativa
        given()
            .when().get("/admin/parts/" + partId)
            .then()
            .statusCode(200)
            .body("active", equalTo(false));

        // Reativação reverte o soft-delete
        given()
            .when().patch("/admin/parts/" + partId + "/reactivate")
            .then()
            .statusCode(200)
            .body("active", equalTo(true));
    }
}
