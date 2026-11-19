@echo off
rem Stops DevFlow AI. Your kits and history are kept.
cd /d "%~dp0"
docker compose down
echo DevFlow AI stopped.
pause
