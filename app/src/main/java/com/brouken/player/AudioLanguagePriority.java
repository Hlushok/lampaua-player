package com.brouken.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

final class AudioLanguagePriority {
    private static final Map<String, String> LEGACY_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("alb", "sqi"); aliases.put("arm", "hye"); aliases.put("baq", "eus");
        aliases.put("bur", "mya"); aliases.put("chi", "zho"); aliases.put("cze", "ces");
        aliases.put("dut", "nld"); aliases.put("fre", "fra"); aliases.put("geo", "kat");
        aliases.put("ger", "deu"); aliases.put("gre", "ell"); aliases.put("ice", "isl");
        aliases.put("mac", "mkd"); aliases.put("mao", "mri"); aliases.put("may", "msa");
        aliases.put("per", "fas"); aliases.put("rum", "ron"); aliases.put("slo", "slk");
        aliases.put("tib", "bod"); aliases.put("wel", "cym");
        LEGACY_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private AudioLanguagePriority() {}

    static List<String> parse(String stored) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (stored != null) {
            for (String value : stored.split(",")) {
                String normalized = normalize(value);
                if (normalized != null) result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    static String serialize(List<String> languages) {
        return String.join(",", parse(languages == null ? null : String.join(",", languages)));
    }

    static String normalize(String language) {
        if (language == null) return null;
        String value = language.trim().toLowerCase(Locale.US).replace('_', '-');
        if (value.isEmpty() || "und".equals(value) || "default".equals(value)
                || "device".equals(value)) return null;
        String base = value;
        int separator = base.indexOf('-');
        if (separator > 0) base = base.substring(0, separator);
        String alias = LEGACY_ALIASES.get(base);
        if (alias != null) return alias;
        try {
            String iso3 = Locale.forLanguageTag(value).getISO3Language();
            if (iso3 == null || iso3.isEmpty()) return null;
            alias = LEGACY_ALIASES.get(iso3.toLowerCase(Locale.US));
            return alias != null ? alias : iso3.toLowerCase(Locale.US);
        } catch (MissingResourceException error) {
            return base.length() == 3 ? base : null;
        }
    }

    static int select(List<String> preferred, List<TrackMetadata> available) {
        if (available == null || available.isEmpty()) return -1;
        for (String preference : preferred == null ? Collections.<String>emptyList() : preferred) {
            String wanted = normalize(preference);
            if (wanted == null) continue;
            for (int index = 0; index < available.size(); index++) {
                if (wanted.equals(normalize(available.get(index).language))) return index;
            }
        }
        return 0;
    }
}
