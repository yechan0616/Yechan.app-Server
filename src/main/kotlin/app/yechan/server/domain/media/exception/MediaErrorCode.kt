package app.yechan.server.domain.media.exception

import app.yechan.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class MediaErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "Asset was not found."),
    ASSET_EMPTY(HttpStatus.BAD_REQUEST, "Uploaded file is empty."),
    ASSET_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Uploaded file is too large."),
    ASSET_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, GIF and WebP images are supported."),
    ASSET_DIMENSIONS_INVALID(HttpStatus.BAD_REQUEST, "Image dimensions are out of range."),
    ASSET_IN_USE(HttpStatus.CONFLICT, "Asset is used by an entry."),
    ;

    override val code: String get() = name
}
