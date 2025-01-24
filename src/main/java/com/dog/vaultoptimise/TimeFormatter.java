package com.dog.vaultoptimise;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class TimeFormatter extends Formatter {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        String time = dateFormat.format(new Date(record.getMillis()));
        return "[" + time + "] [AutoSaveMod] " + record.getMessage() + "\n";
    }
}

