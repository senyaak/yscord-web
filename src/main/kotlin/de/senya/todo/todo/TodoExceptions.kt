package de.senya.todo.todo

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class TodoNotFoundException(id: Long) : RuntimeException("Todo $id not found")

/**
 * Central error mapping — turns domain/validation exceptions into RFC-9457
 * `ProblemDetail` JSON responses.
 *
 * NestJS parallel: this is a global `ExceptionFilter` (`@Catch(...)`), mounted
 * app-wide the way you'd register `APP_FILTER`. `@RestControllerAdvice` applies
 * it across every controller.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(TodoNotFoundException::class)
    fun handleNotFound(ex: TodoNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        problem.title = "Validation failed"
        problem.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
        )
        return problem
    }
}
