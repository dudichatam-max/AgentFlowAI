# Minify is off for release until a signed build is validated.
# If enabling R8 later, keep:
# - kotlinx.serialization generated serializers
# - Room entities / DAOs
# - WorkManager workers
# - Compose
# Never shrink Android Keystore wrappers or EncryptedSharedPreferences.
