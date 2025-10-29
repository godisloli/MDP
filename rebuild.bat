@echo off
echo Cleaning project...
call gradlew.bat clean

echo Building project...
call gradlew.bat assembleDebug

echo Done!
pause

