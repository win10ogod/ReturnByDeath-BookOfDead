import java.nio.file.*;
import java.nio.channels.*;
public final class LockProbe {
    public static void main(String[] args)throws Exception{
        try(var c=FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock lock=c.tryLock();
            if(lock==null){System.out.println("BUSY");return;}
            try(lock){System.out.println("LOCKED");System.out.flush();if(args.length<2)System.in.read();}
        }
    }
}
