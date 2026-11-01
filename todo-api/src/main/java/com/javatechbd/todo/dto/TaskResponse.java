package com.javatechbd.todo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

	private Long id;
	private String title;
	private String description;
	private Boolean completed;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
