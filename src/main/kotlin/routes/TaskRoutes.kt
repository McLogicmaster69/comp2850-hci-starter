package routes

import data.TaskRepository
import data.getFragment
import data.getEditFragment
import data.getInputForm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter
import utils.jsMode
import utils.logValidationError
import utils.timed

fun ApplicationCall.isHtmx(): Boolean = request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true

fun Route.taskRoutes() {
    get("/tasks") { call.handleTaskList() }
    post("/tasks") { call.handleCreateTask() }
    post("/tasks/{id}/delete") { call.handleDeleteTask() }
    post("/tasks/{id}/complete") { call.handleCompleteTask() }
    get("/tasks/{id}/edit") { call.handleEditTask() }
    post("/tasks/{id}/edit") { call.handleUpdateTask() }
    get("/tasks/{id}/view") { call.handleViewTask() }
}

private suspend fun ApplicationCall.handleTaskList() {
    timed("T0_list", jsMode()) {
        val pebble =
        PebbleEngine
            .Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            ).build()

        val model = mapOf(
            "title" to "Tasks",
            "tasks" to TaskRepository.all(),
        )

        val template = pebble.getTemplate("tasks/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleCreateTask() {
    timed("T1_add", jsMode()) {
        val parameters = receiveParameters()
        val title = parameters["title"].orEmpty().trim()
        val description = parameters["description"].orEmpty().trim()
        val priority = parameters["priority"].orEmpty().trim()

        if (title.isBlank()) {
            // Validation error handling
            if (isHtmx()) {
                val error = """<div id="status" hx-swap-oob="true" role="alert" aria-live="assertive">
                    Title is required. Please enter at least one character.
                </div>"""
                return@timed respondText(error, ContentType.Text.Html, HttpStatusCode.BadRequest)
            } else {
                // No-JS: redirect back (could add error query param)
                response.headers.append("Location", "/tasks")
                return@timed respond(HttpStatusCode.SeeOther)
            }
        }

        val task = TaskRepository.add(title, description, priority)

        if (isHtmx()) {
            // Return HTML fragment for new task
            val fragment = getFragment(task)

            val status = """<div id="status" hx-swap-oob="true">Task "${task.title}" added successfully.</div>"""

            val taskAmount = """<h2 id="list-heading" hx-swap-oob="true">Current tasks (${TaskRepository.size})</h2>"""

            val noTaskMsg = """<li id="notasksmsg" hx-swap-oob="true"></li>"""

            val inputForm = getInputForm()

            return@timed respondText(fragment + status + taskAmount + noTaskMsg + inputForm, ContentType.Text.Html, HttpStatusCode.Created)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}

private suspend fun ApplicationCall.handleDeleteTask() {
    timed("T2_delete", jsMode()) {
        val id = parameters["id"]?.toIntOrNull()
        val removed = id?.let { TaskRepository.delete(it) } ?: false

        if (isHtmx()) {

            val message = if (removed) "Task deleted." else "Could not delete task."

            val status = """<div id="status" hx-swap-oob="true">$message</div>"""

            val taskAmount = """<h2 id="list-heading" hx-swap-oob="true">Current tasks (${TaskRepository.size})</h2>"""

            val noTaskMsg = """<li id="notasksmsg" hx-swap-oob="true">${if (TaskRepository.size == 0) "No tasks yet. Add one above!" else ""}</li>"""
            
            return@timed respondText(status + taskAmount + noTaskMsg, ContentType.Text.Html)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}

private suspend fun ApplicationCall.handleCompleteTask() {
    timed("T3_complete", jsMode()) {
        val id = parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.get(id) } ?: TaskRepository.nullTask
        task.completed = !task.completed

        TaskRepository.persist()

        if (isHtmx()) {
            val fragment = getFragment(task, true)
            
            val message = if (task == null) "An error occured: could not find task." else "Task has been set to ${if (task.completed) "" else "not "}completed."
            val status = """<div id="status" hx-swap-oob="true">$message</div>"""

            return@timed respondText(fragment + status, ContentType.Text.Html)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks#task-${id}")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}

private suspend fun ApplicationCall.handleEditTask() {
    timed("T4_editView", jsMode()) {
        val id = parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.get(id) } ?: TaskRepository.nullTask

        task.edit = true
        TaskRepository.persist()

        if (isHtmx()) {
            val fragment = getEditFragment(task)
            return@timed respondText(fragment, ContentType.Text.Html)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks#task-${id}")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}

private suspend fun ApplicationCall.handleUpdateTask() {
    timed("T5_editConfirm", jsMode()) {
        val formParameters = receiveParameters()
        val id = parameters["id"]?.toIntOrNull()
        val title = formParameters["title"].orEmpty().trim()
        val description = formParameters["description"].orEmpty().trim()
        val priority = formParameters["priority"].orEmpty().trim()
        val task = id?.let { TaskRepository.get(id) } ?: TaskRepository.nullTask

        task.title = title
        task.description = description
        task.priority = priority
        task.edit = false

        TaskRepository.persist()

        if (isHtmx()) {
            val fragment = getFragment(task, true)
            return@timed respondText(fragment, ContentType.Text.Html)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks#task-${id}")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}

private suspend fun ApplicationCall.handleViewTask() {
    timed("T6_view", jsMode()) {
        println("View")
        val id = parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.get(id) } ?: TaskRepository.nullTask

        task.edit = false
        TaskRepository.persist()

        if (isHtmx()) {
            val fragment = getFragment(task, true)
            return@timed respondText(fragment, ContentType.Text.Html)
        }

        // No-JS: POST-Redirect-GET pattern (303 See Other)
        response.headers.append("Location", "/tasks#task-${id}")
        return@timed respond(HttpStatusCode.SeeOther)
    }
}