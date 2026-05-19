set dotenv-load := true
set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

default:
    @just --list

# Run all tests
test:
    ./gradlew test --no-daemon -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process

# Run all tests, ignoring cache
test-rerun:
    ./gradlew test --rerun --no-daemon -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process

# Run a single test class (e.g. just test-class NextflowSymbolCategoryTest)
test-class name:
    ./gradlew test --rerun --tests '*{{name}}' --no-daemon -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process

# Run a single test method (e.g. just test-method 'NextflowSymbolCategoryTest.categorizes process by name')
test-method name:
    ./gradlew test --rerun --tests '*{{name}}' --no-daemon -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process

# Build the plugin ZIP
build:
    ./gradlew buildPlugin

# Run the IDE with the plugin loaded
run:
    ./gradlew runIde --args="/home/forest/IdeaProjects/jetbrains-language-nextflow/src/test/resources/fixtures/parity"

# Verify plugin compatibility
verify:
    ./gradlew verifyPlugin

# Print the current plugin version
version:
    @awk -F'=' '/^[[:space:]]*version[[:space:]]*=/{gsub(/[[:space:]]/, "", $2); print $2; exit}' gradle.properties

# Bump the semantic version in gradle.properties: just bump major|minor|patch
bump part:
    @case "{{part}}" in major|minor|patch) ;; *) echo "Usage: just bump major|minor|patch" >&2; exit 2;; esac
    @old="$(just version)"; \
    if [[ ! "$old" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then \
        echo "Version '$old' is not plain semver MAJOR.MINOR.PATCH" >&2; \
        exit 1; \
    fi; \
    IFS=. read -r major minor patch <<< "$old"; \
    case "{{part}}" in \
        major) major=$((major + 1)); minor=0; patch=0 ;; \
        minor) minor=$((minor + 1)); patch=0 ;; \
        patch) patch=$((patch + 1)) ;; \
    esac; \
    new="$major.$minor.$patch"; \
    perl -0pi -e "s/(^\\s*version[ \\t]*=[ \\t]*)\\Q$old\\E[ \\t]*$/\${1}$new/m" gradle.properties; \
    echo "$old -> $new"

# Bump the major version
bump-major:
    just bump major

# Bump the minor version
bump-minor:
    just bump minor

# Bump the patch version
bump-patch:
    just bump patch

# Create a local .env from .env.example
env-init:
    @if [ -f .env ]; then \
        echo ".env already exists"; \
    else \
        cp .env.example .env; \
        echo "Created .env; fill in the JetBrains Marketplace publishing token."; \
    fi

# Check that the publish environment is populated
env-check:
    @missing=0; \
    for name in PUBLISH_TOKEN; do \
        if [ -z "${!name:-}" ]; then \
            echo "Missing $name"; \
            missing=1; \
        fi; \
    done; \
    exit "$missing"

# Publish to JetBrains Marketplace
publish: env-check verify build
    ./gradlew publishPlugin

# Bump, test, build, and publish: just release major|minor|patch
release part:
    just bump {{part}}
    just test
    just publish

# Clean build artifacts
clean:
    ./gradlew clean
