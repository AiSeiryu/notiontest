package com.seiryu.sunoexport;

import java.net.InetAddress;
import java.net.URI;

public final class LabPolicy {
    private LabPolicy() {}

    public static URI validate(String raw) throws Exception {
        URI uri = new URI(raw);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only http:// and https:// are supported.");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty() || !isPrivateHost(host)) {
            throw new IllegalArgumentException("Lab mode is restricted to localhost/private LAN addresses.");
        }
        return uri;
    }

    public static boolean isPrivateHost(String host) throws Exception {
        if ("localhost".equalsIgnoreCase(host) || "localhost.localdomain".equalsIgnoreCase(host)) return true;
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) return false;
        for (InetAddress address : addresses) {
            if (!(address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress())) {
                return false;
            }
        }
        return true;
    }
}
