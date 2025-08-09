package pshb.Utils;

import javax.imageio.spi.IIORegistry;
import com.sun.media.imageioimpl.stream.ChannelImageInputStreamSpi;
import com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi;
import com.sun.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi;

public class ImageIOSetup {
    public static void registerJAIImageIOSpis() {
        IIORegistry registry = IIORegistry.getDefaultInstance();

        // These may already be registered, but re-registering is safe
        registry.registerServiceProvider(new ChannelImageInputStreamSpi());
        registry.registerServiceProvider(new TIFFImageReaderSpi());
        registry.registerServiceProvider(new TIFFImageWriterSpi());
    }
}
