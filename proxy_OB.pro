#
# This ProGuard configuration file illustrates how to process applications.
# Usage:
#     java -jar proguard.jar @applications.pro
#

# Specify the input jars, output jars, and library jars.

-injars  MonaLisa/ProxyService/lib/ClientsFarmProxy.jar
-outjars ClientsFarmProxy_OB.jar

-libraryjar /opt/JAVA/java5/jre/lib/rt.jar
-libraryjar /opt/JAVA/java5/jre/lib/jsse.jar
-libraryjar /opt/JAVA/java5/jre/lib/jce.jar
-libraryjar /opt/JAVA/java5/jre/lib/charsets.jar
-libraryjar MonaLisa/Service/lib/joesnmp-0.3.3.jar
-libraryjar lib/jep-2.4.0.jar
-libraryjar MonaLisa/Service/lib/axis.jar
-libraryjar MonaLisa/Service/lib/backport-util-concurrent.jar
-libraryjar MonaLisa/Service/lib/commons-discovery.jar
-libraryjar MonaLisa/Service/lib/commons-logging.jar
-libraryjar MonaLisa/Service/lib/dnsjava-1.3.2.jar
-libraryjar MonaLisa/Service/lib/jaxrpc.jar
-libraryjar MonaLisa/Service/lib/jini-core.jar
-libraryjar MonaLisa/Service/lib/jini-ext.jar
-libraryjar MonaLisa/Service/lib/snmp-1.4.1.jar
-libraryjar MonaLisa/Service/lib/log4j-1.2.8.jar
-libraryjar MonaLisa/Service/lib/mckoidb.jar
-libraryjar MonaLisa/Service/lib/mysql-driver.jar
-libraryjar MonaLisa/Service/lib/netx.jar
-libraryjar MonaLisa/Service/lib/saaj.jar
#-libraryjar MonaLisa/Service/lib/serviceui-1.1beta4.jar
#-libraryjar MonaLisa/Service/lib/sun-util.jar
#-libraryjar MonaLisa/Service/lib/tools.jar
#-libraryjar MonaLisa/Service/lib/update.jar


# Preserve all public applications.


-keepclasseswithmembers public class * {
    public static void main(java.lang.String[]);
}

# Print out a list of what we're preserving.

-printseeds

# Preserve all annotations.

-keepattributes *Annotation*

# Preserve all native method names and the names of their classes.

-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve a method that is required in all enumeration classes.

-keepclassmembers class * extends java.lang.Enum {
    public **[] values();
}

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn not save them.
# You can comment this out if your application doesn't use serialization.
# If your code contains serializable classes that have to be backward 
# compatible, please refer to the manual.


-keepnames class * implements java.io.Serializable

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#-keepclassmembers class * implements java.io.Serializable {
#    static final long serialVersionUID;
#    private void writeObject(java.io.ObjectOutputStream);
#    private void readObject(java.io.ObjectInputStream);
#    java.lang.Object writeReplace();
#    java.lang.Object readResolve();
#}

# Your application may contain more items that need to be preserved; 
# typically classes that are dynamically created using Class.forName:

-keep public class lia.Monitor.ClientsFarmProxy.Service
-keep public class lia.Monitor.ClientsFarmProxy.ProxyService
-keep public interface lia.Monitor.ClientsFarmProxy.ProxyServiceI
-keep public interface lia.Monitor.ClientsFarmProxy.ServiceI
-keep public class * extends net.jini.entry.AbstractEntry
-keep public class lia.Monitor.JiniSerFarmMon.NoImplProxy
-keep public interface  lia.Monitor.monitor.DataStore
-keep public class lia.Monitor.monitor.Gresult
-keep public class lia.Monitor.monitor.ExtendedResult
-keep public class lia.Monitor.monitor.cmonMessage
-keep public class lia.Monitor.monitor.LoggerConfigClass
-keep public interface lia.Monitor.monitor.AppConfigChangeListener 
-keep public class lia.Monitor.monitor.tcpConnWatchdog

# -keep public class mypackage.MyClass
# -keep public interface mypackage.MyInterface
# -keep public class * implements mypackage.MyInterface
