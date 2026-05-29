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

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-dontwarn com.google.api.**

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
