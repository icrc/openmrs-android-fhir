adb logcat | grep -F "`adb shell ps | grep org.openmrs | tr -s [:space:] ' ' | cut -d' ' -f2`"
