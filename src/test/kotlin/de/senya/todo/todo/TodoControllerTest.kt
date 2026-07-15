package de.senya.todo.todo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Full-slice integration test: real Spring context + H2 in-memory DB.
 * Exercises the CRUD lifecycle end-to-end through the HTTP layer.
 */
@SpringBootTest
class TodoControllerTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val repository: TodoRepository,
) {

    private lateinit var mvc: MockMvcTester

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        mvc = MockMvcTester.create(mockMvc)
    }

    @Test
    fun `creates a todo and returns 201 with Location`() {
        mvc.post().uri("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"Buy milk","description":"2 liters"}""")
            .assertThat()
            .hasStatus(201)
            .containsHeader("Location")
            .bodyJson()
            .extractingPath("$.title").isEqualTo("Buy milk")

        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun `rejects a blank title with 400`() {
        mvc.post().uri("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":""}""")
            .assertThat()
            .hasStatus(400)
    }

    @Test
    fun `returns 404 for an unknown id`() {
        mvc.get().uri("/api/todos/999")
            .assertThat()
            .hasStatus(404)
    }

    @Test
    fun `updates and then deletes a todo`() {
        val saved = repository.save(Todo(title = "Old"))

        mvc.put().uri("/api/todos/${saved.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"New","completed":true}""")
            .assertThat()
            .hasStatus(200)
            .bodyJson()
            .extractingPath("$.completed").isEqualTo(true)

        mvc.delete().uri("/api/todos/${saved.id}")
            .assertThat()
            .hasStatus(204)

        assertThat(repository.existsById(saved.id!!)).isFalse()
    }
}
