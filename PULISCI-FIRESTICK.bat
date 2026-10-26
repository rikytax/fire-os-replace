@echo off
rem Windows: doppio clic. Pulisce la Fire Stick e installa FireHome. Serve Python (lo installa winget se manca).
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>nul && (py -3 scripts\firestick.py %* & goto :fine)
where python >nul 2>nul && (python scripts\firestick.py %* & goto :fine)
echo Python non e' installato: provo a installarlo con winget...
winget install -e --id Python.Python.3.12 --accept-package-agreements --accept-source-agreements
echo.
echo Fatto. CHIUDI questa finestra e fai di nuovo doppio clic su PULISCI-FIRESTICK.bat
:fine
echo.
pause
