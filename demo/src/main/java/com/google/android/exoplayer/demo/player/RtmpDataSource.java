package com.google.android.exoplayer.demo.player;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.UriDataSource;

import net.butterflytv.rtmp_client.RtmpClient;

import java.io.IOException;

/**
 * Created by faraklit on 08.01.2016.
 */
public class RtmpDataSource implements UriDataSource {
    private static final Object lock = new Object();
    private final RtmpClient rtmpClient;
    private String uri;
    boolean opened = false;

    public RtmpDataSource() {
        rtmpClient = new RtmpClient();
    }
    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri.toString();
        return C.LENGTH_UNBOUNDED;
    }

    @Override
    public void close() throws IOException {
        synchronized (lock){
            rtmpClient.close();
            opened = false;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        synchronized (lock){
            if(!opened){
                opened = rtmpClient.open(uri, false, true) >= 0;
            }
            if(opened){
                return rtmpClient.read(buffer, offset, readLength);
            }
            throw new IOException("Couldn't open stream.");
        }
    }
}
