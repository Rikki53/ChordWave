#!/usr/bin/env python3
"""Add dev-flavor client entries to app/google-services.json at CI time.

The Firebase project registers the production package
com.riki.chordwave. The dev flavor of this app builds as
com.riki.chordwave.dev (plus .benchmark build-type suffixes), and the
google-services Gradle plugin fails any variant whose package has no
client entry in the JSON. Copying the production client entry and
changing only the package name lets those variants build; Firebase
identifies the app by its app id, so FCM topic delivery works the same.

If the dev packages are ever registered in the Firebase console and the
CI secret is updated with the combined file, this script finds the
entries already present and changes nothing.

Never prints the file's contents.
"""

import copy
import json
import sys

PATH = "app/google-services.json"
PROD_PACKAGE = "com.riki.chordwave"
EXTRA_PACKAGES = (
    "com.riki.chordwave.dev",
    "com.riki.chordwave.dev.benchmark",
    "com.riki.chordwave.benchmark",
)


def package_of(client):
    return (
        client.get("client_info", {})
        .get("android_client_info", {})
        .get("package_name")
    )


def main():
    with open(PATH, encoding="utf-8") as f:
        cfg = json.load(f)
    clients = cfg.get("client", [])
    base = next((c for c in clients if package_of(c) == PROD_PACKAGE), None)
    if base is None:
        print("::error::no client entry for the production package in google-services.json")
        return 1
    known = {package_of(c) for c in clients}
    changed = False
    for pkg in EXTRA_PACKAGES:
        if pkg not in known:
            clone = copy.deepcopy(base)
            clone["client_info"]["android_client_info"]["package_name"] = pkg
            clients.append(clone)
            changed = True
    if changed:
        cfg["client"] = clients
        with open(PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f)
    print("google-services.json ready (dev client entries ensured)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
