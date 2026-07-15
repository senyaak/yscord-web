package de.senya.todo.todo

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA entity — the persistence-mapped domain object.
 *
 * NestJS parallel: this is your `@Entity()` TypeORM class. `@Id @GeneratedValue`
 * is `@PrimaryGeneratedColumn()`, `@Column` maps the same way. The kotlin-jpa
 * Gradle plugin generates the no-arg constructor Hibernate needs and makes the
 * class `open`, so we can keep idiomatic Kotlin `val`/`var` properties.
 */
@Entity
@Table(name = "todos")
class Todo(
    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(nullable = false)
    var completed: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
