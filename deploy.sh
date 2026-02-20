#!/bin/bash

# Asegurar que el script se detenga si hay errores
set -e

echo "=== Script de Despliegue de Nubo ==="

# 1. Mensaje de commit por defecto
COMMIT_MSG="Deploy - $(date +'%Y-%m-%d %H:%M:%S')"

# 2. Pedir la versión (tag) para la release (ejemplo: v1.0.0)
read -p "Introduce la versión para la release (ejemplo: v1.0.0, empezando por 'v'): " VERSION_TAG

if [[ ! "$VERSION_TAG" =~ ^v ]]; then
  echo "Error: La versión debe empezar por 'v' para lanzar el action release.yml (ejemplo: v1.0.0)"
  exit 1
fi

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
