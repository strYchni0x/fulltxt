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

# Gson + OneDrive-Graph-Modelle (Retrofit/Gson-Deserialisierung)
# Ohne -keepattributes Signature verliert Gson den generischen Typ von
# DriveItemPage.items (List<GraphDriveItem>) und deserialisiert jeden Eintrag als
# LinkedTreeMap, was beim Iterieren in getChanges() eine ClassCastException auslöst.
# Die Modell-Felder (id/name/size/... haben kein @SerializedName) müssen ebenfalls die
# Obfuskierung überstehen, sonst kann Gson sie nicht auf die JSON-Schlüssel abbilden.
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
