package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {

    @Test
    void login_withValidAdminCredentials_shouldReturn200WithToken() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"username": "admin", "password": "admin123"}
                """)
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("token", not(blankOrNullString()))
            .body("username", equalTo("admin"))
            .body("role", equalTo("ADMIN"))
            .body("expiresIn", greaterThan(0));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"username": "admin", "password": "wrongpass"}
                """)
            .when().post("/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    void login_withUnknownUser_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"username": "nonexistent", "password": "pass"}
                """)
            .when().post("/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    void login_withEmptyBody_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/auth/login")
            .then()
            .statusCode(400);
    }

    @Test
    void login_withAttendantCredentials_shouldReturn200() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"username": "atendente", "password": "atendente123"}
                """)
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("role", equalTo("ATTENDANT"));
    }
}
