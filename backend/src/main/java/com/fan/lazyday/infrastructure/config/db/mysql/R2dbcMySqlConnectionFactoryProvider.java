package com.fan.lazyday.infrastructure.config.db.mysql;

import com.mysql.cj.protocol.x.CompressionAlgorithm;
import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactoryProvider;
import io.asyncer.r2dbc.mysql.Query;
import io.asyncer.r2dbc.mysql.constant.SslMode;
import io.asyncer.r2dbc.mysql.constant.ZeroDateOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;
import jakarta.annotation.Nullable;
import org.reactivestreams.Publisher;
import reactor.netty.resources.LoopResources;

import javax.net.ssl.HostnameVerifier;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static io.asyncer.r2dbc.mysql.internal.util.AssertUtils.requireNonNull;
import static io.asyncer.r2dbc.mysql.internal.util.InternalArrays.EMPTY_STRINGS;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * <p>描述: [SPI注册] </p>
 * <p>Copy io.asyncer.r2dbc.mysql.MySqlConnectionFactoryProvider<p/>
 * <p>创建时间: 2025/3/19 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2025/03/19 18:20 fan 创建
 */
public class R2dbcMySqlConnectionFactoryProvider implements ConnectionFactoryProvider {
    /**
     * The name of the driver used for discovery, should not be changed.
     */
    public static final String MYSQL_DRIVER = "mysql";

    /**
     * Option to set the Unix Domain Socket.
     *
     * @since 0.8.1
     */
    public static final Option<String> UNIX_SOCKET = Option.valueOf("unixSocket");

    /**
     * Option to set the time zone conversion.  Default to {@code true} means enable conversion between JVM
     * and {@link #CONNECTION_TIME_ZONE}.
     * <p>
     * Note: disable it will ignore the time zone of connection, and use the JVM local time zone.
     *
     * @since 1.1.2
     */
    public static final Option<Boolean> PRESERVE_INSTANTS = Option.valueOf("preserveInstants");

    /**
     * Option to set the time zone of connection.  Default to {@code LOCAL} means use JVM local time zone.
     * It should be {@code "LOCAL"}, {@code "SERVER"}, or a valid ID of {@code ZoneId}. {@code "SERVER"} means
     * querying the server-side timezone during initialization.
     *
     * @since 1.1.2
     */
    public static final Option<String> CONNECTION_TIME_ZONE = Option.valueOf("connectionTimeZone");

    /**
     * Option to force the time zone of connection to session time zone.  Default to {@code false}.
     * <p>
     * Note: alter the time zone of session will affect the results of MySQL date/time functions, e.g.
     * {@code NOW([n])}, {@code CURRENT_TIME([n])}, {@code CURRENT_DATE()}, etc. Please use with caution.
     *
     * @since 1.1.2
     */
    public static final Option<Boolean> FORCE_CONNECTION_TIME_ZONE_TO_SESSION =
            Option.valueOf("forceConnectionTimeZoneToSession");

    /**
     * Option to set {@link ZoneId} of server. If it is set, driver will ignore the real time zone of
     * server-side.
     *
     * @since 0.8.2
     * @deprecated since 1.1.2, use {@link #CONNECTION_TIME_ZONE} instead.
     */
    @Deprecated
    public static final Option<ZoneId> SERVER_ZONE_ID = Option.valueOf("serverZoneId");

    /**
     * Option to configure handling when MySQL server returning "zero date" (aka. "0000-00-00 00:00:00")
     *
     * @since 0.8.1
     */
    public static final Option<ZeroDateOption> ZERO_DATE = Option.valueOf("zeroDate");

    /**
     * Option to {@link SslMode}.
     *
     * @since 0.8.1
     */
    public static final Option<SslMode> SSL_MODE = Option.valueOf("sslMode");

    /**
     * Option to configure {@link HostnameVerifier}. It is available only if the {@link #SSL_MODE} set to
     * {@link SslMode#VERIFY_IDENTITY}. It can be an implementation class name of {@link HostnameVerifier}
     * with a public no-args constructor.
     *
     * @since 0.8.2
     */
    public static final Option<HostnameVerifier> SSL_HOSTNAME_VERIFIER =
            Option.valueOf("sslHostnameVerifier");

    /**
     * Option to TLS versions for SslContext protocols, see also {@code TlsVersions}. Usually sorted from
     * higher to lower. It can be a {@code Collection<String>}. It can be a {@link String}, protocols will be
     * split by {@code ,}. e.g. "TLSv1.2,TLSv1.1,TLSv1".
     *
     * @since 0.8.1
     */
    public static final Option<String[]> TLS_VERSION = Option.valueOf("tlsVersion");

    /**
     * Option to set a PEM file of server SSL CA. It will be used to verify server certificates. And it will
     * be used only if {@link #SSL_MODE} set to {@link SslMode#VERIFY_CA} or higher level.
     *
     * @since 0.8.1
     */
    public static final Option<String> SSL_CA = Option.valueOf("sslCa");

    /**
     * Option to set a PEM file of client SSL key.
     *
     * @since 0.8.1
     */
    public static final Option<String> SSL_KEY = Option.valueOf("sslKey");

    /**
     * Option to set a PEM file password of client SSL key. It will be used only if {@link #SSL_KEY} and
     * {@link #SSL_CERT} set.
     *
     * @since 0.8.1
     */
    public static final Option<CharSequence> SSL_KEY_PASSWORD = Option.sensitiveValueOf("sslKeyPassword");

    /**
     * Option to set a PEM file of client SSL cert.
     *
     * @since 0.8.1
     */
    public static final Option<String> SSL_CERT = Option.valueOf("sslCert");

    /**
     * Option to custom {@link SslContextBuilder}. It can be an implementation class name of {@link Function}
     * with a public no-args constructor.
     *
     * @since 0.8.2
     */
    public static final Option<Function<SslContextBuilder, SslContextBuilder>>
            SSL_CONTEXT_BUILDER_CUSTOMIZER = Option.valueOf("sslContextBuilderCustomizer");

    /**
     * Enable/Disable TCP KeepAlive.
     *
     * @since 0.8.2
     */
    public static final Option<Boolean> TCP_KEEP_ALIVE = Option.valueOf("tcpKeepAlive");

    /**
     * Enable/Disable TCP NoDelay.
     *
     * @since 0.8.2
     */
    public static final Option<Boolean> TCP_NO_DELAY = Option.valueOf("tcpNoDelay");

    /**
     * Enable/Disable database creation if not exist.
     *
     * @since 1.0.6
     */
    public static final Option<Boolean> CREATE_DATABASE_IF_NOT_EXIST =
            Option.valueOf("createDatabaseIfNotExist");

    /**
     * Enable server preparing for parameterized statements and prefer server preparing simple statements.
     * <p>
     * The value can be a {@link Boolean}. If it is {@code true}, driver will use server preparing for
     * parameterized statements and text query for simple statements. If it is {@code false}, driver will use
     * client preparing for parameterized statements and text query for simple statements.
     * <p>
     * The value can be a {@link Predicate}{@code <}{@link String}{@code >}. If it is set, driver will server
     * preparing for parameterized statements, it configures whether to prefer prepare execution on a
     * statement-by-statement basis (simple statements). The {@link Predicate}{@code <}{@link String}{@code >}
     * accepts the simple SQL query string and returns a {@code boolean} flag indicating preference.
     * <p>
     * The value can be a {@link String}. If it is set, driver will try to convert it to {@link Boolean} or an
     * instance of {@link Predicate}{@code <}{@link String}{@code >} which use reflection with a public
     * no-args constructor.
     *
     * @since 0.8.1
     */
    public static final Option<Object> USE_SERVER_PREPARE_STATEMENT =
            Option.valueOf("useServerPrepareStatement");

    /**
     * Option to set session variables. It should be a list of key-value pairs. e.g.
     * {@code ["sql_mode='ANSI_QUOTES,STRICT_TRANS_TABLES'", "time_zone=00:00"]}.
     *
     * @since 1.1.2
     */
    public static final Option<String[]> SESSION_VARIABLES = Option.valueOf("sessionVariables");

    /**
     * Option to set the allowed local infile path.
     *
     * @since 1.1.0
     */
    public static final Option<String> ALLOW_LOAD_LOCAL_INFILE_IN_PATH =
            Option.valueOf("allowLoadLocalInfileInPath");

    /**
     * Option to set the buffer size for local infile. Default to {@code 8192}.
     *
     * @since 1.1.2
     */
    public static final Option<Integer> LOCAL_INFILE_BUFFER_SIZE =
            Option.valueOf("localInfileBufferSize");

    /**
     * Option to set compression algorithms.  Default to [{@link CompressionAlgorithm#}].
     * <p>
     * It will auto choose an algorithm that's contained in the list and supported by the server, preferring
     * zstd, then zlib. If the list does not contain {@link CompressionAlgorithm#} and the server
     * does not support any algorithm in the list, an exception will be thrown when connecting.
     * <p>
     * Note: zstd requires a dependency {@code com.github.luben:zstd-jni}.
     *
     * @since 1.1.2
     */
    public static final Option<CompressionAlgorithm[]> COMPRESSION_ALGORITHMS =
            Option.valueOf("compressionAlgorithms");

    /**
     * Option to set the zstd compression level.  Default to {@code 3}.
     * <p>
     * It is only used if zstd is chosen for the connection.
     * <p>
     * Note: MySQL protocol does not allow to set the zlib compression level of the server, only zstd is
     * configurable.
     *
     * @since 1.1.2
     */
    public static final Option<Integer> ZSTD_COMPRESSION_LEVEL =
            Option.valueOf("zstdCompressionLevel");

    /**
     * Option to set the {@link LoopResources} for the connection. Default to
     * {@link reactor.netty.tcp.TcpResources#get() global tcp Resources}
     *
     * @since 1.1.2
     */
    public static final Option<LoopResources> LOOP_RESOURCES = Option.valueOf("loopResources");

    /**
     * Option to set the maximum size of the {@link Query} parsing cache.  Default to {@code 256}.
     *
     * @since 0.8.3
     */
    public static final Option<Integer> PREPARE_CACHE_SIZE = Option.valueOf("prepareCacheSize");

    /**
     * Option to set the maximum size of the server-preparing cache.  Default to {@code 0}.
     *
     * @since 0.8.3
     */
    public static final Option<Integer> QUERY_CACHE_SIZE = Option.valueOf("queryCacheSize");

    /**
     * Enable/Disable auto-detect driver extensions.
     *
     * @since 0.8.2
     */
    public static final Option<Boolean> AUTODETECT_EXTENSIONS = Option.valueOf("autodetectExtensions");

    /**
     * Password Publisher function can be used to retrieve password before creating a connection. This can be
     * used with Amazon RDS Aurora IAM authentication, wherein it requires token to be generated. The token is
     * valid for 15 minutes, and this token will be used as password.
     */
    public static final Option<Publisher<String>> PASSWORD_PUBLISHER = Option.valueOf("passwordPublisher");

    @Override
    public ConnectionFactory create(ConnectionFactoryOptions options) {
        requireNonNull(options, "connectionFactoryOptions must not be null");

        return MySqlConnectionFactory.from(setup(options));
    }

    @Override
    public boolean supports(ConnectionFactoryOptions options) {
        requireNonNull(options, "connectionFactoryOptions must not be null");
        return MYSQL_DRIVER.equals(options.getValue(DRIVER));
    }

    @Override
    public String getDriver() {
        return MYSQL_DRIVER;
    }

    /**
     * Visible for unit tests.
     *
     * @param options the {@link ConnectionFactoryOptions} for setup {@link MySqlConnectionConfiguration}.
     * @return completed {@link MySqlConnectionConfiguration}.
     */
    static MySqlConnectionConfiguration setup(ConnectionFactoryOptions options) {
        OptionMapper mapper = new OptionMapper(options);
        MySqlConnectionConfiguration.Builder builder = MySqlConnectionConfiguration.builder();

        builder.extendWith(new R2dbcMysqlCustomCodecRegistrar());

        mapper.requires(USER).asString()
                .to(builder::user);
        mapper.optional(PASSWORD).asPassword()
                .to(builder::password);
        mapper.optional(UNIX_SOCKET).asString()
                .to(builder::unixSocket)
                .otherwise(() -> setupHost(builder, mapper));
        /*mapper.optional(PRESERVE_INSTANTS).asBoolean()
                .to(builder::preserveInstants);
        mapper.optional(CONNECTION_TIME_ZONE).asString()
                .to(builder::connectionTimeZone)
                .otherwise(() -> mapper.optional(SERVER_ZONE_ID)
                        .as(ZoneId.class, id -> ZoneId.of(id, ZoneId.SHORT_IDS))
                        .to(builder::serverZoneId));
        mapper.optional(FORCE_CONNECTION_TIME_ZONE_TO_SESSION).asBoolean()
                .to(builder::forceConnectionTimeZoneToSession);*/
        mapper.optional(TCP_KEEP_ALIVE).asBoolean()
                .to(builder::tcpKeepAlive);
        mapper.optional(TCP_NO_DELAY).asBoolean()
                .to(builder::tcpNoDelay);
        mapper.optional(ZERO_DATE)
                .as(ZeroDateOption.class, id -> ZeroDateOption.valueOf(id.toUpperCase()))
                .to(builder::zeroDateOption);
        mapper.optional(USE_SERVER_PREPARE_STATEMENT).prepare(builder::useClientPrepareStatement,
                builder::useServerPrepareStatement, builder::useServerPrepareStatement);
      /*  mapper.optional(ALLOW_LOAD_LOCAL_INFILE_IN_PATH).asString()
                .to(builder::allowLoadLocalInfileInPath);
        mapper.optional(LOCAL_INFILE_BUFFER_SIZE).asInt()
                .to(builder::localInfileBufferSize);*/
        mapper.optional(QUERY_CACHE_SIZE).asInt()
                .to(builder::queryCacheSize);
        mapper.optional(PREPARE_CACHE_SIZE).asInt()
                .to(builder::prepareCacheSize);
        mapper.optional(AUTODETECT_EXTENSIONS).asBoolean()
                .to(builder::autodetectExtensions);
        mapper.optional(CONNECT_TIMEOUT).as(Duration.class, Duration::parse)
                .to(builder::connectTimeout);
        mapper.optional(DATABASE).asString()
                .to(builder::database);
        mapper.optional(CREATE_DATABASE_IF_NOT_EXIST).asBoolean()
                .to(builder::createDatabaseIfNotExist);
      /*  mapper.optional(COMPRESSION_ALGORITHMS).asArray(
                CompressionAlgorithm[].class,
                it -> CompressionAlgorithm.valueOf(it.toUpperCase()),
                it -> it.split(","),
                CompressionAlgorithm[]::new
        ).to(builder::compressionAlgorithms);
        mapper.optional(ZSTD_COMPRESSION_LEVEL).asInt()
                .to(builder::zstdCompressionLevel);
        mapper.optional(LOOP_RESOURCES).as(LoopResources.class)
                .to(builder::loopResources);*/
        mapper.optional(PASSWORD_PUBLISHER).as(Publisher.class)
                .to(builder::passwordPublisher);
      /*  mapper.optional(SESSION_VARIABLES).asArray(
                String[].class,
                Function.identity(),
                R2dbcMySqlConnectionFactoryProvider::splitVariables,
                String[]::new
        ).to(builder::sessionVariables);
        mapper.optional(LOCK_WAIT_TIMEOUT).as(Duration.class, Duration::parse)
                .to(builder::lockWaitTimeout);
        mapper.optional(STATEMENT_TIMEOUT).as(Duration.class, Duration::parse)
                .to(builder::statementTimeout);*/

        return builder.build();
    }

    /**
     * Set builder of {@link MySqlConnectionConfiguration} for hostname-based address with SSL
     * configurations.
     *
     * @param builder the builder of {@link MySqlConnectionConfiguration}.
     * @param mapper  the {@link OptionMapper} of {@code options}.
     */
    private static void setupHost(MySqlConnectionConfiguration.Builder builder, OptionMapper mapper) {
        mapper.requires(HOST).asString()
                .to(builder::host);
        mapper.optional(PORT).asInt()
                .to(builder::port);
        mapper.optional(SSL).asBoolean()
                .to(isSsl -> builder.sslMode(isSsl ? SslMode.REQUIRED : SslMode.DISABLED));
        mapper.optional(SSL_MODE).as(SslMode.class, id -> SslMode.valueOf(id.toUpperCase()))
                .to(builder::sslMode);
        mapper.optional(TLS_VERSION)
                .asArray(String[].class, Function.identity(), it -> it.split(","), String[]::new)
                .to(builder::tlsVersion);
        mapper.optional(SSL_HOSTNAME_VERIFIER).as(HostnameVerifier.class)
                .to(builder::sslHostnameVerifier);
        mapper.optional(SSL_CERT).asString()
                .to(builder::sslCert);
        mapper.optional(SSL_KEY).asString()
                .to(builder::sslKey);
        mapper.optional(SSL_KEY_PASSWORD).asPassword()
                .to(builder::sslKeyPassword);
        mapper.optional(SSL_CONTEXT_BUILDER_CUSTOMIZER).as(Function.class)
                .to(builder::sslContextBuilderCustomizer);
        mapper.optional(SSL_CA).asString()
                .to(builder::sslCa);
    }

    /**
     * Splits session variables from user input. e.g. {@code sql_mode='ANSI_QUOTE,STRICT',c=d;e=f} will be
     * split into {@code ["sql_mode='ANSI_QUOTE,STRICT'", "c=d", "e=f"]}.
     * <p>
     * It supports escaping characters with backslash, quoted values with single or double quotes, and nested
     * brackets. Priorities are: backslash in quoted &gt; single quote = double quote &gt; bracket, backslash
     * will not be a valid escape character if it is not in a quoted value.
     * <p>
     * Note that it does not strictly check syntax validity, so it will not throw syntax exceptions.
     *
     * @param sessionVariables the session variables from user input.
     * @return the split list
     * @throws IllegalArgumentException if {@code sessionVariables} is {@code null}.
     */
    private static String[] splitVariables(String sessionVariables) {
        requireNonNull(sessionVariables, "sessionVariables must not be null");

        if (sessionVariables.isEmpty()) {
            return EMPTY_STRINGS;
        }

        // 1: bracket, 2: single quote, 3: double quote, 4: backtick
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int index = 0;
        int len = sessionVariables.length();
        List<String> variables = new ArrayList<>();

        for (int i = 0; i < len; ++i) {
            switch (sessionVariables.charAt(i)) {
                case '\\':
                    if (i + 1 < len) {
                        if (stack.isEmpty()) {
                            break;
                        }

                        switch (stack.peekLast()) {
                            case 2:
                            case 3:
                                // All valid escape characters
                                switch (sessionVariables.charAt(i + 1)) {
                                    case '\'':
                                    case '"':
                                    case '\\':
                                    case 'n':
                                    case 'r':
                                    case 't':
                                    case 'b':
                                    case 'f':
                                        ++i;
                                        break;
                                }
                                break;
                            default:
                                // Backtick does not support escape characters
                                break;
                        }
                    }
                    break;
                case ';':
                case ',':
                    if (stack.isEmpty()) {
                        variables.add(sessionVariables.substring(index, i).trim());
                        index = i + 1;
                    }
                    break;
                case '(':
                    if (stack.isEmpty()) {
                        stack.addLast(1);
                        break;
                    }

                    switch (stack.peekLast()) {
                        case 2:
                        case 3:
                        case 4:
                            break;
                        default:
                            stack.addLast(1);
                            break;
                    }
                    break;
                case ')':
                    if (stack.isEmpty()) {
                        // Invalid bracket, ignore
                        break;
                    }

                    if (stack.peekLast() == 1) {
                        stack.pollLast();
                    }
                    break;
                case '\'':
                    if (stack.isEmpty()) {
                        stack.addLast(2);
                        break;
                    }

                    switch (stack.peekLast()) {
                        case 2:
                            stack.pollLast();
                            break;
                        case 3:
                        case 4:
                            break;
                        default:
                            stack.addLast(2);
                            break;
                    }
                    break;
                case '"':
                    if (stack.isEmpty()) {
                        stack.addLast(3);
                        break;
                    }

                    switch (stack.peekLast()) {
                        case 3:
                            stack.pollLast();
                            break;
                        case 2:
                        case 4:
                            break;
                        default:
                            stack.addLast(3);
                            break;
                    }
                    break;
                case '`':
                    if (stack.isEmpty()) {
                        stack.addLast(4);
                        break;
                    }

                    switch (stack.peekLast()) {
                        case 4:
                            stack.pollLast();
                            break;
                        case 2:
                        case 3:
                            break;
                        default:
                            stack.addLast(4);
                            break;
                    }
                    break;
            }
        }

        variables.add(sessionVariables.substring(index).trim());

        return variables.toArray(new String[0]);
    }
    /**
     * An utility data parser for {@link Option}.
     *
     * @see MySqlConnectionFactoryProvider using this utility.
     * @since 0.8.2
     */
    static class OptionMapper {

        private final ConnectionFactoryOptions options;

        OptionMapper(ConnectionFactoryOptions options) {
            this.options = options;
        }

        Source<Object> requires(Option<?> option) {
            return Source.of(options.getRequiredValue(option));
        }

        Source<Object> optional(Option<?> option) {
            return Source.of(options.getValue(option));
        }
    }
    static class Source<T> {

        private static final Source<Object> NIL = new Source<>(null);

        @Nullable
        private final T value;

        private Source(@Nullable T value) {
            this.value = value;
        }

        Otherwise to(Consumer<? super T> consumer) {
            if (value == null) {
                return Otherwise.FALL;
            }

            consumer.accept(value);

            return Otherwise.NOOP;
        }

        <R> Source<R> as(Class<R> type) {
            if (value == null) {
                return nilSource();
            }

            if (type.isInstance(value)) {
                return new Source<>(type.cast(value));
            } else if (value instanceof String) {
                try {
                    Class<?> impl = Class.forName((String) value);

                    if (type.isAssignableFrom(impl)) {
                        return new Source<>(type.cast(impl.getDeclaredConstructor().newInstance()));
                    }
                    // Otherwise, not an implementation, convert failed.
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException("Cannot instantiate '" + value + "'", e);
                }
            }

            throw new IllegalArgumentException(toMessage(value, type.getName()));
        }

        <R> Source<R> as(Class<R> type, Function<String, R> mapping) {
            if (value == null) {
                return nilSource();
            }

            if (type.isInstance(value)) {
                return new Source<>(type.cast(value));
            } else if (value instanceof String) {
                // Type cast for check mapping result.
                return new Source<>(type.cast(mapping.apply((String) value)));
            }

            throw new IllegalArgumentException(toMessage(value, type.getTypeName()));
        }

        <R> Source<R[]> asArray(Class<R[]> arrayType, Function<String, R> mapper,
                                                       Function<String, String[]> splitter, IntFunction<R[]> generator) {
            if (value == null) {
                return nilSource();
            }

            if (arrayType.isInstance(value)) {
                return new Source<>(arrayType.cast(value));
            } else if (value instanceof String[]) {
                return new Source<>(mapArray((String[]) value, mapper, generator));
            } else if (value instanceof String) {
                String[] strings = splitter.apply((String) value);

                if (arrayType.isInstance(strings)) {
                    return new Source<>(arrayType.cast(strings));
                }

                return new Source<>(mapArray(strings, mapper, generator));
            } else if (value instanceof Collection<?>) {
                @SuppressWarnings("unchecked")
                Class<R> type = (Class<R>) arrayType.getComponentType();
                R[] array = ((Collection<?>) value).stream().map(e -> {
                    if (type.isInstance(e)) {
                        return type.cast(e);
                    } else {
                        return mapper.apply(e.toString());
                    }
                }).toArray(generator);

                return new Source<>(array);
            }

            throw new IllegalArgumentException(toMessage(value, arrayType.getTypeName()));
        }

        Source<Boolean> asBoolean() {
            if (value == null) {
                return nilSource();
            }

            if (value instanceof Boolean) {
                return new Source<>((Boolean) value);
            } else if (value instanceof String) {
                return new Source<>(Boolean.parseBoolean((String) value));
            }

            throw new IllegalArgumentException(toMessage(value, "Boolean"));
        }

        Source<Integer> asInt() {
            if (value == null) {
                return nilSource();
            }

            if (value instanceof Integer) {
                // Reduce the cost of re-boxed.
                return new Source<>((Integer) value);
            } else if (value instanceof Number) {
                return new Source<>(((Number) value).intValue());
            } else if (value instanceof String) {
                return new Source<>(Integer.parseInt((String) value));
            }

            throw new IllegalArgumentException(toMessage(value, "Integer"));
        }

        Source<CharSequence> asPassword() {
            if (value == null) {
                return nilSource();
            }

            if (value instanceof CharSequence) {
                return new Source<>((CharSequence) value);
            }

            throw new IllegalArgumentException(toMessage("REDACTED", "CharSequence"));
        }

        Source<String> asString() {
            if (value == null) {
                return nilSource();
            }

            if (value instanceof String) {
                return new Source<>((String) value);
            }

            throw new IllegalArgumentException(toMessage(value, "String"));
        }

        @SuppressWarnings("unchecked")
        void prepare(Runnable client, Runnable server, Consumer<Predicate<String>> preferred) {
            if (value == null) {
                return;
            }

            if (value instanceof Boolean) {
                if ((Boolean) value) {
                    server.run();
                } else {
                    client.run();
                }
                return;
            } else if (value instanceof Predicate<?>) {
                preferred.accept((Predicate<String>) value);
                return;
            } else if (value instanceof String) {
                String stringify = (String) value;

                if ("true".equalsIgnoreCase(stringify)) {
                    server.run();
                    return;
                } else if ("false".equalsIgnoreCase(stringify)) {
                    client.run();
                    return;
                }

                try {
                    Class<?> impl = Class.forName(stringify);

                    if (Predicate.class.isAssignableFrom(impl)) {
                        preferred.accept((Predicate<String>) impl.getDeclaredConstructor().newInstance());
                        return;
                    }
                    // Otherwise, not an implementation, convert failed.
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException("Cannot instantiate '" + value + "'", e);
                }
            }

            throw new IllegalArgumentException(toMessage(value, "Boolean or Predicate<String>"));
        }

        static Source<Object> of(@Nullable Object value) {
            if (value == null) {
                return NIL;
            }

            return new Source<>(value);
        }

        @SuppressWarnings("unchecked")
        private static <T> Source<T> nilSource() {
            return (Source<T>) NIL;
        }

        private static String toMessage(Object value, String type) {
            return "Cannot convert value " + value + " to " + type;
        }

        private static <O> O[] mapArray(String[] input, Function<String, O> mapper, IntFunction<O[]> generator) {
            O[] output = generator.apply(input.length);

            for (int i = 0; i < input.length; i++) {
                output[i] = mapper.apply(input[i]);
            }

            return output;
        }
    }

    enum Otherwise {

        NOOP {
            @Override
            void otherwise(Runnable runnable) {
                // Do nothing
            }
        },

        FALL {
            @Override
            void otherwise(Runnable runnable) {
                runnable.run();
            }
        };

        /**
         * Invoked if the previous {@link Source} outcome did not match.
         *
         * @param runnable the {@link Runnable} that should be invoked.
         */
        abstract void otherwise(Runnable runnable);
    }
}
