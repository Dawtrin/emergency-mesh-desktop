@echo off
chcp 65001 >nul 2>&1
title 📱 MOBILE EMULATOR — Emergency Mesh Rescue
color 0A

echo.
echo ==============================================================
echo  📱 MOBILE EMULATOR — KIỂM THỬ PHẢN HỒI HAI CHIỀU (TCP 8888)
echo ==============================================================
echo.
echo [*] Đang chạy mobile_emulator.py...
echo.

python "%~dp0mobile_emulator.py"

echo.
pause
