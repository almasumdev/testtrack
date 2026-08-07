# Release builds are minified. Add keep rules here as real dependencies arrive —
# reflection-based JSON models and anything named from the manifest are the usual casualties.

# WorkManager keeps its queue in a Room database, and Room instantiates the generated
# WorkDatabase_Impl by name through its no-argument constructor. Nothing in the bytecode calls
# that constructor, so R8 removes it and the process dies inside InitializationProvider before
# a line of our code runs:
#
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
#
# Release only — debug builds are not minified, so the reminder scheduler looks perfectly healthy
# right up until the APK that ships.
#
# Firestore needs no equivalent rule: Repo parses every document by hand through explicit string
# keys rather than toObject(), so renamed fields cannot silently deserialise to nothing.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
