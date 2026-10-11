package dev.khanh.config;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Portable, bounded settings codes; no server, URL, executable action or license data. */
public final class ConfigShareCodec {
    public static final String PREFIX="KHANH:";
    private static final String OLD_MODERN_PREFIX="ZF2:";
    private static final String LEGACY_PREFIX="ZEROFIRE3-";
    private static final int LIMIT=262144, CODE_LIMIT=131072;
    private static final Set<String> FIELDS=Set.of("guiVersion","compact","modules","options","keybinds","actionKeybinds","hudLayout");
    private ConfigShareCodec() { }

    public static String encode(JsonObject settings) {
        JsonObject clean=validate(settings);
        byte[] raw=new Gson().toJson(clean).getBytes(StandardCharsets.UTF_8);
        if (raw.length>LIMIT) throw new IllegalArgumentException("Settings are too large");
        Deflater compressor=new Deflater(9,true);
        try {
            compressor.setInput(raw);compressor.finish();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
            while (!compressor.finished()) out.write(buffer,0,compressor.deflate(buffer));
            String code=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
            if (code.length()>CODE_LIMIT) throw new IllegalArgumentException("Config code is too large");
            return code;
        } finally { compressor.end(); }
    }

    public static JsonObject decode(String input) {
        if (input==null || input.length()>CODE_LIMIT) throw new IllegalArgumentException("Config code is too large");
        String code=input.strip(),json;
        if (code.startsWith("ZEROFIRE2-")) json=code.substring(10);
        else if (code.startsWith(PREFIX) || code.startsWith(OLD_MODERN_PREFIX) || code.startsWith(LEGACY_PREFIX)) {
            boolean modern=code.startsWith(PREFIX) || code.startsWith(OLD_MODERN_PREFIX);
            int prefixLength = code.startsWith(PREFIX) ? PREFIX.length()
                : code.startsWith(OLD_MODERN_PREFIX) ? OLD_MODERN_PREFIX.length() : LEGACY_PREFIX.length();
            String payload=code.substring(prefixLength)
                .replace(" ", "").replace("\n", "").replace("\r", "");
            Inflater inflater=new Inflater(modern);
            try {
                inflater.setInput(Base64.getUrlDecoder().decode(payload));
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
                while (!inflater.finished()) {
                    int count=inflater.inflate(buffer);
                    if (out.size()+count>LIMIT) throw new IllegalArgumentException("Expanded config is too large");
                    out.write(buffer,0,count);
                    if (count==0 && !inflater.finished()) throw new IllegalArgumentException("Config code is incomplete");
                }
                if (inflater.getRemaining()!=0) throw new IllegalArgumentException("Unexpected trailing config data");
                json=out.toString(StandardCharsets.UTF_8);
            } catch (java.util.zip.DataFormatException error) {
                throw new IllegalArgumentException("Config code is damaged",error);
            } finally { inflater.end(); }
        } else throw new IllegalArgumentException("Clipboard does not contain a Khanh Client config code");
        JsonElement parsed=JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Config is not a settings object");
        return validate(parsed.getAsJsonObject());
    }

    private static JsonObject validate(JsonObject root) {
        if (!root.has("modules") || !root.get("modules").isJsonObject()
            || !root.has("options") || !root.get("options").isJsonObject())
            throw new IllegalArgumentException("Config settings are incomplete");
        JsonObject clean=new JsonObject();
        for (String field:FIELDS) if (root.has(field)) clean.add(field,root.get(field).deepCopy());
        if (clean.has("guiVersion")) integer(clean.get("guiVersion"),1,10000);
        if (clean.has("compact") && !(clean.get("compact").isJsonPrimitive() && clean.get("compact").getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("Invalid compact flag");
        for (var entry:clean.getAsJsonObject("modules").entrySet()) {
            name(entry.getKey());primitive(entry.getValue());
            if (!entry.getValue().getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid module toggle");
        }
        for (String section:new String[]{"options","actionKeybinds","hudLayout"}) if (clean.has(section)) {
            if (!clean.get(section).isJsonObject()) throw new IllegalArgumentException("Invalid "+section);
            for (var module:clean.getAsJsonObject(section).entrySet()) {
                name(module.getKey());
                if (!module.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid settings group");
                for (var option:module.getValue().getAsJsonObject().entrySet()) {
                    name(option.getKey());primitive(option.getValue());
                    if (section.equals("actionKeybinds")) integer(option.getValue(),-1,348);
                    if (section.equals("hudLayout")) integer(option.getValue(),-100000,100000);
                }
            }
        }
        if (clean.has("keybinds")) {
            if (!clean.get("keybinds").isJsonObject()) throw new IllegalArgumentException("Invalid keybinds");
            for (var key:clean.getAsJsonObject("keybinds").entrySet()) {name(key.getKey());integer(key.getValue(),-1,348);}
        }
        return clean;
    }
    private static void name(String name) {
        if (!name.matches("[A-Za-z0-9_:-]{1,80}")) throw new IllegalArgumentException("Invalid settings identifier");
    }
    private static void primitive(JsonElement e) {
        if (!e.isJsonPrimitive()) throw new IllegalArgumentException("Invalid settings value");
        JsonPrimitive p=e.getAsJsonPrimitive();
        if (p.isNumber()&&!Double.isFinite(p.getAsDouble())) throw new IllegalArgumentException("Invalid number");
        if (p.isString()&&p.getAsString().length()>2048) throw new IllegalArgumentException("Settings text is too large");
    }
    private static void integer(JsonElement e,int min,int max) {
        primitive(e);
        if (!e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid key or position");
        double n=e.getAsDouble();
        if (n!=Math.rint(n)||n<min||n>max) throw new IllegalArgumentException("Key or position is out of range");
    }
}
