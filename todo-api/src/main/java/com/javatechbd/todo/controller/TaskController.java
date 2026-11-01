package com.javatechbd.todo.controller;

import com.javatechbd.todo.dto.TaskRequest;
import com.javatechbd.todo.dto.TaskResponse;
import com.javatechbd.todo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

	private final TaskService taskService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public TaskResponse create(@RequestBody TaskRequest request) {
		return taskService.create(request);
	}

	@PutMapping("/{id}")
	public TaskResponse update(@PathVariable Long id, @RequestBody TaskRequest request) {
		return taskService.update(id, request);
	}

	@GetMapping
	public List<TaskResponse> findAll() {
		return taskService.findAll();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		taskService.delete(id);
	}
}
