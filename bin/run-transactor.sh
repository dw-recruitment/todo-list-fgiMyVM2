#/usr/bin/env bash
PROJECT_ROOT=$(git rev-parse --show-toplevel)
TRANSACTOR_BIN="$PROJECT_ROOT/transactor/datomic-free-0.9.5350/bin/transactor"
if [ ! -f "$TRANSACTOR_BIN" ]; then
    echo "Can't find transactor."
    echo "Try bin/download-transactor.sh or check transactor/setup.md for instructions."
    exit 1
fi
"$TRANSACTOR_BIN" ../free-transactor.properties