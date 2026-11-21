package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.ADDR;
import static pro.fakeemail.Fixtures.AID;
import static pro.fakeemail.Fixtures.MID;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import pro.fakeemail.models.Attachment;
import pro.fakeemail.models.BodyState;
import pro.fakeemail.models.DetailedMessage;
import pro.fakeemail.models.Domain;
import pro.fakeemail.models.DomainType;
import pro.fakeemail.models.Limits;
import pro.fakeemail.models.Link;
import pro.fakeemail.models.Mailbox;
import pro.fakeemail.models.Page;
import pro.fakeemail.models.Quota;
import pro.fakeemail.models.Sender;
import pro.fakeemail.models.SimpleMessage;
import pro.fakeemail.models.Storage;

/** Checks the SDK against openapi/public.yaml, so a spec change shows up as a failing test. */
class SpecTest {
    private static final Map<String, Object> SPEC = load();
    private static final Map<String, Object> SCHEMAS =
            map(map(SPEC.get("components")).get("schemas"));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> load() {
        try {
            String text = Files.readString(Path.of("openapi", "public.yaml"), StandardCharsets.UTF_8);
            return map(new Yaml().load(text));
        } catch (IOException e) {
            throw new AssertionError("cannot read openapi/public.yaml", e);
        }
    }

    private static String refName(Object ref) {
        String s = (String) ref;
        return s.substring(s.lastIndexOf('/') + 1);
    }

    private static Set<String> properties(String schemaName) {
        Map<String, Object> schema = map(SCHEMAS.get(schemaName));
        Set<String> names = new HashSet<>();
        @SuppressWarnings("unchecked")
        List<Object> parts = schema.containsKey("allOf") ? (List<Object>) schema.get("allOf") : List.of(schema);
        for (Object p : parts) {
            Map<String, Object> part = map(p);
            if (part.containsKey("$ref")) {
                names.addAll(properties(refName(part.get("$ref"))));
            }
            if (part.containsKey("properties")) {
                names.addAll(map(part.get("properties")).keySet());
            }
        }
        return names;
    }

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(Mailbox.class, "Mailbox"),
                Arguments.of(Page.class, "MailboxPage"),
                Arguments.of(Page.class, "MessagePage"),
                Arguments.of(Sender.class, "From"),
                Arguments.of(SimpleMessage.class, "SimpleMessage"),
                Arguments.of(DetailedMessage.class, "DetailedMessage"),
                Arguments.of(Attachment.class, "Attachment"),
                Arguments.of(Link.class, "Link"),
                Arguments.of(Domain.class, "Domain"),
                Arguments.of(Limits.class, "Limits"),
                Arguments.of(Quota.class, "Quota"),
                Arguments.of(Storage.class, "Storage"));
    }

    @ParameterizedTest(name = "{0} covers {1}")
    @MethodSource("models")
    void modelCoversExactlyTheSchema(Class<?> model, String schema) {
        Set<String> fields = Arrays.stream(model.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(properties(schema), fields, model.getSimpleName() + " vs " + schema);
    }

    @Test
    void modelsCoverEveryResponseSchema() {
        // Request bodies, error envelopes, enums and the Domains wrapper are not models.
        Set<String> notModels = Set.of(
                "ErrorCode",
                "Error",
                "QuotaExhaustedError",
                "CreateMailboxRequest",
                "Domains",
                "DomainType",
                "BodyState");
        Set<String> expected = new HashSet<>(SCHEMAS.keySet());
        expected.removeAll(notModels);
        Set<String> covered = models().map(a -> (String) a.get()[1]).collect(Collectors.toSet());
        assertEquals(expected, covered);
    }

    @Test
    void domainsWrapperHasOnlyDomains() {
        assertEquals(Set.of("domains"), properties("Domains"));
    }

    @Test
    void createMailboxRequestFields() {
        assertEquals(Set.of("localPart", "domain"), properties("CreateMailboxRequest"));
    }

    @Test
    void quotaExhaustedErrorFields() {
        Set<String> extra = new HashSet<>(properties("QuotaExhaustedError"));
        extra.removeAll(properties("Error"));
        assertEquals(Set.of("limit", "used", "resetsAt", "upgradeUrl"), extra);
        for (String name : extra) {
            assertTrue(
                    Arrays.stream(QuotaExhaustedError.class.getDeclaredMethods())
                            .anyMatch(m -> m.getName().equals(name) && m.getParameterCount() == 0),
                    "QuotaExhaustedError." + name + "()");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> enumValues(String schema) {
        return (List<Object>) map(SCHEMAS.get(schema)).get("enum");
    }

    @Test
    void enumValues() throws IllegalAccessException {
        assertEquals(
                enumValues("BodyState"),
                BodyState.values().stream().map(BodyState::value).collect(Collectors.toList()));
        assertEquals(
                enumValues("DomainType"),
                DomainType.values().stream().map(DomainType::value).collect(Collectors.toList()));
        List<Object> codes = new ArrayList<>();
        for (Field f : ErrorCode.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                codes.add(f.get(null));
            }
        }
        assertEquals(enumValues("ErrorCode"), codes);
    }

    private static final Map<String, Consumer<FakeEmail>> CALLS = new LinkedHashMap<>();

    static {
        CALLS.put("createMailbox", c -> c.mailboxes().create());
        CALLS.put("listMailboxes", c -> c.mailboxes().list(PageParams.of(0, 1)));
        CALLS.put("getMailbox", c -> c.mailboxes().get(ADDR));
        CALLS.put("deleteMailbox", c -> c.mailboxes().delete(ADDR));
        CALLS.put("listMessages", c -> c.messages().list(ADDR, PageParams.of(0, 1)));
        CALLS.put(
                "waitForMessage",
                c -> c.messages()
                        .wait(
                                ADDR,
                                WaitParams.builder()
                                        .timeout(Duration.ofSeconds(1))
                                        .after(MID)
                                        .from("a")
                                        .subject("b")
                                        .build()));
        CALLS.put("getMessage", c -> c.messages().get(ADDR, MID));
        CALLS.put("deleteMessage", c -> c.messages().delete(ADDR, MID));
        CALLS.put("downloadMessageRaw", c -> c.messages().raw(ADDR, MID));
        CALLS.put("downloadAttachment", c -> c.messages().attachment(ADDR, MID, AID));
        CALLS.put("listDomains", c -> c.domains().list());
        CALLS.put("getLimits", c -> c.limits().get());
    }

    static Stream<Arguments> operations() {
        List<Arguments> result = new ArrayList<>();
        for (Map.Entry<String, Object> path : map(SPEC.get("paths")).entrySet()) {
            for (Map.Entry<String, Object> op : map(path.getValue()).entrySet()) {
                if (Set.of("get", "post", "put", "patch", "delete").contains(op.getKey())) {
                    Map<String, Object> operation = map(op.getValue());
                    result.add(Arguments.of(
                            operation.get("operationId"), op.getKey().toUpperCase(), path.getKey(), operation));
                }
            }
        }
        return result.stream();
    }

    @Test
    void everyOperationHasAMethod() {
        assertEquals(CALLS.keySet(), operations().map(a -> (String) a.get()[0]).collect(Collectors.toSet()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    void operationRequestMatchesTheSpec(String operationId, String method, String path, Map<String, Object> op) {
        // A 404 stops the call right after the request, whatever the response type.
        Harness h = Harness.of(Fixtures.apiError(404, "not_found", "not found"));
        assertThrows(NotFoundError.class, () -> CALLS.get(operationId).accept(h.client));
        FakeHttpClient.Recorded request = h.request(0);
        assertEquals(method, request.method());
        String pattern = "^"
                + Arrays.stream(path.split("/", -1))
                        .map(s -> s.startsWith("{") ? "[^/]+" : Pattern.quote(s))
                        .collect(Collectors.joining("/"))
                + "$";
        assertTrue(request.rawPath().matches(pattern), request.rawPath() + " vs " + path);

        Set<String> allowed = new HashSet<>();
        Map<String, Object> parameters = map(map(SPEC.get("components")).get("parameters"));
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) op.getOrDefault("parameters", List.of());
        for (Object p : params) {
            Map<String, Object> param = map(p);
            if (param.containsKey("$ref")) {
                param = map(parameters.get(refName(param.get("$ref"))));
            }
            if ("query".equals(param.get("in"))) {
                allowed.add((String) param.get("name"));
            }
        }
        assertTrue(
                allowed.containsAll(request.query().keySet()), request.query().keySet() + " vs " + allowed);
        assertEquals(allowed, request.query().keySet(), "the call sends every query parameter of the operation");
    }
}
