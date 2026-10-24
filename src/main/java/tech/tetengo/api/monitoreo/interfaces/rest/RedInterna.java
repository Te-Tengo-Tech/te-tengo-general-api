package tech.tetengo.api.monitoreo.interfaces.rest;

import java.net.InetAddress;

/**
 * The addresses MediaMTX calls the hook from: loopback, link-local and the private ranges (IPv4
 * 10/8, 172.16/12 and 192.168/16; IPv6 unique local fc00::/7), where the Docker networks are. The address
 * is the request's remote address after Tomcat's {@code RemoteIpValve}, so a request forwarded by the
 * reverse proxy has the Internet client's address and is not internal.
 */
final class RedInterna {

    private RedInterna() {}

    static boolean contiene(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return false;
        }
        try {
            InetAddress ip = InetAddress.ofLiteral(direccion);
            byte[] bytes = ip.getAddress();
            boolean ipv6Local = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress() || ipv6Local;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
