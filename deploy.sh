#!/bin/bash

# Asegurar que el script se detenga si hay errores
set -e

echo "=== Script de Despliegue de Nubo ==="

# 1. Pedir mensaje de commit (opcional)
read -p "Introduce el mensaje del commit (presiona enter para 'Actualización y Despliegue'): " COMMIT_MSG

if [ -z "$COMMIT_MSG" ]; then
  COMMIT_MSG="Actualización y Despliegue"
fi

# 2. Pedir la versión (tag) para la release (ejemplo: v1.0.0)
read -p "Introduce la versión para la release (ejemplo: v1.0.0, empezando por 'v'): " VERSION_TAG

if [[ ! "$VERSION_TAG" =~ ^v ]]; then
  echo "Error: La versión debe empezar por 'v' para lanzar el action release.yml (ejemplo: v1.0.0)"
  exit 1
fi

# El usuario hizo referencia a ../.github/workflows/release.yml
# Nos aseguramos de ejecutar los comandos de git en el directorio raíz del repositorio de GitHub correctamante.
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$REPO_ROOT"

# Si el repositorio principal (el que tiene .github) está un nivel arriba de Nubo y Nubo tiene su propio .git,
# es posible que el root detectado solo sea Nubo. Para curarnos en salud, buscamos .github:
if [ ! -d ".github" ] && [ -d "../.github" ]; then
    echo "Carpeta .github no encontrada en $REPO_ROOT, pero sí en el directorio superior."
    echo "Cambiando al directorio superior para ejecutar git push en el repositorio principal..."
    cd ..
fi

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
