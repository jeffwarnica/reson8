package com.coherentnetworksolutions.reson8.audio.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public interface DropChannel {

    void trigger(@Min(0) @Max(100) double volume);

}