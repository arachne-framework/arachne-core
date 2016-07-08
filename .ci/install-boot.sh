#!/usr/bin/env bash

set -e

if which boot; then
    echo "boot already installed"
    exit 0
fi

echo "installing boot..."
cd ~/bin
curl -fsSLo boot https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh
chmod 755 boot
boot -h
