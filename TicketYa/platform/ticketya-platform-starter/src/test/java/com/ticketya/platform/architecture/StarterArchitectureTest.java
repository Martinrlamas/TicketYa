package com.ticketya.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** El starter es solo infraestructura técnica: ninguna dependencia ni vocabulario de negocio. */
class StarterArchitectureTest {

    private static JavaClasses starter;

    @BeforeAll
    static void importClasses() {
        starter = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ticketya.platform");
    }

    @Test
    void no_depende_de_ningun_servicio_de_negocio() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.ticketya.catalog..", "com.ticketya.inventory..", "com.ticketya.order..",
                "com.ticketya.payment..", "com.ticketya.ticket..", "com.ticketya.gateway..")
                .check(starter);
    }

    @Test
    void no_tiene_persistencia_ni_entidades() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..",
                "org.springframework.data.repository..", "org.hibernate..").check(starter);
        noClasses().should().beAnnotatedWith("jakarta.persistence.Entity").check(starter);
    }

    @Test
    void no_define_clases_con_vocabulario_de_negocio() {
        for (String word : new String[] {"Event", "Seat", "Venue", "Payment", "Hold", "Refund", "Inventory", "Catalog", "Purchase"}) {
            noClasses().should().haveSimpleNameContaining(word).check(starter);
        }
    }
}
