package no.kabina.kaboot.scheduler;

import java.util.List;

public class LcmOutput {
    public List<LcmPair> pairs;
    public int minVal;

    public LcmOutput(List<LcmPair> pairs, int minVal) {
        this.pairs = pairs;
        this.minVal = minVal;
    }
}
