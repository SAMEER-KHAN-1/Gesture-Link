# PyInstaller spec for packaging the server into a standalone GestureLink.exe.
# Build with: pyinstaller --clean gesturelink.spec  (run from this folder)

block_cipher = None

a = Analysis(
    ['run.py'],
    pathex=[],
    binaries=[],
    # toggle_radio.ps1 is loaded by path at runtime (server/actions/network.py), so it has
    # to be copied into the bundle at the same relative path or the frozen exe won't find it.
    datas=[('server/actions/scripts/toggle_radio.ps1', 'server/actions/scripts')],
    # uvicorn/websockets pick some of their implementations at runtime, which PyInstaller's
    # static analysis can miss - spelling them out here avoids a "module not found" at launch.
    hiddenimports=[
        'uvicorn.logging',
        'uvicorn.loops',
        'uvicorn.loops.auto',
        'uvicorn.protocols',
        'uvicorn.protocols.http',
        'uvicorn.protocols.http.auto',
        'uvicorn.protocols.websockets',
        'uvicorn.protocols.websockets.auto',
        'uvicorn.lifespan',
        'uvicorn.lifespan.on',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='GestureLink',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,  # tray app, no console window
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
