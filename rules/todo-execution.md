# Todo Files Execution Rules

This document outlines the coordinator workflow and guidelines for executing todo files, focusing on the single source of truth `<todo-file>` and generic todo task files.

---

## 1. Source of Truth

- **`<todo-file>`** is the single source of truth for project fixes.
- **Task ID Format**: `PN-n` (e.g., `P1-1`, `P2-4`).
- **Phase Exit Checks**: Each phase contains exit checks at its end.
- **Coding Agent Workflow Instructions**: Defined in `<todo-file>` (defining branch, implement, test, PR, review loop, merge, cleanup). Do not alter or restate these steps.

---

## 2. Coordinator Execution Workflows

### 2.1 Generic Todo Execution Flow
- **Parallel Execution**: The coordinator agent analyzes the task list to identify dependencies. If tasks within a phase have no mutual dependencies and can be executed concurrently without conflict, the coordinator may parallelize their execution by dispatching multiple subagents simultaneously.
- **Sequential Fallback**: Tasks with dependencies or ordering constraints must be executed sequentially.

### 2.2 `<todo-file>` Strict Execution Flow
For the single source of truth `<todo-file>`, the coordinator must strictly execute tasks sequentially in the following order:

1. **Sequential Processing**: Read `<todo-file>` and process items in order, one at a time.
2. **Staging Status**: Before dispatching a subagent for an item, update that item's state to `[in_progress]` in `<todo-file>`.
3. **Subagent Invocation**: Dispatch a subagent with its own isolated context using the prompt template in Section 5. Pass only the current todo item ID.
   - *Phase Exit Checks*: If the item is the last in its phase, additionally instruct the subagent to run the phase's exit checks (defined in `<todo-file>`) after implementation/PR work is complete, and return the exit-check result (`pass|fail`, with detail).
4. **Final Status Resolution**: On subagent return, determine the final status:
   - If `implementation = failed` or `pr = failed` -> mark item `[failed]` and record the reason.
   - If `implementation = done` and `pr = done`, and the item is **not** the last in its phase -> mark item `[x]`.
   - If `implementation = done` and `pr = done`, and the item **is** the last in its phase:
     - If `exit_checks = pass` -> mark item `[x]`.
     - If `exit_checks = fail` -> mark item `[failed]`, reason = `"exit_check_failed"`.
5. **State Persistence**: Immediately after updating any task to `[x]` (done) in `<todo-file>`, the coordinator must commit the changes to Git:
   ```bash
   git add <todo-file>
   git commit -m "chore(todo): mark <task-id> as completed"
   ```
6. **No Auto-Retry / Error Boundary**: On any failure, do not retry automatically. Mark the item `[failed]` and stop the run. Revert any partial changes (using `git checkout` or `git reset`) so the working tree is left clean. Report the status to the user.
7. **Strict Progression**: Continue to the next item in `<todo-file>` only after the current item is marked `[x]`.

---

## 3. Resume and Restart Procedures

If the coordinator run stops due to a task marking as `[failed]`, recover using the following procedure:

1. **Clean Environment**: Verify that the git workspace has been reverted and is clean.
2. **Identify Failed Task**: Search `<todo-file>` for the task marked `[failed]`.
3. **Address Root Cause**: Fix the underlying issue (e.g., correct a dependency error, adjust configurations, or request human clarification).
4. **Update Status**: Set the task state from `[failed]` to `[in_progress]`.
5. **Re-run Task**: Launch the subagent for the failed task ID and resume execution.

---

## 4. Code Review Loop Constraints

- **Reviewer Request Definition**: A "reviewer request" is strictly defined as changes/feedback requested by a dedicated reviewer subagent or human reviewer as comments on a GitHub PR or issue.
- **Exclusion**: Local test execution failures, build/compilation errors, or linter errors caught during the subagent's local implementation phase do **not** count towards the reviewer request limit.
- **Limit**: If reviewer requests occur more than 3 times for a single task, the subagent must fail with the reason `"review_loop_exceeded"`.

---

## 5. Subagent Command Prompt Template

When dispatching subagents, use the following execution template:

```xml
<task>
Implement task <item-id> from <todo-file>. Read <todo-file> in full and follow
the "Coding Agent Workflow Instructions" section exactly for this task,
including the review loop and cleanup steps. Do not implement any other
task. Mark the item as done, failed, or in_progress as instructed in
<todo-file>.
</task>

<review_loop_limit>
If the reviewer subagent requests changes more than 3 times for this
task, stop and return failed with reason "review_loop_exceeded".
</review_loop_limit>

<tools>
bash, file read/write/edit, git, gh, docker, curl are all available by
default. Do not ask for further permission.
</tools>

<dependencies>
External dependencies, use only if needed:
<dep name="curl">For API testing.</dep>
<dep name="docker">docker/docker compose, for containerization tasks.</dep>
<dep name="gh">For GitHub operations (PR creation, review comments).</dep>
</dependencies>

<failure_handling>
On failure at any step, revert partial changes (git checkout/reset) so
the working tree is left clean, then return failed with a reason.
</failure_handling>

<return_format>
{
  "implementation": "done|failed",
  "pr": "done|failed",
  "reason": "failure_reason or empty string",
  "exit_checks": "pass|fail|not_applicable",
  "exit_checks_detail": "detail string or empty string"
}
</return_format>
```
