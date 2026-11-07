package app.yechan.server.domain.content.exception

import app.yechan.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ContentErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    BLUEPRINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Blueprint was not found."),
    BLUEPRINT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A blueprint with this key already exists."),
    BLUEPRINT_INVALID(HttpStatus.BAD_REQUEST, "Blueprint definition is invalid."),
    BLUEPRINT_IN_USE(HttpStatus.CONFLICT, "Blueprint still has entries or is referenced by another blueprint."),
    BLUEPRINT_KIND_LOCKED(HttpStatus.CONFLICT, "Blueprint kind cannot change while it has entries."),
    BLUEPRINT_REVISION_CONFLICT(HttpStatus.CONFLICT, "Blueprint was changed by someone else."),
    ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Entry was not found."),
    ENTRY_INVALID(HttpStatus.BAD_REQUEST, "Entry content is invalid."),
    ENTRY_SLUG_TAKEN(HttpStatus.CONFLICT, "Another entry already uses this slug."),
    ENTRY_SINGLETON_EXISTS(HttpStatus.CONFLICT, "This singleton already has an entry."),
    ENTRY_REVISION_CONFLICT(HttpStatus.CONFLICT, "Entry was changed by someone else."),
    ENTRY_QUERY_INVALID(HttpStatus.BAD_REQUEST, "Filter or sort is not supported for this blueprint."),
    ENTRY_ORDER_INVALID(HttpStatus.BAD_REQUEST, "Order must list distinct entries of this blueprint."),
    PREVIEW_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Preview link is invalid or has expired."),
    ;

    override val code: String get() = name
}
