package com.javatechbd.todo.service;

import com.javatechbd.todo.dto.TaskRequest;
import com.javatechbd.todo.dto.TaskResponse;
import com.javatechbd.todo.entity.Task;
import com.javatechbd.todo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;

	@Transactional
	public TaskResponse create(TaskRequest request) {
		validateTitle(request.getTitle());

		Task task = Task.builder()
				.title(request.getTitle().trim())
				.description(request.getDescription())
				.completed(request.getCompleted() != null ? request.getCompleted() : false)
				.build();

		return toResponse(taskRepository.save(task));
	}

	@Transactional
	public TaskResponse update(Long id, TaskRequest request) {
		validateTitle(request.getTitle());

		Task task = taskRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + id));

		task.setTitle(request.getTitle().trim());
		task.setDescription(request.getDescription());
		if (request.getCompleted() != null) {
			task.setCompleted(request.getCompleted());
		}

		return toResponse(taskRepository.save(task));
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findAll() {
		return taskRepository.findAll().stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void delete(Long id) {
		if (!taskRepository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + id);
		}
		taskRepository.deleteById(id);
	}

	private void validateTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
		}
	}

	private TaskResponse toResponse(Task task) {
		return TaskResponse.builder()
				.id(task.getId())
				.title(task.getTitle())
				.description(task.getDescription())
				.completed(task.getCompleted())
				.createdAt(task.getCreatedAt())
				.updatedAt(task.getUpdatedAt())
				.build();
	}
}
