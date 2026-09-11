import dev.rbd.io.LosslessPng;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

public final class PngSelfTest {
    public static void main(String[] args)throws Exception{
        var random=new Random(20260911);int checked=0;
        for(int[] size:List.of(new int[]{1,1},new int[]{7,13},new int[]{1920,1080})){
            int w=size[0],h=size[1];int[] pixels=new int[w*h];
            for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
            pixels[0]=0x120034ff; // Distinct channels plus non-opaque alpha catches RGBA/ARGB swaps.
            var encoded=LosslessPng.encode(w,h,pixels);var decoded=ImageIO.read(new ByteArrayInputStream(encoded));
            if(decoded==null||decoded.getWidth()!=w||decoded.getHeight()!=h)throw new AssertionError("PNG dimensions changed");
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int p=pixels[y*w+x],argb=(p&0xff00ff00)|((p&255)<<16)|((p>>>16)&255);
                if(decoded.getRGB(x,y)!=argb)throw new AssertionError("PNG pixel/alpha changed at "+x+","+y);checked++;
            }
        }
        System.out.println("PngSelfTest: "+checked+" exact RGBA pixels passed, including native 1080p");
    }
}
