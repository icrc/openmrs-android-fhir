
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