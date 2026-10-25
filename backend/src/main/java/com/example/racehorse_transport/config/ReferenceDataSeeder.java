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
import java.util.Map;

/**
 * Nạp dữ liệu danh mục và tài khoản mẫu từ tài liệu nhóm (tab Data). Chỉ chạy khi bật APP_SEED_REFERENCE_DATA=true
 * (docker-compose bật sẵn). Mỗi bảng chỉ nạp khi còn trống, nên chạy lại nhiều lần không nhân đôi dữ liệu.
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

    @Override
    public void run(String... args) {
        migrateServiceMetadata();
        if (!enabled) {
            log.info("Reference data seeding skipped (app.seed-reference-data=false).");
            return;
        }
        // Mỗi bảng nạp riêng: một bảng lỗi (ví dụ schema cũ) không làm hỏng các bảng khác và không làm dừng ứng dụng
        step("accounts", this::seedUsers);
        step("categories", this::seedCategories);
        step("services", this::seedServices);
        step("horse types", this::seedHorseTypes);
        step("locations", this::seedLocations);
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

    private void seedHorseTypes() {
        if (horseTypeRepository.count() > 0) return;
        for (String[] r : HORSE_TYPES) {
            HorseType t = new HorseType();
            t.setId(Integer.valueOf(r[0]));
            t.setTypeName(r[1]);
            t.setDescription(r[2]);
            horseTypeRepository.save(t);
        }
    }

    private void seedLocations() {
        if (locationRepository.count() > 0) return;
        for (String[] r : LOCATIONS) {
            Location l = new Location();
            l.setId(Integer.valueOf(r[0]));
            l.setLocationType(r[1]);
            l.setLocationName(r[2]);
            l.setAddress(r[3]);
            l.setCity(r[4]);
            l.setProvince(r[5]);
            l.setCountry(r[6]);
            locationRepository.save(l);
        }
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
