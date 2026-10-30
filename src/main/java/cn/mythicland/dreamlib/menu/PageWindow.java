package cn.mythicland.dreamlib.menu;

/**
 * Immutable page calculation shared by inventory menus and other compact catalog views.
 *
 * @param page            zero-based current page
 * @param pageCount       total page count, always at least one
 * @param pageSize        maximum entries per page
 * @param totalEntryCount total entries before paging
 */
public record PageWindow(int page, int pageCount, int pageSize, int totalEntryCount) {

    /**
     * Validates the page window.
     */
    public PageWindow {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (pageCount < 1) throw new IllegalArgumentException("pageCount must be positive");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        if (totalEntryCount < 0) throw new IllegalArgumentException("totalEntryCount must not be negative");
        if (page >= pageCount) throw new IllegalArgumentException("page exceeds pageCount");
    }

    /**
     * Calculates a clamped page window.
     *
     * @param totalEntryCount total entries
     * @param pageSize        maximum entries per page
     * @param requestedPage   requested zero-based page
     * @return clamped page window
     */
    public static PageWindow of(int totalEntryCount, int pageSize, int requestedPage) {
        if (totalEntryCount < 0) throw new IllegalArgumentException("totalEntryCount must not be negative");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        int pageCount = Math.max(1, (totalEntryCount + pageSize - 1) / pageSize);
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        return new PageWindow(page, pageCount, pageSize, totalEntryCount);
    }

    /**
     * Returns the inclusive start index for this page.
     *
     * @return start index
     */
    public int startIndex() {
        return page * pageSize;
    }

    /**
     * Returns the exclusive end index for this page.
     *
     * @return end index
     */
    public int endIndex() {
        return Math.min(startIndex() + pageSize, totalEntryCount);
    }

    /**
     * Returns whether a previous page exists.
     *
     * @return true when page is not the first page
     */
    public boolean hasPrevious() {
        return page > 0;
    }

    /**
     * Returns whether a next page exists.
     *
     * @return true when page is not the last page
     */
    public boolean hasNext() {
        return page + 1 < pageCount;
    }
}
