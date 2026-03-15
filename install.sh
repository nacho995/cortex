#!/bin/bash
set -e

echo "Installing Cortex CLI..."

# Build fat JAR
echo "Building JAR..."
mvn -q package -DskipTests

# Find the JAR (non-original)
JAR=$(ls target/cortex-*.jar | grep -v original | head -1)
if [ -z "$JAR" ]; then
    echo "Error: Could not find built JAR"
    exit 1
fi

# Copy JAR to lib directory
INSTALL_DIR="$HOME/.cortex"
mkdir -p "$INSTALL_DIR"
cp "$JAR" "$INSTALL_DIR/cortex.jar"

# Create wrapper script
WRAPPER="$INSTALL_DIR/cortex"
cat > "$WRAPPER" << 'SCRIPT'
#!/bin/bash
# Cortex — AI Architecture Decision Engine
# Default server: https://cortex-ai.fly.dev (deployed)
# Override with: cortex --server http://localhost:8000 debate "topic"

java -jar "$HOME/.cortex/cortex.jar" "$@"
SCRIPT

chmod +x "$WRAPPER"

# Add to PATH if not already there
if ! echo "$PATH" | grep -q "$INSTALL_DIR"; then
    SHELL_RC="$HOME/.bashrc"
    if [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    fi
    echo "" >> "$SHELL_RC"
    echo "# Cortex CLI" >> "$SHELL_RC"
    echo "export PATH=\"\$HOME/.cortex:\$PATH\"" >> "$SHELL_RC"
    echo "Added Cortex to PATH in $SHELL_RC"
    echo "Run: source $SHELL_RC"
fi

echo ""
echo "✓ Cortex installed successfully!"
echo ""
echo "Usage:"
echo "  cortex                              Show banner"
echo "  cortex debate \"your topic\"           Start a debate"
echo "  cortex init /path/to/project        Scan a project"  
echo "  cortex review -p /path file.java    Code review"
echo "  cortex health -p /path              Health check"
echo ""
echo "The AI service is deployed at https://cortex-ai.fly.dev"
echo "Use --server http://localhost:8000 for local development"
