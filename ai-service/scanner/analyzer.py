import os
import xml.etree.ElementTree as ET
import json
from pathlib import Path

# Files that indicate a specific language/framework
MARKERS = {
    "pom.xml": {"lang": "Java", "build": "Maven"},
    "build.gradle": {"lang": "Java", "build": "Gradle"},
    "build.gradle.kts": {"lang": "Kotlin", "build": "Gradle"},
    "package.json": {"lang": "JavaScript/TypeScript", "build": "npm"},
    "requirements.txt": {"lang": "Python", "build": "pip"},
    "pyproject.toml": {"lang": "Python", "build": "pyproject"},
    "Pipfile": {"lang": "Python", "build": "pipenv"},
    "go.mod": {"lang": "Go", "build": "go modules"},
    "Cargo.toml": {"lang": "Rust", "build": "cargo"},
    "Gemfile": {"lang": "Ruby", "build": "bundler"},
    "composer.json": {"lang": "PHP", "build": "composer"},
}

INFRA_FILES = {
    "Dockerfile": "Docker",
    "docker-compose.yml": "Docker Compose",
    "docker-compose.yaml": "Docker Compose",
    "fly.toml": "Fly.io",
    "Procfile": "Heroku",
    "vercel.json": "Vercel",
    "netlify.toml": "Netlify",
    ".github/workflows": "GitHub Actions",
    "Jenkinsfile": "Jenkins",
    ".gitlab-ci.yml": "GitLab CI",
}

IGNORE_DIRS = {
    "node_modules", ".git", "target", "build", "dist", ".mvn",
    "__pycache__", ".venv", "venv", ".idea", ".vscode", ".architect",
    ".next", ".angular", "coverage", "bin", "obj",
}


def scan_project(path: str) -> dict:
    """Scan a project directory and return context."""
    root = Path(path)
    if not root.is_dir():
        return {"error": f"Path '{path}' is not a valid directory"}

    context = {
        "project_path": str(root.resolve()),
        "project_name": root.resolve().name,
        "languages": [],
        "frameworks": [],
        "build_tools": [],
        "infrastructure": [],
        "dependencies": {},
        "structure": [],
        "entry_points": [],
    }

    languages = set()
    frameworks = set()
    build_tools = set()
    infrastructure = set()

    # Scan root-level marker files
    for marker, info in MARKERS.items():
        if (root / marker).exists():
            languages.add(info["lang"])
            build_tools.add(info["build"])

    # Scan infrastructure files
    for marker, infra in INFRA_FILES.items():
        if (root / marker).exists() or (root / marker).is_dir():
            infrastructure.add(infra)

    # Detect frameworks from specific files
    frameworks.update(_detect_frameworks(root))

    # Parse dependencies
    deps = {}
    if (root / "pom.xml").exists():
        deps["maven"] = _parse_pom(root / "pom.xml")
    if (root / "package.json").exists():
        deps["npm"] = _parse_package_json(root / "package.json")
    if (root / "requirements.txt").exists():
        deps["pip"] = _parse_requirements(root / "requirements.txt")

    # Build directory tree (max 3 levels deep)
    structure = _scan_tree(root, max_depth=3)

    # Find entry points
    entry_points = _find_entry_points(root)

    context["languages"] = sorted(languages)
    context["frameworks"] = sorted(frameworks)
    context["build_tools"] = sorted(build_tools)
    context["infrastructure"] = sorted(infrastructure)
    context["dependencies"] = deps
    context["structure"] = structure
    context["entry_points"] = entry_points

    return context


def _detect_frameworks(root: Path) -> set:
    """Detect frameworks by reading config files."""
    frameworks = set()

    # Java / Spring Boot
    pom = root / "pom.xml"
    if pom.exists():
        try:
            content = pom.read_text()
            if "spring-boot" in content:
                frameworks.add("Spring Boot")
            if "picocli" in content:
                frameworks.add("PicoCLI")
            if "quarkus" in content:
                frameworks.add("Quarkus")
        except Exception:
            pass

    # JavaScript / TypeScript
    pkg = root / "package.json"
    if pkg.exists():
        try:
            data = json.loads(pkg.read_text())
            all_deps = {**data.get("dependencies", {}), **data.get("devDependencies", {})}
            if "@angular/core" in all_deps:
                frameworks.add("Angular")
            if "react" in all_deps:
                frameworks.add("React")
            if "vue" in all_deps:
                frameworks.add("Vue.js")
            if "next" in all_deps:
                frameworks.add("Next.js")
            if "express" in all_deps:
                frameworks.add("Express")
            if "svelte" in all_deps:
                frameworks.add("Svelte")
        except Exception:
            pass

    # Python
    req = root / "requirements.txt"
    if req.exists():
        try:
            content = req.read_text().lower()
            if "fastapi" in content:
                frameworks.add("FastAPI")
            if "django" in content:
                frameworks.add("Django")
            if "flask" in content:
                frameworks.add("Flask")
        except Exception:
            pass

    return frameworks


def _parse_pom(pom_path: Path) -> list[str]:
    """Extract dependency names from pom.xml."""
    deps = []
    try:
        tree = ET.parse(pom_path)
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        for dep in tree.findall(".//m:dependency", ns):
            gid = dep.find("m:groupId", ns)
            aid = dep.find("m:artifactId", ns)
            if gid is not None and aid is not None:
                deps.append(f"{gid.text}:{aid.text}")
    except Exception:
        pass
    return deps


def _parse_package_json(pkg_path: Path) -> list[str]:
    """Extract dependency names from package.json."""
    deps = []
    try:
        data = json.loads(pkg_path.read_text())
        for key in ("dependencies", "devDependencies"):
            deps.extend(data.get(key, {}).keys())
    except Exception:
        pass
    return deps


def _parse_requirements(req_path: Path) -> list[str]:
    """Extract package names from requirements.txt."""
    deps = []
    try:
        for line in req_path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                name = line.split("==")[0].split(">=")[0].split("<=")[0].split("~=")[0].strip()
                if name:
                    deps.append(name)
    except Exception:
        pass
    return deps


def _scan_tree(root: Path, max_depth: int, current_depth: int = 0) -> list[str]:
    """Build a directory tree as a list of indented strings."""
    entries = []
    if current_depth >= max_depth:
        return entries

    try:
        items = sorted(root.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))
    except PermissionError:
        return entries

    for item in items:
        if item.name in IGNORE_DIRS:
            continue
        if item.name.startswith(".") and item.name not in (".github",):
            continue

        indent = "  " * current_depth
        if item.is_dir():
            entries.append(f"{indent}{item.name}/")
            entries.extend(_scan_tree(item, max_depth, current_depth + 1))
        else:
            entries.append(f"{indent}{item.name}")

    return entries


def _find_entry_points(root: Path) -> list[str]:
    """Find common entry point files."""
    candidates = [
        "src/main/java/**/Main.java",
        "src/main/java/**/*Application.java",
        "src/main/java/**/*CLI.java",
        "src/index.ts",
        "src/index.js",
        "src/main.ts",
        "src/main.py",
        "main.py",
        "app.py",
        "manage.py",
        "cmd/main.go",
        "src/main.rs",
    ]
    found = []
    for pattern in candidates:
        matches = list(root.glob(pattern))
        for m in matches:
            found.append(str(m.relative_to(root)))
    return found
