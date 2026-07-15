package de.yscord

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class YscordApplication

fun main(args: Array<String>) {
    SpringApplication.run(YscordApplication::class.java, *args)
}
