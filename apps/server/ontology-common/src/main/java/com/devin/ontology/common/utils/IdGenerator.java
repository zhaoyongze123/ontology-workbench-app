package com.devin.ontology.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;

public class IdGenerator {

    /**
     * 生成id.
     *
     * @return id
     */
    public static BigInteger generateKey() {
        Snowflake snowflake = IdUtil.getSnowflake(1, 1);
        long id = snowflake.nextId();
        return BigInteger.valueOf(id);
    }

    /**
     * 生成uuid.
     *
     * @return uuid
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成authToken.
     *
     * @return authToken
     */
    public static String generateAuthToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成accessToken.
     *
     * @return accessToken
     */
    public static String generateAccessToken() {
        return CodeGenerator.random(20, false);
    }

    /**
     * BigInteger转UUID.
     *
     * @param id id
     * @return UUID
     */
    public static UUID convertBigIntegerToUUID(final BigInteger id) {
        // Convert BigInteger to byte array
        byte[] bytes = id.toByteArray();
        // Pad byte array to 16 bytes
        byte[] padded = new byte[16];
        int length = Math.min(bytes.length, 16);
        System.arraycopy(bytes, 0, padded, 16 - length, length);
        // Create UUID from byte array
        ByteBuffer buffer = ByteBuffer.wrap(padded);
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * UUID转BigInteger.
     *
     * @param uuidStr uuid字符串
     * @return UUID UUID
     */
    public static BigInteger convertUUIDToBigInteger(final String uuidStr) {
        UUID uuid = UUID.fromString(uuidStr);
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        byte[] bytes = byteBuffer.array();
        return new BigInteger(bytes);
    }
}
