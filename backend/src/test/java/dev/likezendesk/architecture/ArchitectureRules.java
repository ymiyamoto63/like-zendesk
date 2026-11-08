package dev.likezendesk.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.likezendesk.shared.Viewer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * パッケージ構成の規則（docs/design/architecture.md 2.1、2.3）。{@code root} を引数に取るのは、規則そのものを違反例でテストするため。
 */
final class ArchitectureRules {

    static final Set<String> FEATURES = Set.of("tenant", "identity", "ticket", "view", "notification", "devtools");

    /** 同じ機能の中で、各層が依存してよい層。 */
    private static final Map<String, Set<String>> SAME_FEATURE = Map.of(
            "web", Set.of("web", "app", "domain"),
            "app", Set.of("app", "domain", "infra"),
            "domain", Set.of("domain"),
            "infra", Set.of("infra", "domain"));

    /** テナントをまたいで読む唯一のリポジトリ。テナント一覧と識別子の解決だけを担う。 */
    private static final String TENANT_DIRECTORY = "tenant.infra.TenantDirectory";

    private ArchitectureRules() {}

    /** クラスの置き場所。{@code shared} と、ルート直下（起動クラス）は層を持たない。 */
    private record Location(String feature, String layer) {
        static final Location ROOT = new Location("", "");
        static final Location SHARED = new Location("shared", "");

        static Optional<Location> of(String root, String packageName) {
            if (packageName.equals(root)) {
                return Optional.of(ROOT);
            }
            if (!packageName.startsWith(root + ".")) {
                return Optional.empty();
            }
            var parts = packageName.substring(root.length() + 1).split("\\.");
            if (parts[0].equals("shared")) {
                return Optional.of(SHARED);
            }
            if (parts.length < 2 || !FEATURES.contains(parts[0]) || !SAME_FEATURE.containsKey(parts[1])) {
                return Optional.of(new Location("?", packageName));
            }
            return Optional.of(new Location(parts[0], parts[1]));
        }

        boolean isValid() {
            return !feature.equals("?");
        }

        boolean mayDependOn(Location to) {
            if (this.equals(ROOT) || to.equals(SHARED)) {
                return true;
            }
            if (this.equals(SHARED) || to.equals(ROOT)) {
                return false;
            }
            if (!feature.equals(to.feature)) {
                // 機能をまたぐ呼び出しは、相手の app 層だけを経由する。domain の型（値と規則）はどの層からも参照してよい
                return to.layer.equals("domain")
                        || (to.layer.equals("app") && (layer.equals("web") || layer.equals("app")));
            }
            return SAME_FEATURE.get(layer).contains(to.layer);
        }
    }

    /** 全てのクラスが「機能.層」に置かれ、許された方向にだけ依存すること。 */
    static ArchRule featureLayerDependencies(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<>("reside in <feature>.<layer> and depend only in allowed directions") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        var from = Location.of(root, javaClass.getPackageName()).orElseThrow();
                        if (!from.isValid()) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " is not in shared or <feature>.<layer> (features: "
                                            + FEATURES + ", layers: " + SAME_FEATURE.keySet() + ")"));
                            return;
                        }
                        for (var dependency : javaClass.getDirectDependenciesFromSelf()) {
                            var target = dependency.getTargetClass().getBaseComponentType();
                            Location.of(root, target.getPackageName())
                                    .filter(to -> to.isValid() && !from.mayDependOn(to))
                                    .ifPresent(to -> events.add(
                                            SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                        }
                    }
                });
    }

    /** domain 層は Spring にも DB にも依存しない純粋な Java であること。 */
    static ArchRule domainIsPlainJava(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + ".*.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "java.sql..", "javax.sql..");
    }

    /** リポジトリの public メソッドは、第 1 引数に閲覧者を取ること（要件 6.1）。 */
    static ArchRule repositoriesTakeViewerFirst(String root) {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Repository.class)
                .and()
                .areDeclaredInClassesThat()
                .resideInAPackage(root + "..")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveFullyQualifiedName(root + "." + TENANT_DIRECTORY)
                .and()
                .haveModifier(JavaModifier.PUBLIC)
                .should(new ArchCondition<>("take a Viewer as the first parameter") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        var params = method.getRawParameterTypes();
                        if (params.isEmpty() || !params.getFirst().isAssignableTo(Viewer.class)) {
                            events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " does not take a Viewer as the first parameter"));
                        }
                    }
                })
                .allowEmptyShould(true);
    }
}
