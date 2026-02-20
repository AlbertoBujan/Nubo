#!/bin/bash

# 1. Incrementar versionCode automáticamente en el build.gradle.kts
# Busca la línea 'versionCode = X' y le suma 1
sed -i 's/versionCode = \([0-9]*\)/echo "versionCode = $((\1 + 1))"/e' app/build.gradle.kts

# 2. Extraer la versión (versionName) de la línea 18
VERSION_RAW=$(grep "versionName =" app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
VERSION="v$VERSION_RAW"

echo "✅ versionCode incrementado automáticamente."
echo "🔎 Versión detectada: $VERSION"

# 3. Git push y Lanzamiento de Tag
git add .
git commit -m "Release $VERSION"
git push origin main

# Limpieza y subida de Tag
git tag -d $VERSION 2>/dev/null
git push --delete origin $VERSION 2>/dev/null
git tag $VERSION
git push origin $VERSION

echo "🚀 ¡Todo en marcha! Revisa la pestaña Actions en GitHub."