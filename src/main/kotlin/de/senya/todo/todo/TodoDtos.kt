package de.senya.todo.todo

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Request/response DTOs — kept separate from the entity so the HTTP contract
 * and the DB schema can evolve independently.
 *
 * NestJS parallel: these are your `class CreateTodoDto` with `class-validator`
 * decorators. `@field:NotBlank` here is `@IsNotEmpty()` there; validation is
 * triggered by `@Valid` on the controller method (like the `ValidationPipe`).
 */
data class CreateTodoRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:Size(max = 4000)
    val description: String? = null,
)

data class UpdateTodoRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:Size(max = 4000)
    val description: String? = null,

    val completed: Boolean = false,
)

data class TodoResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(todo: Todo): TodoResponse = TodoResponse(
            id = requireNotNull(todo.id),
            title = todo.title,
            description = todo.description,
            completed = todo.completed,
            createdAt = todo.createdAt,
            updatedAt = todo.updatedAt,
        )
    }
}
