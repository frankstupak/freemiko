/*
 * neuterd — FreeMiko reboot-watchdog neuter (arm64 Android, freestanding, no libc).
 *
 * PROBLEM. ServiceExam's SecurityMonitor (com.example.root.serviceexam) greps `ps` for the string
 * "adbd" every ~2s and, if it finds it, execs `su -> reboot`, which freezes a repurposed unit in a
 * reboot loop. Shadowing /system/bin/reboot with a no-op defuses that. The catch — proven on this
 * hardware — is that a bind-mount performed from inside an ordinary app process lands in the app's
 * OWN mount namespace, which ServiceExam (a separate zygote child) cannot see. The mount must be
 * made in init's GLOBAL mount namespace (PID 1) to be visible to the watchdog.
 *
 * APPROACH (credit: the OpenMiko / miko3-adb-boot-agent community documented this technique; this
 * is an independent, from-scratch implementation for FreeMiko, GPL-3.0). This daemon:
 *   1. setns() into /proc/1/ns/mnt so every subsequent mount is global.
 *   2. loops forever: whenever /system/bin/reboot is still the real ELF (shadow missing, e.g. just
 *      after boot), write a no-op shell script and bind-mount it over /system/bin/reboot.
 *   Self-healing: if the shadow is ever wiped, it is re-applied within a few seconds.
 *
 * Device context: Android 9 / MT8167 (arm64), verity ENFORCING (/system read-only), SELinux
 * Permissive (ro.secure=0) so setns+mount from a root context are unrestricted. Run as root; the
 * FreeMiko launcher execs it via /system/bin/su (which on this ROM takes its script on stdin).
 *
 * No NDK / libc: raw aarch64 Linux syscalls only. Build: see build-neuterd.sh.
 */

#define SYS_openat     56
#define SYS_close      57
#define SYS_read       63
#define SYS_write      64
#define SYS_mount      40
#define SYS_mkdirat    34
#define SYS_setns      268
#define SYS_fchmodat   53
#define SYS_nanosleep  101
#define SYS_exit_group 94

#define AT_FDCWD    (-100)
#define O_RDONLY    0
#define O_WRONLY    1
#define O_CREAT     0100
#define O_TRUNC     01000
#define CLONE_NEWNS 0x00020000
#define MS_BIND     4096

/* One raw syscall: x8=nr, x0..x4=args, result in x0. */
static long sys(long nr, long a, long b, long c, long d, long e) {
    register long x8 __asm__("x8") = nr;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    register long x3 __asm__("x3") = d;
    register long x4 __asm__("x4") = e;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4)
                     : "memory", "cc");
    return x0;
}

static unsigned long slen(const char *s) {
    unsigned long n = 0;
    while (s[n]) n++;
    return n;
}

static const char DIR[]  = "/data/local/tmp/freemiko";
static const char NR[]   = "/data/local/tmp/freemiko/nr";
static const char REB[]  = "/system/bin/reboot";
static const char NSP[]  = "/proc/1/ns/mnt";
static const char NOOP[] = "#!/system/bin/sh\nexit 0\n";

/* Write the no-op reboot script and make it executable. */
static void write_noop(void) {
    sys(SYS_mkdirat, AT_FDCWD, (long)DIR, 0755, 0, 0);   /* ok if it already exists */
    long fd = sys(SYS_openat, AT_FDCWD, (long)NR, O_WRONLY | O_CREAT | O_TRUNC, 0755, 0);
    if (fd < 0) return;
    sys(SYS_write, fd, (long)NOOP, (long)slen(NOOP), 0, 0);
    sys(SYS_close, fd, 0, 0, 0, 0);
    sys(SYS_fchmodat, AT_FDCWD, (long)NR, 0755, 0, 0);
}

/* True if /system/bin/reboot is currently the REAL binary (ELF magic) rather than our no-op. */
static int reboot_is_real(void) {
    long fd = sys(SYS_openat, AT_FDCWD, (long)REB, O_RDONLY, 0, 0);
    if (fd < 0) return 1;                 /* can't read -> assume it needs neutering (fail safe) */
    char buf[4] = {0, 0, 0, 0};
    long r = sys(SYS_read, fd, (long)buf, 4, 0, 0);
    sys(SYS_close, fd, 0, 0, 0, 0);
    if (r < 4) return 1;
    return (unsigned char)buf[0] == 0x7f; /* ELF starts 0x7f 'E' 'L' 'F'; our no-op starts '#' */
}

void _start(void) {
    /* 1) enter init's (global) mount namespace, once. */
    long nsfd = sys(SYS_openat, AT_FDCWD, (long)NSP, O_RDONLY, 0, 0);
    if (nsfd >= 0) {
        sys(SYS_setns, nsfd, CLONE_NEWNS, 0, 0, 0);
        sys(SYS_close, nsfd, 0, 0, 0, 0);
    }

    /* 2) self-heal loop: keep /system/bin/reboot shadowed by the no-op, forever. */
    struct { long sec; long nsec; } ts = {5, 0};
    for (;;) {
        if (reboot_is_real()) {
            write_noop();
            /* bind-mount the no-op over the real reboot, in the GLOBAL ns joined above.
             * Guarded by reboot_is_real() so we never stack duplicate mounts. */
            sys(SYS_mount, (long)NR, (long)REB, 0 /*fstype*/, MS_BIND, 0 /*data*/);
        }
        sys(SYS_nanosleep, (long)&ts, 0, 0, 0, 0);
    }

    sys(SYS_exit_group, 0, 0, 0, 0, 0);
}
