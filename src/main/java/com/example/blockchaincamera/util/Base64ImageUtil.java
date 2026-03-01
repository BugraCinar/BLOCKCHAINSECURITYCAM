package com.example.blockchaincamera.util;

import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Base64ImageUtil {
    private Base64ImageUtil() {}

    public enum Encoding {
        NONE,
        DEFLATE
    }

   
    public static String encode(byte[] data, Encoding encoding, boolean unpadded) {
        if (data == null) return null;
        byte[] toEncode = switch (encoding) {
            case NONE -> data;
            case DEFLATE -> deflate(data);
        };
        Base64.Encoder enc = unpadded ? Base64.getEncoder().withoutPadding() : Base64.getEncoder();
        return enc.encodeToString(toEncode);
    }


    public static byte[] decode(String base64, Encoding encoding) {
        if (base64 == null) return null;
       
        String fixed = padBase64IfNeeded(base64);
        byte[] decoded = Base64.getDecoder().decode(fixed);
        return switch (encoding) {
            case NONE -> decoded;
            case DEFLATE -> inflate(decoded);
        };
    }

    public static Encoding fromString(String v) {
        if (v == null || v.isBlank()) return Encoding.NONE;
        try {
            return Encoding.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException iae) {
            return Encoding.NONE;
        }
    }

    private static String padBase64IfNeeded(String s) {
        int len = s.length();
        int mod = len % 4;
        if (mod == 0) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < 4 - mod; i++) sb.append('=');
        return sb.toString();
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        byte[] buffer = new byte[Math.max(256, input.length)];
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return input; 
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] input) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        byte[] buffer = new byte[Math.max(256, input.length * 4)];
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return input; 
        } finally {
            inflater.end();
        }
    }
}
