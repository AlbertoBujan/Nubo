#!/bin/bash
#
# Publica una nueva versión de Nubo.
#
# No toca la versión: la lee de app/build.gradle.kts, así que primero hay que
# subir ahí versionName y versionCode. Empujar el tag dispara
# .github/workflows/release.yml, que compila, firma y publica el APK.

set -euo pipefail

echo "=== Script de Despliegue de Nubo ==="

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$REPO_ROOT"

GRADLE_FILE="app/build.gradle.kts"

if [ ! -f "$GRADLE_FILE" ]; then
  echo "ERROR: no se encuentra $GRADLE_FILE." >&2
  exit 1
fi

# Se extrae de versionName = "0.2.0". Con `set -e` esto no basta para abortar
# si no hay coincidencia, porque el fallo lo daría grep y el último comando de
# la tubería devolvería 0 igualmente, así que se comprueba a mano más abajo.
APP_VERSION=$(grep -E '^\s*versionName\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*"(.*)".*/\1/')

if [ -z "$APP_VERSION" ]; then
  echo "ERROR: no se pudo leer versionName de $GRADLE_FILE." >&2
  exit 1
fi

VERSION_TAG="v$APP_VERSION"

if git rev-parse -q --verify "refs/tags/$VERSION_TAG" >/dev/null; then
  echo "ERROR: el tag $VERSION_TAG ya existe." >&2
  echo "Sube versionName (y versionCode) en $GRADLE_FILE antes de desplegar." >&2
  exit 1
fi

# El versionCode tiene que subir en cada release o Android rechaza la
# actualización. La app Flutter publicó todas sus versiones con el mismo.
VERSION_CODE=$(grep -E '^\s*versionCode\s*=' "$GRADLE_FILE" | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
echo "Versión: $VERSION_TAG (versionCode $VERSION_CODE)"

echo "Ejecutando tests..."
./gradlew testDebugUnitTest

COMMIT_MSG="Deploy - $(date +'%Y-%m-%d %H:%M:%S')"

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
