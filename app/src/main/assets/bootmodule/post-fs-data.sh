# RM Tweaks boot animation: bind the chosen archive over the stock bootanimation.zip, for this boot only.
D=/data/adb/rmtweaks/boot
[ -d $D ] || exit 0
log() { echo "`date '+%m-%d %H:%M:%S'` $*" >> $D/log.txt; }
[ -f $D/selection ] || exit 0
SEL=`cat $D/selection`
[ "$SEL" = stock ] && exit 0
# two boots in a row that never finished with the animation mounted: back to stock
N=`cat $D/attempts 2>/dev/null`
[ "$N" ] || N=0
if [ "$N" -ge 2 ]; then
  echo stock > $D/selection; rm -f $D/attempts; echo rolled_back > $D/result
  log "two unfinished boots with $SEL: switched back to stock"
  exit 0
fi
TARGET=`cat $D/target 2>/dev/null`
case "$TARGET" in
  /system/media/*bootanimation*.zip|/product/media/*bootanimation*.zip|/data/*bootanimation*.zip) ;;
  *) echo bad_target > $D/result; log "unexpected target '$TARGET'"; exit 0 ;;
esac
if [ ! -f "$TARGET" ] || [ ! -f $D/bootanimation.zip ]; then echo missing > $D/result; log "missing $TARGET or the archive"; exit 0; fi
CTX=`ls -Z "$TARGET" 2>/dev/null | awk '{print $1}'`
case "$CTX" in u:object_r:*) ;; *) CTX=u:object_r:system_file:s0 ;; esac
chcon "$CTX" $D/bootanimation.zip
chmod 644 $D/bootanimation.zip
echo $((N + 1)) > $D/attempts
if mount -o bind $D/bootanimation.zip "$TARGET"; then
  echo mounted > $D/result; log "$SEL mounted over $TARGET ($CTX)"
else
  echo mount_failed > $D/result; log "bind mount over $TARGET failed"
fi
