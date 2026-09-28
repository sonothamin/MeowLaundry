package com.sonothamin.meowlaundry.ui.paging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

const val DEFAULT_PAGE_SIZE = 30

/**
 * One window onto a longer list.
 *
 * [isLoading] is true only until the database has answered the first time, so screens can show
 * skeletons instead of flashing an empty state. [total] is the size of the whole (filtered) list,
 * [items] is the part of it that is currently on screen.
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
) {
    val hasMore: Boolean get() = items.size < total
}

/**
 * Grows a window over an already-filtered list one page at a time. Screens only compose (and
 * start decoding photos for) what has been revealed so far; the next page is revealed when the
 * user scrolls close to the end. Call [reset] whenever the filter, search or sort changes so the
 * new list starts from the top again.
 */
class PageWindow(private val pageSize: Int = DEFAULT_PAGE_SIZE) {
    private val pages = MutableStateFlow(1)

    fun reset() {
        pages.value = 1
    }

    fun loadMore() {
        pages.update { it + 1 }
    }

    /** [source] emits null until the first real result is in, then the full filtered list. */
    fun <T> window(source: Flow<List<T>?>): Flow<PagedList<T>> =
        combine(source, pages) { list, page ->
            if (list == null) PagedList(isLoading = true)
            else PagedList(items = list.take(page * pageSize), total = list.size, isLoading = false)
        }
}
