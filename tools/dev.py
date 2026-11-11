#!/usr/bin/env python3
"""Developer helper for this repository. Same commands on Windows, macOS and Linux.

    python tools/dev.py doctor                    check the machine (JDK, Android SDK, adb, Node, devices)
    python tools/dev.py install                   build the debug app and install it on every connected device
    python tools/dev.py install --clean           ... and erase the app data (a first start)
    python tools/dev.py logcat                    the log of the running app only
    python tools/dev.py reverse                   let a device reach servers on this machine through USB
    python tools/dev.py hosts sso.example.org 192.168.1.20
                                                  map a host name inside an emulator (for single sign-on)
    python tools/dev.py adb-restart               restart the adb server

On Windows use `py -3 tools/dev.py ...` when `python` is not on the PATH. Python 3.7 or newer, nothing to
install: only the standard library is used. `python tools/dev.py <command> --help` shows the options.
"""
import argparse
import glob
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IS_WINDOWS = os.name == "nt"
MODULES = ("app", "dev3-app")
DEBUG_PACKAGES = {
    "app": "org.openmrs.android.fhir.app.debug",
    "dev3-app": "org.openmrs.android.fhir.dev3_app.debug",
}
JDK_MIN = 17
JDK_TESTED_MAX = 21

X86_64_INIT_SCRIPT = """\
allprojects {
  afterEvaluate { p ->
    if (p.plugins.hasPlugin('com.android.application')) {
      p.android.splits.abi { include 'x86_64' }
    }
  }
}
"""


class Fail(Exception):
    """An expected problem: printed as one line, exit code 1."""


# ----------------------------------------------------------------------------------------------
# Finding the tools of the machine
# ----------------------------------------------------------------------------------------------


def run(cmd, check=True, cwd=None, env=None, quiet=False):
    """Runs a command, returns (code, stdout). Output is shown unless quiet."""
    try:
        proc = subprocess.run(
            cmd,
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE if quiet else None,
            stderr=subprocess.STDOUT if quiet else None,
            universal_newlines=True,
        )
    except OSError as error:
        raise Fail("cannot run %s: %s" % (cmd[0], error))
    if check and proc.returncode != 0:
        raise Fail("%s failed (exit code %d)" % (" ".join(cmd[:3]), proc.returncode))
    return proc.returncode, (proc.stdout or "")


def read_properties(path):
    values = {}
    try:
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, _, value = line.partition("=")
                    values[key.strip()] = value.strip().replace("\\:", ":").replace("\\\\", "\\")
    except OSError:
        pass
    return values


def android_sdk():
    """The Android SDK folder: local.properties, then the environment, then the usual places."""
    candidates = [
        read_properties(os.path.join(ROOT, "local.properties")).get("sdk.dir"),
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
    ]
    home = os.path.expanduser("~")
    if IS_WINDOWS:
        candidates.append(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"))
    elif sys.platform == "darwin":
        candidates.append(os.path.join(home, "Library", "Android", "sdk"))
    else:
        candidates.append(os.path.join(home, "Android", "Sdk"))
    for candidate in candidates:
        if candidate and os.path.isdir(candidate):
            return candidate
    return None


def find_adb():
    sdk = android_sdk()
    if sdk:
        local = os.path.join(sdk, "platform-tools", "adb.exe" if IS_WINDOWS else "adb")
        if os.path.isfile(local):
            return local
    return shutil.which("adb")


def require_adb():
    adb = find_adb()
    if not adb:
        raise Fail(
            "adb not found. Install the Android SDK platform-tools (Android Studio > SDK Manager) "
            "or put them on the PATH. Run `python tools/dev.py doctor`."
        )
    return adb


def java_major(java_cmd):
    """The major version of a java executable, or None."""
    try:
        proc = subprocess.run(
            [java_cmd, "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
        )
    except OSError:
        return None
    match = re.search(r'version "(\d+)(?:\.(\d+))?', proc.stdout)
    if not match:
        return None
    major = int(match.group(1))
    return int(match.group(2)) if major == 1 and match.group(2) else major


def java_command():
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = os.path.join(home, "bin", "java.exe" if IS_WINDOWS else "java")
        if os.path.isfile(candidate):
            return candidate
    return shutil.which("java")


def gradle_command():
    return [os.path.join(ROOT, "gradlew.bat" if IS_WINDOWS else "gradlew")]


def gradle_env():
    env = dict(os.environ)
    # "BindException: Address already in use" on some Windows machines is cured by the IPv4 stack.
    if IS_WINDOWS and "GRADLE_OPTS" not in env:
        env["GRADLE_OPTS"] = "-Djava.net.preferIPv4Stack=true"
    return env


# ----------------------------------------------------------------------------------------------
# Devices
# ----------------------------------------------------------------------------------------------


def adb_devices(adb):
    """Serial numbers of the devices that are ready."""
    _, out = run([adb, "devices"], quiet=True)
    devices = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def adb_getprop(adb, serial, prop):
    _, out = run([adb, "-s", serial, "shell", "getprop", prop], check=False, quiet=True)
    return out.strip()


def pick_devices(adb, serial):
    if serial:
        return [serial]
    devices = adb_devices(adb)
    if not devices:
        raise Fail("no device connected: start an emulator or plug a phone in (`adb devices`)")
    return devices


# ----------------------------------------------------------------------------------------------
# doctor
# ----------------------------------------------------------------------------------------------


def cmd_doctor(_args):
    problems = []

    def report(ok, label, detail, fix=None, required=True):
        mark = "ok  " if ok else ("MISS" if required else "skip")
        print("[%s] %-18s %s" % (mark, label, detail))
        if not ok:
            if fix:
                print("       -> %s" % fix)
            if required:
                problems.append(label)

    print("Machine: %s %s, Python %s\n" % (platform.system(), platform.release(), platform.python_version()))

    java = java_command()
    major = java_major(java) if java else None
    if major is None:
        report(
            False,
            "JDK",
            "not found",
            "install a JDK 17 (https://adoptium.net) and set JAVA_HOME; Android Studio ships one",
        )
    elif major < JDK_MIN:
        report(
            False,
            "JDK",
            "%s is version %d" % (java, major),
            "Gradle needs a JDK %d or newer to start; point JAVA_HOME at one" % JDK_MIN,
        )
    elif major > JDK_TESTED_MAX:
        report(True, "JDK", "%s (version %d: newer than the %d this project is tested with)" % (java, major, JDK_TESTED_MAX))
        print("       -> if Gradle fails to start, point JAVA_HOME at a JDK %d" % JDK_MIN)
    else:
        report(True, "JDK", "%s (version %d)" % (java, major))

    sdk = android_sdk()
    report(
        sdk is not None,
        "Android SDK",
        sdk or "not found",
        "install Android Studio, or copy local.properties.default to local.properties and set sdk.dir",
    )
    if sdk:
        platform_dir = os.path.join(sdk, "platforms")
        platforms = sorted(os.listdir(platform_dir)) if os.path.isdir(platform_dir) else []
        report(
            bool(platforms),
            "SDK platforms",
            ", ".join(platforms) or "none installed",
            "Gradle downloads what is missing once the licenses are accepted: `sdkmanager --licenses`",
            required=False,
        )
    has_properties = os.path.isfile(os.path.join(ROOT, "local.properties"))
    report(
        has_properties or bool(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")),
        "local.properties",
        "present" if has_properties else "absent (ANDROID_HOME is used if set)",
        "copy local.properties.default to local.properties and set sdk.dir",
    )

    adb = find_adb()
    report(adb is not None, "adb", adb or "not found", "install the SDK platform-tools")
    if adb:
        devices = adb_devices(adb)
        report(
            bool(devices),
            "device",
            ", ".join(devices) or "none connected",
            "start an emulator (Android Studio > Device Manager) or plug a phone in with USB debugging on",
            required=False,
        )

    node = shutil.which("node")
    npm = shutil.which("npm")
    report(
        bool(node and npm),
        "Node.js + npm",
        "found" if node and npm else "not found",
        "only the XML formatting check needs it (spotlessXmlCheck); install Node 18+ or run "
        "`./gradlew spotlessCheck -x spotlessXml`",
        required=False,
    )
    report(
        shutil.which("git") is not None,
        "git",
        shutil.which("git") or "not found",
        "install git",
    )
    docker = shutil.which("docker")
    report(
        docker is not None,
        "Docker",
        docker or "not found",
        "only needed to run a local OpenMRS + Keycloak (docker-compose.yml)",
        required=False,
    )

    print()
    if problems:
        print("Missing: %s. Fix the lines marked MISS, then run doctor again." % ", ".join(problems))
        return 1
    print("Ready. Next: `python tools/dev.py install` or open the folder in Android Studio.")
    return 0


# ----------------------------------------------------------------------------------------------
# install
# ----------------------------------------------------------------------------------------------


def package_of_apk(apk, sdk):
    """The application id of an APK, from aapt2 when the build tools are there."""
    if not sdk:
        return None
    exe = "aapt2.exe" if IS_WINDOWS else "aapt2"
    found = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", exe)))
    if found:
        code, out = run([found[-1], "dump", "packagename", apk], check=False, quiet=True)
        if code == 0 and out.strip():
            return out.strip().splitlines()[0]
    return None


def built_apks(module):
    """The APK files the last build wrote (older builds can leave others in the folder)."""
    apk_dir = os.path.join(ROOT, module, "build", "outputs", "apk", "debug")
    metadata = os.path.join(apk_dir, "output-metadata.json")
    try:
        with open(metadata, encoding="utf-8") as handle:
            text = handle.read()
    except OSError:
        raise Fail("no build output found in %s" % apk_dir)
    names = re.findall(r'"outputFile"\s*:\s*"([^"]+)"', text)
    return apk_dir, names


def cmd_install(args):
    adb = require_adb()
    devices = pick_devices(adb, args.serial)
    module = args.module

    gradle_args = []
    temp_dir = None
    if any(adb_getprop(adb, d, "ro.product.cpu.abi") == "x86_64" for d in devices):
        # An emulator on a PC is x86_64, which the abi list of app/build.gradle.kts leaves out; ask
        # Gradle for that APK for this build only (no file of the project changes).
        temp_dir = tempfile.mkdtemp(prefix="dev-py-")
        init_file = os.path.join(temp_dir, "x86_64.gradle")
        with open(init_file, "w") as handle:
            handle.write(X86_64_INIT_SCRIPT)
        gradle_args = ["-I", init_file]

    try:
        print("== Building :%s:assembleDebug" % module)
        run(gradle_command() + [":%s:assembleDebug" % module, "-q"] + gradle_args, cwd=ROOT, env=gradle_env())
    finally:
        if temp_dir:
            shutil.rmtree(temp_dir, ignore_errors=True)

    devices = pick_devices(adb, args.serial)  # adb may have been restarted meanwhile
    apk_dir, names = built_apks(module)
    sdk = android_sdk()

    for device in devices:
        abis = adb_getprop(adb, device, "ro.product.cpu.abilist").split(",")
        apk = None
        for abi in abis:
            if "%s-%s-debug.apk" % (module, abi) in names:
                apk = os.path.join(apk_dir, "%s-%s-debug.apk" % (module, abi))
                break
        if apk is None and "%s-debug.apk" % module in names:  # a build that is not split by processor
            apk = os.path.join(apk_dir, "%s-debug.apk" % module)
        if apk is None:
            raise Fail("%s: no APK in %s for its processors (%s)" % (device, apk_dir, ", ".join(abis)))

        package = package_of_apk(apk, sdk) or DEBUG_PACKAGES[module]
        print("== %s: installing %s (%s)" % (device, os.path.basename(apk), package))
        _, out = run([adb, "-s", device, "install", "-r", apk], quiet=True)
        print("   " + (out.strip().splitlines() or ["?"])[-1])
        if args.clean:
            print("== %s: erasing the app data" % device)
            run([adb, "-s", device, "shell", "pm", "clear", package], quiet=True)
        if not args.no_launch:
            code, _ = run(
                [adb, "-s", device, "shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1"],
                check=False,
                quiet=True,
            )
            if code != 0:
                print("   (could not open the app: is the screen unlocked?)")
    print("== Done")
    return 0


# ----------------------------------------------------------------------------------------------
# logcat, reverse, hosts, adb-restart
# ----------------------------------------------------------------------------------------------


def cmd_logcat(args):
    adb = require_adb()
    devices = pick_devices(adb, args.serial)
    device = devices[0]
    if len(devices) > 1 and not args.serial:
        raise Fail("several devices connected (%s): pass --serial" % ", ".join(devices))
    package = DEBUG_PACKAGES[args.module]
    _, out = run([adb, "-s", device, "shell", "pidof", package], check=False, quiet=True)
    pids = out.split()
    if not pids:
        raise Fail("%s is not running on %s: open the app first" % (package, device))
    command = [adb, "-s", device, "logcat", "--pid=%s" % pids[0]]
    if args.clear:
        run([adb, "-s", device, "logcat", "-c"], quiet=True)
    try:
        return subprocess.call(command)
    except KeyboardInterrupt:
        return 0


def cmd_reverse(args):
    adb = require_adb()
    devices = pick_devices(adb, args.serial)
    for device in devices:
        for port in args.ports:
            run([adb, "-s", device, "reverse", "tcp:%s" % port, "tcp:%s" % port], quiet=True)
        _, out = run([adb, "-s", device, "reverse", "--list"], quiet=True)
        print("== %s\n%s" % (device, out.strip() or "(no reverse rule)"))
    print("The device now reaches 127.0.0.1:<port> as this machine's <port>.")
    return 0


def cmd_hosts(args):
    """Lays a hosts file over /system/etc/hosts of an emulator (a bind mount; gone at reboot)."""
    if not re.match(r"^[A-Za-z0-9.-]+$", args.host):
        raise Fail("not a host name: %s" % args.host)
    if not re.match(r"^[0-9a-fA-F:.]+$", args.ip):
        raise Fail("not an IP address: %s" % args.ip)
    adb = require_adb()
    devices = pick_devices(adb, args.serial)
    if len(devices) > 1 and not args.serial:
        raise Fail("several devices connected (%s): pass --serial" % ", ".join(devices))
    device = devices[0]
    run([adb, "-s", device, "root"], check=False, quiet=True)
    run([adb, "-s", device, "wait-for-device"], quiet=True)
    script = (
        "umount /system/etc/hosts 2>/dev/null; "
        "grep -v ' {host}$' /system/etc/hosts > /data/local/tmp/hosts; "
        "echo '{ip} {host}' >> /data/local/tmp/hosts; "
        "mount -o bind /data/local/tmp/hosts /system/etc/hosts; "
        "cat /system/etc/hosts"
    ).format(host=args.host, ip=args.ip)
    code, out = run([adb, "-s", device, "shell", script], check=False, quiet=True)
    print(out.strip())
    if code != 0 or args.host not in out:
        raise Fail(
            "the hosts file was not changed: this needs an emulator image that allows `adb root` "
            "(not a 'Google Play' image). See docs/dev/emulator-hosts-file.md"
        )
    print("\n%s -> %s on %s (until the emulator restarts)" % (args.host, args.ip, device))
    return 0


def cmd_adb_restart(_args):
    adb = require_adb()
    run([adb, "kill-server"], check=False, quiet=True)
    run([adb, "start-server"], quiet=True)
    for _ in range(10):  # devices answer "offline" for a moment after the restart
        _, out = run([adb, "devices"], quiet=True)
        if "offline" not in out:
            break
        time.sleep(1)
    print(out.strip())
    return 0


# ----------------------------------------------------------------------------------------------


def build_parser():
    parser = argparse.ArgumentParser(
        prog="dev.py",
        description="Developer helper: same commands on Windows, macOS and Linux.",
    )
    sub = parser.add_subparsers(dest="command", metavar="<command>")
    sub.required = True

    p = sub.add_parser("doctor", help="check the JDK, Android SDK, adb, Node and the devices")
    p.set_defaults(func=cmd_doctor)

    p = sub.add_parser("install", help="build the debug app and install it on the connected devices")
    p.add_argument("--module", choices=MODULES, default="app", help="app (default) or dev3-app")
    p.add_argument("--serial", "-s", help="only this device (see `adb devices`)")
    p.add_argument("--clean", action="store_true", help="erase the app data after installing (a first start)")
    p.add_argument("--no-launch", action="store_true", help="do not open the app")
    p.set_defaults(func=cmd_install)

    p = sub.add_parser("logcat", help="the log of the running app only")
    p.add_argument("--module", choices=MODULES, default="app", help="which debug build to follow (default: app)")
    p.add_argument("--serial", "-s")
    p.add_argument("--clear", action="store_true", help="empty the log buffer first")
    p.set_defaults(func=cmd_logcat)

    p = sub.add_parser("reverse", help="forward ports of the device to this machine through USB (adb reverse)")
    p.add_argument("ports", nargs="*", default=["8080", "8081"], help="default: 8080 8081")
    p.add_argument("--serial", "-s")
    p.set_defaults(func=cmd_reverse)

    p = sub.add_parser("hosts", help="map a host name to an IP inside an emulator (needs `adb root`)")
    p.add_argument("host", help="for example sso.example.org")
    p.add_argument("ip", help="for example 192.168.1.20")
    p.add_argument("--serial", "-s")
    p.set_defaults(func=cmd_hosts)

    p = sub.add_parser("adb-restart", help="restart the adb server and list the devices")
    p.set_defaults(func=cmd_adb_restart)
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except Fail as error:
        print("error: %s" % error, file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
