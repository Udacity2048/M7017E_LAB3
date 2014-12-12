package playRecGst;

import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Pipeline;

/**
 * The Class create a sound recorder pipeline
 *
 * @author Martin
 * @reviewer Baptiste
 */
public final class SoundRecorder extends Pipeline {

    //Declaration of the pipeline elements.
    final Element source = ElementFactory.make("autoaudiosrc", "autoaudiosrc");
    final Element convertor = ElementFactory.make("audioconvert", "audioconvert");
    final Element encoder = ElementFactory.make("lame", "lamemp3enc");
    final Element sink = ElementFactory.make("filesink", "filesink");

    //Declaration of the relative file path.
    private final String filePath = "tmp.mp3";

    //Constructor
    SoundRecorder() {
        
        //Call the parent constructor of the pipeline.
        super("Capture");
        
        //Parameters
        encoder.set("quality", 100);
        
        //Create the pipeline [source -> encoder -> muxer -> sink]
        addMany(source, convertor, encoder, sink);
        Pipeline.linkMany(source, convertor, encoder, sink);
        
        //Set the file folder output for filesink element.
        sink.set("location", this.filePath);
    }

    //**************************************************************************
    
    /**
     * Get the path of the captured audio file
     *
     * @return the full path of the recorded file
     */
    public String getFilePath() {
        return filePath;
    }
    
    //**************************************************************************
}
