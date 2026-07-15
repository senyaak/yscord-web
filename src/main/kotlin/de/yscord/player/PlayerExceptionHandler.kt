package de.yscord.player

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps player/yt-dlp failures to RFC-9457 ProblemDetail responses, mirroring the
 * todo module's [de.senya.todo.todo.ApiExceptionHandler].
 */
@RestControllerAdvice(basePackages = ["de.yscord.player"])
class PlayerExceptionHandler {

    @ExceptionHandler(YtDlpException::class)
    fun handleYtDlp(ex: YtDlpException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "yt-dlp failed").apply {
            title = "Upstream media error"
        }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadArg(ex: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "bad request").apply {
            title = "Invalid request"
        }
}
