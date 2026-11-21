package pro.fakeemail;

/**
 * Which page of a list to fetch: {@code PageParams.of(0, 50)} or {@code PageParams.builder().size(10).build()}.
 * Unset values are left to the server (page 0, size 20).
 */
public final class PageParams {
    private final Integer page;
    private final Integer size;

    private PageParams(Integer page, Integer size) {
        this.page = page;
        this.size = size;
    }

    /** Zero-based {@code page} of {@code size} items (1..50). */
    public static PageParams of(int page, int size) {
        return new PageParams(page, size);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Zero-based page number, or {@code null} for the server's default. */
    public Integer page() {
        return page;
    }

    /** Page size, or {@code null} for the server's default. */
    public Integer size() {
        return size;
    }

    /** Builder of {@link PageParams}. */
    public static final class Builder {
        private Integer page;
        private Integer size;

        private Builder() {}

        /** Zero-based page number. */
        public Builder page(int page) {
            this.page = page;
            return this;
        }

        /** Page size, 1..50 (server default 20). */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public PageParams build() {
            return new PageParams(page, size);
        }
    }
}
