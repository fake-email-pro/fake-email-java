package pro.fakeemail;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import pro.fakeemail.models.Page;

/**
 * Every item of a list, fetching pages lazily as iteration reaches them. Each {@link #iterator()} or {@link
 * #stream()} starts again from the first page. Lists are newest first, so items arriving meanwhile shift pages.
 *
 * <pre>{@code
 * for (Mailbox mb : mail.mailboxes().listAll()) { ... }
 * mail.messages().listAll(address).stream().filter(m -> m.code() != null).findFirst();
 * }</pre>
 *
 * @param <T> item type
 */
public final class PageIterable<T> implements Iterable<T> {
    private final IntFunction<Page<T>> fetchPage;

    PageIterable(IntFunction<Page<T>> fetchPage) {
        this.fetchPage = fetchPage;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int nextPage = 0;
            private boolean last = false;
            private Iterator<T> current = null;

            @Override
            public boolean hasNext() {
                while (current == null || !current.hasNext()) {
                    if (last) {
                        return false;
                    }
                    Page<T> page = fetchPage.apply(nextPage);
                    last = page.content().isEmpty() || nextPage + 1 >= page.totalPages();
                    nextPage++;
                    current = page.content().iterator();
                }
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    /** A sequential stream over every item; pages are fetched as the stream is consumed. */
    public Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false);
    }
}
