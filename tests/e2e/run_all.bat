@echo off
setlocal
cd /d "%~dp0\..\.."
echo ========================================================
echo Running BitChord iPadOS E2E Complete Test Suite
echo ========================================================
python tests\e2e\run_e2e_tests.py --tier all --report build\e2e_test_report.json %*
exit /b %ERRORLEVEL%
