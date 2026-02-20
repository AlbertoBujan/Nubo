#!/bin/bash

# Asegurar que el script se detenga si hay errores
set -e

# Deshabilitar el helper de credenciales problemático del editor y pedir autenticación por consola/sistema si fuera necesario
unset GIT_ASKPASS
unset SSH_ASKPASS

echo "=== Script de Despliegue de Nubo ==="

# 1. Mensaje de commit por defecto
COMMIT_MSG="Deploy - $(date +'%Y-%m-%d %H:%M:%S')"

# 2. Tomar la versión automáticamente del pubspec.yaml (Asegura formato "vX.X.X")
# Busca la línea de version: y extrae solo el número.
APP_VERSION=$(grep '^version: ' pubspec.yaml | sed 's/version: //')

# Añade la 'v' inicial requerida por el GitHub Action (ej. v0.1.0)
VERSION_TAG="v$APP_VERSION"

echo "Versión extraída de pubspec.yaml: $VERSION_TAG"

# Nos aseguramos de ejecutar los comandos de git en el directorio raíz del repositorio de GitHub correctamante.
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$REPO_ROOT"

echo "Añadiendo cambios al stage..."
git add .

echo "Haciendo commit..."
git commit -m "$COMMIT_MSG" || echo "No hay cambios nuevos para confirmar (commit)."

echo "Creando el tag $VERSION_TAG..."
git tag "$VERSION_TAG"

echo "Subiendo cambios a la rama activa..."
git push

echo "Subiendo el tag $VERSION_TAG a GitHub..."
git push origin "$VERSION_TAG"

echo ""
echo "¡Despliegue enviado a GitHub!"
echo "El GitHub Action de release (.github/workflows/release.yml) debería estar ejecutándose."
