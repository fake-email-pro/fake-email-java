package pro.fakeemail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

/**
 * Parsed JSON to models. Reads only the fields a model knows, so a field the server adds later is ignored; a
 * missing or mistyped required field is a {@link FakeEmailError}.
 */
final class Decode {
    private Decode() {}

    static Mailbox mailbox(Object json) {
        Obj o = new Obj(json, "Mailbox");
        return new Mailbox(
                o.str("address"),
                o.instant("activeSince"),
                o.instant("expiresAt"),
                o.lng("storageUsedBytes"),
                o.integer("messagesCount"));
    }

    static <T> Page<T> page(Object json, Function<Object, T> item) {
        Obj o = new Obj(json, "Page");
        List<T> content = new ArrayList<>();
        for (Object entry : o.list("content")) {
            content.add(item.apply(entry));
        }
        return new Page<>(
                content, o.integer("page"), o.integer("size"), o.integer("totalPages"), o.lng("totalElements"));
    }

    static Sender sender(Object json) {
        if (json == null) {
            return null;
        }
        Obj o = new Obj(json, "From");
        return new Sender(o.optStr("name"), o.str("address"));
    }

    static SimpleMessage simpleMessage(Object json) {
        Obj o = new Obj(json, "SimpleMessage");
        return new SimpleMessage(
                o.str("id"),
                o.str("mailboxAddress"),
                o.instant("createdAt"),
                o.lng("sizeBytes"),
                sender(o.opt("from")),
                o.optStr("subject"),
                BodyState.of(o.str("bodyState")),
                o.integer("attachmentsCount"),
                o.optStr("code"),
                o.optStr("confirmLink"));
    }

    static DetailedMessage detailedMessage(Object json) {
        Obj o = new Obj(json, "DetailedMessage");
        List<Attachment> attachments = new ArrayList<>();
        for (Object a : o.list("attachments")) {
            attachments.add(attachment(a));
        }
        List<String> codes = new ArrayList<>();
        for (Object c : o.list("codes")) {
            if (!(c instanceof String)) {
                throw new FakeEmailError("unexpected API response: DetailedMessage.codes holds a non-string");
            }
            codes.add((String) c);
        }
        List<Link> links = new ArrayList<>();
        for (Object l : o.list("links")) {
            links.add(link(l));
        }
        return new DetailedMessage(
                o.str("id"),
                o.instant("createdAt"),
                sender(o.opt("from")),
                o.optStr("subject"),
                BodyState.of(o.str("bodyState")),
                o.optStr("html"),
                o.optStr("text"),
                attachments,
                codes,
                links);
    }

    static Attachment attachment(Object json) {
        Obj o = new Obj(json, "Attachment");
        return new Attachment(o.str("id"), o.str("contentType"), o.optStr("fileName"), o.lng("sizeBytes"));
    }

    static Link link(Object json) {
        Obj o = new Obj(json, "Link");
        return new Link(o.str("url"), o.optStr("text"));
    }

    static Domain domain(Object json) {
        Obj o = new Obj(json, "Domain");
        return new Domain(o.str("name"), DomainType.of(o.str("type")));
    }

    static List<Domain> domains(Object json) {
        Obj o = new Obj(json, "Domains");
        List<Domain> domains = new ArrayList<>();
        for (Object d : o.list("domains")) {
            domains.add(domain(d));
        }
        return List.copyOf(domains);
    }

    static Limits limits(Object json) {
        Obj o = new Obj(json, "Limits");
        return new Limits(
                quota(o.opt("quota")),
                o.integer("requestsPerMinute"),
                o.integer("concurrentWaits"),
                storage(o.opt("storage")),
                o.lng("mailboxTtlSeconds"));
    }

    static Quota quota(Object json) {
        Obj o = new Obj(json, "Quota");
        return new Quota(o.integer("limit"), o.integer("used"), o.instant("resetsAt"));
    }

    static Storage storage(Object json) {
        Obj o = new Obj(json, "Storage");
        return new Storage(o.lng("usedBytes"), o.lng("limitBytes"));
    }

    /** Typed access to a JSON object, with errors that name the schema and the field. */
    private static final class Obj {
        private final Map<?, ?> map;
        private final String schema;

        Obj(Object json, String schema) {
            if (!(json instanceof Map)) {
                throw new FakeEmailError("unexpected API response: " + schema + " is not a JSON object");
            }
            this.map = (Map<?, ?>) json;
            this.schema = schema;
        }

        private FakeEmailError invalid(String field, String expected) {
            return new FakeEmailError("unexpected API response: " + schema + "." + field + " is not " + expected);
        }

        Object opt(String field) {
            return map.get(field);
        }

        String str(String field) {
            Object v = map.get(field);
            if (!(v instanceof String)) {
                throw invalid(field, "a string");
            }
            return (String) v;
        }

        String optStr(String field) {
            Object v = map.get(field);
            if (v == null) {
                return null;
            }
            if (!(v instanceof String)) {
                throw invalid(field, "a string or null");
            }
            return (String) v;
        }

        long lng(String field) {
            Object v = map.get(field);
            if (!(v instanceof Long)) {
                throw invalid(field, "an integer");
            }
            return (Long) v;
        }

        int integer(String field) {
            long v = lng(field);
            if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
                throw invalid(field, "a 32-bit integer");
            }
            return (int) v;
        }

        Instant instant(String field) {
            String v = str(field);
            try {
                return Times.parse(v);
            } catch (RuntimeException e) {
                throw invalid(field, "an RFC 3339 time");
            }
        }

        List<?> list(String field) {
            Object v = map.get(field);
            if (!(v instanceof List)) {
                throw invalid(field, "an array");
            }
            return (List<?>) v;
        }
    }
}
