# Changing the hosts file of an emulator by hand

`tools/emulator-hosts.sh` does this for one name without changing the system image (see
`sign-in-setup.md`). This is the long way, for an image that needs a system-wide hosts file:

```bash
adb root
adb shell avbctl disable-verification
adb disable-verity
adb reboot
adb root && adb remount
adb push hosts /etc
adb reboot
```

Instead of `adb push` you can use the Device Explorer of IntelliJ.
