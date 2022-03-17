package io.seqera.util;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public class LayerAssemblerCli {

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if( args.length != 2){
            System.out.println("source and destination arguments required");
            return;
        }
        LayerAssembler layerAssembler = LayerAssembler.newInstance(args[0], args[1]);
        layerAssembler.buildLayer();
    }
}
