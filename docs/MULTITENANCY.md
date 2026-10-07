# Multi-tenancy por hogar

## Qué es el tenant
El **hogar** reúne:
- la vivienda donde está la cámara;
- el adulto mayor (uno por cuenta, CA-04.2);
- los familiares vinculados (US-08).

Una persona puede pertenecer a más de un hogar; por ejemplo, alguien que cuida a sus padres en dos casas. Por eso la membresía hogar-usuario es una tabla global.

## Por qué una columna y no un esquema por tenant
`reqsai-api` usa un esquema por organización: son pocas empresas, con muchos datos cada una. Te Tengo tendrá **muchos hogares con pocos datos cada uno**. Un esquema por hogar multiplicaría las migraciones y las conexiones sin beneficio, así que se usa la estrategia de **datos particionados por discriminador** de Hibernate (*Hibernate ORM User Guide*, cap. 23, «Multitenancy», secciones 23.2.3 y 23.3.1).

## Cómo funciona
1. **El token trae el hogar:** el familiar o el agente envían un JWT con el claim `hogar_id`.
2. **El filtro lo fija:** `FiltroHogarActual`, que corre después de validar el JWT, lo guarda en `HogarActual` (ThreadLocal) y lo **limpia en `finally`**.
3. **Hibernate lo lee:** `ResolvedorDeHogar` (`CurrentTenantIdentifierResolver<UUID>`) entrega ese hogar a cada sesión.
4. **El filtrado es automático:** en las entidades que extienden `EntidadDelHogar`, el campo `hogarId` lleva `@TenantId`. Hibernate:
   - rellena `hogar_id` al insertar;
   - agrega `hogar_id = ?` a **todas** las consultas, incluidas `findById` y `findAll`.
5. **Falla cerrada:** sin hogar en el contexto se usa un UUID inexistente (`ResolvedorDeHogar.SIN_HOGAR`) y las consultas no devuelven nada.

## Reglas
- **Toda tabla del hogar tiene** `hogar_id uuid not null references hogares(id)` y un índice que empieza por `hogar_id`.
- **Nunca se recibe el hogar** por parámetro, ni en la URL ni en el cuerpo, y nunca se filtra a mano.
- **Las tablas globales** (`hogares`, cuentas y membresías) no extienden `EntidadDelHogar`.
- **Prueba obligatoria por funcionalidad:** dos hogares, y se comprueba que uno no ve ni modifica lo del otro (`CamarasMultitenancyIntegrationTest`).
- **Defensa adicional (opcional):** *Row Level Security* de PostgreSQL con `set_config('app.hogar_id', ...)`. Aunque una consulta nativa olvide el filtro, la base no devolvería filas de otro hogar.

## El agente de la vivienda
El agente recibe un **token por cámara** al registrarla. Ese token incluye `hogar_id` y `camara_id`, así que sus eventos solo pueden escribir en su hogar. Ver [CONTRATO_AGENTE.md](CONTRATO_AGENTE.md).

## Referencia
Hibernate. (s.f.). *Hibernate ORM User Guide*, cap. 23 «Multitenancy». https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html
