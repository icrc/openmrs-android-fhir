#!/usr/bin/env bash
# Maps a host name to an address inside the Android emulator, so the browser of a single sign-on
# can reach a provider that announces its own host name (Keycloak: sso.example.org).
#
# Usage: tools/emulator-hosts.sh <host-name> <ip-address>
#   tools/emulator-hosts.sh sso.example.org 192.168.1.20
#
# Needs an emulator image that allows `adb root` (not the "Google Play" ones). A real phone needs
# the name in its network's DNS or the router instead (see docs/dev/sign-in-setup.md).
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <host-name> <ip-address>" >&2
  exit 1
fi
host="$1"
ip="$2"

adb root >/dev/null
adb remount >/dev/null 2>&1 || {
  # First time on this image: the verity check has to be switched off, then the emulator restarted.
  adb disable-verity
  echo "Restart the emulator (adb reboot) and run this again." >&2
  exit 2
}
export MSYS_NO_PATHCONV=1
# Replace an earlier line for the same name, then add the new one.
adb shell "grep -v ' ${host}\$' /system/etc/hosts > /data/local/tmp/hosts && echo '${ip} ${host}' >> /data/local/tmp/hosts && cp /data/local/tmp/hosts /system/etc/hosts"
adb shell cat /system/etc/hosts
