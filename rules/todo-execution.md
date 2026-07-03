# Todo Files Execution Rules

This document outlines the coordinator workflow and guidelines for executing todo files (e.g., `containerize-todo.md`, `deploy-todo.md`).

---

## 1. Execution Protocol (Coordinator Workflow)

### 1.1 Parallel and Dependency Analysis
- The coordinator agent analyzes the task list to identify dependencies.
- If tasks within a phase have no mutual dependencies and can be executed concurrently without conflict, the coordinator may parallelize their execution by dispatching multiple subagents simultaneously.
- Tasks with dependencies or ordering constraints must be executed sequentially.

### 1.2 Staging Status
- Before dispatching a subagent for a task, the coordinator updates that task's state to `[in_progress]` in the todo file.

### 1.3 Subagent Invocation
- A subagent is launched with an isolated workspace, referencing only the current task ID.

### 1.4 Exit Check Evaluation
- If the task is the last one in its phase, the subagent is instructed to perform the exit checks.

### 1.5 Updating Status
- **Success**: If the task succeeds (and exit checks pass if it was the last task), it is marked `[x]` (done).
- **Failure**: If a failure occurs, it is marked `failed`, the run is stopped, and the working tree is reverted to a clean state.

### 1.6 Post-Completion Cleanup
- Once the entire todo list is completed, the todo file can be moved to a structured documentation directory (like `docs/`).
