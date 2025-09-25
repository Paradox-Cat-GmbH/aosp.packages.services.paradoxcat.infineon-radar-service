package com.paradoxcat.infineon.radar;

import com.paradoxcat.infineon.radar.RangeDopplerImage;

interface IRangeDopplerListener {
    oneway void onFrameReceived(in RangeDopplerImage data);
}