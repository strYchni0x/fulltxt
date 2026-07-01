# PdfBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Apache POI
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**

# MSAL
-keep class com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**

# Gson + OneDrive Graph models (Retrofit/Gson deserialization)
# Without -keepattributes Signature, Gson loses the generic type of
# DriveItemPage.items (List<GraphDriveItem>) and deserializes each entry as a
# LinkedTreeMap, causing a ClassCastException while iterating in getChanges().
# The model fields (id/name/size/... have no @SerializedName) must also survive
# obfuscation, or Gson can't map them to the JSON keys.
-keepattributes Signature
-keepattributes *Annotation*
-keep class me.fulltxt.app.data.cloud.onedrive.DriveItemPage { *; }
-keep class me.fulltxt.app.data.cloud.onedrive.GraphDriveItem { *; }
-keep class me.fulltxt.app.data.cloud.onedrive.GraphFileInfo { *; }
-keep class me.fulltxt.app.data.cloud.onedrive.GraphParentReference { *; }
-keep class me.fulltxt.app.data.cloud.onedrive.GraphDeleted { *; }

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-dontwarn com.google.api.**

# SQLCipher (loads native methods via JNI)
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# R8 missing classes (generated)
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.apache.xmlbeans.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.framework.**
-dontwarn org.tukaani.xz.**
