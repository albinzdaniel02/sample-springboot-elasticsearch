# Troubleshooting Git Credentials & Push Issues

This guide explains how to diagnose and resolve authentication issues when pushing to a Git remote (specifically GitHub) from local repositories, especially when working with multiple GitHub accounts or in non-interactive terminal environments.

---

## 1. The Issue: Non-Interactive Prompt Failure

### Symptoms
When attempting a `git push`, the operation fails with errors like:
```bash
bash: line 1: /dev/tty: No such device or address
error: failed to execute prompt script (exit code 1)
fatal: could not read Password for 'https://<username>@github.com/...': No such file or directory
```

### Why This Happens
This error occurs because:
1. **Username Mismatch:** Git is attempting to authenticate using a username that differs from the account authenticated locally or the account that has permissions for the target repository.
2. **Non-Interactive Shell:** When the authentication helper fails to find credentials for the requested username, Git attempts to fall back to an interactive prompt (asking you to type your password/token). Since code editors, IDE terminals, or automated scripts run in non-interactive shells, the prompt fails immediately with `no such device or address`.

---

## 2. Step-by-Step Diagnosis

Follow these commands to pinpoint the issue:

### Step A: Check Remotes
Verify the URL of your remote:
```bash
git remote -v
```
*Note if it uses HTTPS (`https://github.com/...`) or SSH (`git@github.com:...`).*

### Step B: Inspect Git Configuration Origin
Check where your Git configurations (global, local, etc.) are defined:
```bash
git config --list --show-origin
```
Look specifically for:
- `credential.username` (global vs local)
- `credential.helper`

### Step C: Check GitHub CLI Auth Status
If you use GitHub CLI (`gh`), check which account is currently logged in:
```bash
gh auth status
```
*(If running in PowerShell and encountering parser errors, use `cmd /c gh auth status`.)*

### Step D: Test with a Dry-Run Override
Test if overriding the credential username inline resolves the issue:
```bash
git -c credential.username=<correct_username> push -u origin <branch_name> --dry-run
```
Replace `<correct_username>` with your logged-in GitHub username (e.g., `albinzdaniel02`) and `<branch_name>` with your branch name (e.g., `main`).

---

## 3. Resolution Steps

### Option 1: Override Username Locally (Recommended for Multi-Account Setup)
If you have a global Git account (e.g., for work) but want to use your personal GitHub account for a specific project:
1. **Set the local credential username** to match your personal account:
   ```bash
   git config --local credential.username <personal_username>
   ```
2. **Verify the configuration** using a dry-run push:
   ```bash
   git push -u origin <branch_name> --dry-run
   ```
3. **Push your changes:**
   ```bash
   git push -u origin <branch_name>
   ```

### Option 2: Re-authenticate with GitHub CLI
If the logged-in user is incorrect or authentication has expired:
1. **Log in to the correct account:**
   ```bash
   gh auth login
   ```
2. Follow the prompts to authenticate via your browser or using a Personal Access Token (PAT).

### Option 3: Use SSH Instead of HTTPS
Switching the remote to SSH bypasses the credential manager entirely and relies on SSH keys:
1. **Generate and add an SSH key** to your GitHub account (if not already done).
2. **Change the remote URL to SSH:**
   ```bash
   git remote set-url origin git@github.com:<owner>/<repository-name>.git
   ```
3. **Push to origin:**
   ```bash
   git push -u origin <branch_name>
   ```
