@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
 echo Can cai JDK 17 tro len va them thu muc bin vao PATH.
 exit /b 1
)
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
rem Quote every path: the project can live in a folder containing spaces.
>build\sources.txt (for /r src\main\java %%F in (*.java) do echo "%%F")
javac -encoding UTF-8 --release 17 -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
jar --create --file build\pharmacy.jar --main-class vn.pharmacy.Main -C build\classes .
if errorlevel 1 exit /b 1
echo Build thanh cong. Chay run.bat
endlocal
