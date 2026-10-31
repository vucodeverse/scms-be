package edu.fpt.sba.scms.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Quy tắc phụ thuộc của SCMS (IMPLEMENTATION.md §5.1, điều chỉnh theo quy ước package đã chốt).
 * Chạy trong {@code ./mvnw test}; vi phạm là không merge.
 *
 * <pre>
 * controller (+ controller/handler) -> dto, service, security, common
 * dto           -> common
 * service       -> domain, repository, model, dto, mapper, integration, common, security.jwt (chỉ AuthServiceImpl + adapter)
 * domain        -> java, common/enums
 * mapper        -> model, dto
 * repository    -> model, common
 * model         -> common/enums (chỉ enum)
 * integration   -> common
 * security      -> repository, model, common, dto, config (chỉ đọc AppProperties)
 * common        -> không ai (chỉ thư viện nền)
 * config        -> tất cả để nối dây (composition root) — KHÔNG ai được import config
 * </pre>
 * Package gốc: {@code edu.fpt.sba.scms} (tương ứng {@code com.scms} trong tài liệu).
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "edu.fpt.sba.scms";

    /** Thư viện nền luôn được phép dùng. */
    private static final String[] FRAMEWORK = {
            "java..", "jakarta..", "javax.crypto..", "org.springframework..", "org.slf4j..", "com.fasterxml..",
            "tools.jackson..", "lombok..", "io.swagger..", "io.jsonwebtoken..", "org.hibernate..", "org.mapstruct.."
    };

    /** Tầng được phép đọc config (chỉ đọc thuộc tính, không phải composition). */
    private static final List<String> CONFIG_READERS = List.of(
            ROOT + ".service..", ROOT + ".security..");

    private static String[] allow(String... packages) {
        Set<String> all = new HashSet<>(Arrays.asList(FRAMEWORK));
        for (String p : packages) {
            all.add(p.startsWith(ROOT) ? p : ROOT + "." + p);
        }
        all.add(ROOT + ".config..");
        return all.toArray(String[]::new);
    }

    private static String[] deny(String... packages) {
        return Arrays.stream(packages).map(p -> ROOT + "." + p).toArray(String[]::new);
    }

    // ---------------- controller ----------------
    @ArchTest
    static final ArchRule controller_chi_dung_dto_service_security_common =
            classes().that().resideInAPackage(ROOT + ".controller..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(allow("controller..", "dto..", "service..", "security..", "common.."));

    @ArchTest
    static final ArchRule controller_khong_cham_repository_model_mapper_integration_domain_config =
            noClasses().that().resideInAPackage(ROOT + ".controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("repository..", "model..", "mapper..", "integration..", "domain..",
                            "config.."));

    @ArchTest
    static final ArchRule controller_phai_co_hau_to_Controller =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Controller");

    /** Advice (@RestControllerAdvice là @ControllerAdvice + @ResponseBody) chỉ được nằm trong controller.handler. */
    @ArchTest
    static final ArchRule controller_advice_nam_trong_controller_handler =
            classes().that().areAnnotatedWith(ControllerAdvice.class)
                    .or().areAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class)
                    .should().resideInAPackage(ROOT + ".controller.handler..");

    // ---------------- dto ----------------
    @ArchTest
    static final ArchRule dto_chi_dung_common_va_thu_vien_nen =
            noClasses().that().resideInAPackage(ROOT + ".dto..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "service..", "repository..", "model..", "mapper..",
                            "security..", "domain..", "integration..", "handler..", "util..", "config.."));

    // ---------------- service ----------------
    @ArchTest
    static final ArchRule service_chi_dung_cac_tang_hop_le =
            classes().that().resideInAPackage(ROOT + ".service..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(allow("service..", "domain..", "repository..", "model..", "dto..",
                            "mapper..", "integration..", "common..", "security.jwt..", "config.."));

    @ArchTest
    static final ArchRule service_khong_goi_controller_va_handler =
            noClasses().that().resideInAPackage(ROOT + ".service..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "handler.."));

    /**
     * Ngoại lệ có chủ đích (ĐÃ CHỐT): chỉ AuthServiceImpl (UC01) và TokenServiceAdapter được chạm
     * security.jwt để phát/thu hồi token; service khác phải đi qua dto.TokenService.
     */
    @ArchTest
    static final ArchRule chi_auth_duoc_dung_security_jwt =
            noClasses().that().resideInAPackage(ROOT + ".service..")
                    .and().doNotHaveFullyQualifiedName(ROOT + ".service.impl.AuthServiceImpl")
                    .and().doNotHaveFullyQualifiedName(ROOT + ".service.support.TokenServiceAdapter")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".security.jwt..");

    // ---------------- domain ----------------
    @ArchTest
    static final ArchRule domain_chi_dung_jdk_va_enum =
            noClasses().that().resideInAPackage(ROOT + ".domain..")
                    .should().dependOnClassesThat().resideOutsideOfPackages(
                            "java..", ROOT + ".domain..", ROOT + ".common.enums..")
                    .allowEmptyShould(true);

    // ---------------- mapper ----------------
    @ArchTest
    static final ArchRule mapper_chi_model_va_dto =
            classes().that().resideInAPackage(ROOT + ".mapper..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(allow("mapper..", "model..", "dto.."))
                    .allowEmptyShould(true);

    // ---------------- repository ----------------
    @ArchTest
    static final ArchRule repository_chi_model_va_common =
            classes().that().resideInAPackage(ROOT + ".repository..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(allow("repository..", "model..", "common.."));

    @ArchTest
    static final ArchRule repository_khong_goi_service_controller_domain_mapper_dto =
            noClasses().that().resideInAPackage(ROOT + ".repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("service..", "controller..", "domain..", "mapper..", "dto.."));

    // ---------------- model ----------------
    @ArchTest
    static final ArchRule model_chi_phu_thuoc_enum_trong_common =
            noClasses().that().resideInAPackage(ROOT + ".model..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".common..")
                    .andShould(DependsOnCommonOnlyThroughEnums.INSTANCE);

    @ArchTest
    static final ArchRule model_khong_phu_thuoc_tang_khac =
            noClasses().that().resideInAPackage(ROOT + ".model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "service..", "repository..", "dto..", "mapper..",
                            "handler..", "config..", "integration..", "domain.."));

    // ---------------- integration ----------------
    @ArchTest
    static final ArchRule integration_chi_common_va_thu_vien_nen =
            noClasses().that().resideInAPackage(ROOT + ".integration..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "service..", "repository..", "model..", "mapper..",
                            "security..", "domain..", "handler..", "dto..", "config.."))
                    .allowEmptyShould(true);

    // ---------------- security ----------------
    @ArchTest
    static final ArchRule security_chi_dung_repository_model_common_dto_config =
            classes().that().resideInAPackage(ROOT + ".security..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(allow("security..", "repository..", "model..", "common..", "dto..",
                            "config.."));

    @ArchTest
    static final ArchRule security_khong_goi_controller_service =
            noClasses().that().resideInAPackage(ROOT + ".security..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "service.."));

    // ---------------- common ----------------
    @ArchTest
    static final ArchRule common_khong_phu_thuoc_tang_nao_cua_ung_dung =
            noClasses().that().resideInAPackage(ROOT + ".common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(deny("controller..", "service..", "repository..", "model..", "mapper..",
                            "security..", "domain..", "integration..", "handler..", "dto..", "config.."));

    @ArchTest
    static final ArchRule enum_nam_trong_common_enums =
            classes().that().areEnums()
                    .should().resideInAPackage(ROOT + ".common.enums..")
                    .because("quy ước: gom toàn bộ enum về common.enums để dễ tra cứu");

    // ---------------- config ----------------
    @ArchTest
    static final ArchRule chi_config_va_tang_duoc_doc_thuoc_tinh_moi_dung_config =
            noClasses().that().resideOutsideOfPackages(ROOT + ".config..")
                    .and().resideOutsideOfPackages(CONFIG_READERS.toArray(String[]::new))
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".config..")
                    .because("config là composition root: chỉ config + các lớp đọc thuộc tính (service, security) được dùng");

    /** Model chỉ được dùng common qua các enum trong {@code common.enums}. */
    static final class DependsOnCommonOnlyThroughEnums extends ArchCondition<JavaClass> {

        static final DependsOnCommonOnlyThroughEnums INSTANCE = new DependsOnCommonOnlyThroughEnums();

        private DependsOnCommonOnlyThroughEnums() {
            super("chỉ dùng enum nằm trong common.enums");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            Set<String> outside = item.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass().getName())
                    .filter(n -> n.startsWith(ROOT + ".common") && !n.startsWith(ROOT + ".common.enums"))
                    .collect(Collectors.toSet());
            if (!outside.isEmpty()) {
                events.add(SimpleConditionEvent.violated(item,
                        item.getName() + " phụ thuộc ngoài common.enums: " + outside));
            }
        }
    }
}
