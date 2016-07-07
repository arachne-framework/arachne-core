#!/usr/bin/env bash

set -e

if [ -e ~/.m2/repository/org/arachne-framework/arachne-buildtools/0.1.0 ];
then
    echo "Buildtools lib already installed..."
else
    echo "Installing buildtools..."
    git clone git@github.com:arachne-framework/arachne-buildtools.git
    cd arachne-buildtools
    git checkout 0.1.0
    boot build
fi
