package de.senya.todo.todo

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * REST controller — thin HTTP layer, delegates to the service.
 *
 * NestJS parallel: `@RestController` + `@RequestMapping` is your `@Controller('todos')`,
 * the mapping annotations are `@Get`/`@Post`/`@Put`/`@Delete`, `@RequestBody @Valid`
 * is `@Body()` running through the `ValidationPipe`, `@PathVariable` is `@Param('id')`.
 */
@RestController
@RequestMapping("/api/todos")
class TodoController(private val service: TodoService) {

    @GetMapping
    fun list(): List<TodoResponse> =
        service.findAll().map(TodoResponse::from)

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: Long): TodoResponse =
        TodoResponse.from(service.findById(id))

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateTodoRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<TodoResponse> {
        val created = service.create(request)
        val location: URI = uriBuilder
            .path("/api/todos/{id}")
            .buildAndExpand(created.id)
            .toUri()
        return ResponseEntity.created(location).body(TodoResponse.from(created))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTodoRequest,
    ): TodoResponse =
        TodoResponse.from(service.update(id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) =
        service.delete(id)
}
