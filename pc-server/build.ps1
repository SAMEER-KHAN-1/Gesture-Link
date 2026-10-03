# Packages the server into dist/GestureLink.exe.
# Run from pc-server/ with the venv activated and requirements-build.txt installed:
#   pip install -r requirements-build.txt
#   ./build.ps1

python -m PyInstaller --clean gesturelink.spec
