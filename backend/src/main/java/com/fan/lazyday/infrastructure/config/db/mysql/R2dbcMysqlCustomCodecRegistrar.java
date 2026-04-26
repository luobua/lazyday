package com.fan.lazyday.infrastructure.config.db.mysql;

//import io.asyncer.r2dbc.mysql.MySqlColumnMetadata;

import io.asyncer.r2dbc.mysql.MySqlParameter;
import io.asyncer.r2dbc.mysql.ParameterWriter;
import io.asyncer.r2dbc.mysql.api.MySqlReadableMetadata;
import io.asyncer.r2dbc.mysql.codec.Codec;
import io.asyncer.r2dbc.mysql.codec.CodecContext;
import io.asyncer.r2dbc.mysql.codec.CodecRegistry;
import io.asyncer.r2dbc.mysql.constant.MySqlType;
import io.asyncer.r2dbc.mysql.extension.CodecRegistrar;
import io.asyncer.r2dbc.mysql.internal.util.VarIntUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.codec.Json;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * <p>描述: [类型描述] </p>
 * <p>创建时间: 2025/3/19 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2025/03/19 18:06 fan 创建
 */
public class R2dbcMysqlCustomCodecRegistrar implements CodecRegistrar {


    @Override
    public void register(ByteBufAllocator allocator, CodecRegistry registry) {
        registry.addFirst(new MysqlUuidVarcharCodec(allocator));
        registry.addFirst(new PostgresqlJsonVarcharCodec(allocator));
    }
    static class PostgresqlJsonVarcharCodec implements Codec<Json> {

        private  final ByteBufAllocator allocator;

        PostgresqlJsonVarcharCodec(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public Json decode(ByteBuf byteBuf, MySqlReadableMetadata mySqlReadableMetadata, Class<?> aClass, boolean b, CodecContext codecContext) {
            String uuidStr = byteBuf.toString(StandardCharsets.UTF_8);
            return Json.of(uuidStr);
        }

        @Override
        public boolean canDecode(MySqlReadableMetadata mySqlReadableMetadata, Class<?> aClass) {
            return mySqlReadableMetadata.getType() == MySqlType.VARCHAR && aClass == Json.class;
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof Json;
        }

        @Override
        public MySqlParameter encode(Object o, CodecContext codecContext) {
            Json json = (Json) o;
            // 将 UUID 转换为字符串（如 "6d5a8e7c-9f1a-4b3c-8d6e-0f1a2b3c4d5e"）
            String uuidStr = json.toString();
            // 指定 MySqlType 为 VARCHAR，适配数据库的 VARCHAR(36) 类型
            return new StringMySqlParameter(allocator,uuidStr, codecContext);
        }
    }


    static class MysqlUuidVarcharCodec implements Codec<UUID> {

        private  final ByteBufAllocator allocator;

        MysqlUuidVarcharCodec(ByteBufAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public UUID decode(ByteBuf byteBuf, MySqlReadableMetadata mySqlReadableMetadata, Class<?> aClass, boolean b, CodecContext codecContext) {
            String uuidStr = byteBuf.toString(StandardCharsets.UTF_8);
            return UUID.fromString(uuidStr);
        }

        @Override
        public boolean canDecode(MySqlReadableMetadata mySqlReadableMetadata, Class<?> aClass) {
            return mySqlReadableMetadata.getType() == MySqlType.VARCHAR && aClass == UUID.class;
        }

        @Override
        public boolean canEncode(Object value) {
            return value instanceof UUID;
        }

        @Override
        public MySqlParameter encode(Object value, CodecContext context) {
            UUID uuid = (UUID) value;
            // 将 UUID 转换为字符串（如 "6d5a8e7c-9f1a-4b3c-8d6e-0f1a2b3c4d5e"）
            String uuidStr = uuid.toString();
            // 指定 MySqlType 为 VARCHAR，适配数据库的 VARCHAR(36) 类型
            return new StringMySqlParameter(allocator,uuidStr, context);
        }



    }
    static class StringMySqlParameter implements  MySqlParameter{

        private final ByteBufAllocator allocator;
        private final CharSequence value;
        private final CodecContext context;

        private StringMySqlParameter(ByteBufAllocator allocator, CharSequence value, CodecContext context) {
            this.allocator = allocator;
            this.value = value;
            this.context = context;
        }

        @Override
        public Publisher<ByteBuf> publishBinary(ByteBufAllocator byteBufAllocator) {
            return Mono.fromSupplier(() -> encodeCharSequence(allocator,value, context));
        }

        @Override
        public Mono<Void> publishText(ParameterWriter writer) {
            return Mono.fromRunnable(() -> writer.append(value));
        }

        @Override
        public MySqlType getType() {
            return MySqlType.VARCHAR;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StringMySqlParameter)) {
                return false;
            }

           StringMySqlParameter that = (StringMySqlParameter) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value.toString();
        }


        static ByteBuf encodeCharSequence(ByteBufAllocator allocator, CharSequence value, CodecContext context) {
            int length = value.length();

            if (length <= 0) {
                // It is zero of var int, not terminal.
                return allocator.buffer(Byte.BYTES).writeByte(0);
            }

            Charset charset = context.getClientCollation().getCharset();
            ByteBuf content = allocator.buffer();

            try {
                VarIntUtils.reserveVarInt(content);

                return VarIntUtils.setReservedVarInt(content, content.writeCharSequence(value, charset));
            } catch (Throwable e) {
                content.release();
                throw e;
            }
        }
    }
}
