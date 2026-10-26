#!/usr/bin/env bash
# Maps a host name to an address inside the Android emulator, so the browser of a single sign-on
# can reach a provider that announces its own host name (Keycloak: sso.example.org).
#
# Usage: tools/emulator-hosts.sh <host-name> <ip-address>
#   tools/emulator-hosts.sh sso.example.org 192.168.1.20
#
# It lays a hosts file over /system/etc/hosts (a bind mount), which needs no change to the system
# image but an emulator that allows `adb root` (not the "Google Play" ones). The mapping is gone
# when the emulator restarts: run this again. A real phone needs the name in its network's DNS or
# router instead (see docs/dev/sign-in-setup.md).
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <host-name> <ip-address>" >&2
  exit 1
fi
host="$1"
ip="$2"
export MSYS_NO_PATHCONV=1

adb root >/dev/null
adb wait-for-device
# What is there now (without an earlier line for this name), then the new one.
adb shell "umount /system/etc/hosts 2>/dev/null; grep -v ' ${host}\$' /system/etc/hosts > /data/local/tmp/hosts; echo '${ip} ${host}' >> /data/local/tmp/hosts; mount -o bind /data/local/tmp/hosts /system/etc/hosts; cat /system/etc/hosts"
