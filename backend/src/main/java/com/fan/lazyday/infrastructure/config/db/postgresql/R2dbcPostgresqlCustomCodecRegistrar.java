package com.fan.lazyday.infrastructure.config.db.postgresql;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ByteProcessor;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.r2dbc.postgresql.client.EncodedParameter;
import io.r2dbc.postgresql.codec.Codec;
import io.r2dbc.postgresql.codec.CodecRegistry;
import io.r2dbc.postgresql.extension.CodecRegistrar;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.util.Assert;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.netty.util.CharsetUtil.UTF_8;

/**
 * <p>描述: [类型描述] </p>
 * <p>创建时间: 2025/4/22 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2025/04/22 13:56 fan 创建
 */
public class R2dbcPostgresqlCustomCodecRegistrar implements CodecRegistrar {

    private static final Object EMPTY = new Object();

    enum BuiltinCodec {

        HSTORE("hstore");

        private final String name;

        BuiltinCodec(String name) {
            this.name = name;
        }

        public Codec<?> createCodec(ByteBufAllocator byteBufAllocator, int oid) {

            switch (this) {
                case HSTORE:
                    return new HStoreCodec(byteBufAllocator, oid);
                default:
                    throw new UnsupportedOperationException(String.format("Codec %s for OID %d not supported", name(), oid));
            }
        }

        public String getName() {
            return this.name;
        }

        static BuiltinCodec lookup(@Nullable String name) {

            for (BuiltinCodec codec : values()) {
                if (codec.getName().equalsIgnoreCase(name)) {
                    return codec;
                }
            }

            throw new IllegalArgumentException(String.format("Cannot determine codec for %s", name));
        }
    }

    @Override
    public Publisher<Void> register(PostgresqlConnection connection, ByteBufAllocator byteBufAllocator, CodecRegistry registry) {

        PostgresqlStatement statement = createQuery(connection);

        return statement.execute()
                .flatMap(it -> it.map((row, rowMetadata) -> {

                            Integer oid = row.get("oid", Integer.class);
                            String typname = row.get("typname", String.class);

                            BuiltinCodec lookup = BuiltinCodec.lookup(typname);
                            registry.addLast(lookup.createCodec(byteBufAllocator, oid));

                            return EMPTY;
                        })
                ).then();
    }

    private PostgresqlStatement createQuery(PostgresqlConnection connection) {
        return connection.createStatement(String.format("SELECT oid, typname FROM pg_catalog.pg_type WHERE typname IN (%s)", getPlaceholders()));
    }

    private static String getPlaceholders() {
        return Arrays.stream(BuiltinCodec.values()).map(s -> "'" + s.getName() + "'").collect(Collectors.joining(","));
    }


    static class HStoreCodec implements Codec<Map> {

        /**
         * A {@link ByteProcessor} which finds the first appearance of a specific byte.
         */
        static class IndexOfProcessor implements ByteProcessor {

            private final byte byteToFind;

            public IndexOfProcessor(byte byteToFind) {
                this.byteToFind = byteToFind;
            }

            @Override
            public boolean process(byte value) {
                return value != this.byteToFind;
            }

        }

        private final ByteBufAllocator byteBufAllocator;

        private final Class<Map> type = Map.class;

        private final int oid;

        /**
         * Create a new {@link HStoreCodec}.
         *
         * @param byteBufAllocator the type handled by this codec
         */
        HStoreCodec(ByteBufAllocator byteBufAllocator, int oid) {
            this.byteBufAllocator = Assert.requireNonNull(byteBufAllocator, "byteBufAllocator must not be null");
            this.oid = oid;
        }

        @Override
        public boolean canDecode(int dataType, Format format, Class<?> type) {
            Assert.requireNonNull(format, "format must not be null");
            Assert.requireNonNull(type, "type must not be null");

            Assert.requireNonNull(type, "type must not be null");

            return dataType == this.oid && type.isAssignableFrom(this.type);
        }

        @Override
        public boolean canEncode(Object value) {
            Assert.requireNonNull(value, "value must not be null");

            return this.type.isInstance(value);
        }

        @Override
        public boolean canEncodeNull(Class<?> type) {
            Assert.requireNonNull(type, "type must not be null");

            return this.type.isAssignableFrom(type);
        }

        @Override
        public Map<String, String> decode(ByteBuf buffer, int dataType, Format format, Class<? extends Map> type) {
            if (buffer == null) {
                return null;
            }

            if (format == Format.FORMAT_TEXT) {
                return decodeTextFormat(buffer);
            }

            return decodeBinaryFormat(buffer);
        }

        private static Map<String, String> decodeBinaryFormat(ByteBuf buffer) {
            Map<String, String> map = new LinkedHashMap<>();
            if (!buffer.isReadable()) {
                return map;
            }

            int numElements = buffer.readInt();

            for (int i = 0; i < numElements; ++i) {
                int keyLen = buffer.readInt();
                String key = buffer.readCharSequence(keyLen, UTF_8).toString();
                int valLen = buffer.readInt();
                String val = valLen == -1 ? null : buffer.readCharSequence(valLen, UTF_8).toString();

                map.put(key, val);
            }
            return map;
        }

        private static Map<String, String> decodeTextFormat(ByteBuf buffer) {
            Map<String, String> map = new LinkedHashMap<>();
            StringBuilder mutableBuffer = new StringBuilder();

            while (buffer.isReadable()) {
                String key = readString(mutableBuffer, buffer);
                if (key == null) {
                    break;
                }
                buffer.skipBytes(2); // skip '=>'
                String value;

                if ((char) peekByte(buffer) == 'N') {
                    value = null;
                    buffer.skipBytes(4);// skip 'NULL'.
                } else {
                    value = readString(mutableBuffer, buffer);
                }
                map.put(key, value);

                if (buffer.isReadable()) {
                    buffer.readByte(); // skip ','
                }

            }
            return map;
        }

        private static byte peekByte(ByteBuf buffer) {

            int readerIndex = buffer.readerIndex();
            try {
                return buffer.readByte();
            } finally {
                buffer.readerIndex(readerIndex);
            }
        }

        private static String readString(StringBuilder mutableBuffer, ByteBuf buffer) {
            mutableBuffer.setLength(0);
            int position = buffer.forEachByte(new IndexOfProcessor((byte) '"'));
            if (position > buffer.writerIndex()) {
                return null;
            }

            if (position > -1) {
                buffer.readerIndex(position + 1);
            }

            while (buffer.isReadable()) {
                char c = (char) buffer.readByte();
                if (c == '"') {
                    break;
                } else if (c == '\\') {
                    c = (char) buffer.readByte();
                }
                mutableBuffer.append(c);
            }

            String result = mutableBuffer.toString();
            mutableBuffer.setLength(0);
            return result;
        }

        @Override
        public EncodedParameter encode(Object value) {
            Assert.requireNonNull(value, "value must not be null");
            Map<?, ?> map = (Map<?, ?>) value;

            return new EncodedParameter(Format.FORMAT_BINARY, this.oid, Mono.fromSupplier(() -> {
                ByteBuf buffer = this.byteBufAllocator.buffer(4 + 10 * map.size());
                buffer.writeInt(map.size());

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String k = entry.getKey().toString();
                    byte[] bytes = k.getBytes(UTF_8);
                    buffer.writeInt(bytes.length);
                    buffer.writeBytes(bytes);

                    if (entry.getValue() == null) {
                        buffer.writeInt(-1);
                    } else {
                        String v = entry.getValue().toString();
                        bytes = v.getBytes(UTF_8);
                        buffer.writeInt(bytes.length);
                        buffer.writeBytes(bytes);
                    }
                }
                return buffer;
            }));
        }

        public EncodedParameter encode(Object value, int dataType) {
            throw new UnsupportedOperationException("Cannot encode using a generic enum codec");
        }

        public EncodedParameter encodeNull() {
            throw new UnsupportedOperationException("Cannot encode using a generic enum codec");
        }

    }
}
