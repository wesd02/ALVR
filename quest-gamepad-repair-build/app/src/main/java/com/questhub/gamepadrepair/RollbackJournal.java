package com.questhub.gamepadrepair;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class RollbackJournal {
    private static final String PREFS = "repair_rollback";
    private static final String KEY = "entries_v1";
    private static final int MAX_ENTRIES = 32;

    private final SharedPreferences prefs;

    RollbackJournal(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<RollbackCodec.Entry> load() {
        return new ArrayList<>(RollbackCodec.decode(prefs.getString(KEY, "")));
    }

    synchronized void append(RollbackCodec.Entry entry) {
        if (!RollbackCodec.isSafe(entry)) throw new IllegalArgumentException("unsafe rollback entry");
        List<RollbackCodec.Entry> entries = load();
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        save(entries);
    }

    synchronized void save(List<RollbackCodec.Entry> entries) {
        prefs.edit().putString(KEY, RollbackCodec.encode(entries)).apply();
    }

    synchronized void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
