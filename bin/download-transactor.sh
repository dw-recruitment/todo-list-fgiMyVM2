#/usr/bin/env bash
PROJECT_ROOT=$(git rev-parse --show-toplevel)
cd "$PROJECT_ROOT/transactor"
if [ -d "datomic-free-0.9.5350" ]; then
    echo "datomic-free-0.9.5350 directory already exists. Not attempting download."
    exit 1
fi
if [ -f "datomic-free-0.9.5350.zip" ]; then
    echo "datomic-free-0.9.5350.zip already exists."
    echo "Unzip it manually or delete it and try again."
    exit 1
fi
curl -L "https://my.datomic.com/downloads/free/0.9.5350" -o datomic-free-0.9.5350.zip &&
unzip datomic-free-0.9.5350.zip &&
rm datomic-free-0.9.5350.zip