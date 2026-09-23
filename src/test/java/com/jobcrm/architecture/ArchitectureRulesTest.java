package com.jobcrm.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Architectural invariants enforced across the codebase. Encodes the DDD conventions from
 * .kiro/steering/development-guidelines.md and fails the build ({@code ./gradlew check}) when
 * violated.
 *
 * <p>Rules currently in force:
 *
 * <ol>
 *   <li>Domain layer is pure Java (no framework or cross-layer imports).
 *   <li>Interfaces/CLI layer never touches domain or infrastructure directly.
 *   <li>Bounded-context dependency direction:
 *       <pre>
 *       core        &lt;-- (nothing)
 *       messaging   &lt;-- messaging → core only
 *       agentic     &lt;-- agentic → { core, messaging }
 *       rules       &lt;-- rules → { core, agentic }
 *       ingest      &lt;-- ingest → { core, agentic } (enforced indirectly by others' bans)
 *       </pre>
 *   <li>No cycles between top-level packages.
 *   <li>Concrete repositories under {@code infrastructure.persistence} implement a matching domain
 *       repository interface.
 * </ol>
 */
@AnalyzeClasses(
    packages = "com.jobcrm",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureRulesTest {

  // ---------- Layer rules ----------

  @ArchTest
  static final ArchRule domain_layer_is_pure_java =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.jobcrm.core.domain..",
              "com.jobcrm.messaging.domain..",
              "com.jobcrm.agentic.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              // In-project layers domain must not touch
              "com.jobcrm.app..",
              "com.jobcrm.infrastructure..",
              "com.jobcrm.interfaces..",
              "com.jobcrm.rules..",
              "com.jobcrm.ingest..",
              // Application services orchestrate domain; domain never depends on them
              "com.jobcrm.core.application..",
              "com.jobcrm.messaging.application..",
              "com.jobcrm.agentic.application..",
              // Frameworks banned in domain
              "org.springframework..",
              "org.flywaydb..",
              "java.sql..",
              "javax.sql..",
              "com.fasterxml.jackson..",
              "picocli..",
              "org.xerial..",
              "com.zaxxer.hikari..",
              "com.google..")
          .because("domain must be pure Java per development-guidelines.md § DDD Conventions");

  @ArchTest
  static final ArchRule cli_never_touches_domain_or_infrastructure_directly =
      noClasses()
          .that()
          .resideInAPackage("com.jobcrm.interfaces.cli..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.jobcrm.core.domain..",
              "com.jobcrm.messaging.domain..",
              "com.jobcrm.agentic.domain..",
              "com.jobcrm.infrastructure..")
          .because("CLI goes through application services; never touches domain or infra directly");

  // ---------- Bounded-context direction ----------

  @ArchTest
  static final ArchRule core_context_depends_on_nothing_else =
      noClasses()
          .that()
          .resideInAPackage("com.jobcrm.core..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.jobcrm.messaging..",
              "com.jobcrm.agentic..",
              "com.jobcrm.ingest..",
              "com.jobcrm.rules..",
              "com.jobcrm.interfaces..",
              "com.jobcrm.infrastructure..",
              "com.jobcrm.app..")
          .because("core is authoritative; other contexts depend on core, never the reverse");

  @ArchTest
  static final ArchRule messaging_context_depends_only_on_core =
      noClasses()
          .that()
          .resideInAPackage("com.jobcrm.messaging..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.jobcrm.agentic..",
              "com.jobcrm.ingest..",
              "com.jobcrm.rules..",
              "com.jobcrm.interfaces..",
              "com.jobcrm.infrastructure..",
              "com.jobcrm.app..")
          .because("messaging → core only, per the bounded-context dependency map");

  @ArchTest
  static final ArchRule agentic_context_depends_only_on_core_and_messaging =
      noClasses()
          .that()
          .resideInAPackage("com.jobcrm.agentic..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.jobcrm.ingest..",
              "com.jobcrm.rules..",
              "com.jobcrm.interfaces..",
              "com.jobcrm.infrastructure..",
              "com.jobcrm.app..")
          .because("agentic → { core, messaging } only, per the bounded-context dependency map");

  @ArchTest
  static final ArchRule rules_context_depends_only_on_core_and_agentic =
      noClasses()
          .that()
          .resideInAPackage("com.jobcrm.rules..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.jobcrm.messaging..",
              "com.jobcrm.ingest..",
              "com.jobcrm.interfaces..",
              "com.jobcrm.infrastructure..",
              "com.jobcrm.app..")
          .because("rules → { core, agentic } only, per the bounded-context dependency map");

  // ---------- Cycle detection ----------

  @ArchTest
  static final ArchRule no_cycles_between_top_level_packages =
      slices().matching("com.jobcrm.(*)..").should().beFreeOfCycles();

  // ---------- Persistence adapters implement domain ports ----------

  @ArchTest
  static final ArchRule persistence_repository_classes_implement_a_domain_interface =
      classes()
          .that()
          .resideInAPackage("com.jobcrm.infrastructure.persistence..")
          .and()
          .areNotInterfaces()
          .and()
          .areTopLevelClasses()
          .and()
          .haveSimpleNameEndingWith("Repository")
          .should(implementADomainRepositoryInterface())
          .allowEmptyShould(true)
          .because(
              "concrete repositories in infrastructure.persistence exist solely to implement the "
                  + "corresponding domain repository interface (ports & adapters)");

  private static ArchCondition<JavaClass> implementADomainRepositoryInterface() {
    return new ArchCondition<>("implement a *.domain.*Repository interface") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        boolean implementsDomainRepo =
            item.getAllRawInterfaces().stream()
                .anyMatch(
                    iface ->
                        iface.getPackageName().endsWith(".domain")
                            && iface.getSimpleName().endsWith("Repository"));
        if (!implementsDomainRepo) {
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  "class %s does not implement a *.domain.*Repository interface"
                      .formatted(item.getFullName())));
        }
      }
    };
  }
}
