package dev.rbd.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Exact RGBA PNG with a fast scanline filter and native zlib; no palette or quality reduction. */
public final class LosslessPng {
    private static final ExecutorService ENCODERS=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"RBD lossless PNG");t.setDaemon(true);return t;});
    private record Stripe(byte[] bytes,long adler,int length){}
    public static byte[] encode(int width,int height,int[] abgr) throws IOException {
        return encode(width,height,abgr,false);
    }
    /** Same pixels as vanilla's opaque screenshot, without its render-thread per-pixel pass. */
    public static byte[] encodeOpaque(int width,int height,int[] abgr) throws IOException {
        return encode(width,height,abgr,true);
    }
    private static byte[] encode(int width,int height,int[] abgr,boolean opaque) throws IOException {
        if(width<1||height<1||(long)width*height!=abgr.length||width>(Integer.MAX_VALUE-1)/4)
            throw new IOException("Invalid PNG dimensions");
        int parts=Math.min(height,(long)width*height>=262144?4:1);
        var jobs=new ArrayList<Future<Stripe>>();
        for(int part=0;part<parts;part++){int from=height*part/parts,to=height*(part+1)/parts;jobs.add(ENCODERS.submit(()->stripe(width,from,to,abgr,opaque)));}
        var compressed=new ByteArrayOutputStream();var zlib=new DataOutputStream(compressed);zlib.writeShort(0x7801);
        long a=1,b=0;
        try{for(var job:jobs){var stripe=job.get();zlib.write(stripe.bytes);long a2=stripe.adler&65535,b2=stripe.adler>>>16;b=(b+b2+(long)stripe.length*(a-1))%65521;a=(a+a2-1)%65521;}}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("PNG encoding interrupted",e);}
        catch(ExecutionException e){throw new IOException("PNG encoding failed",e.getCause());}
        // Each stripe ends with FULL_FLUSH (byte-aligned, empty history, BFINAL=0).
        // One final empty stored block and the combined Adler-32 finish a single standard zlib stream.
        zlib.write(new byte[]{1,0,0,(byte)255,(byte)255});zlib.writeInt((int)((b<<16)|a));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeLong(0x89504e470d0a1a0aL);
        var header=new ByteArrayOutputStream();var dimensions=new DataOutputStream(header);
        dimensions.writeInt(width);dimensions.writeInt(height);dimensions.write(new byte[]{8,6,0,0,0});
        chunk(out,"IHDR",header.toByteArray());chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);
        return bytes.toByteArray();
    }
    private static Stripe stripe(int width,int from,int to,int[] abgr,boolean opaque) throws IOException {
        byte[] raw=new byte[Math.multiplyExact(1+width*4,to-from)];
        for(int y=from;y<to;y++){
                int start=(y-from)*(1+width*4);raw[start]=1;
                int previous=0;
                for(int x=0;x<width;x++){
                    int pixel=abgr[y*width+x],offset=start+1+x*4;
                    raw[offset]=(byte)((pixel&255)-(previous&255));
                    raw[offset+1]=(byte)(((pixel>>>8)&255)-((previous>>>8)&255));
                    raw[offset+2]=(byte)(((pixel>>>16)&255)-((previous>>>16)&255));
                    raw[offset+3]=opaque?(byte)(x==0?255:0):(byte)((pixel>>>24)-(previous>>>24));previous=pixel;
                }
        }
        var adler=new Adler32();adler.update(raw);var compressed=new ByteArrayOutputStream();var deflater=new Deflater(Deflater.BEST_SPEED,true);
        try{
            deflater.setInput(raw);byte[] buffer=new byte[65536];int count;
            do{count=deflater.deflate(buffer,0,buffer.length,Deflater.FULL_FLUSH);compressed.write(buffer,0,count);}while(count==buffer.length);
        }finally{deflater.end();}
        return new Stripe(compressed.toByteArray(),adler.getValue(),raw.length);
    }
    private static void chunk(DataOutputStream out,String type,byte[] data) throws IOException {
        byte[] tag=type.getBytes(StandardCharsets.US_ASCII);var crc=new CRC32();crc.update(tag);crc.update(data);
        out.writeInt(data.length);out.write(tag);out.write(data);out.writeInt((int)crc.getValue());
    }
    private LosslessPng(){}
}
