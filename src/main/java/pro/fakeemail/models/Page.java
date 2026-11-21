package pro.fakeemail.models;

import java.util.Iterator;
import java.util.List;

/**
 * A page of a list ({@code MailboxPage}, {@code MessagePage}). Iterating a page yields its items.
 *
 * @param content the items of this page
 * @param page zero-based page number
 * @param size page size asked for
 * @param totalPages number of pages
 * @param totalElements number of items across all pages
 * @param <T> item type
 */
public record Page<T>(List<T> content, int page, int size, int totalPages, long totalElements) implements Iterable<T> {
    public Page {
        content = List.copyOf(content);
    }

    /** Whether a page after this one exists. */
    public boolean hasNextPage() {
        return page + 1 < totalPages;
    }

    @Override
    public Iterator<T> iterator() {
        return content.iterator();
    }
}
