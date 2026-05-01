package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.signal.SignalBucket;

public interface InputChannelFactory {

    InputChannel buildChannel(SignalBucket signalBucket);

}