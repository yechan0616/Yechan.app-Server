package app.yechan.server.global.common.response

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S : Any, T : Any> of(
            page: Page<S>,
            transform: (S) -> T,
        ): PageResponse<T> =
            PageResponse(
                items = page.content.map(transform),
                page = page.number,
                size = page.size,
                totalItems = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
