#!/bin/bash
action=${1:-build}
if [[ $action = 'serve' ]]; then
  docker run --rm -it -p 8001:8000 -v ${PWD}:/docs squidfunk/mkdocs-material
else
  docker run --rm -it -v ${PWD}:/docs squidfunk/mkdocs-material ${action}
fi
