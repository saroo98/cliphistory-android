#!/usr/bin/env python3
"""Compatibility entry point for the reviewed release packager."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name("package-release.py")),run_name="__main__")
