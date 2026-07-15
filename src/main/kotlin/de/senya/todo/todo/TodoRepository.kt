package de.senya.todo.todo

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA derives the implementation at runtime — no boilerplate.
 *
 * NestJS parallel: this replaces `@InjectRepository(Todo)` + the TypeORM
 * `Repository<Todo>`. You get findAll/findById/save/deleteById for free, and
 * can add query methods just by naming them (e.g. `findByCompleted(...)`).
 */
interface TodoRepository : JpaRepository<Todo, Long>
