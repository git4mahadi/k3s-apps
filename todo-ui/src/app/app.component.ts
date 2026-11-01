import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Task } from './models/task.model';
import { TaskService } from './services/task.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent implements OnInit {
  private readonly taskService = inject(TaskService);

  title = '';
  description = '';
  tasks: Task[] = [];
  loading = false;
  error = '';
  apiStatus = '';
  apiHealthy = false;
  editingId: number | null = null;

  ngOnInit(): void {
    this.loadTasks();
  }

  loadTasks(): void {
    this.loading = true;
    this.error = '';
    this.apiStatus = '';
    this.apiHealthy = false;
    this.taskService.getTasks().subscribe({
      next: (tasks) => {
        this.tasks = tasks;
        this.apiHealthy = true;
        this.apiStatus = 'Backend service is running...';
        this.loading = false;
      },
      error: () => {
        this.apiHealthy = false;
        this.apiStatus = 'Backend not running.....';
        this.loading = false;
      },
    });
  }

  saveTask(): void {
    const trimmedTitle = this.title.trim();
    if (!trimmedTitle) {
      this.error = 'Title is required';
      return;
    }

    this.error = '';
    const payload = {
      title: trimmedTitle,
      description: this.description.trim() || undefined,
      completed: false,
    };

    if (this.editingId !== null) {
      this.taskService.updateTask(this.editingId, payload).subscribe({
        next: () => {
          this.resetForm();
          this.loadTasks();
        },
        error: () => {
          this.error = 'Failed to update task';
        },
      });
      return;
    }

    this.taskService.createTask(payload).subscribe({
      next: () => {
        this.resetForm();
        this.loadTasks();
      },
      error: () => {
        this.error = 'Failed to create task';
      },
    });
  }

  startEdit(task: Task): void {
    this.editingId = task.id;
    this.title = task.title;
    this.description = task.description ?? '';
  }

  deleteTask(task: Task): void {
    this.error = '';
    this.taskService.deleteTask(task.id).subscribe({
      next: () => {
        if (this.editingId === task.id) {
          this.resetForm();
        }
        this.loadTasks();
      },
      error: () => {
        this.error = 'Failed to delete task';
      },
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  private resetForm(): void {
    this.title = '';
    this.description = '';
    this.editingId = null;
  }
}
