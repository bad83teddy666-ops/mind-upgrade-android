"""Exercise real Android permission, microphone and foreground-service lifecycle.

No synthetic wake injection: this does not verify spoken Bibi recognition.
"""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "pl.mindupgrade.android"
OUT = pathlib.Path("test-results")
OUT.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=45)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    xml = adb("shell", "cat", "/sdcard/window.xml")
    (OUT / "window.xml").write_text(xml)
    return list(ET.fromstring(xml).iter("node"))


def wait_text(text, timeout=75):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if any(text in n.get("text", "") for n in nodes()):
            print("PASS:", text, flush=True)
            return
        time.sleep(2)
    raise AssertionError("Missing UI state: " + text)


def tap(text):
    for n in nodes():
        if n.get("text", "").casefold() == text.casefold():
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            return
    raise AssertionError("Missing button: " + text)


try:
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    # Assign role on disposable emulator only, through the platform role service.
    adb("shell", "cmd", "role", "add-role-holder", "android.app.role.ASSISTANT", PKG)
    adb("shell", "am", "start", "-n", PKG + "/.BibiHomeActivity")
    wait_text("połączenie: gotowe")
    tap("Włącz Bibi")
    wait_text("Mikrofon: AKTYWNY")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(10)
    state = adb("shell", "dumpsys", "activity", "services", PKG)
    (OUT / "screen-off-services.txt").write_text(state)
    assert "BibiWakeService" in state and "isForeground=true" in state, state
    print("PASS: foreground service survives screen off", flush=True)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-n", PKG + "/.BibiHomeActivity")
    wait_text("Mikrofon: AKTYWNY")
    tap("Wyłącz Bibi")
    wait_text("Mikrofon: nie nasłuchuje")
    # Starting again catches stale model/recorder ownership bugs after stop.
    tap("Włącz Bibi")
    wait_text("Mikrofon: AKTYWNY")
    tap("Wyłącz Bibi")
    wait_text("Mikrofon: nie nasłuchuje")
    # Exercise the activity handoff after wake; this is not acoustic detection.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    adb("shell", "am", "start", "-n", PKG + "/.BibiHomeActivity", "--ez", "bibi_detected", "true")
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        activities=adb("shell","dumpsys","activity","activities")
        if any("com.android.chrome" in line for line in activities.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line):
            print("PASS: screen-off handoff opens browser without an extra app button",flush=True)
            break
        time.sleep(1)
    else:
        raise AssertionError("Wake handoff did not open browser: "+activities)
finally:
    (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "3000"))
    with (OUT / "screen.png").open("wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=30)
