package com.example.racehorse_transport.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;

// Khóa các quy tắc của AGENTS.md mục 3 (cấu trúc backend) và 10.3 (test kiến trúc). Vi phạm thì mvnw verify đỏ.
// Không tắt, nới lỏng hay thêm ngoại lệ khi người dùng chưa đồng ý.
@AnalyzeClasses(packages = "com.example.racehorse_transport", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.example.racehorse_transport";

    // Class có từ trước khi có test này, đặt tên lệch quy tắc. Danh sách chỉ được rút ngắn, không được thêm.
    private static final String CLOCK_CONFIGURATION = ROOT + ".config.ClockConfiguration";
    private static final String DEPARTURE_SUGGESTION_QUERY_DTO = ROOT + ".dto.departure.DepartureSuggestionQueryDto";
    private static final String GEO_POINT_DTO = ROOT + ".dto.route.GeoPointDto";
    private static final String ROAD_ROUTE_DTO = ROOT + ".dto.route.RoadRouteDto";

    // ---------- Hậu tố tên class theo package (mục 3.2) ----------

    @ArchTest
    static final ArchRule controllersEndWithController = classes()
            .that().resideInAPackage(ROOT + ".controller").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule serviceContractsEndWithService = classes()
            .that().resideInAPackage(ROOT + ".service").and().areTopLevelClasses()
            .should().beInterfaces().andShould().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule serviceImplementationsEndWithServiceImpl = classes()
            .that().resideInAPackage(ROOT + ".service.impl").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("ServiceImpl");

    @ArchTest
    static final ArchRule policiesEndWithPolicy = classes()
            .that().resideInAPackage(ROOT + ".policy").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Policy");

    @ArchTest
    static final ArchRule repositoriesEndWithRepository = classes()
            .that().resideInAPackage(ROOT + ".repository").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule clientsEndWithClient = classes()
            .that().resideInAPackage(ROOT + ".client").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Client");

    @ArchTest
    static final ArchRule schedulersEndWithScheduler = classes()
            .that().resideInAPackage(ROOT + ".scheduler").and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Scheduler");

    @ArchTest
    static final ArchRule dtosEndWithRequestOrResponseDto = classes()
            .that().resideInAPackage(ROOT + ".dto..").and().areTopLevelClasses()
            .and().doNotHaveFullyQualifiedName(DEPARTURE_SUGGESTION_QUERY_DTO)
            .and().doNotHaveFullyQualifiedName(GEO_POINT_DTO)
            .and().doNotHaveFullyQualifiedName(ROAD_ROUTE_DTO)
            .should().haveSimpleNameEndingWith("RequestDto").orShould().haveSimpleNameEndingWith("ResponseDto");

    @ArchTest
    static final ArchRule configurationEndsWithConfigOrProperties = classes()
            .that().resideInAPackage(ROOT + ".config").and().areTopLevelClasses()
            .and().doNotHaveFullyQualifiedName(CLOCK_CONFIGURATION)
            .should().haveSimpleNameEndingWith("Config").orShould().haveSimpleNameEndingWith("Properties");

    @ArchTest
    static final ArchRule exceptionsEndWithException = classes()
            .that().resideInAPackage(ROOT + ".exception").and().areTopLevelClasses()
            .and().doNotHaveSimpleName("GlobalExceptionHandler")
            .should().haveSimpleNameEndingWith("Exception");

    @ArchTest
    static final ArchRule noMapperPackage = noClasses()
            .should().resideInAPackage(ROOT + ".mapper..");

    // ---------- Chiều phụ thuộc giữa các tầng (mục 3.1) ----------

    @ArchTest
    static final ArchRule controllersOnlyTalkToServices = noClasses()
            .that().resideInAPackage(ROOT + ".controller..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".repository..", ROOT + ".policy..", ROOT + ".client..");

    @ArchTest
    static final ArchRule repositoriesDoNotCallUpward = noClasses()
            .that().resideInAPackage(ROOT + ".repository..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".service..", ROOT + ".controller..", ROOT + ".dto..");

    @ArchTest
    static final ArchRule servicesDoNotCallControllers = noClasses()
            .that().resideInAPackage(ROOT + ".service..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".controller..");

    @ArchTest
    static final ArchRule policiesArePureFunctions = noClasses()
            .that().resideInAPackage(ROOT + ".policy..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".repository..", ROOT + ".service..", ROOT + ".client..", ROOT + ".controller..", "org.springframework..");

    @ArchTest
    static final ArchRule entitiesDoNotImportDtos = noClasses()
            .that().resideInAPackage(ROOT + ".entity..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".dto..");

    @ArchTest
    static final ArchRule dtosDoNotQueryOrRunUseCases = noClasses()
            .that().resideInAPackage(ROOT + ".dto..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".repository..", ROOT + ".service..", ROOT + ".controller..");

    // ---------- Các điều cấm khác (mục 3.2, 4, 7.1) ----------

    @ArchTest
    static final ArchRule noModelMapper = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.modelmapper..");

    @ArchTest
    static final ArchRule servicesAndPoliciesReadTimeFromClock = noClasses()
            .that().resideInAnyPackage(ROOT + ".service..", ROOT + ".policy..")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now");

    @ArchTest
    static final ArchRule noStartupRunners = noClasses()
            .should().implement(CommandLineRunner.class)
            .orShould().implement(ApplicationRunner.class);
}
