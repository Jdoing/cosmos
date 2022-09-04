package example.cosmos.rpc.compressor;

import example.cosmos.rpc.CompressorType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2022/9/4 18:17
 */
public class CompressorFactory {

    /**
     * The constant COMPRESSOR_MAP.
     */
    protected static final Map<CompressorType, Compressor> COMPRESSOR_MAP = new ConcurrentHashMap<>();

    static {
        COMPRESSOR_MAP.put(CompressorType.NONE, new NoneCompressor());
    }

    /**
     * Get compressor by code.
     *
     * @param code the code
     * @return the compressor
     */
    public static Compressor getCompressor(byte code) {
        return COMPRESSOR_MAP.get(CompressorType.NONE);
    }

    public static class NoneCompressor implements Compressor {
        @Override
        public byte[] compress(byte[] bytes) {
            return bytes;
        }

        @Override
        public byte[] decompress(byte[] bytes) {
            return bytes;
        }
    }
}
