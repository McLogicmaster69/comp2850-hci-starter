package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class Task(
    val id: Int,
    var title: String,
    var description: String,
    var priority: String,
    var completed: Boolean,
    var edit: Boolean
)

fun getFragment(task : Task) : String = 
    """
    <li id="task-${task.id}">
        <h2>${task.title}</h2>
        <span>Priority: ${task.priority}</span>
        <p>${task.description}</p>
        <p>Completed: ${task.completed}</p>
        <form action="/tasks/${task.id}/complete" method="post" style="display: inline;"
            hx-post="/tasks/${task.id}/complete"
            hx-target="#task-${task.id}"
            hx-swap="outerHTML">
        <button type="submit" aria-label="${if (task.completed) "Mark as incomplete" else "Mark as complete"}">${if (task.completed) "Mark as incomplete" else "Mark as complete"}</button>
        </form>
        <form action="/tasks/${task.id}/edit" method="get" style="display: inline;"
            hx-get="/tasks/${task.id}/edit"
            hx-target="#task-${task.id}"
            hx-swap="outerHTML">
        <button type="submit" aria-label="Edit task: ${task.title}"}">Edit</button>
        </form>
        <form action="/tasks/${task.id}/delete" method="post" style="display: inline;"
            hx-post="/tasks/${task.id}/delete"
            hx-target="#task-${task.id}"
            hx-swap="outerHTML">
        <button type="submit" aria-label="Delete task: ${task.title}">Delete</button>
        </form>
        <br>
    </li>
    """

fun getEditFragment(task : Task, swap : Boolean = false) : String =
    """
    <li id="task-${task.id}" class="task-edit">
        <form action="/tasks/${task.id}/edit" method="post"
                hx-post="/tasks/${task.id}/edit"
                hx-target="#task-${task.id}"
                hx-swap="outerHTML">

            <label for="title-${task.id}">Title</label>
            <input type="text"
                id="title-${task.id}"
                name="title"
                value="${task.title}"
                required
                autofocus
                aria-describedby="hint-${task.id}">
            <small id="hint-${task.id}">Keep it short and specific.</small>
            <label for="description-${task.id}">Description</label>
            <textarea
                id="description-${task.id}"
                rows="4"
                cols="50"
                name="description">${task.description}</textarea>
            <label for="priority-${task.id}">Priority</label>
            <input type="text"
                id="priority-${task.id}"
                name="priority"
                value="${task.priority}">
            
            <button type="submit">Save</button>

            <a href="/tasks/${task.id}/view"
                hx-get="/tasks/${task.id}/view"
                hx-target="#task-${task.id}"
                hx-swap="outerHTML"
                role="button">Cancel</a>
        </form>
        <br>
    </li>
    """

object TaskRepository {
    private val file = File("data/tasks.csv")
    private val tasks = mutableListOf<Task>()
    private val idCounter = AtomicInteger(1)

    public val size: Int
        get() = this.tasks.size

    public val nullTask : Task
        get() = Task(-1, "", "", "", false, false)

    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("id,title\n")
        } else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 6)
                if (parts.size == 6) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    tasks.add(Task(id, parts[1], parts[2], parts[3], parts[4].toBoolean(), parts[5].toBoolean()))
                    idCounter.set(maxOf(idCounter.get(), id + 1))
                }
            }
        }
    }

    fun all(): List<Task> = tasks.toList()

    fun add(title: String, description : String, priority : String): Task {
        val task = Task(idCounter.getAndIncrement(), title, description, priority, false, false)
        tasks.add(task)
        persist()
        return task
    }

    fun delete(id: Int): Boolean {
        val removed = tasks.removeIf { it.id == id }
        if (removed) persist()
        return removed
    }

    fun get(id : Int) : Task {
        for (task in tasks) {
            if (task.id == id) return task
        }
        return this.nullTask
    }

    fun persist() {
        file.writeText("id,title\n" + tasks.joinToString("\n") { "${it.id},${it.title.replace(",", "")},${it.description.replace(",", "")},${it.priority.replace(",", "")},${it.completed},${it.edit}"})
    }
}
