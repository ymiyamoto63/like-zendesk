package dev.likezendesk.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** 規則が違反を検出できることを、archfixture パッケージの違反例で確かめる。 */
class ArchitectureRulesTest {

    private static final String ROOT = "archfixture";
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(ROOT);

    @Test
    void detectsForbiddenLayerAndFeatureDependencies() {
        assertThatThrownBy(
                        () -> ArchitectureRules.featureLayerDependencies(ROOT).check(FIXTURES))
                .hasMessageContaining("archfixture.ticket.domain.TicketStatus")
                .hasMessageContaining("archfixture.ticket.infra.TicketRepository")
                .hasMessageContaining("archfixture.view.infra.ViewRepository")
                .hasMessageNotContaining("Method <archfixture.ticket.app.TicketService");
    }

    @Test
    void detectsSpringInDomain() {
        assertThatThrownBy(() -> ArchitectureRules.domainIsPlainJava(ROOT).check(FIXTURES))
                .hasMessageContaining("archfixture.ticket.domain.TicketStatus");
    }

    @Test
    void detectsRepositoryMethodWithoutViewer() {
        assertThatThrownBy(() ->
                        ArchitectureRules.repositoriesTakeViewerFirst(ROOT).check(FIXTURES))
                .hasMessageContaining("TicketRepository.countAll()")
                .hasMessageNotContaining("TicketRepository.count(");
    }
}
