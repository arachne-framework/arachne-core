#!/usr/bin/env bash

if [ -e ~/.m2/repository/org/arachne-framework/buildtools/0.1.0 ];
then
    echo "Buildtools lib already installed..."
else
    echo "Installing buildtools..."
fi
