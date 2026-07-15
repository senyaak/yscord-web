package de.senya.todo.todo

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business layer. Constructor injection — Spring wires `TodoRepository` in.
 *
 * NestJS parallel: `@Service` is `@Injectable()`, the constructor param is the
 * same DI you'd write in a Nest provider. `@Transactional` on update wraps the
 * method in one DB transaction; because the entity is loaded inside it, JPA
 * dirty-checking flushes the changes on commit — no explicit `save` needed.
 */
@Service
@Transactional(readOnly = true)
class TodoService(private val repository: TodoRepository) {

    fun findAll(): List<Todo> = repository.findAll()

    fun findById(id: Long): Todo =
        repository.findById(id).orElseThrow { TodoNotFoundException(id) }

    @Transactional
    fun create(request: CreateTodoRequest): Todo =
        repository.save(
            Todo(
                title = request.title,
                description = request.description,
            ),
        )

    @Transactional
    fun update(id: Long, request: UpdateTodoRequest): Todo {
        val todo = findById(id)
        todo.title = request.title
        todo.description = request.description
        todo.completed = request.completed
        return todo
    }

    @Transactional
    fun delete(id: Long) {
        if (!repository.existsById(id)) throw TodoNotFoundException(id)
        repository.deleteById(id)
    }
}
