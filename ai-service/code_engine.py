"""
Cortex Code Engine — Intelligent code editing with diff, validation, and rollback.
Unlike the simple chat approach, this:
1. Reads FULL project files (not truncated)
2. Builds a dependency graph (who imports what)
3. Sends only RELEVANT files to the LLM
4. Validates every change with linting
5. Auto-rolls back if validation fails
6. Can execute shell commands
7. Verifies builds after changes
"""

import os
import re
import json
import shutil
import asyncio
import subprocess
from pathlib import Path
from typing import Optional
from llm_client import call_llm
from router import get_route

IGNORE_DIRS = {
    "node_modules", ".git", "target", "build", "dist", "__pycache__",
    ".venv", "venv", ".idea", ".vscode", ".architect", "coverage", ".next",
}

CODE_EXTENSIONS = {
    ".js", ".jsx", ".ts", ".tsx", ".css", ".html", ".json",
    ".java", ".py", ".xml", ".yml", ".yaml", ".toml",
    ".properties", ".sql", ".sh", ".env", ".md",
}


class ProjectContext:
    """Understands a project's structure, dependencies, and conventions."""
    
    def __init__(self, project_path: str):
        self.root = Path(project_path)
        self.files: dict[str, str] = {}  # rel_path -> content
        self.tree: list[str] = []
        self.project_type: str = "unknown"
        self.imports: dict[str, list[str]] = {}  # file -> [imported files]
        self.config: dict = {}
        self._scan()
    
    def _scan(self):
        """Scan the entire project."""
        if not self.root.is_dir():
            return
        
        # Detect project type
        if (self.root / "client" / "package.json").exists():
            self.project_type = "mern"
        elif (self.root / "package.json").exists():
            self.project_type = "node"
        elif (self.root / "pom.xml").exists():
            self.project_type = "java"
        elif (self.root / "requirements.txt").exists():
            self.project_type = "python"
        
        # Read all source files
        for path in sorted(self.root.rglob("*")):
            if not path.is_file():
                continue
            rel = str(path.relative_to(self.root))
            if any(ignored in rel.split("/") for ignored in IGNORE_DIRS):
                continue
            if path.suffix not in CODE_EXTENSIONS and path.name not in ("Dockerfile", "Makefile", ".gitignore", ".env"):
                continue
            
            try:
                content = path.read_text(errors="replace")
                self.files[rel] = content
            except Exception:
                continue
        
        # Build tree
        for rel in sorted(self.files.keys()):
            parts = rel.split("/")
            depth = len(parts) - 1
            self.tree.append("  " * depth + parts[-1])
        
        # Parse imports
        for rel, content in self.files.items():
            self.imports[rel] = self._extract_imports(rel, content)
        
        # Read config
        if ".env" in self.files:
            for line in self.files[".env"].splitlines():
                if "=" in line and not line.startswith("#"):
                    key, _, val = line.partition("=")
                    self.config[key.strip()] = val.strip()
    
    def _extract_imports(self, file_path: str, content: str) -> list[str]:
        """Extract import paths from a file."""
        imports = []
        
        # JS/TS imports
        for match in re.finditer(r"""(?:import|require)\s*\(?['"]([./][^'"]+)['"]""", content):
            imports.append(match.group(1))
        
        # Java imports
        for match in re.finditer(r"import\s+([\w.]+);", content):
            imports.append(match.group(1))
        
        # Python imports
        for match in re.finditer(r"from\s+([\w.]+)\s+import|import\s+([\w.]+)", content):
            imp = match.group(1) or match.group(2)
            imports.append(imp)
        
        return imports
    
    def get_relevant_files(self, query: str, max_files: int = 12) -> dict[str, str]:
        """Get files most relevant to a query, including their dependencies."""
        query_lower = query.lower()
        keywords = [w for w in re.split(r'\W+', query_lower) if len(w) > 2]
        
        scored: list[tuple[str, int]] = []
        
        for rel, content in self.files.items():
            score = 0
            rel_lower = rel.lower()
            name_lower = Path(rel).name.lower()
            content_lower = content.lower()
            
            # Score by keyword match
            for kw in keywords:
                if kw in name_lower: score += 10
                if kw in rel_lower: score += 5
                if kw in content_lower[:500]: score += 2
            
            # Always include key files
            if name_lower in ("package.json", ".env", "server.js", "app.jsx", "app.js", "app.tsx", "index.js"):
                score += 8
            
            # Boost config files
            if name_lower.endswith(".json") or name_lower.endswith(".env"):
                score += 3
            
            if score > 0:
                scored.append((rel, score))
        
        # Sort by score
        scored.sort(key=lambda x: -x[1])
        selected = [rel for rel, _ in scored[:max_files]]
        
        # Add dependency files
        deps_to_add = []
        for rel in selected:
            for imp in self.imports.get(rel, []):
                for candidate in self.files:
                    if imp.replace("./", "").replace("../", "") in candidate:
                        if candidate not in selected and candidate not in deps_to_add:
                            deps_to_add.append(candidate)
        
        selected.extend(deps_to_add[:5])
        
        return {rel: self.files[rel] for rel in selected if rel in self.files}
    
    def get_tree_string(self) -> str:
        return "\n".join(self.tree)
    
    def get_consistency_issues(self) -> list[str]:
        """Detect common issues like port mismatches, broken imports, etc."""
        issues = []
        
        # Port consistency (MERN)
        if self.project_type == "mern":
            backend_port = self.config.get("PORT", "5000")
            
            # Check proxy
            client_pkg = self.files.get("client/package.json", "")
            proxy_match = re.search(r'"proxy":\s*"http://localhost:(\d+)"', client_pkg)
            if proxy_match:
                proxy_port = proxy_match.group(1)
                if proxy_port != backend_port:
                    issues.append(f"PORT MISMATCH: .env PORT={backend_port} but client proxy uses {proxy_port}")
            
            # Check hardcoded URLs
            for rel, content in self.files.items():
                if "client/" in rel and ("localhost:" in content):
                    ports = re.findall(r"localhost:(\d+)", content)
                    for p in ports:
                        if p != backend_port and p != "3000":
                            issues.append(f"HARDCODED PORT: {rel} uses localhost:{p} but backend is on {backend_port}")
        
        # Broken imports
        for rel, imports_list in self.imports.items():
            if not rel.endswith((".js", ".jsx", ".ts", ".tsx")):
                continue
            for imp in imports_list:
                if imp.startswith("."):
                    # Resolve relative import
                    base_dir = str(Path(rel).parent)
                    resolved = os.path.normpath(os.path.join(base_dir, imp))
                    # Check if file exists (with common extensions)
                    found = False
                    for ext in ["", ".js", ".jsx", ".ts", ".tsx", ".css", "/index.js", "/index.jsx"]:
                        if (resolved + ext) in self.files:
                            found = True
                            break
                    if not found:
                        issues.append(f"BROKEN IMPORT: {rel} imports '{imp}' but file not found")
        
        return issues


class FileBackup:
    """Manages file backups for rollback."""
    
    def __init__(self, project_path: str):
        self.root = Path(project_path)
        self.backups: dict[str, Optional[str]] = {}  # rel_path -> original content (None = didn't exist)
    
    def backup(self, rel_path: str):
        """Backup a file before modifying."""
        full = self.root / rel_path
        if full.exists():
            self.backups[rel_path] = full.read_text(errors="replace")
        else:
            self.backups[rel_path] = None
    
    def rollback(self, rel_path: str) -> bool:
        """Restore a file to its backup."""
        if rel_path not in self.backups:
            return False
        full = self.root / rel_path
        original = self.backups[rel_path]
        if original is None:
            # File didn't exist, delete it
            if full.exists():
                full.unlink()
        else:
            full.write_text(original)
        return True
    
    def rollback_all(self):
        """Rollback all backed up files."""
        for rel in self.backups:
            self.rollback(rel)


def validate_file(file_path: Path) -> Optional[str]:
    """Validate a file's syntax. Returns error or None."""
    name = file_path.name
    try:
        content = file_path.read_text()
    except Exception:
        return None
    
    if name.endswith(".json"):
        try:
            json.loads(content)
        except json.JSONDecodeError as e:
            return f"Invalid JSON: {e}"
    
    elif name.endswith(".css"):
        braces = 0
        for i, c in enumerate(content):
            if c == '{': braces += 1
            elif c == '}': braces -= 1
            if braces < 0:
                line = content[:i].count('\n') + 1
                return f"Extra }} at line {line}"
        if braces > 0:
            return f"{braces} unclosed block(s)"
    
    elif name.endswith((".js", ".jsx", ".ts", ".tsx")):
        braces = brackets = parens = 0
        in_str = False
        in_comment = False
        in_line_comment = False
        in_template = False
        prev = ''
        for c in content:
            if in_line_comment:
                if c == '\n': in_line_comment = False
                continue
            if in_comment:
                if prev == '*' and c == '/': in_comment = False
                prev = c
                continue
            if c == '/' and prev == '/': in_line_comment = True; continue
            if c == '*' and prev == '/': in_comment = True; continue
            if c == '`': in_template = not in_template
            if c in ('"', "'") and prev != '\\' and not in_template:
                in_str = not in_str
            if not in_str and not in_template:
                if c == '{': braces += 1
                elif c == '}': braces -= 1
                elif c == '[': brackets += 1
                elif c == ']': brackets -= 1
                elif c == '(': parens += 1
                elif c == ')': parens -= 1
            prev = c
        if braces: return f"Unmatched braces (balance: {braces})"
        if brackets: return f"Unmatched brackets (balance: {brackets})"
        if parens: return f"Unmatched parens (balance: {parens})"
    
    return None


def parse_file_blocks(content: str) -> list[dict]:
    """Parse FILE: blocks from LLM response."""
    files = []
    lines = content.split("\n")
    current_path = None
    current_code = []
    in_code = False
    
    file_patterns = [
        re.compile(r"^FILE:\s*(.+)$", re.IGNORECASE),
        re.compile(r"^#{1,4}\s+`?([^\s`]+\.[a-zA-Z]+)`?\s*$"),
        re.compile(r"^#{1,4}\s+`?([^\s`]+/[^\s`]+)`?\s*$"),
        re.compile(r"^\*\*([^\s*]+/[^\s*]+)\*\*\s*$"),
    ]
    
    for line in lines:
        stripped = line.strip()
        
        if stripped.startswith("```") and not in_code:
            in_code = True
            continue
        elif stripped == "```" and in_code:
            in_code = False
            continue
        elif in_code:
            current_code.append(line)
            continue
        
        # Try to detect file path
        for pattern in file_patterns:
            match = pattern.match(stripped)
            if match:
                path = match.group(1).strip()
                if any(path.endswith(ext) for ext in [
                    ".js", ".jsx", ".ts", ".tsx", ".css", ".html", ".json",
                    ".java", ".py", ".xml", ".yml", ".yaml", ".toml",
                    ".properties", ".env", ".md", ".sh",
                ]) or "/" in path:
                    # Save previous file
                    if current_path and current_code:
                        files.append({
                            "path": current_path,
                            "content": "\n".join(current_code).strip(),
                        })
                    current_path = path
                    current_code = []
                break
    
    if current_path and current_code:
        files.append({
            "path": current_path,
            "content": "\n".join(current_code).strip(),
        })
    
    return files


async def execute_edit(
    project_path: str,
    instruction: str,
    conversation_history: list[str] | None = None,
    lang: str = "es",
) -> dict:
    """
    The main edit function. Takes an instruction and applies changes intelligently.
    Returns {response, files_changed, issues_found, build_result}
    """
    ctx = ProjectContext(project_path)
    
    # 1. Check for consistency issues first
    issues = ctx.get_consistency_issues()
    
    # 2. Get relevant files
    relevant = ctx.get_relevant_files(instruction)
    
    # 3. Build prompt
    system = (
        "You are an expert code editor working on a real project. "
        "You can see the project structure and relevant source files. "
        "RULES: "
        "- When making changes, output COMPLETE files using FILE: path/to/file.ext format. "
        "- NEVER truncate files. Include every line. "
        "- ONLY modify files that need changes. Don't touch files that are fine. "
        "- Follow the project's existing conventions (naming, style, patterns). "
        "- If there are consistency issues listed, fix them too. "
        "- Validate your output: matching braces, semicolons, proper JSON. "
        "- For questions, answer concisely (max 5 lines). "
        "- For changes, output FILE: blocks that will be written automatically. "
    )
    
    lang_map = {
        "es": "Respond in Spanish.",
        "en": "",
        "fr": "Respond in French.",
    }
    system += lang_map.get(lang, f"Respond in {lang}.")
    
    user_msg = f"Project type: {ctx.project_type}\n"
    user_msg += f"Structure:\n{ctx.get_tree_string()}\n\n"
    
    if issues:
        user_msg += "KNOWN ISSUES (fix these too):\n"
        for issue in issues:
            user_msg += f"- {issue}\n"
        user_msg += "\n"
    
    user_msg += "Relevant files:\n"
    for rel, content in relevant.items():
        user_msg += f"\n--- {rel} ---\n{content}\n"
    
    if conversation_history is not None and len(conversation_history) > 0:
        user_msg += "\nConversation:\n"
        for msg in conversation_history[-6:]:
            user_msg += msg + "\n"
    
    user_msg += f"\nUser: {instruction}"
    
    # 4. Call LLM
    route = get_route("chat")
    response_text = await call_llm(route, system, user_msg, temperature=0.2, max_tokens=4096)
    
    # 5. Parse file blocks
    file_blocks = parse_file_blocks(response_text)
    
    # 6. Apply changes with backup and validation
    backup = FileBackup(project_path)
    changes = []
    errors = []
    
    for block in file_blocks:
        rel_path = block["path"]
        new_content = block["content"]
        full_path = Path(project_path) / rel_path
        
        # Backup
        backup.backup(rel_path)
        
        # Write
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(new_content)
        
        # Validate
        error = validate_file(full_path)
        if error:
            errors.append({"file": rel_path, "error": error})
            # Rollback this file
            backup.rollback(rel_path)
        else:
            existed = backup.backups[rel_path] is not None
            changes.append({
                "file": rel_path,
                "action": "modified" if existed else "created",
            })
    
    # 7. Run build check if changes were made
    build_result = None
    if changes and ctx.project_type in ("mern", "node"):
        client_dir = Path(project_path) / "client"
        if client_dir.exists():
            try:
                result = subprocess.run(
                    ["npx", "react-scripts", "build"],
                    cwd=str(client_dir),
                    capture_output=True, text=True, timeout=60,
                )
                if result.returncode == 0:
                    build_result = "success"
                else:
                    build_result = result.stderr[-500:] if result.stderr else result.stdout[-500:]
            except Exception as e:
                build_result = str(e)
    
    # 8. Auto-commit if changes were made and build passed
    commit_result = None
    if changes and (build_result == "success" or build_result is None):
        short_instruction = instruction[:60] + "..." if len(instruction) > 60 else instruction
        commit_result = git_auto_commit(project_path, short_instruction)
    
    return {
        "response": response_text,
        "files": [{"path": c["file"], "action": c["action"]} for c in changes],
        "errors": errors,
        "issues_found": issues,
        "build_result": build_result,
        "commit": commit_result,
    }


def git_auto_commit(project_path: str, message: str) -> dict:
    """Auto-commit changes after successful edits."""
    try:
        root = Path(project_path)
        git_dir = root / ".git"
        
        # Init git if not exists
        if not git_dir.exists():
            subprocess.run(["git", "init"], cwd=project_path, capture_output=True)
            subprocess.run(["git", "add", "."], cwd=project_path, capture_output=True)
            subprocess.run(["git", "commit", "-m", "Initial commit by Cortex"], cwd=project_path, capture_output=True)
        
        # Stage and commit
        subprocess.run(["git", "add", "."], cwd=project_path, capture_output=True)
        result = subprocess.run(
            ["git", "commit", "-m", f"cortex: {message}"],
            cwd=project_path, capture_output=True, text=True,
        )
        
        if result.returncode == 0:
            # Get short hash
            hash_result = subprocess.run(
                ["git", "rev-parse", "--short", "HEAD"],
                cwd=project_path, capture_output=True, text=True,
            )
            return {"committed": True, "hash": hash_result.stdout.strip(), "message": message}
        else:
            return {"committed": False, "reason": "No changes to commit"}
    except Exception as e:
        return {"committed": False, "reason": str(e)}


def git_diff(project_path: str) -> str:
    """Show git diff of uncommitted changes."""
    try:
        result = subprocess.run(
            ["git", "diff", "--stat"],
            cwd=project_path, capture_output=True, text=True, timeout=10,
        )
        return result.stdout if result.stdout else "No changes"
    except Exception:
        return "Git not available"


def git_log(project_path: str, n: int = 10) -> list[dict]:
    """Get last N commits."""
    try:
        result = subprocess.run(
            ["git", "log", f"-{n}", "--pretty=format:%h|%s|%cr"],
            cwd=project_path, capture_output=True, text=True, timeout=10,
        )
        commits = []
        for line in result.stdout.strip().splitlines():
            parts = line.split("|", 2)
            if len(parts) == 3:
                commits.append({"hash": parts[0], "message": parts[1], "time": parts[2]})
        return commits
    except Exception:
        return []


def git_undo(project_path: str) -> dict:
    """Undo the last Cortex commit."""
    try:
        # Check if last commit was by cortex
        result = subprocess.run(
            ["git", "log", "-1", "--pretty=format:%s"],
            cwd=project_path, capture_output=True, text=True,
        )
        if result.stdout.startswith("cortex:"):
            subprocess.run(
                ["git", "reset", "--soft", "HEAD~1"],
                cwd=project_path, capture_output=True,
            )
            subprocess.run(
                ["git", "checkout", "."],
                cwd=project_path, capture_output=True,
            )
            return {"undone": True, "message": result.stdout}
        return {"undone": False, "reason": "Last commit was not by Cortex"}
    except Exception as e:
        return {"undone": False, "reason": str(e)}


async def generate_tests(project_path: str, file_path: str, lang: str = "es") -> dict:
    """Generate tests for a specific file."""
    ctx = ProjectContext(project_path)
    
    if file_path not in ctx.files:
        return {"error": f"File not found: {file_path}"}
    
    content = ctx.files[file_path]
    
    # Determine test framework
    test_system = (
        "You are a testing expert. Generate comprehensive tests for the given file. "
        "RULES: "
        "- For .js/.jsx: use Jest with React Testing Library if React component "
        "- For .java: use JUnit 5 with Mockito "
        "- For .py: use pytest "
        "- Generate the test file using FILE: format "
        "- Test all public functions/methods "
        "- Include edge cases "
        "- Use descriptive test names "
    )
    
    # Determine test file path
    if file_path.endswith((".js", ".jsx")):
        test_path = file_path.replace(".jsx", ".test.jsx").replace(".js", ".test.js")
    elif file_path.endswith(".java"):
        test_path = file_path.replace("/main/", "/test/").replace(".java", "Test.java")
    elif file_path.endswith(".py"):
        test_path = file_path.replace(".py", "_test.py")
        if "/" in test_path:
            parts = test_path.rsplit("/", 1)
            test_path = parts[0] + "/test_" + parts[1].replace("_test.py", ".py")
    else:
        test_path = file_path + ".test"
    
    user_msg = f"Generate tests for this file:\n\n--- {file_path} ---\n{content}\n\nSave tests to: {test_path}"
    
    route = get_route("add")
    response = await call_llm(route, test_system, user_msg, temperature=0.2, max_tokens=4096)
    
    files = parse_file_blocks(response)
    
    # Write test files
    written = []
    for f in files:
        full = Path(project_path) / f["path"]
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(f["content"])
        written.append(f["path"])
    
    return {"tests_generated": written, "response": response}


async def execute_shell(project_path: str, command: str) -> dict:
    """Execute a shell command in the project directory."""
    try:
        result = subprocess.run(
            ["bash", "-c", command],
            cwd=project_path,
            capture_output=True, text=True, timeout=120,
        )
        return {
            "stdout": result.stdout[-2000:],
            "stderr": result.stderr[-1000:],
            "exit_code": result.returncode,
        }
    except subprocess.TimeoutExpired:
        return {"stdout": "", "stderr": "Command timed out (120s)", "exit_code": 1}
    except Exception as e:
        return {"stdout": "", "stderr": str(e), "exit_code": 1}
