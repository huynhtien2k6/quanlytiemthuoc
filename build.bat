@echo off
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 17 -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
jar --create --file build\pharmacy.jar --main-class vn.pharmacy.Main -C build\classes .
if errorlevel 1 exit /b 1
echo Build thanh cong. Chay run.bat
