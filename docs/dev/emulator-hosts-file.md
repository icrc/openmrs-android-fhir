# Changing the hosts file of an emulator by hand

The script `tools/emulator-hosts.sh` does this for one name without changing the system image (see
`sign-in-setup.md`). The long way, for an image where a system-wide hosts file is wanted:


To be able to change `/etc/hosts`:

```bash
adb root
> adb shell avbctl disable-verification
Successfully disabled verification. Reboot the device for changes to take effect.
> adb disable-verity
using overlayfs
Successfully disabled verity
Now reboot your device for settings to take effect
> adb reboot
> adb root && adb remount
remount succeeded
> adb push hosts /etc
> adb reboot
```


instead of `adb push` you can use `Device Explorer` view from IntelliJ.