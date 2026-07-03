# Todo Files Creation Rules

This document outlines the guidelines and structure for creating todo files (e.g., `containerize-todo.md`, `deploy-todo.md`).

---

## 1. Filename Convention
- Files must be named using the format `<task>-todo.md` (e.g., `containerize-todo.md`, `deploy-todo.md`).
- Initially create these files in the root workspace directory.

---

## 2. Structure of the Todo File
Each todo file must contain the following sections:

### 2.1 Title and Goal
A clear title stating the objective (e.g., Spring Boot Containerization).

### 2.2 Coding Agent Workflow Instructions
An explicit set of steps that developer subagents must follow:
- **Git Branching**: Branch naming conventions (e.g., named exactly after the task ID).
- **Code Implementation**: Guidelines for code modifications and local verification.
- **PR Creation**: Instructions for creating a Pull Request (`gh pr create`) and invoking the review loop.
- **Code Review**: Requirements for using a reviewer subagent (which comments on GitHub but does not modify code).
- **PR Merging**: Process for merging PRs and performing local/remote branch cleanup.
- **Result Output**: Final coordinator JSON output format.

### 2.3 Phases & Task IDs
- Organize tasks into distinct phases (e.g., Phase 1, Phase 2).
- Assign IDs using the format `P<phase-number>-<task-number>` (e.g., `P1-1`, `P2-1`, `P2-2`).

### 2.4 Phase Exit Checks (EC)
- The final item in each phase must be an exit check (e.g., `P1-EC`).
- Define the precise manual or automated validation steps required before proceeding to the next phase.

### 2.5 Allowed Task States
To maintain structure, task states must strictly use the following markers:
- `[ ]` : Pending task.
- `[in_progress]` : Task currently assigned and executing.
- `[x]` : Task successfully completed.
- `[failed]` : Task failed execution (with failure reasons logged next to it).

---

## 3. Todo File Markdown Template

Use this template as a blueprint when creating a new todo file:

```markdown
# <Task/Feature Name> Todo

Goal: <Define the high-level objective>

## Coding Agent Workflow Instructions
1. **Branching**: Create a local git branch named after the task ID (e.g., `P1-1`).
2. **Implementation & Test**: Implement the changes and verify locally.
3. **Pull Request**: Create a PR (`gh pr create`) and trigger the review loop.
4. **Code Review**: Run a reviewer subagent. The reviewer must only write comments on GitHub and not modify code directly.
5. **Merge & Cleanup**: Merge the PR, pull changes back to the main branch, delete local/remote topic branches.
6. **Output**: Return final JSON results to the coordinator.

## Phase 1: <Phase Name>
- [ ] P1-1: <Task Description>
- [ ] P1-2: <Task Description>
- [ ] P1-EC: Run test suite with `./mvnw test` and verify all tests pass.
```
