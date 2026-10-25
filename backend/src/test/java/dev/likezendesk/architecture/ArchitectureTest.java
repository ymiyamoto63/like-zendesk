package dev.likezendesk.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String ROOT = "dev.likezendesk";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void featureLayerDependencies() {
        ArchitectureRules.featureLayerDependencies(ROOT).check(CLASSES);
    }

    @Test
    void domainIsPlainJava() {
        ArchitectureRules.domainIsPlainJava(ROOT).allowEmptyShould(true).check(CLASSES);
    }

    @Test
    void repositoriesTakeViewerFirst() {
        ArchitectureRules.repositoriesTakeViewerFirst(ROOT).check(CLASSES);
    }
}
