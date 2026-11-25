package com.example.racehorse_transport.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FileFormats {
    private FileFormats() {}

    public static boolean isPdfJpegOrPng(InputStream input) throws IOException {
        byte[] header = input.readNBytes(8);
        return Arrays.equals(header, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})
                || (header.length >= 5 && "%PDF-".equals(new String(header, 0, 5, StandardCharsets.US_ASCII)))
                || (header.length >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8
                        && header[2] == (byte) 0xff);
    }
}
