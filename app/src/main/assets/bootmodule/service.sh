# Boot finished: the animation worked, reset the unfinished-boot counter.
D=/data/adb/rmtweaks/boot
[ -d $D ] || exit 0
until [ "`getprop sys.boot_completed`" = 1 ]; do sleep 5; done
rm -f $D/attempts
[ "`cat $D/result 2>/dev/null`" = mounted ] && echo confirmed > $D/result
exit 0
