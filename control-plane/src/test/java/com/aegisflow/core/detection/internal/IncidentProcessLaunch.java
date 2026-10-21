package com.aegisflow.core.detection.internal;

import java.util.*;

/** Avoid Windows command-length limits and inherited application credentials in restart tests. */
final class IncidentProcessLaunch {
    static String arguments(String classpath) {
        if(classpath.indexOf('\n')>=0 || classpath.indexOf('\r')>=0)
            throw new IllegalArgumentException("Invalid classpath");
        return "-cp\n\""+classpath.replace("\\","\\\\").replace("\"","\\\"")
            +"\"\n"+IncidentProcessMain.class.getName()+"\n";
    }
    static Map<String,String> environment(Map<String,String> inherited) {
        var allowed=Set.of("PATH","SYSTEMROOT","WINDIR","SYSTEMDRIVE","COMSPEC","OS",
            "TEMP","TMP","USERPROFILE","LOCALAPPDATA","APPDATA","JAVA_HOME","LANG","LC_ALL");
        Map<String,String> result=new HashMap<>();
        inherited.forEach((k,v) -> { if(allowed.contains(k.toUpperCase(Locale.ROOT))) result.put(k,v); });
        return result;
    }
}
