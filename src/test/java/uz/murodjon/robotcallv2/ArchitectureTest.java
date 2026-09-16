package uz.murodjon.robotcallv2;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The layering and naming rules of CLAUDE.md §1–§6, checked by the build rather than only in
 * review. A failure here names the class and the dependency that broke a rule; fix the code,
 * not the rule — change a rule only together with CLAUDE.md.
 */
@AnalyzeClasses(packages = "uz.murodjon.robotcallv2", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT_PACKAGE = "uz.murodjon.robotcallv2";

    /** Top-level packages that are not features (CLAUDE.md §2): the voice pipeline and the shared code. */
    private static final Set<String> NON_FEATURES = Set.of("agent", "shared", "config", "security");

    /** §4: the one other feature's configuration every layer may read — for {@code defaultId()}. */
    private static final String COMPANY_PROPERTIES = ROOT_PACKAGE + ".company.infrastructure.config.CompanyProperties";

    /**
     * §6.2: the words a port method may start with. Built from the ports as they are; a new
     * verb is added here on purpose, never a noun or an adjective ({@code effective}, {@code active}).
     */
    private static final Set<String> PORT_VERBS = Set.of(
            "accept", "activate", "add", "append", "apply", "archive", "assign", "attach", "book", "build",
            "call", "cancel", "change", "charge", "claim", "clear", "click", "clone", "close", "cluster",
            "complete", "compose", "connect", "consume", "contains", "count", "create", "deactivate",
            "delete", "deliver", "detach", "disable", "disconnect", "download", "embed", "enable",
            "enqueue", "erase", "evaluate", "evict", "exchange", "execute", "exhaust", "exists",
            "expire", "export", "extract", "fetch", "filter", "finalize", "find", "finish", "generate", "handle",
            "hangup", "has", "hold", "import", "inject", "insert", "install", "invite", "is", "list", "listen",
            "login", "logout", "make", "mark", "monitor", "notify", "originate", "parse", "pause",
            "peek", "play", "post", "prepare", "preview", "publish", "record", "refresh", "register",
            "release", "remember", "remove", "render", "replace", "report", "request", "require",
            "reject", "reserve", "reset", "resolve", "retry", "revoke", "rollback", "rotate", "run", "save",
            "say", "schedule", "search", "send", "set", "settle", "setup", "simulate", "start",
            "store", "subscribe", "sum", "sweep", "switch", "sync", "takeover", "test", "touch",
            "transfer", "trigger", "unhold", "update", "upload", "upsert", "validate", "verify",
            "whisper", "write");

    /** §2/§4: a service sees ports; JPA and every other adapter stay in infrastructure. */
    @ArchTest
    static final ArchRule applicationSeesOnlyItsOwnConfigFromInfrastructure = noClasses()
            .that().resideInAnyPackage("..application..", "..presentation..")
            .should().dependOnClassesThat(resideInAPackage("..infrastructure..")
                    .and(resideOutsideOfPackage("..infrastructure.config..")))
            .because("CLAUDE.md §4: only a feature's own *Properties may be read from infrastructure");

    /** §3: controllers talk to their use case, never to the voice pipeline or a service class. */
    @ArchTest
    static final ArchRule presentationDoesNotSeeTheVoicePipeline = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("uz.murodjon.robotcallv2.agent..")
            .because("CLAUDE.md §3");

    @ArchTest
    static final ArchRule controllersBindToUseCases = noClasses()
            .that().resideInAPackage("..presentation.controller..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..")
            .because("CLAUDE.md §3: a controller binds to its <Feature>UseCase");

    /** §4: the service layer knows nothing about HTTP. */
    @ArchTest
    static final ArchRule applicationDoesNotKnowHttp = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.ResponseEntity")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.HttpStatus")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.server.ResponseStatusException")
            .orShould().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
            .orShould().dependOnClassesThat().resideInAPackage("org.springframework.web.context.request..")
            .because("CLAUDE.md §4");

    /**
     * §2 ({@code shared/stream}) and §4: a service hands out a transport-free
     * {@code EventSubscription}; {@code SseEmitter}, {@code StreamingResponseBody} and the rest of
     * Spring MVC are built only in presentation and {@code shared/api} — never in the application
     * layer or the voice pipeline.
     */
    @ArchTest
    static final ArchRule servletStreamingStaysAtTheWebEdge = noClasses()
            .that().resideInAnyPackage("..application..", "uz.murodjon.robotcallv2.agent..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web.servlet..")
            .because("CLAUDE.md §2/§4: the service layer knows no SSE; SseEmitter is made only through"
                    + " shared/api/SseEventStreams in presentation");

    /** §4: an upload reaches a service as {@code UploadedFile}; the multipart part stays in the controller. */
    @ArchTest
    static final ArchRule applicationDoesNotSeeMultipart = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web.multipart..")
            .because("CLAUDE.md §4");

    /** §4: the company and the user are passed explicitly; only security/ reads the context. */
    @ArchTest
    static final ArchRule onlySecurityReadsTheSecurityContext = noClasses()
            .that().resideOutsideOfPackage("uz.murodjon.robotcallv2.security..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
            .because("CLAUDE.md §4");

    /**
     * §4: a feature — any of its layers — and {@code agent/} reach another feature only through
     * its UseCase: the {@code port/input} interface and the types its signatures speak
     * ({@code domain}, {@code application.dto}). That feature's service, port/output, mapper,
     * infrastructure and presentation are off-limits. {@code shared/}, {@code config/} and
     * {@code security/} are not features and are not checked. The exceptions, each narrow:
     * <ul>
     *   <li>implementing another feature's port/output — the adapter plugs that feature into the
     *       outside world and the dependency still points inward (crm's
     *       {@code UysotDebtorSourceAdapter} for campaign's {@code DebtorSourcePort},
     *       {@code agent/ami/TrunkRegistrationMonitor} for dialer's {@code TrunkRegistrationStatusPort});</li>
     *   <li>{@code CompanyProperties} from anywhere — the documented {@code defaultId()} exception;</li>
     *   <li>another feature's {@code *Properties} from {@code agent/} or an infrastructure adapter,
     *       never from the application layer — a configuration value, not behaviour
     *       ({@code LiveProperties}, {@code OperatorProperties}, {@code PjsipConfigProperties});</li>
     *   <li>persistence → another feature's JPA entity or Spring Data repository — the
     *       {@code @ManyToOne} graph and {@code getReferenceById} are JPA detail that stays inside
     *       persistence (§2.1).</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule otherFeaturesOnlyThroughTheirUseCases = classes()
            .that().resideInAPackage("uz.murodjon.robotcallv2..")
            .should(reachOtherFeaturesOnlyThroughTheirUseCases())
            .because("CLAUDE.md §4: another feature is reached only through its UseCase (port/input)");

    /** §1: a nested type is an implementation detail — a public one belongs in its own file. */
    @ArchTest
    static final ArchRule noPublicNestedTypes = noClasses()
            .that().areMemberClasses()
            .should().bePublic()
            .because("CLAUDE.md §1");

    /** §6.2: a port method starts with a verb — {@code findEffective}, not {@code effective}. */
    @ArchTest
    static final ArchRule portMethodsStartWithAVerb = classes()
            .that().areInterfaces()
            .and().resideInAnyPackage("..application.port.input..", "..application.port.output..")
            .should(declareOnlyMethodsStartingWithAVerb())
            .because("CLAUDE.md §6.2: a method name starts with a verb (PORT_VERBS)");

    private static ArchCondition<JavaClass> reachOtherFeaturesOnlyThroughTheirUseCases() {
        return new ArchCondition<>("reach other features only through their UseCases") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String feature = featureOf(origin.getPackageName());
                if (!isFeature(feature) && !feature.equals("agent")) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetFeature = featureOf(target.getPackageName());
                    if (isFeature(targetFeature) && !targetFeature.equals(feature)
                            && !isOpenToOtherFeatures(origin, target)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** Whether {@code origin}, from another feature or {@code agent/}, may use {@code target}. */
    private static boolean isOpenToOtherFeatures(JavaClass origin, JavaClass target) {
        String originLayer = layerOf(origin.getPackageName());
        String layer = layerOf(target.getPackageName());
        return switch (layer) {
            case "application.port.input", "application.dto" -> true;
            case "application.port.output" -> origin.isAssignableTo(target.getName());
            case "infrastructure.config" -> target.getName().equals(COMPANY_PROPERTIES)
                    || target.getSimpleName().endsWith("Properties")
                    && (featureOf(origin.getPackageName()).equals("agent") || originLayer.startsWith("infrastructure."));
            case "infrastructure.persistence.entity", "infrastructure.persistence.repository" ->
                    originLayer.startsWith("infrastructure.persistence.");
            default -> layer.equals("domain") || layer.startsWith("domain.");
        };
    }

    private static ArchCondition<JavaClass> declareOnlyMethodsStartingWithAVerb() {
        return new ArchCondition<>("declare only methods whose name starts with a verb") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                for (JavaMethod method : port.getMethods()) {
                    Set<JavaModifier> modifiers = method.getModifiers();
                    if (modifiers.contains(JavaModifier.STATIC) || modifiers.contains(JavaModifier.PRIVATE)
                            || modifiers.contains(JavaModifier.SYNTHETIC)) {
                        continue; // not part of the port's contract (helpers, lambda bodies)
                    }
                    String leadingWord = leadingWordOf(method.getName());
                    if (!PORT_VERBS.contains(leadingWord)) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " starts with '" + leadingWord + "', which is not in PORT_VERBS"));
                    }
                }
            }
        };
    }

    /** The {@code <feature>} segment of {@code uz.murodjon.robotcallv2.<feature>...}, or "" outside it. */
    private static String featureOf(String packageName) {
        if (!packageName.startsWith(ROOT_PACKAGE + ".")) {
            return "";
        }
        return packageName.substring(ROOT_PACKAGE.length() + 1).split("[.]")[0];
    }

    /** The package below the feature — {@code application.port.input}, {@code domain.entity}, … */
    private static String layerOf(String packageName) {
        String[] segments = packageName.split("[.]", 5);
        return segments.length == 5 ? segments[4] : "";
    }

    private static boolean isFeature(String feature) {
        return !feature.isEmpty() && !NON_FEATURES.contains(feature);
    }

    /** {@code find} of {@code findByCompanyId}: the leading run of lower-case letters. */
    private static String leadingWordOf(String methodName) {
        int end = 0;
        while (end < methodName.length() && Character.isLowerCase(methodName.charAt(end))) {
            end++;
        }
        return methodName.substring(0, end);
    }
}
