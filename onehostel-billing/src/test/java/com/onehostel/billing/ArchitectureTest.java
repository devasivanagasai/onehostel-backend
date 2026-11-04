package com.onehostel.billing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.onehostel.tenancy.constants.IdentityHeaders;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** House rules (see CLAUDE.md), checked on every build. */
@AnalyzeClasses(packages = "com.onehostel.billing", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule constructorInjectionOnly = noFields().should().beAnnotatedWith(Autowired.class)
            .because("dependencies are private final fields set by @RequiredArgsConstructor")
            .allowEmptyShould(true);

    /** Ratchet at zero: identity comes from @AuthenticatedUser CurrentUser and @CurrentTenant, never from headers. */
    @ArchTest
    static final ArchRule noDirectIdentityHeaders = noClasses().should().accessClassesThat().belongToAnyOf(IdentityHeaders.class)
            .because("the gateway's identity headers are read only by onehostel-common and onehostel-tenancy")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule ulidIdsOnly = noClasses().should().dependOnClassesThat().belongToAnyOf(UUID.class, ObjectId.class)
            .because("ids are ULIDs, assigned by UlidIdCallback")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noRawScheduling = noMethods().should().beAnnotatedWith(Scheduled.class)
            .because("scheduled work is a HostelJob in onehostel-scheduler-core")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule errorsFromTheCatalogueOnly = noClasses().should().callConstructor(RuntimeException.class, String.class)
            .orShould().callConstructor(RuntimeException.class, String.class, Throwable.class)
            .because("errors are raised from an ErrorCode: throw ErrorCode.X.raise(...)")
            .allowEmptyShould(true);
}
