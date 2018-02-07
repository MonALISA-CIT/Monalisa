package lia.util.os;

/**
 *
 * @author ramiro
 *
 */
public final class OSType {

    public static enum OSName {
        LINUX, SUNOS, MACOS, WINDOWS
    }

    public static enum OSArch {
        i86, amd64, sparc32
    }

    private static final OSName JVM_OS_NAME;
    private static final OSArch JVM_OS_ARCH;

    static {
        OSName jvmOSName = null;
        try {
            final String osName = System.getProperty("os.name");
            if (osName.equalsIgnoreCase("sunos")) {
                jvmOSName = OSName.SUNOS;
            } else if (osName.equalsIgnoreCase("linux")) {
                jvmOSName = OSName.LINUX;
            } else {
                //keep it null or add more os
                jvmOSName = null;
            }
        } catch (Throwable t) {
            jvmOSName = null;
        }

        JVM_OS_NAME = jvmOSName;

        OSArch jvmOSArch = null;
        try {
            final String osArch = System.getProperty("os.arch");
            if (osArch.equalsIgnoreCase("amd64")) {
                jvmOSArch = OSArch.amd64;
            } else if (osArch.contains("86") || osArch.contains("32")) {
                jvmOSArch = OSArch.i86;
            } else if (osArch.contains("sparc32")) {
                jvmOSArch = OSArch.sparc32;
            }
        } catch (Throwable t) {
            jvmOSArch = null;
        }

        JVM_OS_ARCH = jvmOSArch;
    }

    public static final OSName getJVMOsName() {
        return JVM_OS_NAME;
    }

    public static final OSArch getJVMOsArch() {
        return JVM_OS_ARCH;
    }
}
