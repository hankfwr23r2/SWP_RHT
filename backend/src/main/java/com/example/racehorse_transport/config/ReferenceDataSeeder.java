package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Category;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Regulation;
import com.example.racehorse_transport.entity.Service;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.CategoryRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Đồng bộ metadata mạng lưới địa điểm mỗi lần khởi động; dữ liệu mẫu chỉ nạp khi bật
 * APP_SEED_REFERENCE_DATA=true (docker-compose bật sẵn).
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class ReferenceDataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ServiceRepository serviceRepository;
    private final HorseTypeRepository horseTypeRepository;
    private final LocationRepository locationRepository;
    private final VehicleRepository vehicleRepository;
    private final RegulationRepository regulationRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;

    @Value("${app.seed-reference-data:false}")
    private boolean enabled;

    // {CategoryID, CategoryName, description}
    private static final String[][] CATEGORIES = {
            new String[] { "1", "Khoang trên xe", "Các lựa chọn khoang vận chuyển ngựa" },
            new String[] { "2", "Gói thức ăn", "Các lựa chọn gói thức ăn cho ngựa" },
            new String[] { "3", "Cữ nước", "Các lựa chọn tần suất cung cấp nước cho ngựa" },
            new String[] { "4", "Bảo hiểm chuyến đi", "Các lựa chọn bảo hiểm cho chuyến vận chuyển ngựa" }
    };

    // {ServiceID, ServiceName, CategoryID, price, status, serviceCode, billingUnit, description}
    private static final String[][] SERVICES = {
            new String[] { "1", "Khoang tiêu chuẩn", "1", "1000000", "ACTIVE", "STALL_STANDARD", "PER_HORSE", "" },
            new String[] { "2", "Khoang đơn mở rộng", "1", "2000000", "ACTIVE", "STALL_SINGLE_EXTENDED", "PER_HORSE", "" },
            new String[] { "3", "Gói cơ bản", "2", "0", "ACTIVE", "FEED_BASIC", "PER_HORSE_DAY", "" },
            new String[] { "4", "Gói nâng cao", "2", "300000", "ACTIVE", "FEED_ADVANCED", "PER_HORSE_DAY", "" },
            new String[] { "5", "Gói thể thao", "2", "500000", "ACTIVE", "FEED_SPORT", "PER_HORSE_DAY", "" },
            new String[] { "6", "Mỗi 3 giờ", "3", "0", "INACTIVE", "WATER_EVERY_3H", "PER_HORSE", "" },
            new String[] { "7", "Mỗi 2 giờ", "3", "50000", "INACTIVE", "WATER_EVERY_2H", "PER_HORSE", "" },
            new String[] { "8", "Mỗi giờ", "3", "100000", "INACTIVE", "WATER_EVERY_1H", "PER_HORSE", "" },
            new String[] { "9", "Mua bảo hiểm", "4", "20000000", "ACTIVE", "INSURANCE_BUY", "PER_HORSE", "" },
            new String[] { "10", "Từ chối", "4", "0", "ACTIVE", "INSURANCE_DECLINED", "PER_HORSE", "" }
    };

    private static final Map<Integer, String> LEGACY_SERVICE_CODES = Map.of(
            1, "STALL_STANDARD",
            2, "STALL_SINGLE_EXTENDED",
            3, "FEED_BASIC",
            4, "FEED_ADVANCED",
            5, "FEED_SPORT",
            6, "WATER_EVERY_3H",
            7, "WATER_EVERY_2H",
            8, "WATER_EVERY_1H",
            9, "INSURANCE_BUY",
            10, "INSURANCE_DECLINED");

    private static final Map<Integer, String> LEGACY_BILLING_UNITS = Map.of(
            1, "PER_HORSE",
            2, "PER_HORSE_DAY",
            3, "PER_HORSE",
            4, "PER_HORSE");

    // {RegulationName, Description, Status, LocationID, DocumentType, Country}
    private static final String[][] REGULATIONS = {
            // Quy định áp dụng chung (Nội địa & chuẩn bị chung)
            new String[] { "Giấy chứng nhận kiểm dịch động vật vận chuyển", "Yêu cầu mộc đỏ của Chi cục Thú y vùng trước khi vận chuyển theo Thông tư 25/2016/TT-BNNPTNT.", "ACTIVE", "", "health_cert", "Việt Nam" },
            new String[] { "Giấy ủy quyền áp tải & vận chuyển ngựa", "Văn bản ủy quyền song ngữ xác nhận lái xe và nhân viên hộ tống chịu trách nhiệm áp tải cá thể ngựa.", "ACTIVE", "", "poa", "Việt Nam" },

            // Cửa khẩu Mộc Bài - Bavet (ID 16 - Campuchia)
            new String[] { "Tờ khai hải quan điện tử cửa khẩu Mộc Bài - Bavet", "Khai báo hải quan qua cặp cửa khẩu Mộc Bài (Tây Ninh) - Bavet (Svay Rieng), đính kèm số vận đơn và biển số xe vận chuyển.", "ACTIVE", "16", "customs_declaration", "Campuchia" },
            new String[] { "Giấy phép nhập khẩu động vật Campuchia (MAFF)", "Giấy phép nhập khẩu do Cục Thú y và Sức khỏe Động vật Campuchia (Bộ Nông Lâm Thủy sản) phê duyệt cho ngựa nhập cảnh qua Bavet.", "ACTIVE", "16", "import_permit", "Campuchia" },
            new String[] { "Chứng thư kiểm dịch thú y biên giới Mộc Bài", "Kiểm tra lâm sàng động vật sống và cấp giấy kiểm dịch biên giới xuất cảnh sang Campuchia.", "ACTIVE", "16", "quarantine_cert", "Campuchia" },

            // Cửa khẩu Tịnh Biên - Phnom Den (ID 18 - Campuchia)
            new String[] { "Tờ khai hải quan xuất nhập cảnh Tịnh Biên - Phnom Den", "Khai báo thủ tục hải quan xuất cảnh ngựa thể thao qua cửa khẩu Tịnh Biên (An Giang).", "ACTIVE", "18", "customs_declaration", "Campuchia" },
            new String[] { "Giấy phép nhập khẩu Campuchia qua Phnom Den", "Giấy phép kiểm dịch nhập khẩu từ Bộ Nông Lâm Thủy sản Campuchia cho ngựa qua tỉnh Takeo.", "ACTIVE", "18", "import_permit", "Campuchia" },

            // Cửa khẩu Lao Bảo - Densavanh (ID 24 - Lào)
            new String[] { "Tờ khai hải quan điện tử cửa khẩu Lao Bảo - Densavanh", "Khai báo hải quan hàng hóa đặc biệt qua Hành lang kinh tế Đông - Tây tại cặp cửa khẩu Lao Bảo (Quảng Trị) - Densavanh (Lào).", "ACTIVE", "24", "customs_declaration", "Lào" },
            new String[] { "Giấy phép nhập khẩu động vật CHDCND Lào (DLF)", "Giấy phép nhập khẩu do Cục Chăn nuôi và Thủy sản Lào (Bộ Nông Lâm) phê duyệt.", "ACTIVE", "24", "import_permit", "Lào" },
            new String[] { "Sổ tạm xuất - tái nhập ATA Carnet qua Lào", "Sổ chứng từ hải quan quốc tế cho ngựa đua/ngựa thể thao và phương tiện chuyên dụng lưu thông tạm thời.", "ACTIVE", "24", "ata_carnet", "Lào" },

            // Cửa khẩu Cầu Treo - Nam Phao (ID 25 - Lào)
            new String[] { "Tờ khai hải quan cửa khẩu quốc tế Cầu Treo - Nam Phao", "Khai báo hải quan phương tiện và động vật sống qua cửa khẩu Cầu Treo (Hà Tĩnh) sang tỉnh Bolikhamxay (Lào).", "ACTIVE", "25", "customs_declaration", "Lào" },
            new String[] { "Giấy phép nhập khẩu động vật qua Cửa khẩu Nam Phao", "Giấy phép nhập khẩu động vật sống vào lãnh thổ Lào qua tỉnh Bolikhamxay.", "ACTIVE", "25", "import_permit", "Lào" }
    };

    // {HorseTypeID, TypeName, Description}
    private static final String[][] HORSE_TYPES = {
            new String[] { "1", "Thoroughbred", "A fast, athletic horse breed primarily used for racing." },
            new String[] { "2", "Arabian", "An elegant horse known for its endurance and distinctive head shape." },
            new String[] { "3", "American Quarter Horse", "A muscular horse known for its acceleration and versatility." },
            new String[] { "4", "Friesian", "A powerful horse known for its black coat and long mane." },
            new String[] { "5", "Andalusian", "A graceful Spanish horse known for its strength and agility." },
            new String[] { "6", "Lusitano", "A Portuguese horse valued for its agility and responsiveness." },
            new String[] { "7", "Appaloosa", "A versatile horse recognized by its distinctive spotted coat." },
            new String[] { "8", "American Paint Horse", "A stock horse known for its colorful coat patterns." },
            new String[] { "9", "Morgan", "A compact, versatile horse known for its strength and friendly temperament." },
            new String[] { "10", "Tennessee Walking Horse", "A riding horse famous for its smooth, comfortable gait." },
            new String[] { "11", "Standardbred", "A horse breed commonly used in harness racing." },
            new String[] { "12", "Hanoverian", "An athletic sport horse suited for dressage and show jumping." },
            new String[] { "13", "Akhal-Teke", "A slender horse famous for its metallic-looking coat and endurance." },
            new String[] { "14", "Mustang", "A hardy horse descended from free-roaming horses in North America." },
            new String[] { "15", "Clydesdale", "A large draft horse traditionally used for heavy pulling." },
            new String[] { "16", "Shire", "A giant draft horse known for its strength and calm temperament." },
            new String[] { "17", "Belgian Draft", "A powerful draft horse bred for heavy farm and hauling work." },
            new String[] { "18", "Percheron", "A strong French draft horse used for pulling and agricultural work." },
            new String[] { "19", "Haflinger", "A sturdy horse known for its chestnut coat and flaxen mane." },
            new String[] { "20", "Shetland Pony", "A small, sturdy pony known for its strength and hardiness." }
    };

    // {LocationID, LocationType, LocationName, Address, City, Province, Country}
    private static final String[][] LOCATIONS = {
            new String[] { "1", "Warehouse", "Kho Đồng Nai", "Trang trại Đua ngựa Long Thành", "Long Thành", "Đồng Nai", "Việt Nam" },
            new String[] { "2", "Warehouse", "Kho Long An", "Trang trại Huấn luyện Mỹ Quỳnh (Đức Hòa)", "Đức Hòa", "Long An", "Việt Nam" },
            new String[] { "3", "Warehouse", "Kho Bình Dương", "Trung tâm Cưỡi ngựa Thủ Dầu Một", "Thủ Dầu Một", "Bình Dương", "Việt Nam" },
            new String[] { "4", "RidingClub", "CLB Cưỡi ngựa Sài Gòn (Saigon Pony Club)", "38, hẻm 42, đường Lê Văn Thịnh, Quận 2", "TP. Thủ Đức", "TP.HCM", "Việt Nam" },
            new String[] { "5", "Warehouse", "Kho Phnom Penh", "Trung tâm Kiểm dịch Động vật Phnom Penh", "Phnom Penh", "Phnom Penh", "Campuchia" },
            new String[] { "6", "Warehouse", "Kho Siem Reap", "Trại Ngựa & Vật nuôi Angkor", "Siem Reap", "Siem Reap", "Campuchia" },
            new String[] { "7", "RidingClub", "CLB Cưỡi ngựa Hoàng gia Phnom Penh", "Câu lạc bộ", "Phnom Penh", "Phnom Penh", "Campuchia" },
            new String[] { "8", "Warehouse", "Kho Viêng Chăn", "Trang trại Chăn nuôi & Kiểm dịch Vientiane", "Viêng Chăn", "Vientiane Capital", "Lào" },
            new String[] { "9", "RidingClub", "CLB Mã cầu & Cưỡi ngựa Viêng Chăn", "Câu lạc bộ", "Viêng Chăn", "Vientiane Capital", "Lào" },
            new String[] { "10", "RaceTrack", "Trường đua Đại Nam", "Khu du lịch Đại Nam, phường Hiệp An", "Thủ Dầu Một", "Bình Dương", "Việt Nam" },
            new String[] { "11", "RidingClub", "Vietgangz Horse Club", "88 Đường số 9, Long Trường", "TP. Thủ Đức", "TP.HCM", "Việt Nam" },
            new String[] { "12", "RidingClub", "Genius Resort Equestrian Centre", "Đường Northbridge, khu Sen Sok", "Phnom Penh", "Phnom Penh", "Campuchia" },
            new String[] { "13", "Stable", "The Happy Ranch Horse Farm", "Cách trung tâm Siem Reap khoảng 2 km", "Siem Reap", "Siem Reap", "Campuchia" },
            new String[] { "14", "Stable", "Asha Stables", "Huyện Sangthong", "Sangthong", "Vientiane Capital", "Lào" },
            new String[] { "15", "Stable", "NamKhan Horses Stables", "Bản Donkeo, gần Bản Phanom", "Luang Prabang", "Luang Prabang", "Lào" },
            new String[] { "16", "BorderGate", "Cửa khẩu quốc tế Mộc Bài - Bavet", "Cặp cửa khẩu với Bavet (Svay Rieng)", "Bến Cầu", "Tây Ninh", "Việt Nam" },
            new String[] { "17", "BorderGate", "Cửa khẩu quốc tế Xa Mát - Trapeang Phlong", "Cặp cửa khẩu với Trapeang Phlong", "Tân Biên", "Tây Ninh", "Việt Nam" },
            new String[] { "18", "BorderGate", "Cửa khẩu quốc tế Tịnh Biên - Phnom Den", "Cặp cửa khẩu với Phnom Den", "Tịnh Biên", "An Giang", "Việt Nam" },
            new String[] { "19", "BorderGate", "Cửa khẩu quốc tế Hà Tiên - Prek Chak", "Cặp cửa khẩu với Prek Chak", "Hà Tiên", "Kiên Giang", "Việt Nam" },
            new String[] { "20", "BorderGate", "Cửa khẩu quốc tế Hoa Lư - Trapeang Sre", "Cặp cửa khẩu với Trapeang Sre", "Lộc Ninh", "Bình Phước", "Việt Nam" },
            new String[] { "21", "BorderGate", "Cửa khẩu quốc tế Lệ Thanh - O Yadaw", "Cặp cửa khẩu với O Yadaw", "Đức Cơ", "Gia Lai", "Việt Nam" },
            new String[] { "22", "BorderGate", "Cửa khẩu quốc tế Bình Hiệp - Prey Vor", "Cặp cửa khẩu với Prey Vor", "Kiến Tường", "Long An", "Việt Nam" },
            new String[] { "23", "BorderGate", "Cửa khẩu quốc tế Dinh Bà - Bon Tia Chak Krey", "Cặp cửa khẩu với Bon Tia Chak Krey", "Tân Hồng", "Đồng Tháp", "Việt Nam" },
            new String[] { "24", "BorderGate", "Cửa khẩu quốc tế Lao Bảo - Densavanh", "Cặp cửa khẩu với Densavanh (Savannakhet)", "Hướng Hóa", "Quảng Trị", "Việt Nam" },
            new String[] { "25", "BorderGate", "Cửa khẩu quốc tế Cầu Treo - Nam Phao", "Cặp cửa khẩu với Nam Phao", "Hương Sơn", "Hà Tĩnh", "Việt Nam" },
            new String[] { "26", "BorderGate", "Cửa khẩu quốc tế Tây Trang - Sop Hun", "Cặp cửa khẩu với Sop Hun", "Điện Biên", "Điện Biên", "Việt Nam" },
            new String[] { "27", "BorderGate", "Cửa khẩu quốc tế Cha Lo - Na Phao", "Cặp cửa khẩu với Na Phao", "Minh Hóa", "Quảng Bình", "Việt Nam" },
            new String[] { "28", "BorderGate", "Cửa khẩu quốc tế Nậm Cắn - Nong Haet", "Cặp cửa khẩu với Nong Haet", "Kỳ Sơn", "Nghệ An", "Việt Nam" },
            new String[] { "29", "BorderGate", "Cửa khẩu quốc tế Na Mèo - Nam Xoi", "Cặp cửa khẩu với Nam Xoi", "Quan Sơn", "Thanh Hóa", "Việt Nam" },
            new String[] { "30", "BorderGate", "Cửa khẩu quốc tế La Lay - La Lay", "Cặp cửa khẩu La Lay phía Lào", "Đakrông", "Quảng Trị", "Việt Nam" },
            new String[] { "31", "RestStop", "Trạm dừng nghỉ Km41+100 (cao tốc TP.HCM - Long Thành - Dầu Giây)", "Km41+100, xã Sông Nhạn", "Cẩm Mỹ", "Đồng Nai", "Việt Nam" },
            new String[] { "32", "RestStop", "Trạm dừng nghỉ Đại Phú 2 (trạm tạm, mùa cao điểm)", "Gần nút giao Dầu Giây", "Xuân Lộc", "Đồng Nai", "Việt Nam" },
            new String[] { "33", "RestStop", "Điểm dừng nghỉ Trảng Bàng (QL22)", "Quốc lộ 22", "Trảng Bàng", "Tây Ninh", "Việt Nam" },
            new String[] { "34", "RestStop", "Điểm dừng nghỉ Gò Dầu (QL22)", "Quốc lộ 22", "Gò Dầu", "Tây Ninh", "Việt Nam" },
            new String[] { "35", "RestStop", "Điểm dừng nghỉ Tân An (QL1)", "Quốc lộ 1", "Tân An", "Long An", "Việt Nam" },
            new String[] { "36", "RestStop", "Điểm dừng nghỉ Châu Đốc", "Hướng cửa khẩu Tịnh Biên", "Châu Đốc", "An Giang", "Việt Nam" },
            new String[] { "37", "RestStop", "Điểm dừng nghỉ Phan Thiết", "Quốc lộ 1", "Phan Thiết", "Bình Thuận", "Việt Nam" },
            new String[] { "38", "RestStop", "Điểm dừng nghỉ Nha Trang", "Quốc lộ 1", "Nha Trang", "Khánh Hòa", "Việt Nam" },
            new String[] { "39", "RestStop", "Điểm dừng nghỉ Tuy Hòa", "Quốc lộ 1", "Tuy Hòa", "Phú Yên", "Việt Nam" },
            new String[] { "40", "RestStop", "Điểm dừng nghỉ Quảng Ngãi", "Quốc lộ 1", "Quảng Ngãi", "Quảng Ngãi", "Việt Nam" },
            new String[] { "41", "RestStop", "Điểm dừng nghỉ Đà Nẵng", "Quốc lộ 1", "Đà Nẵng", "Đà Nẵng", "Việt Nam" },
            new String[] { "42", "RestStop", "Điểm dừng nghỉ Huế", "Quốc lộ 1", "Huế", "Thừa Thiên Huế", "Việt Nam" },
            new String[] { "43", "RestStop", "Điểm dừng nghỉ Đông Hà", "Quốc lộ 1, đầu quốc lộ 9", "Đông Hà", "Quảng Trị", "Việt Nam" },
            new String[] { "44", "RestStop", "Điểm dừng nghỉ Khe Sanh", "Quốc lộ 9, trước cửa khẩu Lao Bảo", "Khe Sanh (Hướng Hóa)", "Quảng Trị", "Việt Nam" },
            new String[] { "45", "RestStop", "Điểm dừng nghỉ Bavet", "Quốc lộ 1 (Campuchia)", "Bavet", "Svay Rieng", "Campuchia" },
            new String[] { "46", "RestStop", "Điểm dừng nghỉ Svay Rieng", "Quốc lộ 1 (Campuchia)", "Svay Rieng", "Svay Rieng", "Campuchia" },
            new String[] { "47", "RestStop", "Điểm dừng nghỉ Neak Loeung", "Quốc lộ 1, cạnh cầu Tsubasa", "Neak Loeung", "Prey Veng", "Campuchia" },
            new String[] { "48", "RestStop", "Điểm dừng nghỉ Kampong Cham", "Quốc lộ 7", "Kampong Cham", "Kampong Cham", "Campuchia" },
            new String[] { "49", "RestStop", "Điểm dừng nghỉ Kampong Thom", "Quốc lộ 6", "Kampong Thom", "Kampong Thom", "Campuchia" },
            new String[] { "50", "RestStop", "Điểm dừng nghỉ Sepon (Xepon)", "Quốc lộ 9 (Lào)", "Sepon", "Savannakhet", "Lào" },
            new String[] { "51", "RestStop", "Điểm dừng nghỉ Savannakhet", "Quốc lộ 9 và 13 (Lào)", "Savannakhet", "Savannakhet", "Lào" },
            new String[] { "52", "RestStop", "Điểm dừng nghỉ Thakhek", "Quốc lộ 13 (Lào)", "Thakhek", "Khammouane", "Lào" },
            new String[] { "53", "RestStop", "Điểm dừng nghỉ Paksan", "Quốc lộ 13 (Lào)", "Paksan", "Bolikhamxay", "Lào" },
            new String[] { "54", "RestStop", "Điểm dừng nghỉ Pakse", "Quốc lộ 13 (Lào)", "Pakse", "Champasak", "Lào" }
    };

    private record Coordinates(double latitude, double longitude) {}

    private static final Map<Integer, Coordinates> LEGACY_LOCATION_COORDINATES = Map.ofEntries(
            Map.entry(1, new Coordinates(10.78, 107.0)), Map.entry(2, new Coordinates(10.88, 106.4)),
            Map.entry(3, new Coordinates(11.0, 106.65)), Map.entry(4, new Coordinates(10.79, 106.74)),
            Map.entry(5, new Coordinates(11.56, 104.92)), Map.entry(6, new Coordinates(13.36, 103.86)),
            Map.entry(7, new Coordinates(11.55, 104.9)), Map.entry(8, new Coordinates(17.97, 102.6)),
            Map.entry(9, new Coordinates(17.98, 102.62)), Map.entry(10, new Coordinates(11.0, 106.65)),
            Map.entry(11, new Coordinates(10.86, 106.83)), Map.entry(12, new Coordinates(11.58, 104.88)),
            Map.entry(13, new Coordinates(13.36, 103.86)), Map.entry(14, new Coordinates(17.97, 102.6)),
            Map.entry(15, new Coordinates(19.89, 102.13)),
            Map.entry(16, new Coordinates(11.07, 106.2)), Map.entry(18, new Coordinates(10.6, 104.95)),
            Map.entry(24, new Coordinates(16.62, 106.6)), Map.entry(25, new Coordinates(18.38, 105.13)),
            Map.entry(26, new Coordinates(21.23, 102.95)),
            Map.entry(33, new Coordinates(11.03, 106.37)), Map.entry(37, new Coordinates(10.93, 108.1)),
            Map.entry(38, new Coordinates(12.24, 109.2)), Map.entry(39, new Coordinates(13.1, 109.3)),
            Map.entry(40, new Coordinates(15.12, 108.8)), Map.entry(41, new Coordinates(16.05, 108.2)),
            Map.entry(42, new Coordinates(16.46, 107.6)), Map.entry(43, new Coordinates(16.82, 107.1)),
            Map.entry(45, new Coordinates(11.08, 106.15)),
            Map.entry(46, new Coordinates(11.09, 105.8)), Map.entry(47, new Coordinates(11.26, 105.28)),
            Map.entry(48, new Coordinates(12.0, 105.46)), Map.entry(49, new Coordinates(12.71, 104.88)),
            Map.entry(51, new Coordinates(16.57, 104.75)),
            Map.entry(52, new Coordinates(17.4, 104.8)), Map.entry(53, new Coordinates(18.38, 103.65)),
            Map.entry(54, new Coordinates(15.12, 105.8)));

    private static final Map<Integer, String> LEGACY_LOCATION_CODES = Map.ofEntries(
            Map.entry(1, "KHO-DN"), Map.entry(2, "KHO-LA"), Map.entry(3, "KHO-BD"),
            Map.entry(4, "CLB-SG"), Map.entry(5, "KHO-PNH"), Map.entry(6, "KHO-SR"),
            Map.entry(7, "SAI-PNH"), Map.entry(8, "KHO-VTE"), Map.entry(9, "CLB-VTE"),
            Map.entry(16, "GATE-MOC-BAI"), Map.entry(18, "GATE-TINH-BIEN"),
            Map.entry(24, "GATE-LAO-BAO"), Map.entry(25, "GATE-CAU-TREO"),
            Map.entry(26, "GATE-TAY-TRANG"), Map.entry(33, "REST-TRANG-BANG"),
            Map.entry(37, "REST-PHAN-THIET"), Map.entry(38, "REST-NHA-TRANG"),
            Map.entry(39, "REST-TUY-HOA"), Map.entry(40, "REST-QUANG-NGAI"),
            Map.entry(41, "REST-DA-NANG"), Map.entry(42, "REST-HUE"),
            Map.entry(43, "REST-DONG-HA"), Map.entry(44, "REST-LAO-BAO"),
            Map.entry(45, "REST-BAVET"), Map.entry(46, "REST-SVAY-RIENG"),
            Map.entry(47, "REST-NEAK-LOEUNG"), Map.entry(48, "REST-KAMPONG-CHAM"),
            Map.entry(49, "REST-KAMPONG-THOM"), Map.entry(50, "REST-SEPON"),
            Map.entry(51, "REST-SAVANNAKHET"), Map.entry(52, "REST-THAKHEK"),
            Map.entry(53, "REST-PAKSAN"), Map.entry(54, "REST-PAKSE"));

    // {code, name, area, country, lat, lng}
    private static final String[][] NETWORK_STATIONS = {
            { "REST-LONG-THANH", "Trạm nghỉ Long Thành", "Đồng Nai", "Việt Nam", "10.78", "107.0" },
            { "REST-BIEN-HOA", "Trạm nghỉ Biên Hòa", "Đồng Nai", "Việt Nam", "10.95", "106.82" },
            { "REST-DUC-HOA", "Trạm nghỉ Đức Hòa", "Long An", "Việt Nam", "10.88", "106.4" },
            { "REST-CU-CHI", "Trạm nghỉ Củ Chi", "TP.HCM", "Việt Nam", "10.97", "106.5" },
            { "REST-TRANG-BANG", "Trạm nghỉ Trảng Bàng", "Tây Ninh", "Việt Nam", "11.03", "106.37" },
            { "REST-MOC-BAI", "Trạm nghỉ Mộc Bài", "Tây Ninh", "Việt Nam", "11.07", "106.2" },
            { "REST-TIEN-GIANG", "Trạm nghỉ Tiền Giang", "Tiền Giang", "Việt Nam", "10.38", "106.35" },
            { "REST-CAN-THO", "Trạm nghỉ Cần Thơ", "Cần Thơ", "Việt Nam", "10.04", "105.74" },
            { "REST-TINH-BIEN", "Trạm nghỉ Tịnh Biên", "An Giang", "Việt Nam", "10.6", "104.95" },
            { "REST-HA-TIEN", "Trạm nghỉ Hà Tiên", "Kiên Giang", "Việt Nam", "10.38", "104.48" },
            { "REST-PHAN-THIET", "Trạm nghỉ Phan Thiết", "Bình Thuận", "Việt Nam", "10.93", "108.1" },
            { "REST-PHAN-RANG", "Trạm nghỉ Phan Rang", "Ninh Thuận", "Việt Nam", "11.56", "108.99" },
            { "REST-NHA-TRANG", "Trạm nghỉ Nha Trang", "Khánh Hòa", "Việt Nam", "12.24", "109.2" },
            { "REST-TUY-HOA", "Trạm nghỉ Tuy Hòa", "Phú Yên", "Việt Nam", "13.1", "109.3" },
            { "REST-QUY-NHON", "Trạm nghỉ Quy Nhơn", "Bình Định", "Việt Nam", "13.78", "109.22" },
            { "REST-QUANG-NGAI", "Trạm nghỉ Quảng Ngãi", "Quảng Ngãi", "Việt Nam", "15.12", "108.8" },
            { "REST-DA-NANG", "Trạm nghỉ Đà Nẵng", "Đà Nẵng", "Việt Nam", "16.05", "108.2" },
            { "REST-HUE", "Trạm nghỉ Huế", "Thừa Thiên Huế", "Việt Nam", "16.46", "107.6" },
            { "REST-DONG-HA", "Trạm nghỉ Đông Hà", "Quảng Trị", "Việt Nam", "16.82", "107.1" },
            { "REST-LAO-BAO", "Trạm nghỉ Lao Bảo", "Quảng Trị", "Việt Nam", "16.62", "106.6" },
            { "REST-DONG-HOI", "Trạm nghỉ Đồng Hới", "Quảng Bình", "Việt Nam", "17.47", "106.6" },
            { "REST-HA-TINH", "Trạm nghỉ Hà Tĩnh", "Hà Tĩnh", "Việt Nam", "18.34", "105.9" },
            { "REST-CAU-TREO", "Trạm nghỉ Cầu Treo", "Hà Tĩnh", "Việt Nam", "18.38", "105.13" },
            { "REST-VINH", "Trạm nghỉ Vinh", "Nghệ An", "Việt Nam", "18.67", "105.68" },
            { "REST-THANH-HOA", "Trạm nghỉ Thanh Hóa", "Thanh Hóa", "Việt Nam", "19.8", "105.78" },
            { "REST-NINH-BINH", "Trạm nghỉ Ninh Bình", "Ninh Bình", "Việt Nam", "20.25", "105.97" },
            { "REST-HA-NOI", "Trạm nghỉ Hà Nội", "Hà Nội", "Việt Nam", "21.03", "105.85" },
            { "REST-DA-LAT", "Trạm nghỉ Đà Lạt", "Lâm Đồng", "Việt Nam", "11.94", "108.44" },
            { "REST-BUON-MA-THUOT", "Trạm nghỉ Buôn Ma Thuột", "Đắk Lắk", "Việt Nam", "12.67", "108.04" },
            { "REST-PLEIKU", "Trạm nghỉ Pleiku", "Gia Lai", "Việt Nam", "13.98", "108.0" },
            { "REST-KON-TUM", "Trạm nghỉ Kon Tum", "Kon Tum", "Việt Nam", "14.35", "108.0" },
            { "REST-SAVANNAKHET", "Trạm nghỉ Savannakhet", "Savannakhet", "Lào", "16.57", "104.75" },
            { "REST-THAKHEK", "Trạm nghỉ Thakhek", "Thakhek", "Lào", "17.4", "104.8" },
            { "REST-PAKSAN", "Trạm nghỉ Paksan", "Paksan", "Lào", "18.38", "103.65" },
            { "REST-VIENTIANE", "Trạm nghỉ Viêng Chăn", "Viêng Chăn", "Lào", "17.97", "102.6" },
            { "REST-VANG-VIENG", "Trạm nghỉ Vang Vieng", "Vang Vieng", "Lào", "18.92", "102.45" },
            { "REST-PAKSE", "Trạm nghỉ Pakse", "Pakse", "Lào", "15.12", "105.8" },
            { "REST-LUANG-PRABANG", "Trạm nghỉ Luang Prabang", "Luang Prabang", "Lào", "19.89", "102.13" },
            { "REST-BAVET", "Trạm nghỉ Bavet", "Bavet", "Campuchia", "11.08", "106.15" },
            { "REST-SVAY-RIENG", "Trạm nghỉ Svay Rieng", "Svay Rieng", "Campuchia", "11.09", "105.8" },
            { "REST-NEAK-LOEUNG", "Trạm nghỉ Neak Loeung", "Neak Loeung", "Campuchia", "11.26", "105.28" },
            { "REST-PHNOM-PENH", "Trạm nghỉ Phnom Penh", "Phnom Penh", "Campuchia", "11.56", "104.92" },
            { "REST-KAMPONG-CHAM", "Trạm nghỉ Kampong Cham", "Kampong Cham", "Campuchia", "12.0", "105.46" },
            { "REST-KAMPONG-THOM", "Trạm nghỉ Kampong Thom", "Kampong Thom", "Campuchia", "12.71", "104.88" },
            { "REST-PURSAT", "Trạm nghỉ Pursat", "Pursat", "Campuchia", "12.53", "103.92" },
            { "REST-BATTAMBANG", "Trạm nghỉ Battambang", "Battambang", "Campuchia", "13.1", "103.2" },
            { "REST-SIEM-REAP", "Trạm nghỉ Siem Reap", "Siem Reap", "Campuchia", "13.36", "103.86" }
    };

    // {code, name, country, lat, lng}
    private static final String[][] PRICING_CITIES = {
            { "hn", "Hà Nội", "Việt Nam", "21.03", "105.85" },
            { "dn", "Đà Nẵng", "Việt Nam", "16.05", "108.2" },
            { "qn", "Quy Nhơn (Bình Định)", "Việt Nam", "13.78", "109.22" },
            { "hcm", "TP. Hồ Chí Minh", "Việt Nam", "10.77", "106.66" },
            { "dni", "Đồng Nai", "Việt Nam", "10.78", "106.95" },
            { "bd", "Bình Dương", "Việt Nam", "11.0", "106.65" },
            { "tn", "Tây Ninh", "Việt Nam", "11.31", "106.1" },
            { "ct", "Cần Thơ", "Việt Nam", "10.03", "105.78" },
            { "vte", "Vientiane", "Lào", "17.97", "102.6" },
            { "svk", "Savannakhet", "Lào", "16.57", "104.75" },
            { "lpb", "Luang Prabang", "Lào", "19.89", "102.13" },
            { "pks", "Pakse", "Lào", "15.12", "105.8" },
            { "pnh", "Phnom Penh", "Campuchia", "11.56", "104.92" },
            { "rep", "Siem Reap", "Campuchia", "13.36", "103.86" },
            { "btb", "Battambang", "Campuchia", "13.1", "103.2" },
            { "kos", "Sihanoukville", "Campuchia", "10.63", "103.5" }
    };

    // {code, name, area, country, phone, lat, lng}
    private static final String[][] RESCUE_LOCATIONS = {
            { "RESCUE-LONG-THANH", "Cứu hộ Long Thành", "Đồng Nai", "Việt Nam", "0901 100 001", "10.8", "106.98" },
            { "RESCUE-LONG-KHANH", "Cứu hộ Long Khánh", "Đồng Nai", "Việt Nam", "0901 100 002", "10.93", "107.25" },
            { "RESCUE-BIEN-HOA", "Cứu hộ Biên Hòa", "Đồng Nai", "Việt Nam", "0901 100 003", "10.94", "106.84" },
            { "RESCUE-THU-DAU-MOT", "Cứu hộ Thủ Dầu Một", "Bình Dương", "Việt Nam", "0901 100 004", "10.99", "106.66" },
            { "RESCUE-DUC-HOA", "Cứu hộ Đức Hòa", "Long An", "Việt Nam", "0901 100 005", "10.9", "106.42" },
            { "RESCUE-CU-CHI", "Cứu hộ Củ Chi", "TP.HCM", "Việt Nam", "0901 100 006", "10.98", "106.48" },
            { "RESCUE-TRANG-BANG", "Cứu hộ Trảng Bàng", "Tây Ninh", "Việt Nam", "0901 100 007", "11.04", "106.35" },
            { "RESCUE-MOC-BAI", "Cứu hộ Mộc Bài", "Tây Ninh", "Việt Nam", "0901 100 008", "11.08", "106.18" },
            { "RESCUE-PHAN-THIET", "Cứu hộ Phan Thiết", "Bình Thuận", "Việt Nam", "0901 100 009", "10.95", "108.1" },
            { "RESCUE-TINH-BIEN", "Cứu hộ Tịnh Biên", "An Giang", "Việt Nam", "0901 100 010", "10.59", "104.94" },
            { "RESCUE-SVAY-RIENG", "Cứu hộ Svay Rieng", "Svay Rieng", "Campuchia", "+855 12 100 011", "11.1", "105.8" },
            { "RESCUE-NEAK-LOEUNG", "Cứu hộ Neak Loeung", "Neak Loeung", "Campuchia", "+855 12 100 012", "11.27", "105.27" },
            { "RESCUE-PHNOM-PENH", "Cứu hộ Phnom Penh", "Phnom Penh", "Campuchia", "+855 12 100 013", "11.55", "104.9" },
            { "RESCUE-LAO-BAO", "Cứu hộ Lao Bảo", "Quảng Trị", "Việt Nam", "0901 100 014", "16.63", "106.6" },
            { "RESCUE-VIENTIANE", "Cứu hộ Viêng Chăn", "Viêng Chăn", "Lào", "+856 20 100 015", "17.97", "102.62" }
    };

    // {Loại xe, Biển số, Sức chứa (ngăn), Trạng thái}
    private static final String[][] VEHICLES = {
            new String[] { "Xe 2 ngăn", "29H-12345", "2", "AVAILABLE" },
            new String[] { "Xe 4 ngăn", "51C-98765", "4", "AVAILABLE" },
            new String[] { "Xe 4 ngăn", "30A-55678", "4", "AVAILABLE" },
            new String[] { "Xe 6 ngăn", "51D-11122", "6", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "43C-222.11", "2", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "65C-101.22", "2", "AVAILABLE" },
            new String[] { "Xe 4 ngăn", "51C-123.45", "4", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "60C-222.10", "2", "AVAILABLE" },
            new String[] { "Xe 4 ngăn", "29H-456.78", "4", "AVAILABLE" },
            new String[] { "Xe 4 ngăn", "51C-888.99", "4", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "61C-345.67", "2", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "70C-045.18", "2", "AVAILABLE" },
            new String[] { "Xe 2 ngăn", "29C-310.77", "2", "AVAILABLE" },
            new String[] { "Xe 9 ngăn", "51D-909.09", "9", "AVAILABLE" }
    };

    // {Username, Email, PasswordHash (BCrypt), Role, StaffCode (trống = khách hàng)}
    private static final String[][] USERS = {
            new String[] { "admin01", "admin01@gmail.com", "$2a$10$NvMt0W6fYHAZBRFOC2oMUuJGaD3DO44Hdj5BMpA3Rl9Uvmg/EthB6", "ADMIN", "ADMIN-01" },
            new String[] { "manager01", "manager01@gmail.com", "$2a$10$oCT1ZM0QYjNPtKNmXb.7mOd5JQXsn.yWtBuJ1ikOSfq7gCRTVgD8S", "MANAGER", "MG-01" },
            new String[] { "coordinator01", "coordinator01@gmail.com", "$2a$10$MYudrVPfSATaXNtwM81Qn.IXgMAuqgSH/M2/ZtjKw7o1UmiUZLyYS", "COORDINATOR", "CO-01" },
            new String[] { "specialist01", "specialist01@gmail.com", "$2a$10$2Q2V4bYeRlVYaVjGPNGkye5xeP9jbddQYFjnArkrs/0yXwABffk5q", "SPECIALIST", "SP-01" },
            new String[] { "driver01", "driver01@gmail.com", "$2a$10$9c3BNoho5vhi0L8xLpCmleZ3E97VQFafkJZJQcfFKWfklEVgx1p1u", "DRIVER", "DR-01" },
            new String[] { "driver02", "driver02@gmail.com", "$2a$10$qT7p9vk7qISLnaqusLiMUOPwxkqim3D8bporY9.ZTgxznbu3Hsx2G", "DRIVER", "DR-02" },
            new String[] { "drive03", "driver03@gmail.com", "$2a$10$fBPO1w/j7TL6OQ5lRAFj8OYCZJD2EwGY7Yob2RZu82sHPdtxVq/q6", "DRIVER", "DR-03" },
            new String[] { "escort01", "escort01@gmail.com", "$2a$10$1qLVpCjGZqVCxBW8M/50ZOjGT1Ax/vGfcPz2dHBqwxipdK8TLOO4K", "ESCORT", "ES-01" },
            new String[] { "escort02", "escort02@gmail.com", "$2a$10$DSFOE.KOZZ/09HcOlWlk2ON.JefUicNnEdagL31Rhim2pySMDriK2", "ESCORT", "ES-02" },
            new String[] { "escort03", "escort03@gmail.com", "$2a$10$iTet6pr/GugLxBk755TcxuTy4BVmcyoE1coaqXMN2v5zC2g3xmRui", "ESCORT", "ES-03" },
            new String[] { "phanlong795@gmail.com", "phanlong795@gmail.com", "$2a$10$/GgrtSBJ8bctpBfi4Heac..YwzNB17.LjdOFmFy8R7CrKIe6sRGVG", "CUSTOMER", "" }
    };

    private static final Map<String, BigDecimal> DEFAULT_BREED_INSURED_VALUES = Map.ofEntries(
            Map.entry("Thoroughbred", new BigDecimal("1000000000")),
            Map.entry("Arabian", new BigDecimal("800000000")),
            Map.entry("American Quarter Horse", new BigDecimal("600000000")),
            Map.entry("Friesian", new BigDecimal("850000000")),
            Map.entry("Andalusian", new BigDecimal("800000000")),
            Map.entry("Lusitano", new BigDecimal("800000000")),
            Map.entry("Appaloosa", new BigDecimal("500000000")),
            Map.entry("American Paint Horse", new BigDecimal("550000000")),
            Map.entry("Morgan", new BigDecimal("550000000")),
            Map.entry("Tennessee Walking Horse", new BigDecimal("550000000")),
            Map.entry("Standardbred", new BigDecimal("700000000")),
            Map.entry("Hanoverian", new BigDecimal("900000000")),
            Map.entry("Akhal-Teke", new BigDecimal("950000000")),
            Map.entry("Mustang", new BigDecimal("500000000")),
            Map.entry("Clydesdale", new BigDecimal("600000000")),
            Map.entry("Shire", new BigDecimal("600000000")),
            Map.entry("Belgian Draft", new BigDecimal("500000000")),
            Map.entry("Percheron", new BigDecimal("550000000")),
            Map.entry("Haflinger", new BigDecimal("450000000")),
            Map.entry("Shetland Pony", new BigDecimal("350000000"))
    );
    private static final BigDecimal DEFAULT_INSURED_VALUE = new BigDecimal("400000000");

    @Override
    public void run(String... args) {
        migrateServiceMetadata();
        migrateHorseTypeInsuredValues();
        if (enabled) {
            step("accounts", this::seedUsers);
            step("categories", this::seedCategories);
            step("services", this::seedServices);
            step("horse types", this::seedHorseTypes);
            step("locations", this::seedLocations);
        }
        migrateLocationMetadata();
        seedNetworkLocations();
        if (!enabled) {
            log.info("Reference data seeding skipped (app.seed-reference-data=false).");
            return;
        }
        // Mỗi bảng nạp riêng: một bảng lỗi (ví dụ schema cũ) không làm hỏng các bảng khác và không làm dừng ứng dụng
        step("vehicles", this::seedVehicles);
        step("regulations", this::seedRegulations);
    }

    private void step(String name, Runnable seed) {
        try {
            seed.run();
        } catch (RuntimeException e) {
            log.error("Seeding {} failed: {}", name, e.getMessage());
        }
    }

    private void seedUsers() {
        for (String[] r : USERS) {
            if (userRepository.findByUsername(r[0]).isPresent() || userRepository.findByEmail(r[1]).isPresent()) continue;
            User user = new User();
            user.setUsername(r[0]);
            user.setEmail(r[1]);
            user.setPasswordHash(r[2]);
            user = userRepository.save(user);
            if (r[4].isEmpty()) {
                Customer customer = new Customer();
                customer.setUser(user);
                customer.setFullName(r[0].contains("@") ? r[0].substring(0, r[0].indexOf('@')) : r[0]);
                customerRepository.save(customer);
            } else {
                Staff staff = new Staff();
                staff.setUser(user);
                staff.setRole(r[3]);
                staff.setStaffCode(r[4]);
                staff.setEmploymentStatus("ACTIVE");
                staff.setHireDate(LocalDate.now());
                staffRepository.save(staff);
            }
        }
        log.info(">>> Seeded accounts: {} users in total", userRepository.count());
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) return;
        for (String[] r : CATEGORIES) {
            Category c = new Category();
            c.setId(Integer.valueOf(r[0]));
            c.setCategoryName(r[1]);
            c.setDescription(r[2]);
            categoryRepository.save(c);
        }
    }

    private void seedServices() {
        if (serviceRepository.count() > 0) return;
        for (String[] r : SERVICES) {
            Service s = new Service();
            s.setId(Integer.valueOf(r[0]));
            s.setServiceName(r[1]);
            s.setCategoryID(categoryRepository.findById(Integer.valueOf(r[2])).orElseThrow());
            s.setPrice(new BigDecimal(r[3]));
            s.setStatus(r[4]);
            s.setServiceCode(r[5]);
            s.setBillingUnit(r[6]);
            s.setDescription(r[7].isBlank() ? null : r[7]);
            serviceRepository.save(s);
        }
    }

    private void migrateServiceMetadata() {
        for (Service service : serviceRepository.findAll()) {
            boolean changed = false;
            Integer categoryId = service.getCategoryID() == null ? null : service.getCategoryID().getId();
            if (service.getServiceCode() == null || service.getServiceCode().isBlank()) {
                String code = LEGACY_SERVICE_CODES.get(service.getId());
                if (code == null && categoryId != null) {
                    String prefix = switch (categoryId) {
                        case 1 -> "STALL";
                        case 2 -> "FEED";
                        case 3 -> "WATER";
                        case 4 -> "INSURANCE";
                        default -> throw new IllegalStateException(
                                "Không thể gán mã cho dịch vụ " + service.getId() + " thuộc danh mục " + categoryId);
                    };
                    code = prefix + "_LEGACY_" + service.getId();
                }
                if (code == null) {
                    throw new IllegalStateException("Dịch vụ " + service.getId() + " không có danh mục để gán mã.");
                }
                service.setServiceCode(code);
                changed = true;
            }
            if (service.getBillingUnit() == null || service.getBillingUnit().isBlank()) {
                String unit = LEGACY_BILLING_UNITS.get(categoryId);
                if (unit == null) {
                    throw new IllegalStateException(
                            "Không thể gán đơn vị tính cho dịch vụ " + service.getId() + " thuộc danh mục " + categoryId);
                }
                service.setBillingUnit(unit);
                changed = true;
            }
            if (Integer.valueOf(3).equals(categoryId) && "ACTIVE".equalsIgnoreCase(service.getStatus())) {
                service.setStatus("INACTIVE");
                changed = true;
            }
            if (changed) {
                serviceRepository.save(service);
            }
        }
        log.info("Service metadata migration completed.");
    }

    private void migrateHorseTypeInsuredValues() {
        for (HorseType t : horseTypeRepository.findAll()) {
            if (t.getInsuredValue() == null) {
                BigDecimal value = DEFAULT_BREED_INSURED_VALUES.getOrDefault(t.getTypeName(), DEFAULT_INSURED_VALUE);
                t.setInsuredValue(value);
                horseTypeRepository.save(t);
            }
        }
        log.info("HorseType insuredValue migration completed.");
    }

    private void seedHorseTypes() {
        if (horseTypeRepository.count() > 0) return;
        for (String[] r : HORSE_TYPES) {
            HorseType t = new HorseType();
            t.setId(Integer.valueOf(r[0]));
            t.setTypeName(r[1]);
            t.setDescription(r[2]);
            t.setInsuredValue(DEFAULT_BREED_INSURED_VALUES.getOrDefault(r[1], DEFAULT_INSURED_VALUE));
            horseTypeRepository.save(t);
        }
    }

    private void seedLocations() {
        for (String[] r : LOCATIONS) {
            Integer locationId = Integer.valueOf(r[0]);
            if (locationRepository.existsById(locationId)) continue;
            Location l = new Location();
            l.setId(locationId);
            l.setLocationType(r[1]);
            l.setLocationName(r[2]);
            l.setAddress(r[3]);
            l.setCity(r[4]);
            l.setProvince(r[5]);
            l.setCountry(r[6]);
            applyLegacyLocationMetadata(l);
            locationRepository.save(l);
        }
    }

    private void migrateLocationMetadata() {
        for (Location location : locationRepository.findAll()) {
            if (applyLegacyLocationMetadata(location)) {
                locationRepository.save(location);
            }
        }
    }

    private boolean applyLegacyLocationMetadata(Location location) {
        boolean changed = false;
        String code = LEGACY_LOCATION_CODES.getOrDefault(location.getId(), "LOC-" + location.getId());
        if (location.getLocationCode() == null || location.getLocationCode().isBlank()) {
            location.setLocationCode(code);
            changed = true;
        }
        Coordinates coordinates = LEGACY_LOCATION_COORDINATES.get(location.getId());
        if (coordinates != null && location.getLatitude() == null && location.getLongitude() == null) {
            location.setLatitude(coordinates.latitude());
            location.setLongitude(coordinates.longitude());
            changed = true;
        }
        if (location.getStatus() == null || location.getStatus().isBlank()) {
            location.setStatus("ACTIVE");
            changed = true;
        }
        if ("BorderGate".equals(location.getLocationType())) {
            if (location.getOpeningMinutes() == null) {
                location.setOpeningMinutes(450);
                changed = true;
            }
            if (location.getClosingMinutes() == null) {
                location.setClosingMinutes(990);
                changed = true;
            }
            if (location.getDestinationCountry() == null) {
                location.setDestinationCountry(inferDestinationCountry(location.getLocationName()));
                changed = true;
            }
        }
        return changed;
    }

    private void seedNetworkLocations() {
        for (String[] row : NETWORK_STATIONS) {
            upsertNetworkLocation(row[0], "RestStop", row[1], row[2], row[3],
                    row[4], row[5], null);
        }
        for (String[] row : PRICING_CITIES) {
            upsertNetworkLocation(row[0], "City", row[1], row[1], row[2],
                    row[3], row[4], null);
        }
        for (String[] row : RESCUE_LOCATIONS) {
            upsertNetworkLocation(row[0], "RescuePoint", row[1], row[2], row[3],
                    row[5], row[6], row[4]);
        }
    }

    private void upsertNetworkLocation(String code, String type, String name, String area,
            String country, String latitude, String longitude, String phone) {
        Location location = locationRepository.findByLocationCodeIgnoreCase(code).orElseGet(() ->
                locationRepository.findAll().stream()
                        .filter(item -> type.equals(item.getLocationType()))
                        .filter(item -> country.equals(item.getCountry()))
                        .filter(item -> normalizedName(item.getLocationName()).contains(normalizedName(name))
                                || normalizedName(name).contains(normalizedName(item.getLocationName())))
                        .findFirst()
                        .orElse(null));
        boolean isNew = location == null;
        if (isNew) {
            location = new Location();
            location.setId(Math.max(locationRepository.findMaxId(), 54) + 1);
            location.setLocationType(type);
            location.setLocationName(name);
            location.setAddress(area);
            location.setCity(area);
            location.setProvince(area);
            location.setCountry(country);
        }
        boolean changed = false;
        if (location.getLocationCode() == null || location.getLocationCode().startsWith("LOC-")) {
            location.setLocationCode(code);
            changed = true;
        }
        if (location.getLatitude() == null || location.getLongitude() == null) {
            location.setLatitude(Double.valueOf(latitude));
            location.setLongitude(Double.valueOf(longitude));
            changed = true;
        }
        if (location.getStatus() == null || location.getStatus().isBlank()) {
            location.setStatus("ACTIVE");
            changed = true;
        }
        if (phone != null && (location.getContactPhone() == null || location.getContactPhone().isBlank())) {
            location.setContactPhone(phone);
            changed = true;
        }
        if ("BorderGate".equals(type) && location.getDestinationCountry() == null) {
            location.setDestinationCountry(inferDestinationCountry(name));
            changed = true;
        }
        if (isNew || changed) {
            locationRepository.save(location);
        }
    }

    private String normalizedName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace("điểm dừng nghỉ", "")
                .replace("trạm nghỉ", "")
                .replace("cứu hộ", "")
                .trim();
    }

    private String inferDestinationCountry(String gateName) {
        String name = gateName == null ? "" : gateName.toLowerCase(Locale.ROOT);
        return name.contains("densavanh") || name.contains("nam phao") || name.contains("sop hun")
                || name.contains("na phao") || name.contains("nong haet") || name.contains("nam xoi")
                || name.contains("la lay") ? "LA" : "KH";
    }

    // Capacity is encoded in vehicle_type because the vehicle table stores only its core fields.
    private void seedVehicles() {
        if (vehicleRepository.count() > 0) return;
        for (String[] r : VEHICLES) {
            vehicleRepository.save(Vehicle.builder()
                    .vehicleType(r[0]).licensePlate(r[1]).status(r[3])
                    .build());
        }
    }

    // Gán người phụ trách là Specialist đã nạp và liên kết quy định tới cửa khẩu tương ứng
    private void seedRegulations() {
        Staff specialist = userRepository.findByUsername("specialist01").flatMap(u -> staffRepository.findById(u.getId())).orElse(null);
        if (specialist == null) {
            log.warn("Regulations not seeded: specialist01 account is missing.");
            return;
        }
        for (String[] r : REGULATIONS) {
            String name = r[0];
            String desc = r[1];
            String status = r[2];
            Integer locId = (r[3] != null && !r[3].trim().isEmpty()) ? Integer.valueOf(r[3].trim()) : null;
            String docType = r[4];
            String country = r[5];

            Location location = locId != null ? locationRepository.findById(locId).orElse(null) : null;
            if (locId != null && location == null) {
                log.warn("Regulation not seeded because its border location is missing: name={}, locationId={}", name, locId);
                continue;
            }

            Regulation g = regulationRepository.findAll().stream()
                    .filter(reg -> name.equalsIgnoreCase(reg.getRegulationName()) ||
                            (docType != null && docType.equalsIgnoreCase(reg.getDocumentType()) &&
                                    (locId == null ? reg.getLocationID() == null : (reg.getLocationID() != null && locId.equals(reg.getLocationID().getId())))))
                    .findFirst()
                    .orElse(null);

            if (g == null) {
                g = new Regulation();
                g.setStaffID(specialist);
                g.setRegulationName(name);
            }
            g.setDescription(desc);
            g.setStatus(status);
            g.setLocationID(location);
            g.setDocumentType(docType);
            g.setCountry(country);
            regulationRepository.save(g);
        }
    }
}
