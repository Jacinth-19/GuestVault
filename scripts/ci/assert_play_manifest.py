#!/usr/bin/env python3
"""Fails the build if a Play-policy-sensitive permission creeps back in.

Reads the merged release manifest (XML) or `aapt2 dump permissions` output and
asserts the shipping build only declares the permissions the listing documents:

    POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE,
    USE_BIOMETRIC, RECEIVE_BOOT_COMPLETED

Crucially it also asserts that the Device Administration receiver
(`BIND_DEVICE_ADMIN`) is gone: Play's Deceptive Device Settings policy forces a
store-description disclosure and an in-app prompt for it, and the feature buys
nothing a normal guest-mode app needs.
"""

from __future__ import annotations

import re
import sys

REQUIRED = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.USE_BIOMETRIC",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}

FORBIDDEN = {
    # Play: "Deceptive Device Settings" — needs a store-listing disclosure.
    "android.permission.BIND_DEVICE_ADMIN",
    # Play: special declaration + user-facing disclosure.
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    # Play: "all files access" declaration and manual review.
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    # Play: sensitive-permission declaration.
    "android.permission.READ_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    # Privacy promise: GuestVault must be unable to phone home.
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    # Play: usage-access declaration.
    "android.permission.PACKAGE_USAGE_STATS",
    # System-level, never needed by an allow-list launcher.
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.QUERY_ALL_PACKAGES",
}


def permissions_from_xml(text: str) -> set[str]:
    """Only real <uses-permission> declarations, not comments or metadata."""
    found: set[str] = set()
    for element in re.findall(r"<uses-permission[^>]*>", text):
        name = re.search(r'android:name="([^"]+)"', element)
        if name:
            found.add(name.group(1))
    return found


def main(path: str) -> int:
    text = open(path, encoding="utf-8", errors="replace").read()
    declared = permissions_from_xml(text) if text.lstrip().startswith("<") else set(
        re.findall(r"android\.permission\.[A-Z0-9_]+", text)
    )

    failures: list[str] = []

    for permission in sorted(FORBIDDEN):
        if permission in declared:
            failures.append(f"forbidden permission in the shipping manifest: {permission}")

    for permission in sorted(REQUIRED):
        if permission not in declared:
            failures.append(f"documented permission is missing: {permission}")

    # The receiver must be gone entirely, not merely unreferenced by a permission.
    if "device_admin" in text or "DeviceAdminReceiver" in text:
        failures.append("the manifest still references Device Administration")

    print("Declared permissions:")
    for permission in sorted(declared):
        marker = "forbidden" if permission in FORBIDDEN else "ok"
        print(f"  [{marker}] {permission}")

    if failures:
        print("\nPlay manifest check FAILED")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("\nPlay manifest check passed: no Device Admin, no internet, no sensitive data.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1] if len(sys.argv) > 1 else "permissions.txt"))
