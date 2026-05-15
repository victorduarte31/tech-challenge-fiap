package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class MetricsResourceTest {

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void getMetrics_asAdmin_shouldReturn200() {
        given()
            .when().get("/admin/metrics")
            .then()
            .statusCode(200)
            .body("totalWorkOrders", greaterThanOrEqualTo(0))
            .body("openWorkOrders", greaterThanOrEqualTo(0))
            .body("finishedWorkOrders", greaterThanOrEqualTo(0))
            .body("cancelledWorkOrders", greaterThanOrEqualTo(0))
            .body("averageExecutionTimeMinutes", greaterThanOrEqualTo(0f))
            .body("totalRevenue", notNullValue())
            .body("lowStockParts", greaterThanOrEqualTo(0));
    }

    @Test
    @TestSecurity(user = "mecanico", roles = {"MECHANIC"})
    void getMetrics_asMechanic_shouldReturn403() {
        given()
            .when().get("/admin/metrics")
            .then()
            .statusCode(403);
    }

    @Test
    void getMetrics_unauthenticated_shouldReturn401() {
        given()
            .when().get("/admin/metrics")
            .then()
            .statusCode(401);
    }
}
