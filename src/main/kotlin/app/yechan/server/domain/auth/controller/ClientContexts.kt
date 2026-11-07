package app.yechan.server.domain.auth.controller

import app.yechan.server.domain.auth.dto.ClientContext
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

fun HttpServletRequest.client() = ClientContext(remoteAddr?.take(45), getHeader(HttpHeaders.USER_AGENT)?.take(255))
