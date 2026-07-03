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
- Assign IDs using the format `P<phase-number>-<task-number>` (e.g., `P1-01`, `P2-01`, `P2-02`).

### 2.4 Phase Exit Checks (EC)
- The final item in each phase must be an exit check (e.g., `P1-EC`).
- Define the precise manual or automated validation steps required before proceeding to the next phase.
