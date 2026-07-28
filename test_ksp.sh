#!/bin/bash
curl -s "https://api.github.com/repos/google/ksp/releases?per_page=10" | grep tag_name
