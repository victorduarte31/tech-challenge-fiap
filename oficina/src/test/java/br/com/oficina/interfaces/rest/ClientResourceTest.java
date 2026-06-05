package br.com.oficina.interfaces.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ClientResourceTest {

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void listAll_shouldReturn200() {
        given()
            .when().get("/admin/clients")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withValidData_shouldReturn201() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Maria Oliveira",
                    "cpfCnpj": "529.982.247-25",
                    "clientType": "PF",
                    "email": "maria@email.com",
                    "phone": "11988887777"
                }
                """)
            .when().post("/admin/clients")
            .then()
            .statusCode(201)
            .body("name", equalTo("Maria Oliveira"))
            .body("cpfCnpj", equalTo("52998224725"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withInvalidCpf_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Teste",
                    "cpfCnpj": "123.456.789-00",
                    "clientType": "PF",
                    "email": "test@test.com"
                }
                """)
            .when().post("/admin/clients")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withMissingName_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "cpfCnpj": "111.444.777-35",
                    "clientType": "PF"
                }
                """)
            .when().post("/admin/clients")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void findById_whenNotFound_shouldReturn404() {
        given()
            .when().get("/admin/clients/99999")
            .then()
            .statusCode(404);
    }

    @Test
    void listAll_withoutAuth_shouldReturn401() {
        given()
            .when().get("/admin/clients")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withNameTooLong_shouldReturn400() {
        String longName = "A".repeat(200); // coluna name é VARCHAR(150)
        given()
            .contentType(ContentType.JSON)
            .body(String.format("{\"name\":\"%s\",\"cpfCnpj\":\"111.444.777-35\",\"clientType\":\"PF\"}", longName))
            .when().post("/admin/clients")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void create_withEmptyBody_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .when().post("/admin/clients")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void createAndRetrieve_fullCycle() {
        Integer id = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Carlos Souza",
                    "cpfCnpj": "021.643.498-06",
                    "clientType": "PF",
                    "email": "carlos@email.com"
                }
                """)
            .when().post("/admin/clients")
            .then()
            .statusCode(201)
            .extract().path("id");

        given()
            .when().get("/admin/clients/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Carlos Souza"));
    }
}
