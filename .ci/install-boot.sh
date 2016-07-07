#!/usr/bin/env bash

if which boot; then
    echo "boot already installed"
else
    echo "installing boot..."
    cd /usr/local/bin
    curl -fsSLo boot https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh
    chmod 755 boot
fi
