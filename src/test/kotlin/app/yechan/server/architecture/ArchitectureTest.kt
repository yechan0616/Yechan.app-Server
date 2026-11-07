package app.yechan.server.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

class ArchitectureTest {
    private val classes: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(ROOT)

    @Test
    fun `global은 domain에 의존하지 않는다`() {
        noClasses()
            .that()
            .resideInAPackage("$ROOT.global..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("$ROOT.domain..")
            .check(classes)
    }

    @Test
    fun `도메인 간 순환 의존이 없다`() {
        slices()
            .matching("$ROOT.domain.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `다른 도메인의 repository와 entity 내부에 직접 접근하지 않고 service를 통한다`() {
        slices()
            .matching("$ROOT.domain.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                DescribedPredicate.describe("service or dto") { target: JavaClass ->
                    target.packageName.endsWith(".service") || target.packageName.endsWith(".dto")
                },
            ).allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `controller는 repository에 직접 접근하지 않는다`() {
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `controller는 엔티티를 직접 다루지 않는다`() {
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(Entity::class.java)
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `계층별 클래스는 정해진 패키지에 둔다`() {
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .should()
            .resideInAPackage("$ROOT.domain..controller..")
            .allowEmptyShould(true)
            .check(classes)

        classes()
            .that()
            .areAnnotatedWith(Entity::class.java)
            .should()
            .resideInAPackage("$ROOT.domain..entity..")
            .allowEmptyShould(true)
            .check(classes)

        classes()
            .that()
            .areAnnotatedWith(RestControllerAdvice::class.java)
            .should()
            .resideInAPackage("$ROOT.global.error..")
            .check(classes)
    }

    private companion object {
        const val ROOT = "app.yechan.server"
    }
}
