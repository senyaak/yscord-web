package de.senya.todo

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

// Scans the original todo module plus the new player module (de.yscord.player,
// deliberately outside the de.senya base package).
@SpringBootApplication(scanBasePackages = ["de.senya.todo", "de.yscord.player"])
class TodoApplication

fun main(args: Array<String>) {
    SpringApplication.run(TodoApplication::class.java, *args)
}
