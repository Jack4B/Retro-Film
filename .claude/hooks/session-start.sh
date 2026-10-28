#!/bin/bash
# Start sesji w chmurze: plugin Ponytail oraz Graphify (mapa wiedzy projektu).
# settings.json deklaruje pluginy, a tutaj instalujemy je jawnie, bo sesja w chmurze nie robi tego sama.
# Tylko sesje zdalne; idempotentne (każdy krok jest bezpiecznie powtarzalny, błąd pluginu nie blokuje sesji).
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

export PATH="$HOME/.local/bin:$PATH"

if command -v claude >/dev/null 2>&1; then
  claude plugin marketplace add DietrichGebert/ponytail || true
  claude plugin install ponytail@ponytail || true
fi

if ! command -v graphify >/dev/null 2>&1; then
  if command -v uv >/dev/null 2>&1; then
    uv tool install graphifyy
  else
    pip install --user graphifyy
  fi
fi

# rejestruje skill /graphify w ~/.claude (bezpiecznie powtarzalne)
graphify install --platform claude >/dev/null
