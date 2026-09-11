"""
Capture pixel-perfect PNGs from the 4 HTML terminal views using Headless Chrome.
"""
import subprocess
import os
import shutil

SCREENSHOTS_DIR = os.path.join(os.path.dirname(__file__), '..', 'docs', 'screenshots')
ARTIFACTS_DIR = r'C:\Users\alexander\.gemini\antigravity-ide\brain\c44dba44-d2f9-407b-a59c-773a3075917a'

views = [
    ('01_tui_dashboard.html',    '01_tui_dashboard.png'),
    ('02_python_sdk.html',       '02_python_sdk.png'),
    ('03_benchmarks.html',       '03_benchmarks.png'),
    ('04_integration_test.html', '04_integration_test.png'),
]

# Locate Chrome
CHROME_PATHS = [
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe',
]
chrome = next((p for p in CHROME_PATHS if os.path.exists(p)), None)
if not chrome:
    raise RuntimeError('Chrome not found')

print(f'Using Chrome: {chrome}')

for html_file, png_file in views:
    html_path = os.path.abspath(os.path.join(SCREENSHOTS_DIR, html_file))
    png_path = os.path.abspath(os.path.join(SCREENSHOTS_DIR, png_file))
    artifact_path = os.path.join(ARTIFACTS_DIR, png_file)

    cmd = [
        chrome,
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        '--hide-scrollbars',
        '--disable-extensions',
        f'--screenshot={png_path}',
        '--window-size=980,700',
        '--force-device-scale-factor=2',
        html_path
    ]
    subprocess.run(cmd, check=True, capture_output=True)

    # Copy to artifacts
    shutil.copy2(png_path, artifact_path)
    print(f'OK {png_file}')

print('\nAll screenshots captured.')
