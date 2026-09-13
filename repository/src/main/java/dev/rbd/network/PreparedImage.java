package dev.rbd.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;

/** Validated immutable image data. Construction is safe on an image worker. */
public record PreparedImage(int width,int height,String png) {
    public static PreparedImage validate(int width,int height,String png) throws IOException {
        byte[] data=Base64.getDecoder().decode(png);
        if(width<1||height<1||(long)width*height>64000000L||data.length<24||ByteBuffer.wrap(data).getLong()!=0x89504E470D0A1A0AL||ByteBuffer.wrap(data).getInt(16)!=width||ByteBuffer.wrap(data).getInt(20)!=height)
            throw new IOException("Invalid rendered memory image");
        return new PreparedImage(width,height,png);
    }
}
