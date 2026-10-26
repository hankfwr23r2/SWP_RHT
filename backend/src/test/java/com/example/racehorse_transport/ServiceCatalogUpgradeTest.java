package com.example.racehorse_transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Nâng cấp lên bản có mã dịch vụ và đơn vị tính trên một DB đang chạy thật: bảng dịch vụ đã có dữ liệu từ bản cũ.
// Ứng dụng phải khởi động được, giữ nguyên dữ liệu cũ, gán mã và đơn vị tính cho từng dịch vụ,
// và khách mở bước chọn dịch vụ (GET /public/services) vẫn thấy danh sách.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceCatalogUpgradeTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
        createCatalogFromPreviousRelease();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Test
    void startsOnExistingCatalogAndKeepsServicesBookable() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select serviceid, service_name, price, service_code, billing_unit from service order by serviceid")) {
            while (rs.next()) {
                rows.add(new String[] { rs.getString(1), rs.getString(2), rs.getBigDecimal(3).toPlainString(),
                        rs.getString(4), rs.getString(5) });
            }
        }
        assertThat(rows).hasSize(10);
        assertThat(rows).extracting(r -> r[1]).contains("Khoang tiêu chuẩn", "Gói thể thao", "Mua bảo hiểm");
        assertThat(rows).filteredOn(r -> r[0].equals("5")).first()
                .satisfies(r -> assertThat(new BigDecimal(r[2])).isEqualByComparingTo("500000"));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r[3]).as("mã dịch vụ của %s", r[1]).isNotBlank();
            assertThat(r[4]).as("đơn vị tính của %s", r[1]).isIn("PER_TRIP", "PER_HORSE", "PER_HORSE_DAY");
        });

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/public/services")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode services = new ObjectMapper().readTree(response.body()).path("data");
        assertThat(services.isArray()).isTrue();
        assertThat(services.size()).isPositive();
        services.forEach(service -> {
            assertThat(service.path("serviceCode").asText()).isNotBlank();
            assertThat(service.path("billingUnit").asText()).isNotBlank();
        });
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    // Cấu trúc và dữ liệu của hai bảng như trên DB đang chạy trước khi thêm service_code, billing_unit
    private static void createCatalogFromPreviousRelease() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table category (
                        categoryid integer primary key,
                        category_name varchar(255),
                        description text)""");
            statement.execute("""
                    create table service (
                        serviceid integer primary key,
                        price numeric(18,2) not null,
                        service_name varchar(255) not null,
                        status varchar(50) not null,
                        categoryid integer not null references category(categoryid),
                        description varchar(1000))""");
            statement.execute("""
                    insert into category values
                        (1, 'Khoang trên xe', 'Các lựa chọn khoang vận chuyển ngựa'),
                        (2, 'Gói thức ăn', 'Các lựa chọn gói thức ăn cho ngựa'),
                        (3, 'Cữ nước', 'Các lựa chọn tần suất cung cấp nước cho ngựa'),
                        (4, 'Bảo hiểm chuyến đi', 'Các lựa chọn bảo hiểm cho chuyến vận chuyển ngựa')""");
            statement.execute("""
                    insert into service (serviceid, categoryid, service_name, price, status) values
                        (1, 1, 'Khoang tiêu chuẩn', 1000000, 'ACTIVE'),
                        (2, 1, 'Khoang đơn mở rộng', 2000000, 'ACTIVE'),
                        (3, 2, 'Gói cơ bản', 0, 'ACTIVE'),
                        (4, 2, 'Gói nâng cao', 300000, 'ACTIVE'),
                        (5, 2, 'Gói thể thao', 500000, 'ACTIVE'),
                        (6, 3, 'Mỗi 3 giờ', 0, 'ACTIVE'),
                        (7, 3, 'Mỗi 2 giờ', 50000, 'ACTIVE'),
                        (8, 3, 'Mỗi giờ', 100000, 'ACTIVE'),
                        (9, 4, 'Mua bảo hiểm', 20000000, 'ACTIVE'),
                        (10, 4, 'Từ chối', 0, 'ACTIVE')""");
        } catch (SQLException e) {
            throw new IllegalStateException("Không dựng được dữ liệu của bản cũ", e);
        }
    }
}
