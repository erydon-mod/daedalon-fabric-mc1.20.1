@echo off
setlocal
pushd "%~dp0" || exit /b 1
call "%~dp0gradlew.bat" --no-daemon -I "%~dp0gradle\with-erydon.init.gradle" runClientWithErydon %*
set "DAEDALON_COMBINED_EXIT=%ERRORLEVEL%"
popd
exit /b %DAEDALON_COMBINED_EXIT%
