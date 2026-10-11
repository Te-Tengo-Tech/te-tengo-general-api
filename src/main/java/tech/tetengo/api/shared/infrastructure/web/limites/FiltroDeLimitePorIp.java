package tech.tetengo.api.shared.infrastructure.web.limites;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;
import tech.tetengo.api.shared.infrastructure.web.limites.LimitadorDeSolicitudes.Limite;
import tech.tetengo.api.shared.infrastructure.web.limites.LimitadorDeSolicitudes.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limits the public endpoints anyone can call without a token per client address
 * ({@link PropiedadesDeLimites}): over the limit they answer {@code 429 DEMASIADAS_SOLICITUDES}
 * ({@code application/problem+json}) with {@code Retry-After} in seconds. On the token endpoint a
 * device code poll also gets {@code error: slow_down} (RFC 8628 §3.5), so an OAuth client that reads
 * the body slows down as it does for a poll that came too soon.
 *
 * <p>The client address is {@link HttpServletRequest#getRemoteAddr()}: behind the reverse proxy, Tomcat
 * replaces it with the {@code X-Forwarded-For} address the proxy set, and only when the connection
 * comes from a trusted proxy ({@code server.forward-headers-strategy: native},
 * {@code server.tomcat.remoteip.internal-proxies}), so a client cannot choose its own bucket by sending
 * the header itself. IPv6 clients are grouped by their /64 network, which one host usually owns.
 */
public class FiltroDeLimitePorIp extends OncePerRequestFilter {

    public static final String DEMASIADAS_SOLICITUDES = "DEMASIADAS_SOLICITUDES";

    static final String VINCULACION = ApiVersioning.BASE + "/agente/vinculacion";
    static final String TOKEN = ApiVersioning.BASE + "/agente/token";
    static final String SESIONES = ApiVersioning.BASE + "/sesiones";
    static final String RECUPERACIONES = ApiVersioning.BASE + "/recuperaciones";

    private static final String GRANT_DISPOSITIVO = "urn:ietf:params:oauth:grant-type:device_code";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(FiltroDeLimitePorIp.class);

    private final LimitadorDeSolicitudes limitador;
    private final Map<String, List<Limite>> limitesPorRuta;

    public FiltroDeLimitePorIp(LimitadorDeSolicitudes limitador, PropiedadesDeLimites propiedades) {
        this.limitador = limitador;
        Map<String, List<Limite>> rutas = new LinkedHashMap<>();
        rutas.put(VINCULACION, propiedades.vinculacion().limites());
        rutas.put(TOKEN, propiedades.token().limites());
        rutas.put(SESIONES, propiedades.sesiones().limites());
        rutas.put(RECUPERACIONES, propiedades.recuperaciones().limites());
        rutas.values().removeIf(List::isEmpty);
        this.limitesPorRuta = Map.copyOf(rutas);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !HttpMethod.POST.matches(peticion.getMethod()) || !limitesPorRuta.containsKey(ruta(peticion));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String ruta = ruta(peticion);
        String cliente = claveDeCliente(peticion.getRemoteAddr());
        Resultado resultado = limitador.consumir(ruta + " " + cliente, limitesPorRuta.get(ruta));
        if (resultado.permitido()) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        long segundos = Math.max(1, (resultado.reintentarEn().toMillis() + 999) / 1000);
        log.debug("{} desde {}: demasiadas solicitudes, reintentar en {} s", ruta, cliente, segundos);
        responder(peticion, respuesta, ruta, segundos);
    }

    private static void responder(
            HttpServletRequest peticion, HttpServletResponse respuesta, String ruta, long segundos) throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "about:blank");
        problema.put("title", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        problema.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problema.put("detail", "Hiciste demasiados intentos. Espera un momento y vuelve a intentarlo.");
        problema.put("instance", ruta);
        problema.put("codigo", DEMASIADAS_SOLICITUDES);
        if (TOKEN.equals(ruta) && GRANT_DISPOSITIVO.equals(peticion.getParameter("grant_type"))) {
            problema.put("error", "slow_down");
            problema.put("error_description", "Demasiadas consultas desde esta dirección; consulta más despacio.");
        }
        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(segundos));
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), problema);
    }

    private static String ruta(HttpServletRequest peticion) {
        return peticion.getRequestURI().substring(peticion.getContextPath().length());
    }

    /**
     * The bucket of a client address: the IPv4 address itself, or the /64 network of an IPv6 address
     * (an IPv4-mapped IPv6 address is its IPv4 address).
     */
    static String claveDeCliente(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return "desconocida";
        }
        InetAddress ip;
        try {
            ip = InetAddress.ofLiteral(direccion.strip());
        } catch (IllegalArgumentException e) {
            return direccion.strip();
        }
        if (ip instanceof Inet6Address) {
            byte[] red = Arrays.copyOf(ip.getAddress(), 16);
            Arrays.fill(red, 8, 16, (byte) 0);
            try {
                return InetAddress.getByAddress(red).getHostAddress() + "/64";
            } catch (java.net.UnknownHostException e) {
                return ip.getHostAddress();
            }
        }
        return ip.getHostAddress();
    }
}
