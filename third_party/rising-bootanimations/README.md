# Rising boot animations (same set as nubia-ly)

Source: RisingOS-Revived/android_vendor_rising, commit `8e4650c0ba33de2dfcc1f9748bc2dcdc0d819371`, `prebuilts/bootanimation`
— the pinned catalogue nubia-ly uses (`catalog.json`: SHA-256 + byte count per archive).

CI (`tools/bootanim_assets.py`) downloads each archive from that immutable commit, verifies it, copies it unchanged into
`app/src/main/assets/bootanimations/<id>.zip` and renders small preview frames next to it. Nothing is downloaded on the
phone; the generated folder is not committed.

Artwork and branding belong to the RisingOS project and the original authors (credited in the app); used here as in
nubia-ly, whose port this module absorbs.
