package com.canju12.zhuzicanju;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TikuDataTest {

    @Test
    public void parseFenLine_shouldSplitFenAndTitle() {
        TikuData data = new TikuData("test", "test.txt");
        data.setFenList(Arrays.asList(
                "5a3/4k4/4b4/9/4N4/r3CR3/3p5/2C4n1/4p4/3K4c w - - 0 1 \u5b64\u52c7\u8005",
                "9/9/9/9/9/9/9/9/9/9 w - - 0 1"
        ));

        assertEquals("5a3/4k4/4b4/9/4N4/r3CR3/3p5/2C4n1/4p4/3K4c w - - 0 1",
                data.getFENByIndex(0));
        assertEquals("\u5b64\u52c7\u8005", data.getQuestionTitle(0));
        assertEquals("9/9/9/9/9/9/9/9/9/9 w - - 0 1", data.getFENByIndex(1));
        assertEquals("", data.getQuestionTitle(1));
    }

    @Test
    public void parseFenLine_shouldIgnoreBomAtStart() {
        TikuData data = new TikuData("test", "test.txt");
        data.setFenList(Arrays.asList("\uFEFF5a3/4k4/4b4/9/4N4/r3CR3/3p5/2C4n1/4p4/3K4c w - - 0 1"));

        assertEquals("5a3/4k4/4b4/9/4N4/r3CR3/3p5/2C4n1/4p4/3K4c w - - 0 1",
                data.getFENByIndex(0));
    }
}
