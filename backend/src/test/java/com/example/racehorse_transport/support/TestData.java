package com.example.racehorse_transport.support;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RoleRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

// Dựng nhanh dữ liệu cho test tích hợp (docs/KE_HOACH_TRIEN_KHAI.md, P0.3-A). Dùng kèm @PostgresIntegrationTest.
// Quy tắc dùng chung: chỉ THÊM method mới, không sửa method đã có (tránh làm hỏng test của luồng khác).
@TestComponent
public class TestData {

    // Mật khẩu của mọi tài khoản do TestData tạo (chỉ dùng trong test)
    public static final String PASSWORD = "Test@123456";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private CustomerRepository customers;
    @Autowired private CountryRepository countries;
    @Autowired private StaffRepository staff;
    @Autowired private LocationRepository locations;
    @Autowired private BookingRepository bookings;
    @Autowired private RouteRepository routes;
    @Autowired private DepartureRepository departures;
    @Autowired private PasswordEncoder passwordEncoder;

    // Tên duy nhất trong một lần chạy test
    public String unique(String prefix) {
        return prefix + "-" + System.nanoTime() % 1_000_000_000 + "-" + SEQUENCE.incrementAndGet();
    }

    // Tài khoản ACTIVE, đã xác thực email, có vai trò cho trước. Chưa có hồ sơ customer hay staff
    public AppUser user(String username, String roleCode) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@test.racehorse");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setRole(roles.getReferenceById(roleCode));
        return users.saveAndFlush(user);
    }

    // Khách hàng: tài khoản CUSTOMER kèm hồ sơ customer
    public Customer customer(String username) {
        AppUser user = user(username, "CUSTOMER");
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setCustomerType(CustomerType.PERSON);
        customer.setFullName("Khách " + username);
        customer.setPhone("0909000999");
        customer.setCountry(countries.getReferenceById("VN"));
        return customers.saveAndFlush(customer);
    }

    // Nhân viên thuộc trụ sở với một vai trò nội bộ
    public Staff staff(String username, String roleCode) {
        AppUser user = user(username, roleCode);
        Staff profile = new Staff();
        profile.setUser(user);
        profile.setStaffCode(unique("STF"));
        profile.setFullName("Nhân viên " + username);
        profile.setPhone("0900000999");
        profile.setHireDate(LocalDate.of(2026, 1, 1));
        profile.setEmploymentStatus(EmploymentStatus.ACTIVE);
        profile.setHomeHub(locations.findByCode("HUB-HCM").orElseThrow());
        return staff.saveAndFlush(profile);
    }

    // Đơn tối thiểu ở trạng thái cho trước, chưa có ngựa và địa chỉ
    public Booking booking(Customer customer, BookingType type, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCode(unique("TEST"));
        booking.setCustomer(customer);
        booking.setBookingType(type);
        booking.setRequestedPickupDate(LocalDate.of(2027, 1, 15));
        booking.setStatus(status);
        booking.setCreatedBy(customer.getUser());
        return bookings.saveAndFlush(booking);
    }

    // Chuyến đột xuất (không theo lịch) trên một tuyến, giờ tính theo UTC cho đơn giản
    public Departure departure(String routeCode, LocalDate date, int capacityStalls) {
        Route route = routes.findByCode(routeCode).orElseThrow();
        Departure departure = new Departure();
        departure.setRoute(route);
        departure.setDepartureDate(date);
        departure.setPlannedDepartAt(date.atTime(6, 0).toInstant(ZoneOffset.UTC));
        departure.setBookingCutoffAt(date.minusDays(3).atTime(6, 0).toInstant(ZoneOffset.UTC));
        departure.setCapacityStalls(capacityStalls);
        departure.setStatus(DepartureStatus.OPEN);
        return departures.saveAndFlush(departure);
    }

    // Tiện cho test cần mốc thời gian cố định
    public static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }
}
